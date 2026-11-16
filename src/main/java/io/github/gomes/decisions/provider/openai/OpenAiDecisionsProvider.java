package io.github.gomes.decisions.provider.openai;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.domain.QuestionType;
import io.github.gomes.decisions.provider.Answers;
import io.github.gomes.decisions.provider.Capabilities;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneCodec;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

    public OpenAiDecisionsProvider(String id, URI endpoint, Map<String, String> headers, String defaultModel,
                                   RestClient restClient) {
        super(id, endpoint, headers, defaultModel, CAPABILITIES, restClient);
    }

    public static OpenAiDecisionsProvider openAi(String apiKey, String baseUrl, String model, RestClient restClient) {
        return new OpenAiDecisionsProvider("openai",
                URI.create(trimSlash(isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl) + "/v1/decisions"),
                Map.of("Authorization", "Bearer " + apiKey),
                isBlank(model) ? DEFAULT_MODEL : model, restClient);
    }

    /** Azure OpenAI v1 API. {@code deployment} is the name you gave the gpt-6-luna deployment. */
    public static OpenAiDecisionsProvider azure(String endpoint, String apiKey, String deployment,
                                                RestClient restClient) {
        return new OpenAiDecisionsProvider("azure-openai",
                URI.create(trimSlash(endpoint) + "/openai/v1/decisions"),
                Map.of("api-key", apiKey),
                isBlank(deployment) ? DEFAULT_MODEL : deployment, restClient);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);

        ArrayNode input = body.putArray("input");
        if (request.hasText()) {
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
            n.put("instructions", SystemOneCodec.instructionsWithCriteria(q));
            if (q.type().hasOptions()) {
                ArrayNode opts = n.putArray(q.type() == QuestionType.SCORE ? "levels" : "choices");
                for (Option o : q.options()) {
                    ObjectNode on = opts.addObject().put("value", o.value());
                    if (!o.description().isBlank()) on.put("description", o.description());
                }
            }
        }
        return body;
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        JsonNode answers = response.path("answers");
        List<Question> questions = request.questions();
        List<Answer> out = new ArrayList<>();
        if (answers.isObject()) { // tolerate a name-keyed map as well
            for (Question q : questions) {
                out.add(Answers.parse(id, q, answers.get(q.name())));
            }
            return out;
        }
        if (!answers.isArray()) {
            throw new DecisionException(id, "response has no 'answers' array: " + response);
        }
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            JsonNode match = null;
            for (JsonNode a : answers) {
                if (q.name().equals(a.path("name").asString(null))) {
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
