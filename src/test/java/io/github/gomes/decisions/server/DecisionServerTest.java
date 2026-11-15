package io.github.gomes.decisions.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gomes.decisions.DecisionService;
import io.github.gomes.decisions.ProviderRegistry;
import io.github.gomes.decisions.RequestCodec;
import io.github.gomes.decisions.http.JsonHttp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecisionServerTest {

    DecisionServer server;
    final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        var service = new DecisionService(ProviderRegistry.fromEnvironment(Map.of()));
        server = new DecisionServer(service, "127.0.0.1", 0, "secret").start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    HttpResponse<String> call(String method, String path, String body, String token) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsPublicButApiNeedsToken() throws Exception {
        assertEquals(200, call("GET", "/health", null, null).statusCode());
        assertEquals(401, call("GET", "/v1/providers", null, null).statusCode());
        assertEquals(401, call("GET", "/v1/providers", null, "wrong").statusCode());
        var resp = call("GET", "/v1/providers", null, "secret");
        assertEquals(200, resp.statusCode());
        assertEquals("local", JsonHttp.MAPPER.readTree(resp.body()).get(0).get("id").asText());
    }

    @Test
    void decideAndCompare() throws Exception {
        var resp = call("POST", "/v1/decisions?provider=local", RequestCodec.EXAMPLE, "secret");
        assertEquals(200, resp.statusCode(), resp.body());
        JsonNode body = JsonHttp.MAPPER.readTree(resp.body());
        assertEquals("local", body.get("provider").asText());
        assertEquals("shipping", body.at("/answers/1/value").asText());
        assertTrue(body.path("raw").isMissingNode(), "raw provider response is not exposed");

        resp = call("POST", "/v1/compare", RequestCodec.EXAMPLE, "secret");
        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(1, JsonHttp.MAPPER.readTree(resp.body()).size());
    }

    @Test
    void errors() throws Exception {
        assertEquals(400, call("POST", "/v1/decisions", RequestCodec.EXAMPLE, "secret").statusCode());
        assertEquals(404, call("POST", "/v1/decisions?provider=nope", RequestCodec.EXAMPLE, "secret").statusCode());
        var bad = call("POST", "/v1/decisions?provider=local", "{\"input\":\"x\",\"questions\":[]}", "secret");
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains("at least one question"), bad.body());
        assertEquals(404, call("GET", "/v1/nothing", null, "secret").statusCode());
    }
}
