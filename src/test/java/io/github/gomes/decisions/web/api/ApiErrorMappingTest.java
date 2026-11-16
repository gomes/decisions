package io.github.gomes.decisions.web.api;

import io.github.gomes.decisions.TestFixtures;
import io.github.gomes.decisions.provider.DecisionException;
import io.github.gomes.decisions.service.DecisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How provider failures surface in the API, with the service mocked. */
@WebMvcTest(controllers = DecisionApiController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiTokenFilter.class))
@ActiveProfiles("test")
class ApiErrorMappingTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DecisionService service;

    @Test
    void upstreamErrorIs502WithStatus() throws Exception {
        when(service.decide(eq("openai"), any())).thenThrow(
                new DecisionException("openai", "HTTP 429: rate limited", 429, "{}", null));
        mvc.perform(post("/api/v1/decisions").param("provider", "openai")
                        .contentType(MediaType.APPLICATION_JSON).content(TestFixtures.TICKET_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.upstreamStatus").value(429))
                .andExpect(jsonPath("$.provider").value("openai"));
    }

    @Test
    void unsupportedRequestIs422() throws Exception {
        when(service.decide(eq("typesafe"), any())).thenThrow(
                new DecisionException("typesafe", "this provider does not accept image input"));
        mvc.perform(post("/api/v1/decisions").param("provider", "typesafe")
                        .contentType(MediaType.APPLICATION_JSON).content(TestFixtures.TICKET_JSON))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("[typesafe] this provider does not accept image input"));
    }
}
