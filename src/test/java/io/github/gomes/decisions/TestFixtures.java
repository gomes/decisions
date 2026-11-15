package io.github.gomes.decisions;

import io.github.gomes.decisions.model.DecisionRequest;
import io.github.gomes.decisions.model.Option;
import io.github.gomes.decisions.model.Question;

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

    public static DecisionRequest ticketWithImage() {
        DecisionRequest t = ticket();
        return new DecisionRequest(t.input(), t.data(), java.util.List.of("https://example.com/mug.jpg"),
                t.questions(), null);
    }
}
