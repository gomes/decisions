package io.github.gomes.decisions.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gomes.decisions.spi.DecisionException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Minimal JSON-over-HTTP helper on top of {@link java.net.http.HttpClient}. */
public final class JsonHttp {

    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final HttpClient client;
    private final Duration timeout;

    public JsonHttp(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public JsonHttp() {
        this(Duration.ofSeconds(30));
    }

    /** Result of a call: parsed body and elapsed time. */
    public record Response(JsonNode body, long latencyMs) {
    }

    public Response post(String provider, URI uri, Map<String, String> headers, JsonNode body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        headers.forEach(b::header);

        long start = System.nanoTime();
        HttpResponse<String> resp;
        try {
            resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DecisionException(provider, "HTTP call to " + uri + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DecisionException(provider, "interrupted", e);
        }
        long latency = (System.nanoTime() - start) / 1_000_000;

        if (resp.statusCode() / 100 != 2) {
            throw new DecisionException(provider, "HTTP " + resp.statusCode() + ": " + errorMessage(resp.body()),
                    resp.statusCode(), resp.body(), null);
        }
        try {
            return new Response(MAPPER.readTree(resp.body()), latency);
        } catch (IOException e) {
            throw new DecisionException(provider, "response is not valid JSON", resp.statusCode(), resp.body(), e);
        }
    }

    /** Pulls {@code error.message} (OpenAI/Google style) or {@code message}/{@code detail} out of an error body. */
    static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        try {
            JsonNode n = MAPPER.readTree(body);
            if (n.path("error").isTextual()) return n.get("error").asText();
            if (n.path("error").path("message").isTextual()) return n.path("error").path("message").asText();
            if (n.path("message").isTextual()) return n.get("message").asText();
            if (n.path("detail").isTextual()) return n.get("detail").asText();
        } catch (IOException ignored) {
            // fall through
        }
        return body.length() > 500 ? body.substring(0, 500) + "…" : body;
    }
}
