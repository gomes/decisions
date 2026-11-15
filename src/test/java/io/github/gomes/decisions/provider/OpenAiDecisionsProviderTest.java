package io.github.gomes.decisions.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gomes.decisions.StubHttp;
import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiDecisionsProviderTest {

    static final String REPLY = """
            {"id": "dec_123", "model": "gpt-6-luna",
             "answers": [
               {"type": "predicate", "name": "is_damaged", "probability": 0.97},
               {"type": "choice", "name": "queue", "value": "shipping",
                "probabilities": [{"value": "billing", "probability": 0.05}, {"value": "shipping", "probability": 0.95}],
                "confidence": 0.93},
               {"type": "score", "name": "severity", "score": 1.1,
                "probabilities": [{"value": "low", "probability": 0.1}, {"value": "medium", "probability": 0.7},
                                  {"value": "high", "probability": 0.2}],
                "confidence": 0.5}
             ],
             "usage": {"input_tokens": 77}}
            """;

    @Test
    void openAiRequestAndResponse() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            var p = OpenAiDecisionsProvider.openAi("sk-test", stub.baseUrl(), null, new JsonHttp());
            DecisionRequest req = TestFixtures.ticketWithImage();
            DecisionResult r = p.decide(req);

            assertEquals("/v1/decisions", stub.lastPath);
            assertEquals("Bearer sk-test", stub.lastHeaders.get("authorization"));
            JsonNode b = stub.lastBody;
            assertEquals("gpt-6-luna", b.get("model").asText());
            assertEquals("input_text", b.at("/input/0/type").asText());
            assertEquals("input_image", b.at("/input/1/type").asText());
            assertEquals("https://example.com/mug.jpg", b.at("/input/1/image_url").asText());
            assertEquals("predicate", b.at("/questions/0/type").asText());
            assertTrue(b.at("/questions/0/instructions").asText().contains("True when: broken or cracked"));
            assertEquals("billing", b.at("/questions/1/choices/0/value").asText());
            assertEquals("Payments and refunds", b.at("/questions/1/choices/0/description").asText());
            assertEquals("high", b.at("/questions/2/levels/2/value").asText());

            assertEquals(0.97, r.answer("is_damaged").probability());
            assertEquals("shipping", r.answer("queue").value());
            assertEquals(0.95, r.answer("queue").probabilities().get("shipping"));
            assertEquals("medium", r.answer("severity").value());
            assertEquals(1.1, r.answer("severity").score(), 1e-9);
        }
    }

    @Test
    void sendsStructuredDataAsInputJson() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            var p = OpenAiDecisionsProvider.openAi("k", stub.baseUrl(), null, new JsonHttp());
            DecisionRequest t = TestFixtures.ticket();
            var data = JsonHttp.MAPPER.readTree("{\"order\": 4411, \"vip\": true}");
            p.decide(new DecisionRequest(null, data, null, t.questions(), "gpt-6-luna-mini"));
            assertEquals("gpt-6-luna-mini", stub.lastBody.get("model").asText());
            assertEquals(1, stub.lastBody.get("input").size());
            assertEquals("input_json", stub.lastBody.at("/input/0/type").asText());
            assertEquals(4411, stub.lastBody.at("/input/0/json/order").asInt());
        }
    }

    @Test
    void unnamedAnswersAreMatchedByPosition() throws Exception {
        String reply = """
                {"answers": [{"probability": 0.1},
                             {"value": "billing", "probabilities": {"billing": 0.9, "shipping": 0.1}},
                             {"probabilities": [0.6, 0.3, 0.1]}]}
                """;
        try (StubHttp stub = new StubHttp().reply(200, reply)) {
            var p = OpenAiDecisionsProvider.openAi("k", stub.baseUrl(), null, new JsonHttp());
            DecisionResult r = p.decide(TestFixtures.ticket());
            assertEquals("false", r.answer("is_damaged").value());
            assertEquals("billing", r.answer("queue").value());
            assertEquals(0.8, r.answer("queue").confidence(), 1e-9, "confidence derived from margin");
            assertEquals("low", r.answer("severity").value());
            assertEquals(0.5, r.answer("severity").score(), 1e-9);
        }
    }

    @Test
    void azureUsesDeploymentAndApiKey() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            var p = OpenAiDecisionsProvider.azure(stub.baseUrl(), "az", "luna-prod", new JsonHttp());
            DecisionResult r = p.decide(TestFixtures.ticket());
            assertEquals("/openai/v1/decisions", stub.lastPath);
            assertEquals("az", stub.lastHeaders.get("api-key"));
            assertNull(stub.lastHeaders.get("authorization"));
            assertEquals("luna-prod", stub.lastBody.get("model").asText());
            assertEquals("azure-openai", r.provider());
        }
    }
}
