package io.github.gomes.decisions.provider.systemone;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.domain.QuestionType;
import io.github.gomes.decisions.provider.Answers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Encoding shared by the "System One" family (TypeSafe Jev, Microsoft-Decision-1) and Gemini Decisions,
 * which all key questions by name and use {@code noul | choice | score} types:
 *
 * <pre>{@code
 * "questions": {
 *   "is_billing": { "type": "noul",   "instructions": "...", "criteria": {"true": "...", "false": "..."} },
 *   "queue":      { "type": "choice", "instructions": "...", "criteria": {"billing": "...", "tech": "..."} },
 *   "severity":   { "type": "score",  "instructions": "...", "criteria": {"low": "...", "high": "..."} }
 * }
 * }</pre>
 *
 * Answers come back under {@code answers.<name>} (or at the top level, keyed by name).
 *
 * <p>Gemini differs only for score questions: {@code criteria} is a list of level labels, lowest first, and
 * level probabilities come back keyed by level index ({@code "0"}, {@code "1"}, ...) with a {@code legend}.
 */
public final class SystemOneCodec {

    private static final JsonMapper JSON = JsonMapper.shared();

    private SystemOneCodec() {
    }

    public static ObjectNode questions(List<Question> questions) {
        return questions(questions, false);
    }

    /**
     * @param scoreLevelsAsList Gemini takes score levels as an ordered list of strings
     *                          ({@code ["low: ...", "high: ..."]}) instead of a name-keyed object
     */
    public static ObjectNode questions(List<Question> questions, boolean scoreLevelsAsList) {
        ObjectNode out = JSON.createObjectNode();
        for (Question q : questions) {
            ObjectNode n = out.putObject(q.name());
            n.put("type", switch (q.type()) {
                case YES_NO -> "noul";
                case CHOICE -> "choice";
                case SCORE -> "score";
            });
            n.put("instructions", q.instructions());
            if (q.type() == QuestionType.SCORE && scoreLevelsAsList) {
                var levels = n.putArray("criteria");
                for (Option o : q.options()) {
                    levels.add(o.description().isBlank() ? o.value() : o.value() + ": " + o.description());
                }
            } else if (q.type().hasOptions()) {
                // Insertion order is preserved: for score questions this is lowest -> highest.
                ObjectNode c = n.putObject("criteria");
                for (Option o : q.options()) {
                    c.put(o.value(), o.description().isBlank() ? o.value() : o.description());
                }
            } else if (q.trueWhen() != null || q.falseWhen() != null) {
                ObjectNode c = n.putObject("criteria");
                if (q.trueWhen() != null) c.put("true", q.trueWhen());
                if (q.falseWhen() != null) c.put("false", q.falseWhen());
            }
        }
        return out;
    }

    /** Text when there is text; the raw JSON object when only structured data was given. */
    public static JsonNode state(DecisionRequest request) {
        if (!request.hasText() && request.hasData()) {
            return request.data();
        }
        return JSON.getNodeFactory().stringNode(request.textWithData());
    }

    public static List<Answer> answers(String provider, DecisionRequest request, JsonNode response) {
        JsonNode answers = response.has("answers") ? response.get("answers") : response;
        List<Answer> out = new ArrayList<>();
        for (Question q : request.questions()) {
            out.add(Answers.parse(provider, q, answers.get(q.name())));
        }
        return out;
    }
}
