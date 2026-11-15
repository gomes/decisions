package io.github.gomes.decisions.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.spi.Capabilities;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Template for providers that are a single JSON POST: build body, call, parse answers. */
public abstract class HttpDecisionProvider implements DecisionProvider {

    protected final String id;
    protected final String displayName;
    protected final URI endpoint;
    protected final Map<String, String> headers;
    protected final String defaultModel;
    protected final Capabilities capabilities;
    protected final JsonHttp http;

    protected HttpDecisionProvider(String id, String displayName, URI endpoint, Map<String, String> headers,
                                   String defaultModel, Capabilities capabilities, JsonHttp http) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.headers = Map.copyOf(headers);
        this.defaultModel = Objects.requireNonNull(defaultModel);
        this.capabilities = Objects.requireNonNull(capabilities);
        this.http = http == null ? new JsonHttp() : http;
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public String defaultModel() { return defaultModel; }
    @Override public Capabilities capabilities() { return capabilities; }

    public URI endpoint() { return endpoint; }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        validate(request);
        String model = request.model() != null ? request.model() : defaultModel;
        ObjectNode body = buildBody(request, model);
        JsonHttp.Response resp = http.post(id, endpoint, headers, body);
        List<Answer> answers = parseAnswers(request, resp.body());
        JsonNode usage = resp.body().get("usage");
        return new DecisionResult(id, model, answers, resp.latencyMs(), usage, resp.body());
    }

    /** Builds the provider-specific request body. */
    public abstract ObjectNode buildBody(DecisionRequest request, String model);

    /** Maps the provider response to normalized answers, in request order. */
    public abstract List<Answer> parseAnswers(DecisionRequest request, JsonNode response);

    protected static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
