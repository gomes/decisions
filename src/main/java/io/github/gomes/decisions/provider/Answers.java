package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.domain.QuestionType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tolerant parsing of answer objects. Decision APIs share the same concepts but differ in field names
 * (e.g. {@code noul} vs {@code probability}, {@code choice} vs {@code value}, probabilities as a map or
 * as a list), so the parser accepts all known spellings.
 */
public final class Answers {

    private Answers() {
    }

    public static Answer parse(String provider, Question q, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new DecisionException(provider, "missing answer for question '" + q.name() + "'");
        }
        if ("refusal".equals(node.path("type").asString(null))) {
            return Answer.refusal(q.name(), q.type());
        }
        return switch (q.type()) {
            case YES_NO -> yesNo(provider, q, node);
            case CHOICE -> categorical(provider, q, node, false);
            case SCORE -> categorical(provider, q, node, true);
        };
    }

    private static Answer yesNo(String provider, Question q, JsonNode node) {
        Double p = null;
        if (node.isNumber()) {
            p = node.asDouble();
        } else {
            for (String f : List.of("noul", "probability", "predicate", "p_true", "yes", "true")) {
                p = number(node, f);
                if (p != null) break;
            }
            if (p == null) {
                Map<String, Double> m = probabilityMap(node.path("probabilities"));
                p = m.containsKey("true") ? m.get("true") : m.get("yes");
            }
            if (p == null && node.path("value").isBoolean()) {
                p = node.get("value").asBoolean() ? 1.0 : 0.0;
            }
        }
        if (p == null) {
            throw new DecisionException(provider, "could not read yes/no probability for '" + q.name() + "': " + node);
        }
        Double confidence = number(node, "confidence");
        if (confidence == null) {
            confidence = round(Math.abs(2 * p - 1));
        }
        return new Answer(q.name(), QuestionType.YES_NO, String.valueOf(p >= 0.5), p, null, null, confidence);
    }

    private static Answer categorical(String provider, Question q, JsonNode node, boolean isScore) {
        Map<String, Double> probs = probabilityMap(node.path("probabilities"));
        if (probs.isEmpty()) {
            probs = probabilityMap(node.path("scores"));
        }
        // Level probabilities may also come as a bare array aligned with the options.
        JsonNode arr = node.path("probabilities");
        if (probs.isEmpty() && arr.isArray() && arr.size() == q.options().size() && arr.get(0).isNumber()) {
            for (int i = 0; i < arr.size(); i++) {
                probs.put(q.options().get(i).value(), arr.get(i).asDouble());
            }
        }

        probs = byOptionValue(q.options(), probs);

        String value = firstString(node, isScore
                ? List.of("level", "label", "value", "choice", "answer")
                : List.of("choice", "value", "label", "answer", "selected"));
        Double score = isScore ? number(node, "score") : null;

        value = resolveOption(q.options(), value);
        if ((value == null || !isOption(q.options(), value)) && !probs.isEmpty()) {
            value = probs.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        }
        if (value == null && isScore && score != null) {
            int idx = (int) Math.round(Math.max(0, Math.min(q.options().size() - 1, score)));
            value = q.options().get(idx).value();
        }
        if (value == null) {
            throw new DecisionException(provider, "could not read answer for '" + q.name() + "': " + node);
        }
        if (isScore && score == null && !probs.isEmpty()) {
            score = expectedIndex(q.options(), probs);
        }

        Double confidence = number(node, "confidence");
        if (confidence == null && !probs.isEmpty()) {
            confidence = margin(probs);
        }
        return new Answer(q.name(), q.type(), value, null, score, probs.isEmpty() ? null : probs, confidence);
    }

    /**
     * When every key is a level/option index ({@code "0"}, {@code "1"}, ...) instead of an option value, as
     * Gemini does for score questions, re-key the map by option value.
     */
    public static Map<String, Double> byOptionValue(List<Option> options, Map<String, Double> probs) {
        if (probs.isEmpty() || options.stream().anyMatch(o -> probs.containsKey(o.value()))) {
            return probs;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : probs.entrySet()) {
            int idx;
            try {
                idx = Integer.parseInt(e.getKey().trim());
            } catch (NumberFormatException notAnIndex) {
                return probs;
            }
            if (idx < 0 || idx >= options.size()) {
                return probs;
            }
            out.put(options.get(idx).value(), e.getValue());
        }
        return out;
    }

    /** Maps an index ("2") or a sent label ("high: ...") back to the option value; otherwise returns it as is. */
    static String resolveOption(List<Option> options, String raw) {
        if (raw == null || isOption(options, raw)) {
            return raw;
        }
        String trimmed = raw.trim();
        if (trimmed.matches("\\d+")) {
            int idx = Integer.parseInt(trimmed);
            if (idx < options.size()) return options.get(idx).value();
        }
        for (Option o : options) {
            if (trimmed.startsWith(o.value() + ":")) return o.value();
        }
        return raw;
    }

    private static boolean isOption(List<Option> options, String value) {
        return options.stream().anyMatch(o -> o.value().equals(value));
    }

    /** Accepts {@code {"a":0.7,"b":0.3}} or {@code [{"value":"a","probability":0.7}, ...]}. */
    public static Map<String, Double> probabilityMap(JsonNode node) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getValue().isNumber()) {
                    out.put(e.getKey(), e.getValue().asDouble());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isObject()) continue;
                String key = firstString(item, List.of("value", "label", "choice", "level", "name", "option"));
                Double p = number(item, "probability");
                if (p == null) p = number(item, "p");
                if (key != null && p != null) {
                    out.put(key, p);
                }
            }
        }
        return out;
    }

    public static double expectedIndex(List<Option> levels, Map<String, Double> probs) {
        double total = 0;
        double weighted = 0;
        for (int i = 0; i < levels.size(); i++) {
            double p = probs.getOrDefault(levels.get(i).value(), 0.0);
            weighted += i * p;
            total += p;
        }
        return total == 0 ? 0 : round(weighted / total);
    }

    /** Difference between the two largest probabilities (1.0 = certain, 0 = tie). */
    public static double margin(Map<String, Double> probs) {
        List<Double> sorted = new ArrayList<>(probs.values());
        sorted.sort(Comparator.reverseOrder());
        return round(sorted.size() == 1 ? sorted.get(0) : sorted.get(0) - sorted.get(1));
    }

    public static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asDouble() : null;
    }

    private static String firstString(JsonNode node, List<String> fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isString()) return v.asString();
        }
        return null;
    }
}
