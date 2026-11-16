package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** OpenAI and Azure OpenAI through the official openai-java SDK, against a local server. */
class OpenAiDecisionsProviderTest {

    /** Response shape of the SDK's Decision model. */
    static final String REPLY = """
            {"model": "gpt-6-luna",
             "answers": [
               {"type": "predicate", "name": "is_damaged", "probability": 0.97},
               {"type": "choice", "name": "queue", "choice": "shipping", "confidence": 0.93,
                "probabilities": [{"value": "billing", "probability": 0.05}, {"value": "shipping", "probability": 0.95}]},
               {"type": "score", "name": "severity", "score": 1.1, "confidence": 0.5,
                "probabilities": [{"label": "low", "value": 0, "probability": 0.1},
                                  {"label": "medium", "value": 1, "probability": 0.7},
                                  {"label": "high", "value": 2, "probability": 0.2}]}
             ],
             "usage": {"input_tokens": 77, "output_tokens": 0, "total_tokens": 77,
                       "input_tokens_details": {"cached_tokens": 0}, "output_tokens_details": {"reasoning_tokens": 0}}}
            """;

    StubServer stub;
    OpenAiDecisionsProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        provider = OpenAiDecisionsProvider.openAi("sk-test", stub.url(), null, Duration.ofSeconds(5), 0);
    }

    @AfterEach
    void tearDown() {
        provider.close();
        stub.close();
    }

    @Test
    void sendsTheOfficialRequestShape() {
        stub.reply(200, REPLY);
        provider.decide(TestFixtures.ticketWithImage());

        assertThat(stub.path).isEqualTo("/v1/decisions");
        assertThat(stub.headers).containsEntry("authorization", "Bearer sk-test");
        JsonNode b = stub.json();
        assertThat(b.get("model").asString()).isEqualTo("gpt-6-luna");
        JsonNode parts = b.at("/input/0/content");
        assertThat(b.at("/input/0/role").asString()).isEqualTo("user");
        assertThat(parts.at("/0/type").asString()).isEqualTo("input_text");
        assertThat(parts.at("/1/type").asString()).isEqualTo("input_image");
        assertThat(parts.at("/1/image_url").asString()).isEqualTo("https://example.com/mug.jpg");

        assertThat(b.at("/questions/0/type").asString()).isEqualTo("predicate");
        assertThat(b.at("/questions/0/instructions").asString()).contains("True when: broken or cracked");
        assertThat(b.at("/questions/1/type").asString()).isEqualTo("choice");
        assertThat(b.at("/questions/1/choices/0/value").asString()).isEqualTo("billing");
        assertThat(b.at("/questions/1/choices/0/description").asString()).isEqualTo("Payments and refunds");
        assertThat(b.at("/questions/2/type").asString()).isEqualTo("score");
        assertThat(b.at("/questions/2/levels/2/label").asString()).as("score levels use label").isEqualTo("high");
        assertThat(b.at("/questions/2/levels/2/value").isMissingNode()).isTrue();
    }

    @Test
    void textOnlyInputIsAString() {
        stub.reply(200, REPLY);
        provider.decide(TestFixtures.ticket());
        assertThat(stub.json().get("input").asString()).isEqualTo("My blue mug arrived cracked. I want a refund.");
    }

    @Test
    void sendsStructuredDataAsInputJsonAndHonoursModelOverride() {
        stub.reply(200, REPLY);
        DecisionRequest t = TestFixtures.ticket();
        JsonNode data = JsonMapper.shared().readTree("{\"order\": 4411, \"vip\": true, \"tags\": [\"a\"]}");
        provider.decide(new DecisionRequest(null, data, null, t.questions(), "gpt-6-luna-mini"));

        JsonNode b = stub.json();
        assertThat(b.get("model").asString()).isEqualTo("gpt-6-luna-mini");
        assertThat(b.at("/input/0/content/0/type").asString()).isEqualTo("input_json");
        assertThat(b.at("/input/0/content/0/data/order").asInt()).isEqualTo(4411);
        assertThat(b.at("/input/0/content/0/data/tags/0").asString()).isEqualTo("a");
    }

    @Test
    void mapsTypedAnswers() {
        stub.reply(200, REPLY);
        DecisionResult r = provider.decide(TestFixtures.ticket());

        assertThat(r.answer("is_damaged").probability()).isEqualTo(0.97);
        assertThat(r.answer("is_damaged").value()).isEqualTo("true");
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("queue").confidence()).isEqualTo(0.93);
        assertThat(r.answer("queue").probabilities()).containsExactly(entry("billing", 0.05), entry("shipping", 0.95));
        assertThat(r.answer("severity").value()).isEqualTo("medium");
        assertThat(r.answer("severity").score()).isEqualTo(1.1);
        assertThat(r.usage().get("input_tokens").asInt()).isEqualTo(77);
    }

    @Test
    void refusalDoesNotDropTheOtherAnswers() {
        stub.reply(200, """
                {"model": "gpt-6-luna",
                 "answers": [{"type": "predicate", "name": "is_damaged", "probability": 0.2},
                             {"type": "refusal", "name": "queue"},
                             {"type": "refusal", "name": "severity"}],
                 "usage": {"input_tokens": 1, "output_tokens": 0, "total_tokens": 1,
                           "input_tokens_details": {"cached_tokens": 0}, "output_tokens_details": {"reasoning_tokens": 0}}}
                """);
        DecisionResult r = provider.decide(TestFixtures.ticket());
        assertThat(r.answer("is_damaged").value()).isEqualTo("false");
        assertThat(r.answer("queue").isRefused()).isTrue();
        assertThat(r.answer("queue").value()).isNull();
        assertThat(r.answer("severity").isRefused()).isTrue();
    }

    @Test
    void apiErrorsCarryStatus() {
        stub.reply(429, "{\"error\": {\"message\": \"Rate limit reached\", \"type\": \"rate_limit\"}}");
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("Rate limit reached")
                .satisfies(e -> assertThat(((DecisionException) e).upstreamStatus()).isEqualTo(429));
    }

    @Test
    void azureUsesUnifiedEndpointDeploymentAndApiKey() throws Exception {
        try (var azure = OpenAiDecisionsProvider.azure(stub.url(), "az-key", "luna-prod", Duration.ofSeconds(5), 0)) {
            stub.reply(200, REPLY);
            DecisionResult r = azure.decide(TestFixtures.ticket());

            assertThat(stub.path).isEqualTo("/openai/v1/decisions");
            assertThat(stub.headers).containsEntry("api-key", "az-key");
            assertThat(stub.headers).doesNotContainKey("authorization");
            assertThat(stub.json().get("model").asString()).isEqualTo("luna-prod");
            assertThat(r.provider()).isEqualTo("azure-openai");
        }
    }
}
