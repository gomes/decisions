package io.github.gomes.decisions.provider;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** The configured providers, plus the status of every known provider (for discovery in the UI/API). */
public final class ProviderRegistry implements AutoCloseable {

    private final Map<String, DecisionProvider> providers = new LinkedHashMap<>();
    private final Map<ProviderKind, ProviderStatus> statuses = new EnumMap<>(ProviderKind.class);

    public ProviderRegistry() {
        for (ProviderKind kind : ProviderKind.values()) {
            statuses.put(kind, ProviderStatus.disabled(kind));
        }
    }

    public ProviderRegistry register(ProviderKind kind, DecisionProvider provider) {
        providers.put(provider.id(), provider);
        statuses.put(kind, ProviderStatus.enabled(kind, provider));
        return this;
    }

    public Optional<DecisionProvider> find(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public DecisionProvider get(String id) {
        return find(id).orElseThrow(() -> new NoSuchElementException(
                "provider '" + id + "' is not enabled; enabled providers: " + providers.keySet()));
    }

    public Collection<DecisionProvider> enabled() {
        return providers.values();
    }

    public List<String> enabledIds() {
        return List.copyOf(providers.keySet());
    }

    /** Releases the HTTP clients of providers that hold one (the SDK-based ones). */
    @Override
    public void close() {
        for (DecisionProvider p : providers.values()) {
            if (p instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // best effort on shutdown
                }
            }
        }
    }

    /** Every known provider, enabled or not, in a stable order. */
    public List<ProviderStatus> statuses() {
        return List.copyOf(statuses.values());
    }
}
