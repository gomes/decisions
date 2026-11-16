package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.systemone.SystemOneProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SystemOneProviderTest {

    public static final String REPLY = """
            {"answers": {
               "is_damaged": {"noul": 0.98},
               "queue": {"choice": "shipping", "probabilities": {"billing": 0.2, "shipping": 0.8}, "confidence": 0.6},
               "severity": {"score": 1.1, "probabilities": {"low": 0.1, "medium": 0.7, "high": 0.2}, "confidence": 0.5}
             },
             "usage": {"input_tokens": 42}}
            """;

    final MockProvider mock = new MockProvider();

    @Test
    void typesafeRequestAndResponse() {
        mock.server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(mock.captureBody())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        DecisionResult r = SystemOneProvider.typesafe("sk-test", null, null, mock.restClient)
                .decide(TestFixtures.ticket());

        mock.server.verify();
        JsonNode b = mock.body();
        assertThat(b.get("model").asString()).isEqualTo("jev-latest");
        assertThat(b.get("state").asString()).isEqualTo("My blue mug arrived cracked. I want a refund.");
        assertThat(b.at("/questions/is_damaged/type").asString()).isEqualTo("noul");
        assertThat(b.at("/questions/is_damaged/criteria/true").asString()).isEqualTo("broken or cracked");
        assertThat(b.at("/questions/queue/criteria/billing").asString()).isEqualTo("Payments and refunds");
        assertThat(b.at("/questions/severity/criteria").propertyNames())
                .as("score levels keep their order").containsExactly("low", "medium", "high");

        assertThat(r.answer("is_damaged").value()).isEqualTo("true");
        assertThat(r.answer("is_damaged").probability()).isEqualTo(0.98);
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("queue").confidence()).isEqualTo(0.6);
        assertThat(r.answer("severity").value()).isEqualTo("medium");
        assertThat(r.answer("severity").score()).isEqualTo(1.1);
        assertThat(r.usage().get("input_tokens").asInt()).isEqualTo(42);
    }

    @Test
    void sendsJsonStateWhenOnlyDataIsGiven() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        var t = TestFixtures.ticket();
        var data = tools.jackson.databind.json.JsonMapper.shared().readTree("{\"order\": 4411}");
        SystemOneProvider.typesafe("k", null, null, mock.restClient)
                .decide(new io.github.gomes.decisions.domain.DecisionRequest(null, data, null, t.questions(), null));
        assertThat(mock.body().at("/state/order").asInt()).isEqualTo(4411);
    }

    @Test
    void microsoftFoundryUsesApiKeyAndProviderPath() {
        mock.server.expect(requestTo("https://res.services.ai.azure.com/providers/microsoft/v1/systemone"))
                .andExpect(header("api-key", "az-key"))
                .andExpect(mock.captureBody())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        SystemOneProvider.microsoftFoundry("https://res.services.ai.azure.com/", "az-key", "my-decision-1",
                mock.restClient).decide(TestFixtures.ticket());
        mock.server.verify();
        assertThat(mock.body().get("model").asString()).isEqualTo("my-decision-1");
    }

    @Test
    void rejectsImagesBeforeCallingTheApi() {
        var provider = SystemOneProvider.typesafe("k", null, null, mock.restClient);
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticketWithImage()))
                .isInstanceOf(DecisionException.class)
                .hasMessageContaining("image")
                .satisfies(e -> assertThat(((DecisionException) e).isUpstreamError()).isFalse());
        mock.server.verify(); // no request was made
    }

    @Test
    void httpErrorsCarryStatusAndProviderMessage() {
        mock.server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"not enabled for this key\"}}"));
        var provider = SystemOneProvider.typesafe("k", null, null, mock.restClient);
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("not enabled for this key")
                .satisfies(e -> assertThat(((DecisionException) e).upstreamStatus()).isEqualTo(403));
    }

    @Test
    void missingAnswerIsReported() {
        mock.server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess("{\"answers\": {}}", MediaType.APPLICATION_JSON));
        var provider = SystemOneProvider.typesafe("k", null, null, mock.restClient);
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessageContaining("missing answer for question 'is_damaged'");
    }
}
