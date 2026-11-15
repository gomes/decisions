package io.github.gomes.decisions.provider.local;

import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.model.Option;
import io.github.gomes.decisions.model.Question;
import io.github.gomes.decisions.model.QuestionType;
import io.github.gomes.decisions.provider.Answers;
import io.github.gomes.decisions.spi.Capabilities;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline, deterministic provider based on word overlap. It is NOT a decision model: it exists so the
 * app, the CLI and the HTTP gateway can be exercised without API keys, and as a baseline in comparisons.
 */
public class LocalHeuristicProvider implements DecisionProvider {

    private static final double TEMPERATURE = 0.35;
    /** English + Portuguese stopwords (accents are stripped before lookup). */
    private static final Set<String> STOPWORDS = Set.copyOf(java.util.List.of(
            "a", "an", "the", "of", "to", "in", "on", "and", "or", "is", "are", "be", "it", "this", "that",
            "for", "with", "as", "at", "by", "from", "about", "my", "i", "o", "os", "de", "do", "da", "dos",
            "das", "e", "ou", "um", "uma", "em", "no", "na", "para", "com", "por", "que", "se", "meu", "minha"));

    @Override public String id() { return "local"; }
    @Override public String displayName() { return "Local heuristic (offline baseline)"; }
    @Override public String defaultModel() { return "keyword-overlap-v1"; }
    @Override public Capabilities capabilities() { return new Capabilities(10_000, true, true); }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        validate(request);
        long start = System.nanoTime();
        Set<String> input = tokens(request.textWithData());
        List<Answer> answers = new ArrayList<>();
        for (Question q : request.questions()) {
            answers.add(answer(q, input));
        }
        long latency = (System.nanoTime() - start) / 1_000_000;
        String model = request.model() != null ? request.model() : defaultModel();
        return new DecisionResult(id(), model, answers, latency, null, null);
    }

    private Answer answer(Question q, Set<String> input) {
        if (q.type() == QuestionType.YES_NO) {
            double yes = overlap(input, tokens(q.trueWhen() != null ? q.trueWhen() : q.instructions()));
            double no = q.falseWhen() != null ? overlap(input, tokens(q.falseWhen())) : 0.15;
            Map<String, Double> p = softmax(Map.of("true", yes, "false", no));
            double pTrue = p.get("true");
            return new Answer(q.name(), q.type(), String.valueOf(pTrue >= 0.5), pTrue, null, null,
                    Math.round(Math.abs(2 * pTrue - 1) * 10_000) / 10_000.0);
        }
        Map<String, Double> raw = new LinkedHashMap<>();
        for (Option o : q.options()) {
            raw.put(o.value(), overlap(input, tokens(o.value() + " " + o.description())));
        }
        Map<String, Double> probs = softmax(raw);
        String best = probs.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        Double score = q.type() == QuestionType.SCORE ? Answers.expectedIndex(q.options(), probs) : null;
        return new Answer(q.name(), q.type(), best, null, score, probs, Answers.margin(probs));
    }

    private static double overlap(Set<String> input, Set<String> target) {
        if (target.isEmpty()) return 0;
        int hits = 0;
        for (String t : target) {
            if (input.contains(t)) hits++;
        }
        return (double) hits / target.size();
    }

    private static Map<String, Double> softmax(Map<String, Double> scores) {
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        Map<String, Double> exp = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            double v = Math.exp((e.getValue() - max) / TEMPERATURE);
            exp.put(e.getKey(), v);
            sum += v;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : exp.entrySet()) {
            out.put(e.getKey(), Math.round(e.getValue() / sum * 10_000) / 10_000.0);
        }
        return out;
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        String norm = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (String t : norm.split("[^a-z0-9]+")) {
            if (t.length() > 1 && !STOPWORDS.contains(t)) {
                out.add(t.length() > 5 ? t.substring(0, 5) : t); // crude stemming
            }
        }
        return out;
    }
}
