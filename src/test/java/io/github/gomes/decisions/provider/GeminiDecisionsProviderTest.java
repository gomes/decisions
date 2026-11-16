package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.provider.gemini.GeminiDecisionsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** Gemini through the official google-genai SDK, against a local server. */
class GeminiDecisionsProviderTest {

    /** Response shape of the SDK's CreateDecisionResponse (answers are typed by "type"). */
    static final String REPLY = """
            {"answers": {
               "is_damaged": {"type": "noul", "noul": 0.98, "confidence": 0.98},
               "queue": {"type": "choice", "choice": "shipping",
                         "probabilities": {"shipping": 0.9, "billing": 0.1}, "confidence": 0.9},
               "severity": {"type": "score", "score": 1.12, "probabilities": {"0": 0.05, "1": 0.78, "2": 0.17},
                            "legend": {"0": "low", "1": "medium", "2": "high: outage"}, "confidence": 0.78}
             },
             "usage": {"total_input_tokens": 120, "total_tokens": 120}}
            """;

    StubServer stub;
    GeminiDecisionsProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        provider = new GeminiDecisionsProvider("g-key", stub.url(), null);
    }

    @AfterEach
    void tearDown() throws Exception {
        provider.close();
        stub.close();
    }

    @Test
    void sendsTheOfficialRequestShape() {
        stub.reply(200, REPLY);
        provider.decide(TestFixtures.ticketWithLevelDescriptions());

        assertThat(stub.method).isEqualTo("POST");
        assertThat(stub.path).isEqualTo("/v1beta/decisions");
        assertThat(stub.headers).containsEntry("x-goog-api-key", "g-key");
        JsonNode b = stub.json();
        assertThat(b.get("model").asString()).isEqualTo("gemini-decision-1");
        assertThat(b.get("input").asString()).isEqualTo("My blue mug arrived cracked. I want a refund.");

        JsonNode isDamaged = b.at("/questions/is_damaged");
        assertThat(isDamaged.get("type").asString()).isEqualTo("noul");
        assertThat(isDamaged.get("instructions").asString()).isEqualTo("Is the item damaged?");
        assertThat(isDamaged.at("/criteria/true_description").asString()).isEqualTo("broken or cracked");
        assertThat(isDamaged.at("/criteria/false_description").asString()).isEqualTo("fine");

        JsonNode queue = b.at("/questions/queue");
        assertThat(queue.get("type").asString()).isEqualTo("choice");
        assertThat(queue.at("/criteria/billing").asString()).isEqualTo("Payments and refunds");

        JsonNode severity = b.at("/questions/severity");
        assertThat(severity.get("type").asString()).isEqualTo("score");
        assertThat(severity.get("criteria").values()).extracting(JsonNode::asString)
                .containsExactly("low", "medium", "high: outage");
    }

    @Test
    void mapsTypedAnswers() {
        stub.reply(200, REPLY);
        DecisionResult r = provider.decide(TestFixtures.ticketWithLevelDescriptions());

        assertThat(r.answer("is_damaged").value()).isEqualTo("true");
        assertThat(r.answer("is_damaged").probability()).isEqualTo(0.98);
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("queue").probabilities()).as("option order of the question")
                .containsExactly(entry("billing", 0.1), entry("shipping", 0.9));
        assertThat(r.answer("severity").value()).isEqualTo("medium");
        assertThat(r.answer("severity").score()).isEqualTo(1.12);
        assertThat(r.answer("severity").probabilities())
                .containsExactly(entry("low", 0.05), entry("medium", 0.78), entry("high", 0.17));
        assertThat(r.usage().get("input_tokens").asInt()).isEqualTo(120);
    }

    @Test
    void imagesBecomeContentParts() {
        stub.reply(200, REPLY);
        provider.decide(TestFixtures.ticketWithImage());
        JsonNode input = stub.json().get("input");
        assertThat(input.at("/0/type").asString()).isEqualTo("text");
        assertThat(input.at("/1/type").asString()).isEqualTo("image");
        assertThat(input.at("/1/uri").asString()).isEqualTo("https://example.com/mug.jpg");
        assertThat(input.at("/1/mime_type").asString()).isEqualTo("image/jpeg");
    }

    @Test
    void dataUrisAreSentAsInlineBase64() {
        var part = GeminiDecisionsProvider.imagePart("data:image/png;base64,iVBORw0KGgo=");
        assertThat(part.data()).hasValue("iVBORw0KGgo=");
        assertThat(part.uri()).isEmpty();
        assertThat(part.mimeType()).hasValueSatisfying(m -> assertThat(m.value()).isEqualTo("image/png"));
    }

    @Test
    void blockedInputIsReported() {
        stub.reply(200, """
                {"prompt_feedback": {"block_reason": "SAFETY", "block_reason_message": "unsafe content"}}""");
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessage("[gemini] input blocked by content filter: SAFETY (unsafe content)");
    }

    @Test
    void apiErrorsCarryStatusAndMessage() {
        stub.reply(400, """
                {"error": {"code": 400, "message": "Unknown parameter 'true' at 'questions.q.criteria'.",
                           "status": "INVALID_ARGUMENT"}}""");
        assertThatThrownBy(() -> provider.decide(TestFixtures.ticket()))
                .isInstanceOf(DecisionException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("Unknown parameter 'true'")
                .satisfies(e -> assertThat(((DecisionException) e).upstreamStatus()).isEqualTo(400));
    }

    @Test
    void mimeTypeFromDataUri() {
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("data:image/png;base64,AAAA")).isEqualTo("image/png");
        assertThat(GeminiDecisionsProvider.mimeFromDataUri("https://x/y.png")).isNull();
    }
}
