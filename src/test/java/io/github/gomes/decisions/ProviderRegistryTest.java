package io.github.gomes.decisions;

import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.spi.DecisionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRegistryTest {

    @Test
    void onlyLocalWithoutCredentials() {
        ProviderRegistry r = ProviderRegistry.fromEnvironment(Map.of());
        assertEquals(List.of("local"), r.all().stream().map(DecisionProvider::id).toList());
        assertThrows(NoSuchElementException.class, () -> r.get("openai"));
    }

    @Test
    void enablesEveryProviderWithCredentials() {
        ProviderRegistry r = ProviderRegistry.fromEnvironment(Map.ofEntries(
                Map.entry("TYPESAFE_API_KEY", "a"),
                Map.entry("OPENAI_API_KEY", "b"),
                Map.entry("AZURE_OPENAI_API_KEY", "c"),
                Map.entry("AZURE_OPENAI_ENDPOINT", "https://res.openai.azure.com"),
                Map.entry("GEMINI_API_KEY", "d"),
                Map.entry("FOUNDRY_API_KEY", "e"),
                Map.entry("FOUNDRY_ENDPOINT", "https://res.services.ai.azure.com"),
                Map.entry("GEMINI_DECISIONS_MODEL", "gemini-decision-1-preview")));
        assertEquals(List.of("typesafe", "openai", "azure-openai", "gemini", "microsoft", "local"),
                r.all().stream().map(DecisionProvider::id).toList());
        assertEquals("gemini-decision-1-preview", r.get("gemini").defaultModel());
        assertEquals("https://res.openai.azure.com/openai/v1/decisions",
                ((HttpDecisionProvider) r.get("azure-openai")).endpoint().toString());
        assertEquals("https://res.services.ai.azure.com/providers/microsoft/v1/systemone",
                ((HttpDecisionProvider) r.get("microsoft")).endpoint().toString());
    }
}
