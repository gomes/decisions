package io.github.gomes.decisions.config;

import io.github.gomes.decisions.config.DecisionsProperties.Provider;
import io.github.gomes.decisions.provider.ProviderKind;
import io.github.gomes.decisions.provider.ProviderRegistry;
import io.github.gomes.decisions.provider.gemini.GeminiDecisionsProvider;
import io.github.gomes.decisions.provider.local.LocalHeuristicProvider;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Creates the {@link ProviderRegistry}: a provider is enabled when its credentials are configured and it
 * is not switched off. Timeouts and other HTTP settings come from {@code spring.http.clients.*}.
 */
@Configuration(proxyBeanMethods = false)
public class DecisionProvidersConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DecisionProvidersConfiguration.class);

    @Bean
    ProviderRegistry providerRegistry(DecisionsProperties properties, RestClient.Builder restClientBuilder) {
        RestClient http = restClientBuilder.build();
        DecisionsProperties.Providers p = properties.providers();
        ProviderRegistry registry = new ProviderRegistry();

        if (usable(p.typesafe())) {
            registry.register(ProviderKind.TYPESAFE, SystemOneProvider.typesafe(
                    p.typesafe().apiKey(), p.typesafe().baseUrl(), p.typesafe().model(), http));
        }
        if (usable(p.openai())) {
            registry.register(ProviderKind.OPENAI, OpenAiDecisionsProvider.openAi(
                    p.openai().apiKey(), p.openai().baseUrl(), p.openai().model(), http));
        }
        if (usable(p.azureOpenai()) && p.azureOpenai().hasEndpoint()) {
            registry.register(ProviderKind.AZURE_OPENAI, OpenAiDecisionsProvider.azure(
                    p.azureOpenai().endpoint(), p.azureOpenai().apiKey(), p.azureOpenai().model(), http));
        }
        if (usable(p.gemini())) {
            registry.register(ProviderKind.GEMINI, new GeminiDecisionsProvider(
                    p.gemini().apiKey(), p.gemini().baseUrl(), p.gemini().model(), http));
        }
        if (usable(p.microsoft()) && p.microsoft().hasEndpoint()) {
            registry.register(ProviderKind.MICROSOFT, SystemOneProvider.microsoftFoundry(
                    p.microsoft().endpoint(), p.microsoft().apiKey(), p.microsoft().model(), http));
        }
        if (usable(p.systemone()) && p.systemone().hasEndpoint() && p.systemone().hasModel()) {
            registry.register(ProviderKind.SYSTEMONE, SystemOneProvider.generic(
                    p.systemone().endpoint(), p.systemone().apiKey(), p.systemone().model(), http));
        }
        if (p.local().enabled()) {
            registry.register(ProviderKind.LOCAL, new LocalHeuristicProvider());
        }

        log.info("Decision providers enabled: {}", registry.enabledIds());
        return registry;
    }

    private static boolean usable(Provider provider) {
        return provider.enabled() && provider.hasApiKey();
    }
}
