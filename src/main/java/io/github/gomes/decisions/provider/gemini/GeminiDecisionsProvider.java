package io.github.gomes.decisions.provider.gemini;

import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.provider.Capabilities;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneCodec;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini Decisions API ({@code POST /v1beta/decisions}, model {@code gemini-decision-1}).
 *
 * <p>Body accepts only {@code model}, {@code input} and {@code questions} (a map keyed by name, types
 * {@code noul | choice | score}); up to 1,000 questions per call and 2–255 options per choice.
 * Question and answer shapes follow the models of the official google-genai Java SDK (2.3.0,
 * {@code com.google.genai.gaos.models.decisions}); see {@link SystemOneCodec.Dialect#GEMINI}.
 * Images are {@code {"type":"image","uri"|"data","mime_type"}} parts.
 * The input is multimodal: plain text is sent as a string, text + images as a list of parts.
 */
public class GeminiDecisionsProvider extends HttpDecisionProvider {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String DEFAULT_MODEL = "gemini-decision-1";
    public static final Capabilities CAPABILITIES = new Capabilities(1000, true, false);

    public GeminiDecisionsProvider(String apiKey, String baseUrl, String model, RestClient restClient) {
        super("gemini",
                URI.create(trimSlash(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                        + "/v1beta/decisions"),
                Map.of("x-goog-api-key", apiKey),
                model == null || model.isBlank() ? DEFAULT_MODEL : model, CAPABILITIES, restClient);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        String text = request.textWithData();
        if (!request.hasImages()) {
            body.put("input", text);
        } else {
            ArrayNode parts = body.putArray("input");
            if (!text.isEmpty()) {
                parts.addObject().put("type", "text").put("text", text);
            }
            for (String img : request.images()) {
                imagePart(parts.addObject(), img);
            }
        }
        body.set("questions", SystemOneCodec.questions(request.questions(), SystemOneCodec.Dialect.GEMINI));
        return body;
    }

    /** {@code data:} URIs become inline base64 {@code data}; other URLs are sent as {@code uri}. */
    public static void imagePart(ObjectNode part, String image) {
        part.put("type", "image");
        String mime = mimeFromDataUri(image);
        int comma = image.indexOf(',');
        if (image.startsWith("data:") && comma > 0 && image.substring(0, comma).endsWith(";base64")) {
            part.put("data", image.substring(comma + 1));
        } else {
            part.put("uri", image);
            mime = mime != null ? mime : mimeFromExtension(image);
        }
        if (mime != null) {
            part.put("mime_type", mime);
        }
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        JsonNode feedback = response.path("prompt_feedback");
        if (feedback.hasNonNull("block_reason")) {
            // The input was blocked by content filters: no answers are returned.
            String message = feedback.path("block_reason_message").asString("");
            throw new DecisionException(id, "input blocked by content filter: " + feedback.get("block_reason").asString()
                    + (message.isBlank() ? "" : " (" + message + ")"));
        }
        return SystemOneCodec.answers(id, request, response);
    }

    static String mimeFromExtension(String url) {
        String path = url.toLowerCase(java.util.Locale.ROOT).replaceAll("[?#].*$", "");
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".heic")) return "image/heic";
        return null;
    }

    public static String mimeFromDataUri(String uri) {
        if (!uri.startsWith("data:")) return null;
        int end = uri.indexOf(';');
        if (end < 0) end = uri.indexOf(',');
        return end > 5 ? uri.substring(5, end) : null;
    }
}
