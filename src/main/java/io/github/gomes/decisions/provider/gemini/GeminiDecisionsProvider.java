package io.github.gomes.decisions.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.Answer;
import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneCodec;
import io.github.gomes.decisions.spi.Capabilities;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini Decisions API ({@code POST /v1beta/decisions}, model {@code gemini-decision-1}).
 *
 * <p>Body accepts only {@code model}, {@code input} and {@code questions} (a map keyed by name, types
 * {@code noul | choice | score}); up to 1,000 questions per call and 2–255 options per choice.
 * The input is multimodal: plain text is sent as a string, text + images as a list of parts.
 */
public class GeminiDecisionsProvider extends HttpDecisionProvider {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String DEFAULT_MODEL = "gemini-decision-1";
    public static final Capabilities CAPABILITIES = new Capabilities(1000, true, false);

    public GeminiDecisionsProvider(String apiKey, String baseUrl, String model, JsonHttp http) {
        super("gemini", "Google Gemini Decisions",
                URI.create(trimSlash(baseUrl == null ? DEFAULT_BASE_URL : baseUrl) + "/v1beta/decisions"),
                Map.of("x-goog-api-key", apiKey),
                model == null ? DEFAULT_MODEL : model, CAPABILITIES, http);
    }

    @Override
    public ObjectNode buildBody(DecisionRequest request, String model) {
        ObjectNode body = JsonHttp.MAPPER.createObjectNode();
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
                ObjectNode p = parts.addObject().put("type", "image").put("uri", img);
                String mime = mimeFromDataUri(img);
                if (mime != null) p.put("mime_type", mime);
            }
        }
        body.set("questions", SystemOneCodec.questions(request.questions()));
        return body;
    }

    @Override
    public List<Answer> parseAnswers(DecisionRequest request, JsonNode response) {
        return SystemOneCodec.answers(id, request, response);
    }

    public static String mimeFromDataUri(String uri) {
        if (!uri.startsWith("data:")) return null;
        int end = uri.indexOf(';');
        if (end < 0) end = uri.indexOf(',');
        return end > 5 ? uri.substring(5, end) : null;
    }
}
