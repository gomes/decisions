package io.github.gomes.decisions.provider.systemone;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.provider.Capabilities;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * The "System One" decision protocol ({@code POST .../v1/systemone}) introduced by TypeSafe for Jev and
 * also served by Microsoft Foundry for Microsoft-Decision-1 and by other hosts (e.g. DigitalOcean).
 *
 * <pre>{@code
 * { "model": "jev-latest", "state": "<text or JSON>", "questions": { ... see SystemOneCodec ... } }
 * }</pre>
 */
public class SystemOneProvider extends HttpDecisionProvider {

    public static final String TYPESAFE_DEFAULT_BASE_URL = "https://api.typesafe.ai";
    public static final String TYPESAFE_DEFAULT_MODEL = "jev-latest";
    public static final String MICROSOFT_DEFAULT_DEPLOYMENT = "microsoft-decision-1";

    /** Jev and Decision-1 are text-only with a 32K context; questions are capped conservatively. */
    public static final Capabilities CAPABILITIES = new Capabilities(256, false, true);

    public SystemOneProvider(String id, URI endpoint, Map<String, String> headers, String defaultModel,
                             RestClient restClient) {
        super(id, endpoint, headers, defaultModel, CAPABILITIES, restClient);
    }

    /** TypeSafe Jev, native API: {@code https://api.typesafe.ai/v1/systemone}, Bearer auth. */
    public static SystemOneProvider typesafe(String apiKey, String baseUrl, String model, RestClient restClient) {
        String base = trimSlash(isBlank(baseUrl) ? TYPESAFE_DEFAULT_BASE_URL : baseUrl);
        return new SystemOneProvider("typesafe", URI.create(base + "/v1/systemone"),
                Map.of("Authorization", "Bearer " + apiKey),
                isBlank(model) ? TYPESAFE_DEFAULT_MODEL : model, restClient);
    }

    /**
     * Microsoft-Decision-1 on Microsoft Foundry: {@code <resource-endpoint>/providers/microsoft/v1/systemone}.
     * The model field is your deployment name. Uses the {@code api-key} header.
     */
    public static SystemOneProvider microsoftFoundry(String endpoint, String apiKey, String deployment,
                                                     RestClient restClient) {
        return new SystemOneProvider("microsoft",
                URI.create(trimSlash(endpoint) + "/providers/microsoft/v1/systemone"),
                Map.of("api-key", apiKey),
                isBlank(deployment) ? MICROSOFT_DEFAULT_DEPLOYMENT : deployment, restClient);
    }

    /** Any other System One compatible host, given the full endpoint URL and a Bearer token. */
    public static SystemOneProvider generic(String endpointUrl, String apiKey, String model, RestClient restClient) {
        return new SystemOneProvider("systemone", URI.create(endpointUrl),
                Map.of("Authorization", "Bearer " + apiKey), model, restClient);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.set("state", SystemOneCodec.state(request));
        body.set("questions", SystemOneCodec.questions(request.questions()));
        return body;
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        return SystemOneCodec.answers(id, request, response);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
