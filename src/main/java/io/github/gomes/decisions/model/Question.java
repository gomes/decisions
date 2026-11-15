package io.github.gomes.decisions.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
public record Question(String name, QuestionType type, String instructions, List<Option> options,
                       String trueWhen, String falseWhen) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_\\-]{0,63}");
    public static final int MAX_OPTIONS = 255;

    public Question {
        Objects.requireNonNull(name, "question name is required");
        Objects.requireNonNull(type, "question type is required");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid question name '" + name
                    + "': use letters, digits, '_' or '-', starting with a letter (max 64)");
        }
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("question '" + name + "' needs instructions");
        }
        options = options == null ? List.of() : List.copyOf(options);
        switch (type) {
            case YES_NO -> {
                if (!options.isEmpty()) {
                    throw new IllegalArgumentException("yes/no question '" + name + "' must not have options");
                }
            }
            case CHOICE, SCORE -> {
                if (options.size() < 2 || options.size() > MAX_OPTIONS) {
                    throw new IllegalArgumentException("question '" + name + "' needs between 2 and "
                            + MAX_OPTIONS + " options, got " + options.size());
                }
                Set<String> seen = new HashSet<>();
                for (Option o : options) {
                    if (!seen.add(o.value())) {
                        throw new IllegalArgumentException("question '" + name + "' has duplicate option '"
                                + o.value() + "'");
                    }
                }
            }
        }
        if (type != QuestionType.YES_NO && (trueWhen != null || falseWhen != null)) {
            throw new IllegalArgumentException("trueWhen/falseWhen only apply to yes/no questions");
        }
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
}
