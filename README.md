# decisions

Aplicação **Spring Boot** que fala com as **Decision APIs** mais usadas hoje (modelos que *escolhem* entre
respostas definidas por você, em vez de gerar texto) através de **um único formato de requisição**. Inclui:

- **Playground** em `/`: formulário com explicações e exemplos prontos. Ele mostra quais provedores estão
  habilitados, deixa você escolher em quais executar e compara as respostas lado a lado.
- **API REST** em `/api/v1`, com erros no padrão `ProblemDetail` (RFC 9457).
- **Actuator** em `/actuator/health`.

| Provedor (`id`) | API | Cliente | Endpoint | Modelo padrão | Imagens |
|---|---|---|---|---|---|
| `typesafe` | TypeSafe **Jev** (protocolo *System One*) | `RestClient` | `POST https://api.typesafe.ai/v1/systemone` | `jev-latest` | não |
| `openai` | **OpenAI Decisions API** | SDK oficial `openai-java` | `POST https://api.openai.com/v1/decisions` | `gpt-6-luna` | sim |
| `azure-openai` | **Azure OpenAI Decisions** (Foundry) | SDK oficial `openai-java` | `POST https://<recurso>.openai.azure.com/openai/v1/decisions` | nome do deployment | sim |
| `gemini` | **Gemini Decisions API** | SDK oficial `google-genai` | `POST https://generativelanguage.googleapis.com/v1beta/decisions` | `gemini-decision-1` | sim |
| `microsoft` | **Microsoft-Decision-1** (Foundry, System One) | `RestClient` | `POST <endpoint>/providers/microsoft/v1/systemone` | nome do deployment | não |
| `systemone` | Qualquer host compatível com System One (ex.: DigitalOcean) | `RestClient` | configurável | configurável | não |
| `local` | Heurística offline (não é um modelo; serve para testar sem chaves) | — | — | `keyword-overlap-v1` | ignora |

OpenAI, Azure OpenAI e Gemini usam os **SDKs Java oficiais** (`com.openai:openai-java`,
`com.google.genai:google-genai`), então os formatos de requisição e resposta são os tipos mantidos pelos
próprios fornecedores. Jev e Microsoft-Decision-1 não têm SDK Java e usam o `RestClient` do Spring.

Todas usam os mesmos três tipos de pergunta, com nomes diferentes. A app normaliza:

| Tipo na app | System One / Gemini | OpenAI / Azure | Resposta normalizada |
|---|---|---|---|
| `yes_no` | `noul` | `predicate` | `value` ("true"/"false"), `probability` = P(sim), `confidence` |
| `choice` | `choice` | `choice` | `value` escolhido, `probabilities` por opção, `confidence` |
| `score` | `score` | `score` | `value` (nível mais provável), `score` (índice esperado, 0 = mais baixo), `probabilities`, `confidence` |

Quando o provedor não devolve `confidence`, ela é calculada como a margem entre as duas respostas mais prováveis.
Se o provedor se recusar a responder uma pergunta (a OpenAI pode devolver `refusal` em qualquer pergunta), a
resposta vem com `"refused": true` e sem valor, e as demais perguntas não são afetadas.

## Stack

- Java 21, **Spring Boot 4.1** (Spring Framework 7, Jackson 3, JUnit 6)
- `spring-boot-starter-webmvc`, `-restclient`, `-thymeleaf`, `-validation`, `-actuator`
- SDKs oficiais: `openai-java` 4.105 e `google-genai` 2.3 (trazem OkHttp, Kotlin stdlib e Jackson 2, que convivem
  com o Jackson 3 do Spring)
- Virtual threads ativadas (`spring.threads.virtual.enabled`); o `compare` chama os provedores em paralelo

## Como rodar

```bash
./mvnw spring-boot:run                 # http://localhost:8080
# ou
./mvnw package && java -jar target/decisions-0.2.0-SNAPSHOT.jar
```

Sem nenhuma chave configurada, só o provedor `local` fica habilitado. Ele basta para explorar o playground e a API.

## Configuração

Cada provedor é habilitado quando as credenciais existem. Os valores ficam em `application.yml`
(`decisions.providers.*`) e, por padrão, são lidos destas variáveis de ambiente:

| Provedor | Variáveis obrigatórias | Opcionais |
|---|---|---|
| `typesafe` | `TYPESAFE_API_KEY` | `TYPESAFE_BASE_URL`, `TYPESAFE_MODEL` |
| `openai` | `OPENAI_API_KEY` | `OPENAI_BASE_URL`, `OPENAI_DECISIONS_MODEL` |
| `azure-openai` | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` | `AZURE_OPENAI_DECISIONS_DEPLOYMENT` |
| `gemini` | `GEMINI_API_KEY` | `GEMINI_BASE_URL`, `GEMINI_DECISIONS_MODEL` |
| `microsoft` | `FOUNDRY_API_KEY`, `FOUNDRY_ENDPOINT` | `FOUNDRY_DECISION_DEPLOYMENT` |
| `systemone` | `SYSTEMONE_ENDPOINT` (URL completa), `SYSTEMONE_API_KEY`, `SYSTEMONE_MODEL` | — |

Outras configurações:

| Propriedade | Variável | Padrão | Efeito |
|---|---|---|---|
| `decisions.api-token` | `DECISIONS_GATEWAY_TOKEN` | vazio | Se definido, `/api/**` exige `Authorization: Bearer <token>` |
| `decisions.playground.enabled` | `DECISIONS_PLAYGROUND_ENABLED` | `true` | Desliga o playground (ex.: em produção) |
| `decisions.providers.<id>.enabled` | — | `true` | Desliga um provedor mesmo com chave |
| `decisions.providers.local.enabled` | `DECISIONS_LOCAL_ENABLED` | `true` | Provedor offline |
| `spring.http.clients.read-timeout` | `DECISIONS_TIMEOUT` | `30s` | Timeout das chamadas aos provedores (também aplicado ao SDK da OpenAI) |
| `decisions.max-retries` | — | `2` | Retentativas em erros transitórios nos provedores com SDK |
| `spring.http.clients.connect-timeout` | `DECISIONS_CONNECT_TIMEOUT` | `10s` | |
| `server.port` | `PORT` | `8080` | |

`*_BASE_URL` permite apontar para gateways compatíveis (OpenRouter, AI/ML API, Portkey...).
O token do gateway protege só `/api/**`. Se a aplicação ficar exposta, desligue o playground ou proteja-o
na frente (proxy/SSO), porque ele usa as chaves configuradas no servidor.

## Playground

Abra `http://localhost:8080/`. A tela tem quatro passos:

1. **Exemplos**: triagem de suporte, moderação, roteamento de agente, política com dados JSON e imagem.
2. **Provedores**: todos os provedores conhecidos, com status *habilitado/desabilitado*, as variáveis que faltam,
   capacidades (limite de perguntas, imagens) e um campo opcional de modelo. Marque um ou vários.
3. **Entrada**: texto, dados JSON e URLs de imagem, cada um com explicação e exemplo.
4. **Perguntas**: adicione, remova e troque o tipo. Os campos mudam conforme o tipo (critérios para sim/não,
   opções para escolha, níveis ordenados para escala).

O resultado mostra uma tabela comparativa (com 2 ou mais provedores), barras de probabilidade por opção,
confiança, latência, erros de cada provedor e a requisição equivalente na API REST com o comando `curl`.

## API REST

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/v1/providers` | Todos os provedores conhecidos, com `enabled`, modelo padrão e capacidades |
| `POST` | `/api/v1/decisions?provider=<id>[&model=<m>]` | Decide com um provedor |
| `POST` | `/api/v1/compare[?providers=a,b][&model.<id>=<m>]` | Mesma requisição em vários provedores, em paralelo |
| `GET` | `/actuator/health` | Saúde da aplicação |

```bash
curl -s -X POST 'localhost:8080/api/v1/decisions?provider=local' \
  -H 'Content-Type: application/json' --data-binary @examples/ticket.json
```

Formato da requisição:

```json
{
  "input": "Meu pedido chegou com a caneca quebrada. Podem enviar outra?",
  "data":   {"order": 4411},
  "images": ["https://example.com/foto.jpg"],
  "model":  "opcional, sobrescreve o modelo padrão",
  "questions": [
    {"name": "is_damaged", "type": "yes_no", "instructions": "O item chegou danificado?",
     "trueWhen": "quebrado, rachado", "falseWhen": "chegou em bom estado"},
    {"name": "queue", "type": "choice", "instructions": "Qual fila deve atender?",
     "options": [{"value": "billing", "description": "pagamentos e reembolsos"},
                 {"value": "shipping", "description": "entrega e avarias"}]},
    {"name": "urgency", "type": "score", "instructions": "Quão urgente é?",
     "options": [{"value": "low"}, {"value": "medium"}, {"value": "high"}]}
  ]
}
```

- `input`, `data` e `images` são opcionais individualmente, mas ao menos um é obrigatório.
- Em `score`, as `options` são os níveis **do mais baixo para o mais alto**.
- `choice`/`score`: de 2 a 255 opções. Limite de perguntas: 400 (OpenAI), 1000 (Gemini), 256 (System One).
- Os tipos também aceitam os nomes nativos (`noul`, `predicate`, `rubric`...).

Erros (`application/problem+json`):

| Status | Quando |
|---|---|
| `400` | JSON malformado ou validação (`errors[]` com `field` e `message`) |
| `401` | Token ausente ou inválido (quando `decisions.api-token` está definido) |
| `404` | Provedor não habilitado |
| `422` | O provedor não suporta o pedido (ex.: imagens no Jev), sem chamar a API |
| `502` | A API do provedor respondeu com erro (`upstreamStatus`, `provider`) |

As mensagens de validação seguem o `Accept-Language` (inglês ou português; sem o cabeçalho, português).

## Estrutura

```
DecisionsApplication
config/       DecisionsProperties (@ConfigurationProperties), DecisionProvidersConfiguration
domain/       DecisionRequest, Question, Option, Answer, DecisionResult (Bean Validation)
provider/     DecisionProvider, ProviderRegistry, ProviderKind/ProviderStatus, Answers (parser tolerante)
  systemone/  TypeSafe Jev, Microsoft-Decision-1, hosts System One genéricos (RestClient)
  openai/     OpenAI e Azure OpenAI (SDK openai-java)
  gemini/     Gemini Decisions (SDK google-genai)
  local/      heurística offline
service/      DecisionService (decide / compare em virtual threads)
web/api/      DecisionApiController, ApiExceptionHandler (ProblemDetail), ApiTokenFilter
web/ui/       PlaygroundController, formulário, exemplos (templates/playground.html)
```

Para adicionar um provedor: implemente `DecisionProvider` (usando o SDK do fornecedor, se houver, ou estendendo
`HttpDecisionProvider` com `buildBody` e `parseAnswers`), inclua-o em `ProviderKind` e registre-o em
`DecisionProvidersConfiguration`.

## Testes

```bash
./mvnw test
```

- Provedores com `RestClient`: `MockRestServiceServer` valida URL, cabeçalhos, corpo enviado e leitura da resposta.
- Provedores com SDK: um servidor HTTP local valida o JSON que o próprio SDK envia e o mapeamento das respostas
  tipadas (incluindo `refusal`, bloqueio por filtro de conteúdo e erros HTTP).
- Configuração: `ApplicationContextRunner` cobre quais provedores são habilitados por quais propriedades.
- Web: `MockMvc` (`@SpringBootTest` e `@WebMvcTest`) cobre API, mapeamento de erros, token e playground.
- O perfil `test` zera todas as chaves, então os testes nunca chamam APIs reais, mesmo com chaves no ambiente.

## Aviso sobre os formatos das APIs

Essas APIs são muito recentes (out–nov/2026) e várias estão em beta. OpenAI, Azure e Gemini seguem os tipos
dos SDKs oficiais; para atualizar, basta subir `openai-java.version` / `google-genai.version` no `pom.xml`.
Jev e Microsoft-Decision-1 seguem a documentação pública do protocolo System One, com parser tolerante; se o
contrato mudar, o ajuste fica em `buildBody`/`parseAnswers` de `SystemOneProvider`.
