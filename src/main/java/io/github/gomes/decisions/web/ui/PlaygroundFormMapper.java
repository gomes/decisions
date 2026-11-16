package io.github.gomes.decisions.web.ui;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.domain.QuestionType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the playground form into a {@link DecisionRequest}, reporting every problem as a field error on the
 * form so the page can show it next to the right input.
 */
@Component
public class PlaygroundFormMapper {

    private static final Pattern QUESTION_PATH = Pattern.compile("questions\\[(\\d+)]\\.(\\w+).*");

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PlaygroundFormMapper(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** Returns the request, or empty when errors were added to {@code errors}. */
    public Optional<DecisionRequest> toRequest(PlaygroundForm form, BindingResult errors) {
        JsonNode data = parseData(form.getData(), errors);

        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < form.getQuestions().size(); i++) {
            QuestionForm qf = form.getQuestions().get(i);
            String field = "questions[" + i + "]";
            QuestionType type;
            try {
                type = QuestionType.parse(qf.getType());
            } catch (IllegalArgumentException e) {
                errors.rejectValue(field + ".type", "type", e.getMessage());
                continue;
            }
            List<Option> options = type.hasOptions() ? parseOptions(qf.getOptions()) : List.of();
            questions.add(new Question(qf.getName(), type, qf.getInstructions(), options,
                    type == QuestionType.YES_NO ? qf.getTrueWhen() : null,
                    type == QuestionType.YES_NO ? qf.getFalseWhen() : null));
        }

        DecisionRequest request = new DecisionRequest(form.getInput(), data, lines(form.getImages()), questions,
                null);
        for (ConstraintViolation<DecisionRequest> v : validator.validate(request)) {
            reject(errors, v.getPropertyPath().toString(), v.getMessage());
        }
        return errors.hasErrors() ? Optional.empty() : Optional.of(request);
    }

    /** {@code valor | descrição} per line; blank lines are ignored. */
    static List<Option> parseOptions(String text) {
        List<Option> out = new ArrayList<>();
        for (String line : lines(text)) {
            int bar = line.indexOf('|');
            String value = (bar < 0 ? line : line.substring(0, bar)).trim();
            String description = bar < 0 ? "" : line.substring(bar + 1).trim();
            if (!value.isEmpty()) {
                out.add(new Option(value, description));
            }
        }
        return out;
    }

    private JsonNode parseData(String text, BindingResult errors) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (!node.isObject() && !node.isArray()) {
                errors.rejectValue("data", "json", "Informe um objeto {...} ou uma lista [...] JSON.");
                return null;
            }
            return node;
        } catch (JacksonException e) {
            errors.rejectValue("data", "json", "JSON inválido: " + e.getOriginalMessage());
            return null;
        }
    }

    /** Maps a violation on the domain model to the closest form field. */
    private static void reject(BindingResult errors, String path, String message) {
        Matcher m = QUESTION_PATH.matcher(path);
        if (m.matches()) {
            String prop = switch (m.group(2)) {
                case "name", "type", "instructions", "trueWhen", "falseWhen" -> m.group(2);
                case "isCriteriaOnlyForYesNo", "criteriaOnlyForYesNo" -> "trueWhen";
                default -> "options";
            };
            rejectOnce(errors, "questions[" + m.group(1) + "]." + prop, message);
        } else if (path.startsWith("images")) {
            rejectOnce(errors, "images", message);
        } else if (path.equals("inputPresent") || path.equals("input")) {
            rejectOnce(errors, "input", message);
        } else if (path.equals("questions") && errors.hasFieldErrors("questions")) {
            return;
        } else {
            errors.reject("invalid", message);
        }
    }

    /** The form-level @Valid pass may already have flagged the same field; show one message per field. */
    private static void rejectOnce(BindingResult errors, String field, String message) {
        if (!errors.hasFieldErrors(field)) {
            errors.rejectValue(field, "invalid", message);
        }
    }

    private static List<String> lines(String text) {
        if (text == null) return List.of();
        return text.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
