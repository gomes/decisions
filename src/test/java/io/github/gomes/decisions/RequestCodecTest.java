package io.github.gomes.decisions;

import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.QuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestCodecTest {

    @Test
    void readsExample() {
        DecisionRequest r = RequestCodec.read(RequestCodec.EXAMPLE);
        assertEquals(3, r.questions().size());
        assertEquals(QuestionType.YES_NO, r.questions().get(0).type());
        assertEquals("shipping", r.questions().get(1).options().get(1).value());
        assertEquals(QuestionType.SCORE, r.questions().get(2).type());
    }

    @Test
    void acceptsProviderSpecificTypeNames() {
        DecisionRequest r = RequestCodec.read("""
                {"input": "x", "questions": [
                  {"name": "a", "type": "noul", "instructions": "?"},
                  {"name": "b", "type": "predicate", "instructions": "?"}]}
                """);
        assertEquals(QuestionType.YES_NO, r.questions().get(0).type());
        assertEquals(QuestionType.YES_NO, r.questions().get(1).type());
    }

    @Test
    void validationErrorsAreReadable() {
        var e = assertThrows(IllegalArgumentException.class, () -> RequestCodec.read("""
                {"input": "x", "questions": [
                  {"name": "q", "type": "choice", "instructions": "?", "options": [{"value": "only"}]}]}
                """));
        assertTrue(e.getMessage().contains("between 2 and 255"), e.getMessage());

        e = assertThrows(IllegalArgumentException.class, () -> RequestCodec.read("""
                {"input": "x", "questions": [
                  {"name": "q", "type": "yes_no", "instructions": "?"},
                  {"name": "q", "type": "yes_no", "instructions": "?"}]}
                """));
        assertTrue(e.getMessage().contains("duplicate"), e.getMessage());

        e = assertThrows(IllegalArgumentException.class, () -> RequestCodec.read("{\"questions\": []}"));
        assertTrue(e.getMessage().contains("input"), e.getMessage());

        e = assertThrows(IllegalArgumentException.class, () -> RequestCodec.read("""
                {"input": "x", "questions": [{"name": "q", "type": "maybe", "instructions": "?"}]}
                """));
        assertTrue(e.getMessage().contains("unknown question type"), e.getMessage());
    }
}
