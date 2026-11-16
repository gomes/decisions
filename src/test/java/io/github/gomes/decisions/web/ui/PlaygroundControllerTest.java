package io.github.gomes.decisions.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaygroundControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void showsProvidersWithStatusAndTheFirstExample() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("playground"))
                .andExpect(model().attribute("selectedExample", "ticket"))
                .andExpect(content().string(allOf(
                        containsString("OpenAI Decisions"),
                        containsString("OPENAI_API_KEY"),
                        containsString("desabilitado"),
                        containsString("habilitado"),
                        containsString("value=\"is_damaged\""),
                        containsString("Executar decisão"))));
    }

    @Test
    void loadsAnExample() throws Exception {
        mvc.perform(get("/").param("example", "refund"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("daysSinceDelivery")));
    }

    @Test
    void runsTheFormAndShowsResults() throws Exception {
        mvc.perform(post("/")
                        .param("providers", "local")
                        .param("models[local]", "")
                        .param("input", "Meu pedido chegou com a caneca quebrada e a caixa danificada")
                        .param("questions[0].name", "is_damaged")
                        .param("questions[0].type", "yes_no")
                        .param("questions[0].instructions", "O item chegou danificado?")
                        .param("questions[0].trueWhen", "quebrada danificada")
                        .param("questions[1].name", "queue")
                        .param("questions[1].type", "choice")
                        .param("questions[1].instructions", "Qual fila?")
                        .param("questions[1].options", "billing | pagamentos\nshipping | entrega, quebrada, danificada")
                        .param("data", "{\"order\": 4411}"))
                .andExpect(status().isOk())
                .andExpect(model().hasNoErrors())
                .andExpect(model().attributeExists("outcomes", "requestJson"))
                .andExpect(content().string(allOf(
                        containsString("Resultado"),
                        containsString("class=\"value\">shipping<"),
                        containsString("/api/v1/compare?providers=local"))));
    }

    @Test
    void showsFieldErrorsInPortuguese() throws Exception {
        mvc.perform(post("/").header("Accept-Language", "pt-BR")
                        .param("input", "x")
                        .param("data", "{not json")
                        .param("questions[0].name", "1bad")
                        .param("questions[0].type", "choice")
                        .param("questions[0].instructions", "?")
                        .param("questions[0].options", "only"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "providers", "data",
                        "questions[0].name", "questions[0].options"))
                .andExpect(model().attributeDoesNotExist("outcomes"))
                .andExpect(content().string(allOf(
                        containsString("selecione ao menos um provedor"),
                        containsString("JSON inválido"),
                        not(containsString("Resultado")))));
    }

    @Test
    void rejectsProvidersThatAreNotEnabled() throws Exception {
        mvc.perform(post("/")
                        .param("providers", "openai")
                        .param("input", "x")
                        .param("questions[0].name", "q")
                        .param("questions[0].type", "yes_no")
                        .param("questions[0].instructions", "?"))
                .andExpect(model().attributeHasFieldErrors("form", "providers"))
                .andExpect(model().attributeDoesNotExist("outcomes"));
    }

    @Test
    void parsesOptionLines() {
        var options = PlaygroundFormMapper.parseOptions("""
                billing | pagamentos | e reembolsos

                shipping
                  technical |  login  """);
        org.assertj.core.api.Assertions.assertThat(options).extracting("value", "description").containsExactly(
                org.assertj.core.groups.Tuple.tuple("billing", "pagamentos | e reembolsos"),
                org.assertj.core.groups.Tuple.tuple("shipping", ""),
                org.assertj.core.groups.Tuple.tuple("technical", "login"));
    }
}
