package io.github.gomes.decisions.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "decisions.playground.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaygroundDisabledTest {

    @Autowired
    MockMvc mvc;

    @Test
    void playgroundCanBeTurnedOff() throws Exception {
        mvc.perform(get("/")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/providers")).andExpect(status().isOk());
    }
}
