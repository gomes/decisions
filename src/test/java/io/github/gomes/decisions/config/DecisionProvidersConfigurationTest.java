package io.github.gomes.decisions.config;

import io.github.gomes.decisions.provider.HttpDecisionProvider;
import io.github.gomes.decisions.provider.ProviderRegistry;
import io.github.gomes.decisions.provider.ProviderStatus;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionProvidersConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DecisionsProperties.class)
    static class Props {
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(Props.class, DecisionProvidersConfiguration.class);

    @Test
    void onlyLocalWithoutCredentialsButEveryProviderIsListed() {
        runner.run(ctx -> {
            ProviderRegistry registry = ctx.getBean(ProviderRegistry.class);
            assertThat(registry.enabledIds()).containsExactly("local");
            assertThat(registry.statuses()).extracting(ProviderStatus::id).containsExactly(
                    "typesafe", "openai", "azure-openai", "gemini", "microsoft", "systemone", "local");
            assertThat(registry.statuses()).filteredOn(s -> s.id().equals("openai")).singleElement()
                    .satisfies(s -> {
                        assertThat(s.enabled()).isFalse();
                        assertThat(s.requiredEnv()).containsExactly("OPENAI_API_KEY");
                    });
        });
    }

    @Test
    void enablesProvidersWhoseCredentialsAreSet() {
        runner.withPropertyValues(
                "decisions.providers.typesafe.api-key=a",
                "decisions.providers.openai.api-key=b",
                "decisions.providers.azure-openai.api-key=c",
                "decisions.providers.azure-openai.endpoint=https://res.openai.azure.com",
                "decisions.providers.gemini.api-key=d",
                "decisions.providers.gemini.model=gemini-decision-1-preview",
                "decisions.providers.microsoft.api-key=e",
                "decisions.providers.microsoft.endpoint=https://res.services.ai.azure.com",
                "decisions.providers.systemone.api-key=f",
                "decisions.providers.systemone.endpoint=https://inference.example.com/v1/systemone",
                "decisions.providers.systemone.model=jev-1.14.0"
        ).run(ctx -> {
            ProviderRegistry registry = ctx.getBean(ProviderRegistry.class);
            assertThat(registry.enabledIds()).containsExactly(
                    "typesafe", "openai", "azure-openai", "gemini", "microsoft", "systemone", "local");
            assertThat(registry.get("gemini").defaultModel()).isEqualTo("gemini-decision-1-preview");
            assertThat(((OpenAiDecisionsProvider) registry.get("azure-openai")).baseUrl())
                    .isEqualTo("https://res.openai.azure.com/openai/v1");
            assertThat(((OpenAiDecisionsProvider) registry.get("openai")).baseUrl())
                    .isEqualTo("https://api.openai.com/v1");
            assertThat(((HttpDecisionProvider) registry.get("microsoft")).endpoint())
                    .hasToString("https://res.services.ai.azure.com/providers/microsoft/v1/systemone");
        });
    }

    @Test
    void sdkClientsAreClosedWithTheContext() {
        runner.withPropertyValues("decisions.providers.openai.api-key=b").run(ctx ->
                assertThat(ctx.getBean(ProviderRegistry.class)).isInstanceOf(AutoCloseable.class));
    }

    @Test
    void endpointBasedProvidersNeedTheEndpointAndProvidersCanBeSwitchedOff() {
        runner.withPropertyValues(
                "decisions.providers.azure-openai.api-key=c",
                "decisions.providers.openai.api-key=b",
                "decisions.providers.openai.enabled=false",
                "decisions.providers.local.enabled=false"
        ).run(ctx -> assertThat(ctx.getBean(ProviderRegistry.class).enabledIds()).isEmpty());
    }
}
