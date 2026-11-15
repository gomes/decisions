package io.github.gomes.decisions.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.gomes.decisions.StubHttp;
import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.provider.systemone.SystemOneProvider;
import io.github.gomes.decisions.spi.DecisionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SystemOneProviderTest {

    public static final String REPLY = """
            {"answers": {
               "is_damaged": {"noul": 0.98},
               "queue": {"choice": "shipping", "probabilities": {"billing": 0.2, "shipping": 0.8}, "confidence": 0.6},
               "severity": {"score": 1.1, "probabilities": {"low": 0.1, "medium": 0.7, "high": 0.2}, "confidence": 0.5}
             },
             "usage": {"input_tokens": 42}}
            """;

    @Test
    void typesafeRequestAndResponse() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            SystemOneProvider p = SystemOneProvider.typesafe("sk-test", stub.baseUrl(), null, new JsonHttp());
            DecisionResult r = p.decide(TestFixtures.ticket());

            assertEquals("/v1/systemone", stub.lastPath);
            assertEquals("Bearer sk-test", stub.lastHeaders.get("authorization"));
            JsonNode b = stub.lastBody;
            assertEquals("jev-latest", b.get("model").asText());
            assertEquals("My blue mug arrived cracked. I want a refund.", b.get("state").asText());
            assertEquals("noul", b.at("/questions/is_damaged/type").asText());
            assertEquals("broken or cracked", b.at("/questions/is_damaged/criteria/true").asText());
            assertEquals("Payments and refunds", b.at("/questions/queue/criteria/billing").asText());
            List<String> levels = new ArrayList<>();
            b.at("/questions/severity/criteria").fieldNames().forEachRemaining(levels::add);
            assertEquals(List.of("low", "medium", "high"), levels, "score levels keep order");

            assertEquals("true", r.answer("is_damaged").value());
            assertEquals(0.98, r.answer("is_damaged").probability());
            assertEquals("shipping", r.answer("queue").value());
            assertEquals(0.6, r.answer("queue").confidence());
            assertEquals("medium", r.answer("severity").value());
            assertEquals(1.1, r.answer("severity").score());
            assertEquals(42, r.usage().get("input_tokens").asInt());
        }
    }

    @Test
    void microsoftFoundryUsesApiKeyAndProviderPath() throws Exception {
        try (StubHttp stub = new StubHttp().reply(200, REPLY)) {
            SystemOneProvider p = SystemOneProvider.microsoftFoundry(stub.baseUrl() + "/", "az-key",
                    "my-decision-1", new JsonHttp());
            p.decide(TestFixtures.ticket());
            assertEquals("/providers/microsoft/v1/systemone", stub.lastPath);
            assertEquals("az-key", stub.lastHeaders.get("api-key"));
            assertEquals("my-decision-1", stub.lastBody.get("model").asText());
        }
    }

    @Test
    void rejectsImagesBeforeCallingTheApi() {
        SystemOneProvider p = SystemOneProvider.typesafe("k", "http://127.0.0.1:1", null, new JsonHttp());
        var req = TestFixtures.ticketWithImage();
        DecisionException e = assertThrows(DecisionException.class, () -> p.decide(req));
        assertTrue(e.getMessage().contains("image"));
    }

    @Test
    void httpErrorsCarryStatusAndMessage() throws Exception {
        try (StubHttp stub = new StubHttp().reply(403, "{\"error\":{\"message\":\"not enabled\"}}")) {
            SystemOneProvider p = SystemOneProvider.typesafe("k", stub.baseUrl(), null, new JsonHttp());
            DecisionException e = assertThrows(DecisionException.class, () -> p.decide(TestFixtures.ticket()));
            assertEquals(403, e.httpStatus());
            assertTrue(e.getMessage().contains("not enabled"));
        }
    }
}
