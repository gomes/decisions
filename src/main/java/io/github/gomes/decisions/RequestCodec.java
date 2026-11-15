package io.github.gomes.decisions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import io.github.gomes.decisions.http.JsonHttp;
import io.github.gomes.decisions.model.DecisionRequest;

/** Reads the app's provider-neutral request JSON (see README). */
public final class RequestCodec {

    private RequestCodec() {
    }

    public static DecisionRequest read(String json) {
        try {
            return JsonHttp.MAPPER.readValue(json, DecisionRequest.class);
        } catch (ValueInstantiationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new IllegalArgumentException(c.getMessage(), e);
        } catch (JsonProcessingException e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String msg = root instanceof IllegalArgumentException ? root.getMessage() : e.getOriginalMessage();
            throw new IllegalArgumentException("invalid request JSON: " + msg, e);
        }
    }

    public static String write(Object value) {
        try {
            return JsonHttp.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final String EXAMPLE = """
            {
              "input": "Olá, meu pedido #4411 chegou com a caneca azul quebrada e a caixa danificada. Podem enviar outra?",
              "questions": [
                {
                  "name": "is_damaged",
                  "type": "yes_no",
                  "instructions": "Does the customer report a damaged or broken item?",
                  "trueWhen": "item arrived broken, cracked, damaged, quebrada, danificada",
                  "falseWhen": "item arrived fine, question unrelated to damage"
                },
                {
                  "name": "queue",
                  "type": "choice",
                  "instructions": "Which support queue should handle this message?",
                  "options": [
                    {"value": "billing",  "description": "payments, invoices, refund, reembolso"},
                    {"value": "shipping", "description": "delivery, tracking, broken or damaged items, quebrada, danificada"},
                    {"value": "technical","description": "login, bugs, app errors"}
                  ]
                },
                {
                  "name": "urgency",
                  "type": "score",
                  "instructions": "How urgent is this message?",
                  "options": [
                    {"value": "low",    "description": "general question, no impact"},
                    {"value": "medium", "description": "customer impacted, refund or replacement needed"},
                    {"value": "high",   "description": "legal threat, safety issue, outage"}
                  ]
                }
              ]
            }
            """;
}
