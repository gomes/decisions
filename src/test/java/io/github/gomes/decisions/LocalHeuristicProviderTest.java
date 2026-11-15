package io.github.gomes.decisions;

import io.github.gomes.decisions.model.DecisionResult;
import io.github.gomes.decisions.provider.local.LocalHeuristicProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalHeuristicProviderTest {

    @Test
    void exampleIsRoutedSensibly() {
        DecisionResult r = new LocalHeuristicProvider().decide(RequestCodec.read(RequestCodec.EXAMPLE));
        assertEquals("true", r.answer("is_damaged").value());
        assertEquals("shipping", r.answer("queue").value());
        assertEquals(1.0, r.answer("queue").probabilities().values().stream().mapToDouble(d -> d).sum(), 1e-3);
        assertNotNull(r.answer("urgency").score());
    }
}
