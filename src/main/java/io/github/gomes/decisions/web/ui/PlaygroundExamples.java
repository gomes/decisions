package io.github.gomes.decisions.web.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Ready-made scenarios that show what decision APIs are good at. */
public final class PlaygroundExamples {

    /** @param needsImages only providers that accept images can run it */
    public record Example(String id, String title, String description, boolean needsImages,
                          Supplier<PlaygroundForm> form) {
    }

    private static final Map<String, Example> EXAMPLES = new LinkedHashMap<>();

    static {
        add(new Example("ticket", "Triagem de suporte",
                "Classifica um e-mail de cliente: houve avaria? Qual fila atende? Qual a urgência?", false,
                () -> {
                    PlaygroundForm f = new PlaygroundForm();
                    f.setInput("Olá, meu pedido #4411 chegou com a caneca azul quebrada e a caixa danificada. "
                            + "Podem enviar outra?");
                    return f.question(QuestionForm.yesNo("is_damaged",
                                    "O cliente relata um item danificado ou quebrado?",
                                    "item chegou quebrado, rachado ou danificado",
                                    "item chegou em bom estado ou a mensagem não fala de avaria"))
                            .question(QuestionForm.choice("queue", "Qual fila de atendimento deve tratar a mensagem?",
                                    """
                                    billing | pagamentos, faturas, reembolso
                                    shipping | entrega, rastreio, itens quebrados ou danificados
                                    technical | login, erros no app, bugs"""))
                            .question(QuestionForm.score("urgency", "Quão urgente é a mensagem?",
                                    """
                                    low | dúvida geral, sem impacto
                                    medium | cliente prejudicado, precisa de troca ou reembolso
                                    high | ameaça legal, risco de segurança, indisponibilidade"""));
                }));

        add(new Example("moderation", "Moderação de conteúdo",
                "Decide se um comentário pode ser publicado, em que categoria cai e quão tóxico é.", false,
                () -> {
                    PlaygroundForm f = new PlaygroundForm();
                    f.setInput("Que produto horrível, quem comprou é idiota. Me chama no zap 11 99999-0000 "
                            + "que eu vendo um melhor e mais barato.");
                    return f.question(QuestionForm.yesNo("has_personal_data",
                                    "O texto expõe dados pessoais (telefone, e-mail, documento, endereço)?", null, null))
                            .question(QuestionForm.choice("category", "Qual a principal categoria do comentário?",
                                    """
                                    ok | crítica legítima ou elogio, pode publicar
                                    spam | propaganda, link ou contato para vender algo
                                    offensive | insultos ou linguagem ofensiva
                                    harassment | ataque direcionado a uma pessoa específica"""))
                            .question(QuestionForm.score("toxicity", "Quão tóxico é o comentário?",
                                    """
                                    none | nenhum insulto
                                    mild | grosseria leve
                                    severe | insultos fortes, ódio ou ameaça"""));
                }));

        add(new Example("routing", "Roteamento de agente",
                "Usa decisões rápidas para escolher ferramenta e tamanho de modelo antes de chamar um LLM.", false,
                () -> {
                    PlaygroundForm f = new PlaygroundForm();
                    f.setInput("Qual foi o faturamento do último trimestre por região? Quero um gráfico de barras.");
                    return f.question(QuestionForm.choice("tool", "Qual ferramenta o agente deve usar primeiro?",
                                    """
                                    sql_query | consultar dados internos da empresa
                                    web_search | buscar informação pública e atual na internet
                                    calculator | fazer uma conta simples
                                    none | responder direto, sem ferramenta"""))
                            .question(QuestionForm.yesNo("needs_clarification",
                                    "A pergunta é ambígua a ponto de exigir esclarecimento antes de agir?",
                                    "faltam informações essenciais para responder",
                                    "dá para responder com o que foi dito"))
                            .question(QuestionForm.score("complexity", "Qual a complexidade da tarefa?",
                                    """
                                    simple | um passo, modelo pequeno resolve
                                    moderate | alguns passos
                                    complex | raciocínio longo, use o modelo maior"""));
                }));

        add(new Example("refund", "Política com dados estruturados",
                "Envia um JSON com os dados do pedido (campo Dados) e pergunta se o reembolso está dentro da política.",
                false,
                () -> {
                    PlaygroundForm f = new PlaygroundForm();
                    f.setInput("Política: reembolso integral até 30 dias após a entrega; clientes gold até 60 dias. "
                            + "Itens personalizados não têm reembolso.");
                    f.setData("""
                            {
                              "order": 4411,
                              "customerTier": "gold",
                              "daysSinceDelivery": 41,
                              "personalized": false,
                              "amount": 189.90
                            }""");
                    return f.question(QuestionForm.yesNo("within_policy",
                                    "O pedido de reembolso está dentro da política?", null, null))
                            .question(QuestionForm.choice("action", "Qual a próxima ação?",
                                    """
                                    approve | aprovar o reembolso automaticamente
                                    deny | negar com base na política
                                    human | encaminhar para um atendente"""));
                }));

        add(new Example("image", "Imagem (multimodal)",
                "Analisa uma foto enviada pelo cliente. Troque a URL por uma foto real (https ou data: URI). "
                        + "Só OpenAI, Azure OpenAI e Gemini aceitam imagens.", true,
                () -> {
                    PlaygroundForm f = new PlaygroundForm();
                    f.setInput("Foto enviada pelo cliente junto com o pedido de troca.");
                    f.setImages("https://example.com/fotos/caneca-do-cliente.jpg");
                    return f.question(QuestionForm.yesNo("visible_damage",
                                    "A foto mostra algum dano visível no produto?", null, null))
                            .question(QuestionForm.choice("product", "Que tipo de produto aparece na foto?",
                                    """
                                    mug | caneca ou xícara
                                    plate | prato
                                    other | outro objeto"""));
                }));
    }

    private PlaygroundExamples() {
    }

    private static void add(Example e) {
        EXAMPLES.put(e.id(), e);
    }

    public static List<Example> all() {
        return List.copyOf(EXAMPLES.values());
    }

    public static Optional<Example> find(String id) {
        return Optional.ofNullable(id == null ? null : EXAMPLES.get(id));
    }
}
