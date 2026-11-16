package io.github.gomes.decisions.provider.gemini;

import com.google.genai.gaos.GenAI;
import com.google.genai.gaos.models.decisions.ChoiceAnswer;
import com.google.genai.gaos.models.decisions.ChoiceQuestion;
import com.google.genai.gaos.models.decisions.ChoiceQuestionInstructions;
import com.google.genai.gaos.models.decisions.CreateDecisionRequest;
import com.google.genai.gaos.models.decisions.CreateDecisionResponse;
import com.google.genai.gaos.models.decisions.DecisionInputContent;
import com.google.genai.gaos.models.decisions.Input;
import com.google.genai.gaos.models.decisions.NoulAnswer;
import com.google.genai.gaos.models.decisions.NoulCriteria;
import com.google.genai.gaos.models.decisions.NoulQuestion;
import com.google.genai.gaos.models.decisions.NoulQuestionInstructions;
import com.google.genai.gaos.models.decisions.ScoreAnswer;
import com.google.genai.gaos.models.decisions.ScoreQuestion;
import com.google.genai.gaos.models.decisions.ScoreQuestionInstructions;
import com.google.genai.gaos.models.errors.GaosBaseException;
import com.google.genai.gaos.models.interactions.ImageContent;
import com.google.genai.gaos.models.interactions.ImageContentMimeType;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.shared.Security;
import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.domain.QuestionType;
import io.github.gomes.decisions.provider.Answers;
import io.github.gomes.decisions.provider.Capabilities;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.provider.DecisionProvider;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Google Gemini Decisions API ({@code POST /v1beta/decisions}, model {@code gemini-decision-1}) through the
 * official {@code com.google.genai:google-genai} SDK ({@code com.google.genai.gaos.GenAI#decisions()}).
 *
 * <p>Up to 1,000 questions per call and 2–255 options per choice/score question. Input is multimodal:
 * plain text is sent as a string, text + images as a list of content parts.
 */
public class GeminiDecisionsProvider implements DecisionProvider, AutoCloseable {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String DEFAULT_MODEL = "gemini-decision-1";
    /** The SDK leaves the {@code {api_version}} path segment unset unless it is configured explicitly. */
    public static final String API_VERSION = "v1beta";
    public static final Capabilities CAPABILITIES = new Capabilities(1000, true, false);

    private static final JsonMapper JSON = JsonMapper.shared();

    private final GenAI client;
    private final String baseUrl;
    private final String defaultModel;

    public GeminiDecisionsProvider(String apiKey, String baseUrl, String model) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        this.defaultModel = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.client = GenAI.builder()
                .serverURL(this.baseUrl)
                .apiVersion(API_VERSION)
                .security(Security.builder().apiKey(apiKey).build())
                .build();
    }

    @Override public String id() { return "gemini"; }
    @Override public String defaultModel() { return defaultModel; }
    @Override public Capabilities capabilities() { return CAPABILITIES; }

    public String baseUrl() { return baseUrl; }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        checkSupported(request);
        String model = request.model() != null ? request.model() : defaultModel;
        CreateDecisionRequest body = toSdkRequest(request, model);

        long start = System.nanoTime();
        CreateDecisionResponse response;
        try {
            response = client.decisions().create(body).createDecisionResponse()
                    .orElseThrow(() -> new DecisionException(id(), "empty response body"));
        } catch (GaosBaseException e) {
            String responseBody = e.bodyAsString().orElse(null);
            throw new DecisionException(id(), "HTTP " + e.code() + ": " + errorMessage(e, responseBody),
                    e.code(), responseBody, e);
        } catch (DecisionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DecisionException(id(), "call failed: " + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return new DecisionResult(id(), model, toAnswers(request, response), latencyMs, usage(response), null);
    }

    /** Maps the neutral request onto the SDK's typed request. */
    static CreateDecisionRequest toSdkRequest(DecisionRequest request, String model) {
        Map<String, com.google.genai.gaos.models.decisions.Question> questions = new LinkedHashMap<>();
        for (Question q : request.questions()) {
            questions.put(q.name(), switch (q.type()) {
                case YES_NO -> NoulQuestion.builder()
                        .instructions(NoulQuestionInstructions.of(q.instructions()))
                        .criteria(q.trueWhen() == null && q.falseWhen() == null ? null
                                : NoulCriteria.builder().trueDescription(q.trueWhen())
                                        .falseDescription(q.falseWhen()).build())
                        .build();
                case CHOICE -> {
                    Map<String, String> options = new LinkedHashMap<>();
                    q.options().forEach(o -> options.put(o.value(), describe(o)));
                    yield ChoiceQuestion.builder()
                            .instructions(ChoiceQuestionInstructions.of(q.instructions()))
                            .criteria(options)
                            .build();
                }
                case SCORE -> ScoreQuestion.builder()
                        .instructions(ScoreQuestionInstructions.of(q.instructions()))
                        // Ordered levels, lowest first; the label keeps the option value as a prefix.
                        .criteria(q.options().stream()
                                .map(o -> o.description().isBlank() ? o.value() : o.value() + ": " + o.description())
                                .toList())
                        .build();
            });
        }
        return CreateDecisionRequest.builder()
                .model(model)
                .input(input(request))
                .questions(questions)
                .build();
    }

    private static Input input(DecisionRequest request) {
        String text = request.textWithData();
        if (!request.hasImages()) {
            return Input.of(text);
        }
        List<DecisionInputContent> parts = new ArrayList<>();
        if (!text.isEmpty()) {
            parts.add(TextContent.builder().text(text).build());
        }
        for (String image : request.images()) {
            parts.add(imagePart(image));
        }
        return Input.of(parts);
    }

    /** {@code data:} URIs become inline base64 {@code data}; other URLs are sent as {@code uri}. */
    public static ImageContent imagePart(String image) {
        ImageContent.Builder part = ImageContent.builder();
        String mime = mimeFromDataUri(image);
        int comma = image.indexOf(',');
        if (image.startsWith("data:") && comma > 0 && image.substring(0, comma).endsWith(";base64")) {
            part.data(image.substring(comma + 1));
        } else {
            part.uri(image);
            mime = mime != null ? mime : mimeFromExtension(image);
        }
        if (mime != null) {
            part.mimeType(ImageContentMimeType.of(mime));
        }
        return part.build();
    }

    static List<Answer> toAnswers(DecisionRequest request, CreateDecisionResponse response) {
        response.promptFeedback().ifPresent(feedback -> feedback.blockReason().ifPresent(reason -> {
            // The input was blocked by content filters: no answers are returned.
            String message = feedback.blockReasonMessage().filter(m -> !m.isBlank()).map(m -> " (" + m + ")").orElse("");
            throw new DecisionException("gemini", "input blocked by content filter: " + reason.value() + message);
        }));
        Map<String, com.google.genai.gaos.models.decisions.Answer> answers = response.answers().orElse(Map.of());
        List<Answer> out = new ArrayList<>();
        for (Question q : request.questions()) {
            out.add(toAnswer(q, answers.get(q.name())));
        }
        return out;
    }

    private static Answer toAnswer(Question q, com.google.genai.gaos.models.decisions.Answer answer) {
        if (q.type() == QuestionType.YES_NO && answer instanceof NoulAnswer a) {
            double p = a.noul().orElseThrow(() -> missing(q));
            return new Answer(q.name(), q.type(), String.valueOf(p >= 0.5), p, null, null,
                    a.confidence().orElse(Answers.round(Math.abs(2 * p - 1))));
        }
        if (q.type() == QuestionType.CHOICE && answer instanceof ChoiceAnswer a) {
            Map<String, Double> probs = ordered(q.options(), a.probabilities().orElse(Map.of()));
            String value = a.choice().orElseGet(() -> argmax(probs));
            return new Answer(q.name(), q.type(), value, null, null, probs.isEmpty() ? null : probs,
                    a.confidence().orElse(probs.isEmpty() ? null : Answers.margin(probs)));
        }
        if (q.type() == QuestionType.SCORE && answer instanceof ScoreAnswer a) {
            // Level probabilities are keyed by 0-based level index.
            Map<String, Double> probs = ordered(q.options(),
                    Answers.byOptionValue(q.options(), a.probabilities().orElse(Map.of())));
            Double score = a.score().orElse(probs.isEmpty() ? null : Answers.expectedIndex(q.options(), probs));
            String value = probs.isEmpty()
                    ? q.options().get((int) Math.round(Math.max(0, Math.min(q.options().size() - 1, score)))).value()
                    : argmax(probs);
            return new Answer(q.name(), q.type(), value, null, score, probs.isEmpty() ? null : probs,
                    a.confidence().orElse(probs.isEmpty() ? null : Answers.margin(probs)));
        }
        if (answer == null) {
            throw missing(q);
        }
        throw new DecisionException("gemini", "unexpected answer type '" + answer.type() + "' for question '"
                + q.name() + "' (" + q.type().wireName() + ")");
    }

    private static ObjectNode usage(CreateDecisionResponse response) {
        return response.usage().map(u -> {
            ObjectNode n = JSON.createObjectNode();
            u.totalInputTokens().ifPresent(v -> n.put("input_tokens", v));
            u.totalOutputTokens().ifPresent(v -> n.put("output_tokens", v));
            u.totalCachedTokens().ifPresent(v -> n.put("cached_tokens", v));
            u.totalTokens().ifPresent(v -> n.put("total_tokens", v));
            return n;
        }).orElse(null);
    }

    /** Keeps the option order of the question (the API returns a map without a meaningful order). */
    private static Map<String, Double> ordered(List<Option> options, Map<String, Double> probs) {
        Map<String, Double> out = new LinkedHashMap<>();
        options.forEach(o -> {
            if (probs.containsKey(o.value())) out.put(o.value(), probs.get(o.value()));
        });
        probs.forEach(out::putIfAbsent);
        return out;
    }

    private static String argmax(Map<String, Double> probs) {
        return probs.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static String describe(Option o) {
        return o.description().isBlank() ? o.value() : o.description();
    }

    private static DecisionException missing(Question q) {
        return new DecisionException("gemini", "missing answer for question '" + q.name() + "'");
    }

    private static String errorMessage(GaosBaseException e, String body) {
        if (body != null && !body.isBlank()) {
            try {
                var node = JSON.readTree(body);
                if (node.path("error").path("message").isString()) return node.path("error").path("message").asString();
            } catch (RuntimeException ignored) {
                // not JSON
            }
        }
        return e.message();
    }

    public static String mimeFromDataUri(String uri) {
        if (!uri.startsWith("data:")) return null;
        int end = uri.indexOf(';');
        if (end < 0) end = uri.indexOf(',');
        return end > 5 ? uri.substring(5, end) : null;
    }

    static String mimeFromExtension(String url) {
        String path = url.toLowerCase(Locale.ROOT).replaceAll("[?#].*$", "");
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".gif")) return "image/gif";
        if (path.endsWith(".heic")) return "image/heic";
        return null;
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
