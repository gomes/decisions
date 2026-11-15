package io.github.gomes.decisions.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.Option;
import io.github.gomes.decisions.model.Question;
import io.github.gomes.decisions.provider.Answers;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.spi.Capabilities;
import io.github.gomes.decisions.spi.DecisionException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Decisions API ({@code POST /v1/decisions}, model {@code gpt-6-luna}), also served by Azure OpenAI
 * in Microsoft Foundry at {@code https://<resource>.openai.azure.com/openai/v1/decisions}.
 *
 * <pre>{@code
 * {
 *   "model": "gpt-6-luna",
 *   "input": [ {"type": "input_text", "text": "..."},
 *              {"type": "input_json", "json": {...}},
 *              {"type": "input_image", "image_url": "https://..."} ],
 *   "questions": [
 *     {"type": "predicate", "name": "damaged", "instructions": "..."},
 *     {"type": "choice", "name": "queue", "instructions": "...",
 *      "choices": [{"value": "billing", "description": "Payments and refunds"}]},
 *     {"type": "score", "name": "severity", "instructions": "...",
 *      "levels": [{"value": "low", "description": "..."}, {"value": "high", "description": "..."}]}
 *   ]
 * }
 * }</pre>
 *
 * Answers come back as an {@code answers} array, in question order, each carrying its {@code name}.
 */
public class OpenAiDecisionsProvider extends HttpDecisionProvider {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final String DEFAULT_MODEL = "gpt-6-luna";
    public static final Capabilities CAPABILITIES = new Capabilities(400, true, true);

    public OpenAiDecisionsProvider(String id, String displayName, URI endpoint, Map<String, String> headers,
                                   String defaultModel, JsonHttp http) {
        super(id, displayName, endpoint, headers, defaultModel, CAPABILITIES, http);
    }

    public static OpenAiDecisionsProvider openAi(String apiKey, String baseUrl, String model, JsonHttp http) {
        return new OpenAiDecisionsProvider("openai", "OpenAI Decisions",
                URI.create(trimSlash(baseUrl == null ? DEFAULT_BASE_URL : baseUrl) + "/v1/decisions"),
                Map.of("Authorization", "Bearer " + apiKey),
                model == null ? DEFAULT_MODEL : model, http);
    }

    /** Azure OpenAI v1 API. {@code deployment} is the name you gave the gpt-6-luna deployment. */
    public static OpenAiDecisionsProvider azure(String endpoint, String apiKey, String deployment, JsonHttp http) {
        return new OpenAiDecisionsProvider("azure-openai", "Azure OpenAI Decisions (Foundry)",
                URI.create(trimSlash(endpoint) + "/openai/v1/decisions"),
                Map.of("api-key", apiKey),
                deployment == null ? DEFAULT_MODEL : deployment, http);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JsonHttp.MAPPER.createObjectNode();
        body.put("model", model);

        ArrayNode input = body.putArray("input");
        if (request.input() != null && !request.input().isBlank()) {
            input.addObject().put("type", "input_text").put("text", request.input());
        }
        if (request.hasData()) {
            input.addObject().put("type", "input_json").set("json", request.data());
        }
        for (String img : request.images()) {
            input.addObject().put("type", "input_image").put("image_url", img);
        }

        ArrayNode qs = body.putArray("questions");
        for (Question q : request.questions()) {
            ObjectNode n = qs.addObject();
            n.put("type", switch (q.type()) {
                case YES_NO -> "predicate";
                case CHOICE -> "choice";
                case SCORE -> "score";
            });
            n.put("name", q.name());
            n.put("instructions", instructions(q));
            if (!q.options().isEmpty()) {
                ArrayNode opts = n.putArray(q.type() == io.github.gomes.decisions.model.QuestionType.SCORE
                        ? "levels" : "choices");
                for (Option o : q.options()) {
                    ObjectNode on = opts.addObject().put("value", o.value());
                    if (!o.description().isBlank()) on.put("description", o.description());
                }
            }
        }
        return body;
    }

    /** Predicates have no separate true/false criteria, so they are folded into the instructions. */
    private static String instructions(Question q) {
        StringBuilder sb = new StringBuilder(q.instructions());
        if (q.trueWhen() != null) sb.append("\nTrue when: ").append(q.trueWhen());
        if (q.falseWhen() != null) sb.append("\nFalse when: ").append(q.falseWhen());
        return sb.toString();
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        JsonNode answers = response.path("answers");
        if (answers.isObject()) {
            // Tolerate a name-keyed map as well.
            List<Answer> out = new ArrayList<>();
            for (Question q : request.questions()) {
                out.add(Answers.parse(id, q, answers.get(q.name())));
            }
            return out;
        }
        if (!answers.isArray()) {
            throw new DecisionException(id, "response has no 'answers' array: " + response);
        }
        List<Answer> out = new ArrayList<>();
        List<Question> questions = request.questions();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            JsonNode match = null;
            for (JsonNode a : answers) {
                if (q.name().equals(a.path("name").asText(null))) {
                    match = a;
                    break;
                }
            }
            if (match == null && i < answers.size() && !answers.get(i).has("name")) {
                match = answers.get(i); // answers are documented to be in question order
            }
            out.add(Answers.parse(id, q, match));
        }
        return out;
    }
}
