package io.github.gomes.decisions.provider;

import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicReference;

/** A RestClient wired to a {@link MockRestServiceServer}, plus a matcher that captures the JSON body. */
final class MockProvider {

    final RestClient restClient;
    final MockRestServiceServer server;
    private final AtomicReference<String> body = new AtomicReference<>();

    MockProvider() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    RequestMatcher captureBody() {
        return request -> body.set(((MockClientHttpRequest) request).getBodyAsString());
    }

    JsonNode body() {
        return JsonMapper.shared().readTree(body.get());
    }
}
