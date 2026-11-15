package io.github.gomes.decisions.provider.systemone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.Option;
import io.github.gomes.decisions.model.Question;
import io.github.gomes.decisions.provider.Answers;

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
 */
public final class SystemOneCodec {

    private SystemOneCodec() {
    }

    public static ObjectNode questions(List<Question> questions) {
        ObjectNode out = JsonHttp.MAPPER.createObjectNode();
        for (Question q : questions) {
            ObjectNode n = out.putObject(q.name());
            n.put("type", switch (q.type()) {
                case YES_NO -> "noul";
                case CHOICE -> "choice";
                case SCORE -> "score";
            });
            n.put("instructions", q.instructions());
            switch (q.type()) {
                case YES_NO -> {
                    if (q.trueWhen() != null || q.falseWhen() != null) {
                        ObjectNode c = n.putObject("criteria");
                        if (q.trueWhen() != null) c.put("true", q.trueWhen());
                        if (q.falseWhen() != null) c.put("false", q.falseWhen());
                    }
                }
                case CHOICE, SCORE -> {
                    // Insertion order is preserved: for score questions this is lowest -> highest.
                    ObjectNode c = n.putObject("criteria");
                    for (Option o : q.options()) {
                        c.put(o.value(), o.description().isBlank() ? o.value() : o.description());
                    }
                }
            }
        }
        return out;
    }

    /** Text when there is text; the raw JSON object when only structured data was given. */
    public static JsonNode state(DecisionRequest request) {
        boolean hasText = request.input() != null && !request.input().isBlank();
        if (!hasText && request.hasData()) {
            return request.data();
        }
        return JsonHttp.MAPPER.getNodeFactory().textNode(request.textWithData());
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
