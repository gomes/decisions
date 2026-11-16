package io.github.gomes.decisions.web.api;

import io.github.gomes.decisions.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application with only the offline provider enabled. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecisionApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void listsEveryProviderWithItsStatus() throws Exception {
        mvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[?(@.id == 'openai')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.id == 'local')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.id == 'local')].capabilities.maxQuestions").value(10_000));
    }

    @Test
    void decidesWithTheLocalProvider() throws Exception {
        mvc.perform(post("/api/v1/decisions").param("provider", "local")
                        .contentType(MediaType.APPLICATION_JSON).content(TestFixtures.TICKET_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.answers[0].name").value("is_damaged"))
                .andExpect(jsonPath("$.answers[0].value").value("true"))
                .andExpect(jsonPath("$.answers[1].value").value("shipping"))
                .andExpect(jsonPath("$.raw").doesNotExist());
    }

    @Test
    void comparesAcrossProviders() throws Exception {
        mvc.perform(post("/api/v1/compare").param("model.local", "custom-model")
                        .contentType(MediaType.APPLICATION_JSON).content(TestFixtures.TICKET_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].provider").value("local"))
                .andExpect(jsonPath("$[0].result.model").value("custom-model"));
    }

    @Test
    void unknownProviderIs404ProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/decisions").param("provider", "openai")
                        .contentType(MediaType.APPLICATION_JSON).content(TestFixtures.TICKET_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Provider not enabled"));
    }

    @Test
    void invalidRequestListsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/decisions").param("provider", "local").header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"questions": [{"name": "q", "type": "choice", "instructions": "?",
                                                "options": [{"value": "only"}]}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'input')].message")
                        .value("provide input text, data or at least one image"))
                .andExpect(jsonPath("$.errors[?(@.field == 'questions[0].options')]").exists());
    }

    @Test
    void validationMessagesFollowAcceptLanguage() throws Exception {
        mvc.perform(post("/api/v1/decisions").param("provider", "local").header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questions\": [{\"name\": \"q\", \"type\": \"yes_no\", \"instructions\": \"?\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("informe um texto, dados JSON ou ao menos uma imagem"));
    }

    @Test
    void malformedJsonExplainsTheReason() throws Exception {
        mvc.perform(post("/api/v1/decisions").param("provider", "local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\": \"x\", \"questions\": [{\"name\": \"q\", \"type\": \"maybe\", \"instructions\": \"?\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("unknown question type: maybe"));
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
