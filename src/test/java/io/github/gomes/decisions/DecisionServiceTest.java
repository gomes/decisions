package io.github.gomes.decisions;

import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.provider.local.LocalHeuristicProvider;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecisionServiceTest {

    @Test
    void compareCollectsSuccessesAndFailures() throws Exception {
        try (StubHttp ok = new StubHttp().reply(200, SystemOneProviderTest.REPLY);
             StubHttp down = new StubHttp().reply(503, "{\"error\": \"overloaded\"}")) {
            ProviderRegistry reg = new ProviderRegistry()
                    .register(SystemOneProvider.typesafe("k", ok.baseUrl(), null, new JsonHttp()))
                    .register(OpenAiDecisionsProvider.openAi("k", down.baseUrl(), null, new JsonHttp()))
                    .register(new LocalHeuristicProvider());
            var outcomes = new DecisionService(reg).compare(TestFixtures.ticket(), List.of(),
                    Map.of("typesafe", "jev-1.14.0"));

            assertEquals(3, outcomes.size());
            assertTrue(outcomes.get(0).ok());
            assertEquals("jev-1.14.0", ok.lastBody.get("model").asText());
            assertFalse(outcomes.get(1).ok());
            assertEquals(503, outcomes.get(1).httpStatus());
            assertTrue(outcomes.get(1).error().contains("overloaded"));
            assertTrue(outcomes.get(2).ok());
        }
    }
}

class SystemOneProviderTest {
    static final String REPLY = io.github.gomes.decisions.provider.SystemOneProviderTest.REPLY;
}
