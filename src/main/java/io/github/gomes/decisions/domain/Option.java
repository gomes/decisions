package io.github.gomes.decisions.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A possible answer for a choice question, or a level of a score question. */
public record Option(
        @NotBlank @Size(max = 200) String value,
        @Size(max = 2000) String description) {

    public Option {
        description = description == null ? "" : description;
    }

    public static Option of(String value) {
        return new Option(value, "");
    }

    public static Option of(String value, String description) {
        return new Option(value, description);
    }
}
