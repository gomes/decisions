package io.github.gomes.decisions.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A provider-neutral answer.
 *
 * <ul>
 *   <li>YES_NO: {@code probability} is P(true); {@code value} is "true"/"false" (threshold 0.5).</li>
 *   <li>CHOICE: {@code value} is the chosen option; {@code probabilities} per option.</li>
 *   <li>SCORE: {@code value} is the most likely level; {@code score} is the expected level index
 *       (0 = lowest); {@code probabilities} per level.</li>
 * </ul>
 *
 * {@code confidence} is the provider-reported confidence when available; otherwise it is derived as
 * the margin between the two most likely outcomes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Answer(String name, QuestionType type, String value, Double probability, Double score,
                     Map<String, Double> probabilities, Double confidence) {

    public Answer {
        probabilities = probabilities == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }

    public Optional<Boolean> asBoolean() {
        return type == QuestionType.YES_NO && value != null
                ? Optional.of(Boolean.parseBoolean(value)) : Optional.empty();
    }
}
