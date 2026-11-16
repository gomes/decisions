package io.github.gomes.decisions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings under {@code decisions.*}. See {@code application.yml}: by default every value is
 * read from the same environment variables the app always used (OPENAI_API_KEY, GEMINI_API_KEY, ...).
 *
 * @param apiToken   when set, every {@code /api/**} call needs {@code Authorization: Bearer <token>}
 * @param playground the HTML test page at {@code /}
 * @param providers  per-provider credentials and defaults
 */
@ConfigurationProperties("decisions")
public record DecisionsProperties(
        String apiToken,
        @DefaultValue Playground playground,
        @DefaultValue Providers providers) {

    public boolean apiTokenRequired() {
        return apiToken != null && !apiToken.isBlank();
    }

    /** @param enabled set to false to remove the playground (e.g. in production) */
    public record Playground(@DefaultValue("true") boolean enabled) {
    }

    public record Providers(
            @DefaultValue Provider typesafe,
            @DefaultValue Provider openai,
            @DefaultValue Provider azureOpenai,
            @DefaultValue Provider gemini,
            @DefaultValue Provider microsoft,
            @DefaultValue Provider systemone,
            @DefaultValue Local local) {
    }

    /**
     * @param enabled  switch to turn a provider off even when credentials exist
     * @param apiKey   provider API key
     * @param baseUrl  API base URL (OpenAI, TypeSafe, Gemini); lets you point at compatible gateways
     * @param endpoint resource endpoint (Azure OpenAI, Foundry) or full URL (generic System One)
     * @param model    default model or deployment name
     */
    public record Provider(@DefaultValue("true") boolean enabled, String apiKey, String baseUrl, String endpoint,
                           String model) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean hasEndpoint() {
            return endpoint != null && !endpoint.isBlank();
        }

        public boolean hasModel() {
            return model != null && !model.isBlank();
        }
    }

    public record Local(@DefaultValue("true") boolean enabled) {
    }
}
