package io.github.gomes.decisions.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionRequestValidationTest {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    static List<String> paths(DecisionRequest r) {
        Set<ConstraintViolation<DecisionRequest>> v = validator.validate(r);
        return v.stream().map(c -> c.getPropertyPath().toString()).sorted().toList();
    }

    @Test
    void validRequestHasNoViolations() {
        assertThat(paths(DecisionRequest.builder().input("x")
                .question(Question.choice("q", "?", Option.of("a"), Option.of("b"))).build())).isEmpty();
    }

    @Test
    void needsSomeInputAndAtLeastOneQuestion() {
        assertThat(paths(new DecisionRequest(" ", null, List.of(" "), List.of(), null)))
                .containsExactly("inputPresent", "questions");
    }

    @Test
    void checksQuestionShapes() {
        DecisionRequest r = DecisionRequest.builder().input("x")
                .question(new Question("1bad", QuestionType.CHOICE, "?", List.of(Option.of("only")), null, null))
                .question(new Question("dup", QuestionType.YES_NO, "?", List.of(Option.of("a"), Option.of("b")), null, null))
                .question(new Question("dup", QuestionType.SCORE, "?", List.of(Option.of("a"), Option.of("a")), "t", null))
                .build();
        assertThat(paths(r)).containsExactly(
                "questionNamesUnique",
                "questions[0].name",
                "questions[0].optionsConsistentWithType",
                "questions[1].optionsConsistentWithType",
                "questions[2].criteriaOnlyForYesNo",
                "questions[2].optionValuesUnique");
    }

    @Test
    void questionTypeAcceptsProviderSpecificNames() {
        assertThat(QuestionType.parse("noul")).isEqualTo(QuestionType.YES_NO);
        assertThat(QuestionType.parse("predicate")).isEqualTo(QuestionType.YES_NO);
        assertThat(QuestionType.parse("Multiple-Choice")).isEqualTo(QuestionType.CHOICE);
        assertThat(QuestionType.parse("rubric")).isEqualTo(QuestionType.SCORE);
        assertThatThrownBy(() -> QuestionType.parse("maybe")).hasMessageContaining("unknown question type");
    }
}
