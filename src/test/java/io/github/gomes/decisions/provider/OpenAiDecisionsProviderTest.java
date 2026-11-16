package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

    final MockProvider mock = new MockProvider();

    @Test
    void openAiRequestAndResponse() {
        mock.server.expect(requestTo("https://api.openai.com/v1/decisions"))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(mock.captureBody())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        DecisionResult r = OpenAiDecisionsProvider.openAi("sk-test", null, null, mock.restClient)
                .decide(TestFixtures.ticketWithImage());

        JsonNode b = mock.body();
        assertThat(b.get("model").asString()).isEqualTo("gpt-6-luna");
        assertThat(b.at("/input/0/type").asString()).isEqualTo("input_text");
        assertThat(b.at("/input/1/type").asString()).isEqualTo("input_image");
        assertThat(b.at("/input/1/image_url").asString()).isEqualTo("https://example.com/mug.jpg");
        assertThat(b.at("/questions/0/type").asString()).isEqualTo("predicate");
        assertThat(b.at("/questions/0/instructions").asString()).contains("True when: broken or cracked");
        assertThat(b.at("/questions/1/choices/0/value").asString()).isEqualTo("billing");
        assertThat(b.at("/questions/1/choices/0/description").asString()).isEqualTo("Payments and refunds");
        assertThat(b.at("/questions/2/levels/2/value").asString()).isEqualTo("high");

        assertThat(r.answer("is_damaged").probability()).isEqualTo(0.97);
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("queue").probabilities()).containsEntry("shipping", 0.95);
        assertThat(r.answer("severity").value()).isEqualTo("medium");
        assertThat(r.answer("severity").score()).isCloseTo(1.1, within(1e-9));
    }

    @Test
    void sendsStructuredDataAsInputJsonAndHonoursModelOverride() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        DecisionRequest t = TestFixtures.ticket();
        JsonNode data = JsonMapper.shared().readTree("{\"order\": 4411, \"vip\": true}");

        OpenAiDecisionsProvider.openAi("k", null, null, mock.restClient)
                .decide(new DecisionRequest(null, data, null, t.questions(), "gpt-6-luna-mini"));

        JsonNode b = mock.body();
        assertThat(b.get("model").asString()).isEqualTo("gpt-6-luna-mini");
        assertThat(b.get("input").size()).isEqualTo(1);
        assertThat(b.at("/input/0/type").asString()).isEqualTo("input_json");
        assertThat(b.at("/input/0/json/order").asInt()).isEqualTo(4411);
    }

    @Test
    void unnamedAnswersAreMatchedByPosition() {
        mock.server.expect(requestTo("https://api.openai.com/v1/decisions")).andRespond(withSuccess("""
                {"answers": [{"probability": 0.1},
                             {"value": "billing", "probabilities": {"billing": 0.9, "shipping": 0.1}},
                             {"probabilities": [0.6, 0.3, 0.1]}]}
                """, MediaType.APPLICATION_JSON));

        DecisionResult r = OpenAiDecisionsProvider.openAi("k", null, null, mock.restClient)
                .decide(TestFixtures.ticket());

        assertThat(r.answer("is_damaged").value()).isEqualTo("false");
        assertThat(r.answer("queue").value()).isEqualTo("billing");
        assertThat(r.answer("queue").confidence()).as("derived from margin").isCloseTo(0.8, within(1e-9));
        assertThat(r.answer("severity").value()).isEqualTo("low");
        assertThat(r.answer("severity").score()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void azureUsesDeploymentAndApiKey() {
        mock.server.expect(requestTo("https://res.openai.azure.com/openai/v1/decisions"))
                .andExpect(header("api-key", "az"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(mock.captureBody())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        DecisionResult r = OpenAiDecisionsProvider.azure("https://res.openai.azure.com", "az", "luna-prod",
                mock.restClient).decide(TestFixtures.ticket());

        assertThat(mock.body().get("model").asString()).isEqualTo("luna-prod");
        assertThat(r.provider()).isEqualTo("azure-openai");
    }
}
