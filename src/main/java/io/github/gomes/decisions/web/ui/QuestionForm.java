package io.github.gomes.decisions.web.ui;

import io.github.gomes.decisions.domain.Question;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * One question as typed in the playground. Options are entered one per line as {@code valor | descrição}.
 */
public class QuestionForm {

    @NotBlank(message = "{playground.question.name.required}")
    @Pattern(regexp = Question.NAME_PATTERN, message = "{decisions.question.name}")
    private String name;

    @NotBlank
    private String type = "yes_no";

    @NotBlank(message = "{playground.question.instructions.required}")
    private String instructions;

    private String options;
    private String trueWhen;
    private String falseWhen;

    public QuestionForm() {
    }

    public QuestionForm(String name, String type, String instructions, String options, String trueWhen,
                        String falseWhen) {
        this.name = name;
        this.type = type;
        this.instructions = instructions;
        this.options = options;
        this.trueWhen = trueWhen;
        this.falseWhen = falseWhen;
    }

    public static QuestionForm yesNo(String name, String instructions, String trueWhen, String falseWhen) {
        return new QuestionForm(name, "yes_no", instructions, null, trueWhen, falseWhen);
    }

    public static QuestionForm choice(String name, String instructions, String options) {
        return new QuestionForm(name, "choice", instructions, options, null, null);
    }

    public static QuestionForm score(String name, String instructions, String levels) {
        return new QuestionForm(name, "score", instructions, levels, null, null);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getTrueWhen() { return trueWhen; }
    public void setTrueWhen(String trueWhen) { this.trueWhen = trueWhen; }
    public String getFalseWhen() { return falseWhen; }
    public void setFalseWhen(String falseWhen) { this.falseWhen = falseWhen; }
}
