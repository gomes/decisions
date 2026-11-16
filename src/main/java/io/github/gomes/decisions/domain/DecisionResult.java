package io.github.gomes.decisions.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Normalized result returned by any provider.
 *
 * @param provider  provider id (e.g. "openai")
 * @param model     model actually requested
 * @param answers   one answer per question, in request order
 * @param latencyMs wall-clock latency of the provider call
 * @param usage     provider usage block, as returned (may be null)
 * @param raw       full provider response, for debugging (never serialized)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DecisionResult(String provider, String model, List<Answer> answers, long latencyMs, JsonNode usage,
                             @JsonIgnore JsonNode raw) {

    public DecisionResult {
        answers = List.copyOf(answers);
    }

    public Answer answer(String name) {
        return answers.stream().filter(a -> a.name().equals(name)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("no answer for question " + name));
    }
}
