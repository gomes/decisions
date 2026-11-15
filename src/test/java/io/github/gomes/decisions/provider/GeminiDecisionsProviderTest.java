package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.StubHttp;
import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.provider.gemini.GeminiDecisionsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiDecisionsProviderTest {

    static final String REPLY = """
            {"answers": {
               "is_damaged": {"noul": 0.98, "confidence": 0.96},
               "queue": {"choice": "shipping", "probabilities": {"billing": 0.1, "shipping": 0.9}, "confidence": 0.8},
               "severity": {"score": 2.0, "probabilities": {"low": 0.0, "medium": 0.0, "high": 1.0}}
             }}
            """;

    @Test
    void textOnlyInputIsAString() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            var p = new GeminiDecisionsProvider("g-key", stub.baseUrl(), null, new JsonHttp());
            DecisionResult r = p.decide(TestFixtures.ticket());

            assertEquals("/v1beta/decisions", stub.lastPath);
            assertEquals("g-key", stub.lastHeaders.get("x-goog-api-key"));
            assertEquals("gemini-decision-1", stub.lastBody.get("model").asText());
            assertTrue(stub.lastBody.get("input").isTextual());
            assertEquals("noul", stub.lastBody.at("/questions/is_damaged/type").asText());
            assertEquals(3, stub.lastBody.size(), "only model, input and questions are allowed");

            assertEquals(0.96, r.answer("is_damaged").confidence());
            assertEquals("shipping", r.answer("queue").value());
            assertEquals("high", r.answer("severity").value());
        }
    }

    @Test
    void imagesBecomeParts() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            var p = new GeminiDecisionsProvider("g-key", stub.baseUrl(), null, new JsonHttp());
            p.decide(TestFixtures.ticketWithImage());
            assertEquals("text", stub.lastBody.at("/input/0/type").asText());
            assertEquals("image", stub.lastBody.at("/input/1/type").asText());
            assertEquals("https://example.com/mug.jpg", stub.lastBody.at("/input/1/uri").asText());
        }
    }

    @Test
    void mimeTypeFromDataUri() {
        assertEquals("image/png", GeminiDecisionsProvider.mimeFromDataUri("data:image/png;base64,AAAA"));
        assertNull(GeminiDecisionsProvider.mimeFromDataUri("https://x/y.png"));
    }
}
