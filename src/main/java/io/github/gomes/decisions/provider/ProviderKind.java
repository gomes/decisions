package io.github.gomes.decisions.provider;

import java.util.List;

/**
 * Every provider the app knows how to talk to, with the metadata the playground and the API show
 * (even when the provider is not configured).
 */
public enum ProviderKind {

    TYPESAFE("typesafe", "TypeSafe Jev", "System One",
            "Modelo de decisão da TypeSafe. Só texto; devolve probabilidades calibradas por resposta.",
            List.of("TYPESAFE_API_KEY"), "https://docs.typesafe.ai/api/system-one"),
    OPENAI("openai", "OpenAI Decisions", "OpenAI Decisions",
            "Endpoint /v1/decisions da OpenAI (gpt-6-luna). Aceita texto, JSON e imagens; até 400 perguntas.",
            List.of("OPENAI_API_KEY"), "https://developers.openai.com/api/docs/guides/decisions"),
    AZURE_OPENAI("azure-openai", "Azure OpenAI Decisions", "OpenAI Decisions",
            "A mesma API da OpenAI servida pelo Azure OpenAI / Microsoft Foundry, com o seu deployment.",
            List.of("AZURE_OPENAI_API_KEY", "AZURE_OPENAI_ENDPOINT"),
            "https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/decisions"),
    GEMINI("gemini", "Gemini Decisions", "Gemini Decisions",
            "Gemini Decisions API (gemini-decision-1). Multimodal; até 1.000 perguntas por chamada.",
            List.of("GEMINI_API_KEY"),
            "https://blog.google/innovation-and-ai/technology/developers-tools/gemini-decisions-api/"),
    MICROSOFT("microsoft", "Microsoft-Decision-1", "System One",
            "Modelo de decisão da Microsoft no Foundry, via protocolo System One. Só texto.",
            List.of("FOUNDRY_API_KEY", "FOUNDRY_ENDPOINT"),
            "https://learn.microsoft.com/en-us/azure/foundry/foundry-models/how-to/use-foundry-models-microsoft-decision"),
    SYSTEMONE("systemone", "System One (genérico)", "System One",
            "Qualquer host compatível com System One (ex.: DigitalOcean), informando URL, chave e modelo.",
            List.of("SYSTEMONE_ENDPOINT", "SYSTEMONE_API_KEY", "SYSTEMONE_MODEL"),
            "https://docs.digitalocean.com/products/inference/how-to/use-system-one-api/"),
    LOCAL("local", "Heurística local", "offline",
            "Baseline offline por sobreposição de palavras. NÃO é um modelo de decisão: serve para testar a app sem chaves.",
            List.of(), null);

    private final String id;
    private final String displayName;
    private final String protocol;
    private final String description;
    private final List<String> requiredEnv;
    private final String docsUrl;

    ProviderKind(String id, String displayName, String protocol, String description, List<String> requiredEnv,
                 String docsUrl) {
        this.id = id;
        this.displayName = displayName;
        this.protocol = protocol;
        this.description = description;
        this.requiredEnv = requiredEnv;
        this.docsUrl = docsUrl;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String protocol() { return protocol; }
    public String description() { return description; }
    public List<String> requiredEnv() { return requiredEnv; }
    public String docsUrl() { return docsUrl; }
}
