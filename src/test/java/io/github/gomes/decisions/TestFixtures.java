package io.github.gomes.decisions;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;

import java.util.List;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static DecisionRequest ticket() {
        return DecisionRequest.builder()
                .input("My blue mug arrived cracked. I want a refund.")
                .question(Question.yesNo("is_damaged", "Is the item damaged?", "broken or cracked", "fine"))
                .question(Question.choice("queue", "Which queue?",
                        Option.of("billing", "Payments and refunds"),
                        Option.of("shipping", "Delivery and damage")))
                .question(Question.score("severity", "How severe?",
                        Option.of("low"), Option.of("medium"), Option.of("high")))
                .build();
    }

    /** Same as {@link #ticket()}, but the top severity level has a description. */
    public static DecisionRequest ticketWithLevelDescriptions() {
        DecisionRequest t = ticket();
        var questions = new java.util.ArrayList<>(t.questions());
        questions.set(2, Question.score("severity", "How severe?",
                Option.of("low"), Option.of("medium"), Option.of("high", "outage")));
        return new DecisionRequest(t.input(), null, null, questions, null);
    }

    public static DecisionRequest ticketWithImage() {
        DecisionRequest t = ticket();
        return new DecisionRequest(t.input(), t.data(), List.of("https://example.com/mug.jpg"), t.questions(), null);
    }

    public static final String TICKET_JSON = """
            {
              "input": "Meu pedido chegou com a caneca quebrada e a caixa danificada",
              "questions": [
                {"name": "is_damaged", "type": "yes_no", "instructions": "O item chegou danificado?",
                 "trueWhen": "quebrada, danificada"},
                {"name": "queue", "type": "choice", "instructions": "Qual fila?",
                 "options": [{"value": "billing", "description": "pagamentos, reembolso"},
                             {"value": "shipping", "description": "entrega, quebrada, danificada"}]}
              ]
            }
            """;
}
