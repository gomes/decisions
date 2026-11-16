package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.gemini.GeminiDecisionsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiDecisionsProviderTest {

    static final String REPLY = """
            {"answers": {
               "is_damaged": {"noul": 0.98, "confidence": 0.96},
               "queue": {"choice": "shipping", "probabilities": {"billing": 0.1, "shipping": 0.9}, "confidence": 0.8},
               "severity": {"score": 2.0, "probabilities": {"low": 0.0, "medium": 0.0, "high": 1.0}}
             }}
            """;

    final MockProvider mock = new MockProvider();

    @Test
    void textOnlyInputIsAString() {
        mock.server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/decisions"))
                .andExpect(header("x-goog-api-key", "g-key"))
                .andExpect(mock.captureBody())
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        DecisionResult r = new GeminiDecisionsProvider("g-key", null, null, mock.restClient)
                .decide(TestFixtures.ticket());

        var b = mock.body();
        assertThat(b.get("model").asString()).isEqualTo("gemini-decision-1");
        assertThat(b.get("input").isString()).isTrue();
        assertThat(b.at("/questions/is_damaged/type").asString()).isEqualTo("noul");
        assertThat(b.propertyNames()).as("only model, input and questions are allowed")
                .containsExactlyInAnyOrder("model", "input", "questions");

        assertThat(r.answer("is_damaged").confidence()).isEqualTo(0.96);
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("severity").value()).isEqualTo("high");
    }

    @Test
    void imagesBecomeParts() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        new GeminiDecisionsProvider("g-key", null, null, mock.restClient).decide(TestFixtures.ticketWithImage());
        var b = mock.body();
        assertThat(b.at("/input/0/type").asString()).isEqualTo("text");
        assertThat(b.at("/input/1/type").asString()).isEqualTo("image");
        assertThat(b.at("/input/1/uri").asString()).isEqualTo("https://example.com/mug.jpg");
    }

    @Test
    void mimeTypeFromDataUri() {
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("data:image/png;base64,AAAA")).isEqualTo("image/png");
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("https://x/y.png")).isNull();
    }
}
