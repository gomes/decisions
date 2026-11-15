package io.github.gomes.decisions.provider.systemone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.spi.Capabilities;

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

    /** Both Jev and Decision-1 are text-only and use a 32K context; questions are capped conservatively. */
    public static final Capabilities CAPABILITIES = new Capabilities(256, false, true);

    public SystemOneProvider(String id, String displayName, URI endpoint, Map<String, String> headers,
                             String defaultModel, JsonHttp http) {
        super(id, displayName, endpoint, headers, defaultModel, CAPABILITIES, http);
    }

    /** TypeSafe Jev, native API: {@code https://api.typesafe.ai/v1/systemone}, Bearer auth. */
    public static SystemOneProvider typesafe(String apiKey, String baseUrl, String model, JsonHttp http) {
        String base = trimSlash(baseUrl == null ? TYPESAFE_DEFAULT_BASE_URL : baseUrl);
        return new SystemOneProvider("typesafe", "TypeSafe Jev (System One)",
                URI.create(base + "/v1/systemone"),
                Map.of("Authorization", "Bearer " + apiKey),
                model == null ? TYPESAFE_DEFAULT_MODEL : model, http);
    }

    /**
     * Microsoft-Decision-1 on Microsoft Foundry: {@code <resource-endpoint>/providers/microsoft/v1/systemone}.
     * The model field is your deployment name. Uses the {@code api-key} header.
     */
    public static SystemOneProvider microsoftFoundry(String endpoint, String apiKey, String deployment, JsonHttp http) {
        return new SystemOneProvider("microsoft", "Microsoft-Decision-1 (Foundry)",
                URI.create(trimSlash(endpoint) + "/providers/microsoft/v1/systemone"),
                Map.of("api-key", apiKey),
                deployment == null ? MICROSOFT_DEFAULT_DEPLOYMENT : deployment, http);
    }

    /** Any other System One compatible host, given the full endpoint URL and a Bearer token. */
    public static SystemOneProvider generic(String endpointUrl, String apiKey, String model, JsonHttp http) {
        return new SystemOneProvider("systemone", "System One compatible endpoint",
                URI.create(endpointUrl), Map.of("Authorization", "Bearer " + apiKey), model, http);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JsonHttp.MAPPER.createObjectNode();
        body.put("model", model);
        body.set("state", SystemOneCodec.state(request));
        body.set("questions", SystemOneCodec.questions(request.questions()));
        return body;
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        return SystemOneCodec.answers(id, request, response);
    }
}
