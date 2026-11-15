package io.github.gomes.decisions.model;

import java.util.Objects;

/** A possible answer for a choice question, or a level of a score question. */
public record Option(String value, String description) {
    public Option {
        Objects.requireNonNull(value, "option value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("option value must not be blank");
        }
        description = description == null ? "" : description;
    }

    public static Option of(String value) {
        return new Option(value, "");
    }

    public static Option of(String value, String description) {
        return new Option(value, description);
    }
}
