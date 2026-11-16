package io.github.gomes.decisions.provider;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** What the API and the playground show about each known provider, configured or not. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderStatus(String id, String name, String protocol, String description, boolean enabled,
                             String defaultModel, Capabilities capabilities, List<String> requiredEnv,
                             String docsUrl) {

    static ProviderStatus enabled(ProviderKind kind, DecisionProvider p) {
        return new ProviderStatus(kind.id(), kind.displayName(), kind.protocol(), kind.description(), true,
                p.defaultModel(), p.capabilities(), kind.requiredEnv(), kind.docsUrl());
    }

    static ProviderStatus disabled(ProviderKind kind) {
        return new ProviderStatus(kind.id(), kind.displayName(), kind.protocol(), kind.description(), false,
                null, null, kind.requiredEnv(), kind.docsUrl());
    }
}
