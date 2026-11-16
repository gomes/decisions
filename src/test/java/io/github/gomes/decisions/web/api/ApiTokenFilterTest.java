package io.github.gomes.decisions.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "decisions.api-token=s3cret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTokenFilterTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiNeedsTheBearerToken() throws Exception {
        mvc.perform(get("/api/v1/providers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/v1/providers").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/providers").header("Authorization", "Bearer s3cret"))
                .andExpect(status().isOk());
    }

    @Test
    void playgroundAndHealthAreNotAffected() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
