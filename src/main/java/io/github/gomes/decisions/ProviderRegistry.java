package io.github.gomes.decisions;

import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.provider.gemini.GeminiDecisionsProvider;
import io.github.gomes.decisions.provider.local.LocalHeuristicProvider;
import io.github.gomes.decisions.provider.openai.OpenAiDecisionsProvider;
import io.github.gomes.decisions.provider.systemone.SystemOneProvider;
import io.github.gomes.decisions.spi.DecisionProvider;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Holds the configured providers. {@link #fromEnvironment(Map)} enables each provider whose credentials
 * are present; the offline {@code local} provider is always available.
 */
public final class ProviderRegistry {

    private final Map<String, DecisionProvider> providers = new LinkedHashMap<>();

    public ProviderRegistry register(DecisionProvider p) {
        providers.put(p.id(), p);
        return this;
    }

    public Optional<DecisionProvider> find(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public DecisionProvider get(String id) {
        return find(id).orElseThrow(() -> new NoSuchElementException(
                "provider '" + id + "' is not configured; available: " + providers.keySet()));
    }

    public Collection<DecisionProvider> all() {
        return providers.values();
    }

    public static ProviderRegistry fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static ProviderRegistry fromEnvironment(Map<String, String> env) {
        Duration timeout = Duration.ofSeconds(Long.parseLong(env.getOrDefault("DECISIONS_TIMEOUT_SECONDS", "30")));
        JsonHttp http = new JsonHttp(timeout);
        ProviderRegistry r = new ProviderRegistry();

        if (has(env, "TYPESAFE_API_KEY")) {
            r.register(SystemOneProvider.typesafe(env.get("TYPESAFE_API_KEY"),
                    env.get("TYPESAFE_BASE_URL"), env.get("TYPESAFE_MODEL"), http));
        }
        if (has(env, "OPENAI_API_KEY")) {
            r.register(OpenAiDecisionsProvider.openAi(env.get("OPENAI_API_KEY"),
                    env.get("OPENAI_BASE_URL"), env.get("OPENAI_DECISIONS_MODEL"), http));
        }
        if (has(env, "AZURE_OPENAI_API_KEY") && has(env, "AZURE_OPENAI_ENDPOINT")) {
            r.register(OpenAiDecisionsProvider.azure(env.get("AZURE_OPENAI_ENDPOINT"),
                    env.get("AZURE_OPENAI_API_KEY"), env.get("AZURE_OPENAI_DECISIONS_DEPLOYMENT"), http));
        }
        if (has(env, "GEMINI_API_KEY")) {
            r.register(new GeminiDecisionsProvider(env.get("GEMINI_API_KEY"),
                    env.get("GEMINI_BASE_URL"), env.get("GEMINI_DECISIONS_MODEL"), http));
        }
        if (has(env, "FOUNDRY_API_KEY") && has(env, "FOUNDRY_ENDPOINT")) {
            r.register(SystemOneProvider.microsoftFoundry(env.get("FOUNDRY_ENDPOINT"),
                    env.get("FOUNDRY_API_KEY"), env.get("FOUNDRY_DECISION_DEPLOYMENT"), http));
        }
        if (has(env, "SYSTEMONE_ENDPOINT") && has(env, "SYSTEMONE_API_KEY") && has(env, "SYSTEMONE_MODEL")) {
            r.register(SystemOneProvider.generic(env.get("SYSTEMONE_ENDPOINT"),
                    env.get("SYSTEMONE_API_KEY"), env.get("SYSTEMONE_MODEL"), http));
        }
        r.register(new LocalHeuristicProvider());
        return r;
    }

    private static boolean has(Map<String, String> env, String key) {
        String v = env.get(key);
        return v != null && !v.isBlank();
    }
}
