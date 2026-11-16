package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AnswersTest {

    static final List<Option> LEVELS = List.of(Option.of("low"), Option.of("medium"), Option.of("high"));
    static final Question SCORE = Question.score("s", "?", LEVELS.toArray(Option[]::new));

    @Test
    void indexKeysAreMappedToOptionValues() {
        assertThat(Answers.byOptionValue(LEVELS, Map.of("2", 0.7)))
                .containsExactly(entry("high", 0.7));
        assertThat(Answers.byOptionValue(LEVELS, Map.of("low", 0.7)))
                .as("already keyed by value").containsExactly(entry("low", 0.7));
        assertThat(Answers.byOptionValue(LEVELS, Map.of("9", 0.7)))
                .as("out of range is left alone").containsExactly(entry("9", 0.7));
    }

    @Test
    void labelsAndIndexesResolveToTheOptionValue() {
        assertThat(Answers.resolveOption(LEVELS, "high: outage")).isEqualTo("high");
        assertThat(Answers.resolveOption(LEVELS, "1")).isEqualTo("medium");
        assertThat(Answers.resolveOption(LEVELS, "low")).isEqualTo("low");
    }

    @Test
    void scoreAnswerWithLabelValueAndIndexProbabilities() {
        var node = JsonMapper.shared().readTree("""
                {"level": "high: outage", "score": 1.9, "probabilities": {"0": 0.0, "1": 0.1, "2": 0.9}}""");
        var a = Answers.parse("gemini", SCORE, node);
        assertThat(a.value()).isEqualTo("high");
        assertThat(a.probabilities()).containsEntry("high", 0.9);
        assertThat(a.confidence()).isEqualTo(0.8);
    }

    @Test
    void refusalIsAnAnswerWithoutValue() {
        var a = Answers.parse("x", SCORE, JsonMapper.shared().readTree("{\"type\": \"refusal\", \"name\": \"s\"}"));
        assertThat(a.isRefused()).isTrue();
        assertThat(a.value()).isNull();
        assertThat(a.refused()).isTrue();
    }
}
