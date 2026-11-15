package io.github.gomes.decisions.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.Option;
import io.github.gomes.decisions.model.Question;
import io.github.gomes.decisions.model.QuestionType;
import io.github.gomes.decisions.spi.DecisionException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
                JsonNode v = node.get(f);
                if (v != null && v.isNumber()) {
                    p = v.asDouble();
                    break;
                }
            }
            if (p == null) {
                JsonNode probs = node.path("probabilities");
                Map<String, Double> m = probabilityMap(probs);
                p = firstNonNull(m.get("true"), m.get("yes"));
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
            confidence = Math.abs(2 * p - 1);
        }
        return new Answer(q.name(), QuestionType.YES_NO, String.valueOf(p >= 0.5), p, null, null, confidence);
    }

    private static Answer categorical(String provider, Question q, JsonNode node, boolean isScore) {
        Map<String, Double> probs = probabilityMap(node.path("probabilities"));
        if (probs.isEmpty()) {
            probs = probabilityMap(node.path("scores"));
        }
        // Score level probabilities may come as a bare array aligned with the levels.
        if (probs.isEmpty() && node.path("probabilities").isArray()) {
            JsonNode arr = node.path("probabilities");
            if (arr.size() == q.options().size() && arr.get(0) != null && arr.get(0).isNumber()) {
                for (int i = 0; i < arr.size(); i++) {
                    probs.put(q.options().get(i).value(), arr.get(i).asDouble());
                }
            }
        }

        String value = null;
        for (String f : isScore ? List.of("level", "label", "value", "choice", "answer")
                : List.of("choice", "value", "label", "answer", "selected")) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual()) {
                value = v.asText();
                break;
            }
        }

        Double score = isScore ? number(node, "score") : null;

        if (value == null && !probs.isEmpty()) {
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

    /** Accepts {@code {"a":0.7,"b":0.3}} or {@code [{"value":"a","probability":0.7}, ...]}. */
    public static Map<String, Double> probabilityMap(JsonNode node) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isNumber()) {
                    out.put(e.getKey(), e.getValue().asDouble());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isObject()) continue;
                String key = firstText(item, "value", "label", "choice", "level", "name", "option");
                Double p = firstNonNull(number(item, "probability"), number(item, "p"), number(item, "score"));
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
        return total == 0 ? 0 : weighted / total;
    }

    /** Difference between the two largest probabilities (1.0 = certain, 0 = tie). */
    public static double margin(Map<String, Double> probs) {
        List<Double> sorted = new ArrayList<>(probs.values());
        sorted.sort(Comparator.reverseOrder());
        double m = sorted.size() == 1 ? sorted.get(0) : sorted.get(0) - sorted.get(1);
        return Math.round(m * 10_000) / 10_000.0;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asDouble() : null;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual()) return v.asText();
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
