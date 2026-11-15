# decisions

App Java que fala com as **Decision APIs** mais usadas hoje (modelos que *escolhem* entre respostas
definidas por você, em vez de gerar texto) através de **um único formato de requisição**.

| Provedor (`id`) | API | Endpoint | Modelo padrão | Imagens |
|---|---|---|---|---|
| `typesafe` | TypeSafe **Jev** (protocolo *System One*) | `POST https://api.typesafe.ai/v1/systemone` | `jev-latest` | não |
| `openai` | **OpenAI Decisions API** | `POST https://api.openai.com/v1/decisions` | `gpt-6-luna` | sim |
| `azure-openai` | **Azure OpenAI Decisions** (Foundry) | `POST https://<recurso>.openai.azure.com/openai/v1/decisions` | nome do deployment | sim |
| `gemini` | **Gemini Decisions API** | `POST https://generativelanguage.googleapis.com/v1beta/decisions` | `gemini-decision-1` | sim |
| `microsoft` | **Microsoft-Decision-1** (Foundry, System One) | `POST <endpoint>/providers/microsoft/v1/systemone` | nome do deployment | não |
| `systemone` | Qualquer host compatível com System One (ex.: DigitalOcean) | configurável | configurável | não |
| `local` | Heurística offline (não é um modelo; serve para testes e como baseline) | — | `keyword-overlap-v1` | ignora |

Todas usam os mesmos três tipos de pergunta, com nomes diferentes. A app normaliza:

| Tipo na app | System One / Gemini | OpenAI / Azure | Resposta normalizada |
|---|---|---|---|
| `yes_no` | `noul` | `predicate` | `value` ("true"/"false"), `probability` = P(true), `confidence` |
| `choice` | `choice` | `choice` | `value` escolhido, `probabilities` por opção, `confidence` |
| `score` | `score` | `score` | `value` (nível mais provável), `score` (índice esperado, 0 = mais baixo), `probabilities`, `confidence` |

Quando o provedor não devolve `confidence`, ela é derivada como a margem entre as duas respostas mais prováveis.

## Requisitos

- Java 21+
- Maven 3.9+

```bash
mvn package            # compila, roda os testes e gera target/decisions-0.1.0-SNAPSHOT.jar
```

## Configuração

Cada provedor é habilitado automaticamente quando suas variáveis de ambiente existem:

| Provedor | Variáveis obrigatórias | Opcionais |
|---|---|---|
| `typesafe` | `TYPESAFE_API_KEY` | `TYPESAFE_BASE_URL`, `TYPESAFE_MODEL` |
| `openai` | `OPENAI_API_KEY` | `OPENAI_BASE_URL`, `OPENAI_DECISIONS_MODEL` |
| `azure-openai` | `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` | `AZURE_OPENAI_DECISIONS_DEPLOYMENT` |
| `gemini` | `GEMINI_API_KEY` | `GEMINI_BASE_URL`, `GEMINI_DECISIONS_MODEL` |
| `microsoft` | `FOUNDRY_API_KEY`, `FOUNDRY_ENDPOINT` | `FOUNDRY_DECISION_DEPLOYMENT` |
| `systemone` | `SYSTEMONE_ENDPOINT` (URL completa), `SYSTEMONE_API_KEY`, `SYSTEMONE_MODEL` | — |
| gateway | — | `DECISIONS_GATEWAY_TOKEN`, `DECISIONS_TIMEOUT_SECONDS` (padrão 30) |

`*_BASE_URL` permite apontar para proxies/gateways compatíveis (ex.: OpenRouter, AI/ML API, Portkey).

## Formato da requisição

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

- `input`, `data` e `images` são opcionais individualmente, mas pelo menos um é obrigatório.
- Para `score`, as `options` são os níveis **do mais baixo para o mais alto**.
- `choice`/`score`: de 2 a 255 opções. Limite de perguntas: 400 (OpenAI), 1000 (Gemini), 256 (System One).
- Os tipos também aceitam os nomes nativos (`noul`, `predicate`, `rubric`…).
- Provedores sem suporte a imagens recusam a requisição *antes* de chamar a API.
- Em provedores só de texto, `data` é serializado e anexado ao texto (System One aceita JSON puro em `state`).

Veja [`examples/`](examples/).

## CLI

```bash
J=target/decisions-0.1.0-SNAPSHOT.jar
java -jar $J providers                                  # provedores configurados
java -jar $J example > req.json                         # requisição de exemplo
java -jar $J decide  -p openai -f req.json              # um provedor
java -jar $J decide  -p gemini -m gemini-decision-1 -f req.json
cat req.json | java -jar $J compare                     # todos os provedores em paralelo
java -jar $J compare -p typesafe,microsoft,local -f req.json
```

## Gateway HTTP

```bash
DECISIONS_GATEWAY_TOKEN=segredo java -jar $J serve --host 127.0.0.1 --port 8080
```

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | sem autenticação |
| `GET` | `/v1/providers` | provedores configurados |
| `POST` | `/v1/decisions?provider=<id>[&model=<m>]` | decide com um provedor |
| `POST` | `/v1/compare[?providers=a,b][&model.<id>=<m>]` | mesmo pedido em vários provedores, em paralelo |

```bash
curl -s -H 'Authorization: Bearer segredo' -X POST \
  --data-binary @examples/ticket.json 'localhost:8080/v1/decisions?provider=typesafe'
```

Erros: `400` requisição inválida, `401` token, `404` provedor/rota inexistente,
`422` provedor não suporta o pedido (ex.: imagens), `502` erro da API do provedor (com `upstreamStatus`).

## Uso como biblioteca

```java
var service = new DecisionService(ProviderRegistry.fromEnvironment());

var request = DecisionRequest.builder()
        .input("Meu pedido chegou com a caneca quebrada.")
        .question(Question.yesNo("is_damaged", "O item chegou danificado?"))
        .question(Question.choice("queue", "Qual fila?",
                Option.of("billing", "pagamentos"), Option.of("shipping", "entrega e avarias")))
        .build();

DecisionResult r = service.decide("gemini", request);
if (r.answer("is_damaged").probability() > 0.9) { ... }

List<DecisionService.Outcome> all = service.compare(request, List.of(), Map.of());
```

Para adicionar um provedor, implemente `DecisionProvider` (ou estenda `HttpDecisionProvider`,
implementando só `buildBody` e `parseAnswers`) e registre no `ProviderRegistry`.

## Arquitetura

```
model/        DecisionRequest, Question, Option, Answer, DecisionResult (formato neutro)
spi/          DecisionProvider, Capabilities, DecisionException
provider/
  Answers                 parser tolerante (noul/probability, choice/value, mapa ou lista de probabilidades)
  systemone/              TypeSafe Jev, Microsoft-Decision-1, hosts System One genéricos
  openai/                 OpenAI e Azure OpenAI
  gemini/                 Gemini Decisions
  local/                  heurística offline
DecisionService           decide / compare (virtual threads)
server/DecisionServer     gateway HTTP (JDK HttpServer, sem frameworks)
cli/Main                  linha de comando
```

Dependências de runtime: apenas Jackson. HTTP via `java.net.http`.

## Aviso sobre os formatos de API

Essas APIs são muito recentes (out–nov/2026) e várias estão em beta. Os formatos implementados seguem a
documentação pública e guias disponíveis no lançamento; os campos marcados como incertos são tratados de forma
tolerante no parser (vários nomes aceitos). Se um provedor mudar o contrato, o ajuste fica concentrado em
`buildBody`/`parseAnswers` da classe correspondente, e os testes em `src/test` documentam o formato esperado.
Os testes usam um servidor HTTP falso local, nenhum faz chamadas reais.
