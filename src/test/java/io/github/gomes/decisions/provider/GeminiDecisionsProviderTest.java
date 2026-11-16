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

    /** Real API: 400 "The value is invalid for 'questions.urgency.criteria'. Expected string" for an object. */
    @Test
    void scoreLevelsAreAnOrderedListAndIndexKeyedProbabilitiesAreMapped() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess("""
                {"answers": {
                   "is_damaged": {"noul": 0.98, "confidence": 0.98},
                   "queue": {"choice": "shipping", "probabilities": {"billing": 0.1, "shipping": 0.9}, "confidence": 0.9},
                   "severity": {"score": 1.12,
                                "probabilities": {"0": 0.05, "1": 0.78, "2": 0.17},
                                "legend": {"0": "low", "1": "medium", "2": "high: outage"},
                                "confidence": 0.78}
                 }}
                """, MediaType.APPLICATION_JSON));

        DecisionResult r = new GeminiDecisionsProvider("g-key", null, null, mock.restClient)
                .decide(TestFixtures.ticketWithLevelDescriptions());

        var criteria = mock.body().at("/questions/severity/criteria");
        assertThat(criteria.isArray()).isTrue();
        assertThat(criteria.values()).extracting(n -> n.asString())
                .containsExactly("low", "medium", "high: outage");
        assertThat(mock.body().at("/questions/queue/criteria").isObject())
                .as("choice options stay an id -> description object").isTrue();

        // Real API: 400 "Unknown parameter 'true' at 'questions.is_damaged.criteria'"; the SDK's NoulCriteria
        // uses true_description / false_description.
        var isDamaged = mock.body().at("/questions/is_damaged");
        assertThat(isDamaged.at("/criteria/true_description").asString()).isEqualTo("broken or cracked");
        assertThat(isDamaged.at("/criteria/false_description").asString()).isEqualTo("fine");
        assertThat(isDamaged.at("/criteria").propertyNames()).containsExactly("true_description", "false_description");
        assertThat(isDamaged.get("instructions").asString()).isEqualTo("Is the item damaged?");

        var severity = r.answer("severity");
        assertThat(severity.value()).isEqualTo("medium");
        assertThat(severity.score()).isEqualTo(1.12);
        assertThat(severity.probabilities()).containsExactly(
                org.assertj.core.api.Assertions.entry("low", 0.05),
                org.assertj.core.api.Assertions.entry("medium", 0.78),
                org.assertj.core.api.Assertions.entry("high", 0.17));
    }

    @Test
    void imagesBecomeParts() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        new GeminiDecisionsProvider("g-key", null, null, mock.restClient).decide(TestFixtures.ticketWithImage());
        var b = mock.body();
        assertThat(b.at("/input/0/type").asString()).isEqualTo("text");
        assertThat(b.at("/input/1/type").asString()).isEqualTo("image");
        assertThat(b.at("/input/1/uri").asString()).isEqualTo("https://example.com/mug.jpg");
        assertThat(b.at("/input/1/mime_type").asString()).isEqualTo("image/jpeg");
    }

    @Test
    void dataUrisAreSentAsInlineBase64() {
        var part = tools.jackson.databind.json.JsonMapper.shared().createObjectNode();
        GeminiDecisionsProvider.imagePart(part, "data:image/png;base64,iVBORw0KGgo=");
        assertThat(part.toString()).isEqualTo("{\"type\":\"image\",\"data\":\"iVBORw0KGgo=\",\"mime_type\":\"image/png\"}");
    }

    @Test
    void blockedInputIsReportedInsteadOfMissingAnswers() {
        mock.server.expect(mock.captureBody()).andRespond(withSuccess("""
                {"prompt_feedback": {"block_reason": "SAFETY", "block_reason_message": "unsafe content"}}
                """, MediaType.APPLICATION_JSON));
        var provider = new GeminiDecisionsProvider("g-key", null, null, mock.restClient);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessage("[gemini] input blocked by content filter: SAFETY (unsafe content)");
    }

    @Test
    void mimeTypeFromDataUri() {
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("data:image/png;base64,AAAA")).isEqualTo("image/png");
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("https://x/y.png")).isNull();
    }
}
