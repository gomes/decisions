package io.github.gomes.decisions.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A provider-neutral question.
 *
 * @param name         key used to match the answer (unique within a request)
 * @param type         yes/no, choice or score
 * @param instructions what the model should decide
 * @param options      choices (CHOICE) or ordered levels, lowest first (SCORE); empty for YES_NO
 * @param trueWhen     optional description of the "true" case (YES_NO only)
 * @param falseWhen    optional description of the "false" case (YES_NO only)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Question(
        @NotBlank @Pattern(regexp = NAME_PATTERN, message = "{decisions.question.name}") String name,
        @NotNull QuestionType type,
        @NotBlank @Size(max = 4000) String instructions,
        @Valid @Size(max = MAX_OPTIONS) List<Option> options,
        @Size(max = 2000) String trueWhen,
        @Size(max = 2000) String falseWhen) {

    public static final String NAME_PATTERN = "[A-Za-z][A-Za-z0-9_\\-]{0,63}";
    public static final int MAX_OPTIONS = 255;

    public Question {
        options = options == null ? List.of() : List.copyOf(options);
        trueWhen = blankToNull(trueWhen);
        falseWhen = blankToNull(falseWhen);
    }

    @JsonIgnore
    @AssertTrue(message = "{decisions.question.options}")
    public boolean isOptionsConsistentWithType() {
        if (type == null) return true;
        if (!type.hasOptions()) return options.isEmpty();
        return options.size() >= 2 && options.size() <= MAX_OPTIONS;
    }

    @JsonIgnore
    @AssertTrue(message = "{decisions.question.options.unique}")
    public boolean isOptionValuesUnique() {
        Set<String> seen = new HashSet<>();
        return options.stream().allMatch(o -> o.value() == null || seen.add(o.value()));
    }

    @JsonIgnore
    @AssertTrue(message = "{decisions.question.criteria}")
    public boolean isCriteriaOnlyForYesNo() {
        return type == null || type == QuestionType.YES_NO || (trueWhen == null && falseWhen == null);
    }

    public static Question yesNo(String name, String instructions) {
        return new Question(name, QuestionType.YES_NO, instructions, List.of(), null, null);
    }

    public static Question yesNo(String name, String instructions, String trueWhen, String falseWhen) {
        return new Question(name, QuestionType.YES_NO, instructions, List.of(), trueWhen, falseWhen);
    }

    public static Question choice(String name, String instructions, Option... options) {
        return new Question(name, QuestionType.CHOICE, instructions, List.of(options), null, null);
    }

    /** Levels are ordered from lowest to highest. */
    public static Question score(String name, String instructions, Option... levels) {
        return new Question(name, QuestionType.SCORE, instructions, List.of(levels), null, null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
