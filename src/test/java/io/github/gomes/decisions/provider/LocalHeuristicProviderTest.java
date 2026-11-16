package io.github.gomes.decisions.provider;

import io.github.gomes.decisions.domain.DecisionRequest;
import io.github.gomes.decisions.domain.DecisionResult;
import io.github.gomes.decisions.domain.Option;
import io.github.gomes.decisions.domain.Question;
import io.github.gomes.decisions.provider.local.LocalHeuristicProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LocalHeuristicProviderTest {

    @Test
    void routesAnUnambiguousExample() {
        DecisionRequest req = DecisionRequest.builder()
                .input("Olá, meu pedido chegou com a caneca quebrada e a caixa danificada. Podem enviar outra?")
                .question(Question.yesNo("is_damaged", "Item danificado?", "quebrada, danificada", null))
                .question(Question.choice("queue", "Qual fila?",
                        Option.of("billing", "pagamentos, reembolso"),
                        Option.of("shipping", "entrega, quebrada, danificada"),
                        Option.of("technical", "login, bugs")))
                .question(Question.score("urgency", "Urgência?", Option.of("low"), Option.of("high")))
                .build();

        DecisionResult r = new LocalHeuristicProvider().decide(req);

        assertThat(r.answer("is_damaged").value()).isEqualTo("true");
        assertThat(r.answer("queue").value()).isEqualTo("shipping");
        assertThat(r.answer("queue").probabilities().keySet()).containsExactly("billing", "shipping", "technical");
        assertThat(r.answer("queue").probabilities().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, within(1e-3));
        assertThat(r.answer("urgency").score()).isNotNull();
    }
}
