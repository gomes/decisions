package io.github.gomes.decisions.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * The three question shapes every current decision API supports, under different names.
 *
 * <ul>
 *   <li>{@link #YES_NO}: {@code noul} (TypeSafe/System One, Gemini), {@code predicate} (OpenAI/Azure)</li>
 *   <li>{@link #CHOICE}: {@code choice} everywhere</li>
 *   <li>{@link #SCORE}: {@code score} everywhere (an ordered scale / rubric)</li>
 * </ul>
 */
public enum QuestionType {
    YES_NO, CHOICE, SCORE;

    @JsonCreator
    public static QuestionType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("question type is required");
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "yes_no", "yesno", "boolean", "noul", "predicate" -> YES_NO;
            case "choice", "multiple_choice", "classification" -> CHOICE;
            case "score", "rubric", "scale", "ordinal" -> SCORE;
            default -> throw new IllegalArgumentException("unknown question type: " + raw);
        };
    }

    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean hasOptions() {
        return this != YES_NO;
    }
}
