package com.anthem.cartman.toolaccess;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With no {@code CARTMAN_ADMIN_PASSWORD} in the environment (the default test context),
 * the gate must fail closed — 503 with an actionable message, never a silent pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminGateUnconfiguredHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("unlock answers 503 and says which variable to set")
    void unlockAnswersServiceUnavailable() throws Exception {
        mvc.perform(post("/api/tool-access/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"anything\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("CARTMAN_ADMIN_PASSWORD")));
    }
}
