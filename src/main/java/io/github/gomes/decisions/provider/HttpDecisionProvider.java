package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Template for providers that are a single JSON POST: build body, call, parse answers. */
public abstract class HttpDecisionProvider implements DecisionProvider {

    /** Shared mapper for building request trees; immutable and thread-safe in Jackson 3. */
    protected static final JsonMapper JSON = JsonMapper.shared();

    protected final String id;
    protected final URI endpoint;
    protected final Map<String, String> headers;
    protected final String defaultModel;
    protected final Capabilities capabilities;
    protected final RestClient restClient;

    protected HttpDecisionProvider(String id, URI endpoint, Map<String, String> headers, String defaultModel,
                                   Capabilities capabilities, RestClient restClient) {
        this.id = Objects.requireNonNull(id);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.headers = Map.copyOf(headers);
        this.defaultModel = Objects.requireNonNull(defaultModel);
        this.capabilities = Objects.requireNonNull(capabilities);
        this.restClient = Objects.requireNonNull(restClient);
    }

    @Override public String id() { return id; }
    @Override public String defaultModel() { return defaultModel; }
    @Override public Capabilities capabilities() { return capabilities; }

    public URI endpoint() { return endpoint; }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        checkSupported(request);
        String model = request.model() != null ? request.model() : defaultModel;
        ObjectNode body = buildBody(request, model);

        long start = System.nanoTime();
        JsonNode response = post(body);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<Answer> answers = parseAnswers(request, response);
        return new DecisionResult(id, model, answers, latencyMs, response.get("usage"), response);
    }

    private JsonNode post(ObjectNode body) {
        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> headers.forEach(h::set))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new DecisionException(id, "empty response body");
            }
            return response;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            throw new DecisionException(id, "HTTP " + e.getStatusCode().value() + ": " + errorMessage(responseBody),
                    e.getStatusCode().value(), responseBody, e);
        } catch (ResourceAccessException e) {
            throw new DecisionException(id, "could not reach " + endpoint + ": " + e.getMostSpecificCause().getMessage(), e);
        } catch (RestClientException e) {
            throw new DecisionException(id, "invalid response from " + endpoint + ": " + e.getMessage(), e);
        }
    }

    /** Builds the provider-specific request body. */
    public abstract ObjectNode buildBody(DecisionRequest request, String model);

    /** Maps the provider response to normalized answers, in request order. */
    public abstract List<Answer> parseAnswers(DecisionRequest request, JsonNode response);

    /** Pulls {@code error.message} (OpenAI/Google style) or {@code message}/{@code detail} out of an error body. */
    static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        try {
            JsonNode n = JSON.readTree(body);
            if (n.path("error").isString()) return n.get("error").asString();
            if (n.path("error").path("message").isString()) return n.path("error").path("message").asString();
            if (n.path("message").isString()) return n.get("message").asString();
            if (n.path("detail").isString()) return n.get("detail").asString();
        } catch (RuntimeException ignored) {
            // not JSON: fall through
        }
        return body.length() > 500 ? body.substring(0, 500) + "…" : body;
    }

    protected static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
