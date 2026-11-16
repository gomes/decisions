package io.github.gomes.decisions.provider.openai;

import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.DecisionInputJson;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionChoiceValue;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputItem;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.models.decisions.DecisionInputText;
import io.github.gomes.decisions.domain.Answer;
import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.provider.Answers;
import io.github.gomes.decisions.provider.Capabilities;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.provider.DecisionProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Decisions API ({@code POST /v1/decisions}, model {@code gpt-6-luna}) through the official
 * {@code com.openai:openai-java} SDK. The same class serves Azure OpenAI in Microsoft Foundry, using the
 * unified {@code https://<resource>.openai.azure.com/openai/v1} endpoint and the {@code api-key} header.
 *
 * <p>Up to 400 questions per call. Any question may come back as a {@code refusal} instead of an answer.
 */
public class OpenAiDecisionsProvider implements DecisionProvider, AutoCloseable {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final String DEFAULT_MODEL = "gpt-6-luna";
    public static final Capabilities CAPABILITIES = new Capabilities(400, true, true);

    private static final JsonMapper JSON = JsonMapper.shared();

    private final String id;
    private final OpenAIClient client;
    private final String baseUrl;
    private final String defaultModel;

    OpenAiDecisionsProvider(String id, OpenAIClient client, String baseUrl, String defaultModel) {
        this.id = id;
        this.client = client;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
    }

    /** @param baseUrl API host, without {@code /v1} (default {@value #DEFAULT_BASE_URL}) */
    public static OpenAiDecisionsProvider openAi(String apiKey, String baseUrl, String model, Duration timeout,
                                                 int maxRetries) {
        String url = trimSlash(isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl) + "/v1";
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(url)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
        return new OpenAiDecisionsProvider("openai", client, url, isBlank(model) ? DEFAULT_MODEL : model);
    }

    /**
     * Azure OpenAI v1 API. {@code endpoint} is the resource endpoint ({@code https://<resource>.openai.azure.com})
     * and {@code deployment} the name you gave the gpt-6-luna deployment.
     */
    public static OpenAiDecisionsProvider azure(String endpoint, String apiKey, String deployment, Duration timeout,
                                                int maxRetries) {
        String url = trimSlash(endpoint) + "/openai/v1";
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(url)
                .credential(AzureApiKeyCredential.create(apiKey))
                .azureUrlPathMode(AzureUrlPathMode.UNIFIED)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
        return new OpenAiDecisionsProvider("azure-openai", client, url, isBlank(deployment) ? DEFAULT_MODEL : deployment);
    }

    @Override public String id() { return id; }
    @Override public String defaultModel() { return defaultModel; }
    @Override public Capabilities capabilities() { return CAPABILITIES; }

    /** Base URL handed to the SDK (includes {@code /v1} or {@code /openai/v1}). */
    public String baseUrl() { return baseUrl; }

    @Override
    public DecisionResult decide(DecisionRequest request) {
        checkSupported(request);
        String model = request.model() != null ? request.model() : defaultModel;
        DecisionCreateParams params = toSdkRequest(request, model);

        long start = System.nanoTime();
        Decision decision;
        try {
            decision = client.decisions().create(params);
        } catch (OpenAIServiceException e) {
            String body = String.valueOf(e.body());
            throw new DecisionException(id, "HTTP " + e.statusCode() + ": " + e.getMessage(), e.statusCode(), body, e);
        } catch (OpenAIIoException e) {
            throw new DecisionException(id, "could not reach " + baseUrl + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new DecisionException(id, "call failed: " + e.getMessage(), e);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return new DecisionResult(id, model, toAnswers(request, decision), latencyMs, usage(decision), null);
    }

    /** Maps the neutral request onto the SDK's typed request. */
    static DecisionCreateParams toSdkRequest(DecisionRequest request, String model) {
        DecisionCreateParams.Builder params = DecisionCreateParams.builder().model(model);

        if (!request.hasData() && !request.hasImages()) {
            params.input(request.input());
        } else {
            List<DecisionInputPart> parts = new ArrayList<>();
            if (request.hasText()) {
                parts.add(DecisionInputPart.ofInputText(DecisionInputText.builder().text(request.input()).build()));
            }
            if (request.hasData()) {
                parts.add(DecisionInputPart.ofInputJson(inputJson(request.data())));
            }
            for (String image : request.images()) {
                parts.add(DecisionInputPart.ofInputImage(DecisionInputImage.builder().imageUrl(image).build()));
            }
            params.inputOfDecisionInputItems(List.of(DecisionInputItem.ofDecisionInputMessage(
                    DecisionInputMessage.ofUser(DecisionInputMessage.User.builder().contentOfParts(parts).build()))));
        }

        for (Question q : request.questions()) {
            switch (q.type()) {
                // Predicates have no separate true/false criteria, so they are folded into the instructions.
                case YES_NO -> params.addQuestion(DecisionCreateParams.Question.Predicate.builder()
                        .name(q.name())
                        .instructions(SystemOneCodec.instructionsWithCriteria(q))
                        .build());
                case CHOICE -> {
                    var choice = DecisionCreateParams.Question.Choice.builder()
                            .name(q.name())
                            .instructions(q.instructions());
                    for (Option o : q.options()) {
                        var option = DecisionChoiceOption.builder().value(o.value());
                        if (!o.description().isBlank()) option.description(o.description());
                        choice.addChoice(option.build());
                    }
                    params.addQuestion(choice.build());
                }
                case SCORE -> {
                    var score = DecisionCreateParams.Question.Score.builder()
                            .name(q.name())
                            .instructions(q.instructions());
                    for (Option o : q.options()) {
                        var level = DecisionCreateParams.Question.Score.Level.builder().label(o.value());
                        if (!o.description().isBlank()) level.description(o.description());
                        score.addLevel(level.build());
                    }
                    params.addQuestion(score.build());
                }
            }
        }
        return params.build();
    }

    /** {@code input_json} takes an object or a list of objects; anything else is wrapped in {@code {"value": ...}}. */
    private static DecisionInputJson inputJson(JsonNode data) {
        if (data.isArray() && data.valueStream().allMatch(JsonNode::isObject)) {
            List<Map<String, JsonValue>> maps = new ArrayList<>();
            data.valueStream().forEach(item -> maps.add(toJsonValues(item)));
            return DecisionInputJson.builder().dataOfMaps(maps).build();
        }
        JsonNode object = data.isObject() ? data : JSON.createObjectNode().set("value", data);
        return DecisionInputJson.builder()
                .data(DecisionInputJson.Data.UnionMember0.builder().additionalProperties(toJsonValues(object)).build())
                .build();
    }

    private static Map<String, JsonValue> toJsonValues(JsonNode object) {
        Map<String, JsonValue> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : object.properties()) {
            out.put(e.getKey(), JsonValue.from(JSON.convertValue(e.getValue(), Object.class)));
        }
        return out;
    }

    static List<Answer> toAnswers(DecisionRequest request, Decision decision) {
        List<Decision.Answer> answers = decision.answers();
        List<Question> questions = request.questions();
        List<Answer> out = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Decision.Answer answer = find(answers, q.name()).orElse(i < answers.size() ? answers.get(i) : null);
            out.add(toAnswer(q, answer));
        }
        return out;
    }

    private static java.util.Optional<Decision.Answer> find(List<Decision.Answer> answers, String name) {
        return answers.stream().filter(a -> name.equals(nameOf(a))).findFirst();
    }

    private static String nameOf(Decision.Answer a) {
        if (a.isPredicate()) return a.asPredicate().name().orElse(null);
        if (a.isChoice()) return a.asChoice().name().orElse(null);
        if (a.isScore()) return a.asScore().name().orElse(null);
        if (a.isRefusal()) return a.asRefusal().name().orElse(null);
        return null;
    }

    private static Answer toAnswer(Question q, Decision.Answer a) {
        if (a == null) {
            throw new DecisionException("openai", "missing answer for question '" + q.name() + "'");
        }
        if (a.isRefusal()) {
            return Answer.refusal(q.name(), q.type());
        }
        if (a.isPredicate()) {
            double p = a.asPredicate().probability();
            return new Answer(q.name(), q.type(), String.valueOf(p >= 0.5), p, null, null,
                    Answers.round(Math.abs(2 * p - 1)));
        }
        if (a.isChoice()) {
            var choice = a.asChoice();
            Map<String, Double> probs = new LinkedHashMap<>();
            choice.probabilities().forEach(p -> probs.put(text(p.value()), p.probability()));
            return new Answer(q.name(), q.type(), text(choice.choice()), null, null, probs, choice.confidence());
        }
        if (a.isScore()) {
            var score = a.asScore();
            Map<String, Double> probs = new LinkedHashMap<>();
            score.probabilities().forEach(p -> probs.put(p.label(), p.probability()));
            String value = probs.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                    .orElse(null);
            return new Answer(q.name(), q.type(), value, null, score.score(), probs, score.confidence());
        }
        throw new DecisionException("openai", "unexpected answer for question '" + q.name() + "'");
    }

    private static String text(DecisionChoiceValue value) {
        return value.isString() ? value.asString() : String.valueOf(value.asBool());
    }

    private static ObjectNode usage(Decision decision) {
        com.openai.models.decisions.Decision.Usage u;
        try {
            u = decision.usage();
        } catch (RuntimeException missing) {
            return null;
        }
        ObjectNode n = JSON.createObjectNode();
        n.put("input_tokens", u.inputTokens());
        n.put("output_tokens", u.outputTokens());
        n.put("total_tokens", u.totalTokens());
        return n;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimSlash(String s) {
        return s.replaceAll("/+$", "");
    }

    @Override
    public void close() {
        client.close();
    }
}
