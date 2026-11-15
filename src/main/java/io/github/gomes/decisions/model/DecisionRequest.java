package io.github.gomes.decisions.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A provider-neutral decision request: one shared input and N questions about it.
 *
 * @param input  free text to decide about (the "state" in System One terms)
 * @param data   optional structured JSON context
 * @param images optional image URLs (https or data: URIs); only multimodal providers accept them
 * @param model  optional model override; when null the provider's configured default is used
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DecisionRequest(String input, JsonNode data, List<String> images, List<Question> questions,
                              String model) {

    public DecisionRequest {
        images = images == null ? List.of() : List.copyOf(images);
        questions = questions == null ? List.of() : List.copyOf(questions);
        boolean hasInput = input != null && !input.isBlank();
        boolean hasData = data != null && !data.isNull() && !data.isMissingNode();
        if (!hasInput && !hasData && images.isEmpty()) {
            throw new IllegalArgumentException("request needs input text, data or at least one image");
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("request needs at least one question");
        }
        Set<String> names = new HashSet<>();
        for (Question q : questions) {
            if (!names.add(q.name())) {
                throw new IllegalArgumentException("duplicate question name: " + q.name());
            }
        }
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public boolean hasData() {
        return data != null && !data.isNull() && !data.isMissingNode();
    }

    /** Input text plus serialized JSON data, for providers that only take a single text state. */
    public String textWithData() {
        StringBuilder sb = new StringBuilder();
        if (input != null && !input.isBlank()) {
            sb.append(input);
        }
        if (hasData()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(data.toString());
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public DecisionRequest withModel(String newModel) {
        return new DecisionRequest(input, data, images, questions, newModel);
    }

    public static final class Builder {
        private String input;
        private JsonNode data;
        private String model;
        private final List<String> images = new ArrayList<>();
        private final List<Question> questions = new ArrayList<>();

        public Builder input(String input) { this.input = input; return this; }
        public Builder data(JsonNode data) { this.data = data; return this; }
        public Builder image(String url) { this.images.add(url); return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder question(Question q) { this.questions.add(q); return this; }

        public DecisionRequest build() {
            return new DecisionRequest(input, data, images, questions, model);
        }
    }
}
