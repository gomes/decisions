package io.github.gomes.decisions.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

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
public record DecisionRequest(
        @Size(max = 200_000) String input,
        JsonNode data,
        @Size(max = 16) List<@Size(max = 10_000_000) String> images,
        @Valid @NotEmpty(message = "{decisions.request.questions}") @Size(max = 1000) List<Question> questions,
        @Size(max = 200) String model) {

    public DecisionRequest {
        images = images == null ? List.of() : images.stream().filter(s -> s != null && !s.isBlank()).toList();
        questions = questions == null ? List.of() : List.copyOf(questions);
        model = model == null || model.isBlank() ? null : model.trim();
        if (data != null && (data.isNull() || data.isMissingNode())) {
            data = null;
        }
    }

    @JsonIgnore
    @AssertTrue(message = "{decisions.request.input}")
    public boolean isInputPresent() {
        return hasText() || hasData() || hasImages();
    }

    @JsonIgnore
    @AssertTrue(message = "{decisions.request.questions.unique}")
    public boolean isQuestionNamesUnique() {
        Set<String> names = new HashSet<>();
        return questions.stream().allMatch(q -> q == null || q.name() == null || names.add(q.name()));
    }

    public boolean hasText() {
        return input != null && !input.isBlank();
    }

    public boolean hasImages() {
        return !images.isEmpty();
    }

    public boolean hasData() {
        return data != null;
    }

    /** Input text plus serialized JSON data, for providers that only take a single text state. */
    public String textWithData() {
        StringBuilder sb = new StringBuilder();
        if (hasText()) {
            sb.append(input);
        }
        if (hasData()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(data);
        }
        return sb.toString();
    }

    public DecisionRequest withModel(String newModel) {
        return new DecisionRequest(input, data, images, questions, newModel);
    }

    public static Builder builder() {
        return new Builder();
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
