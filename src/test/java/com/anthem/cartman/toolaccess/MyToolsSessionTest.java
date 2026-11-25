package com.anthem.cartman.toolaccess;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-1.7 — the shell reads its toolbar from {@code /api/tool-access/me}.
 *
 * <p>This endpoint originally took a {@code Principal}, which the interim session sign-in never
 * populates, so it answered 401 for every valid session and the toolbar would have been empty
 * for everyone. These tests pin it to the session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "classpath:tm-login-h2.sql")
class MyToolsSessionTest {

    @Autowired
    private MockMvc mvc;

    private MockHttpSession signIn(String user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + user + "\",\"password\":\"realpw\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    @Test
    @DisplayName("without a session the answer is 401")
    void anonymousIsUnauthorised() throws Exception {
        mvc.perform(get("/api/tool-access/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("with a session it returns that user's granted tools")
    void returnsGrantedTools() throws Exception {
        // TenCharUsr's seeded mask is all ones, so every grantable tool comes back.
        mvc.perform(get("/api/tool-access/me").session(signIn("TenCharUsr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@ == 'USER_MANAGER')]").exists())
                .andExpect(jsonPath("$[?(@ == 'PERMISSIONS')]").exists());
    }

    /**
     * The five indices the Delphi commented out of {@code UtilityClick} open nothing, so they are
     * never returned however the mask reads, and neither are the four positions that are out of
     * CARTMAN's scope. Every other position is a wired tile and does come back — Password Encryptor
     * among them, which the Delphi wires at 28.
     */
    @Test
    @DisplayName("the five positions that open nothing never come back, whatever the mask says")
    void nonGrantableToolsAreNeverGranted() throws Exception {
        mvc.perform(get("/api/tool-access/me").session(signIn("TenCharUsr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == 'REPORT_RESET')]").doesNotExist())
                .andExpect(jsonPath("$[?(@ == 'ROTATION_MANAGER')]").doesNotExist())
                .andExpect(jsonPath("$[?(@ == 'RATE_CALC')]").doesNotExist())
                .andExpect(jsonPath("$[?(@ == 'RATE_DG')]").doesNotExist())
                .andExpect(jsonPath("$[?(@ == 'DELETE_NOTES')]").doesNotExist())
                .andExpect(jsonPath("$[?(@ == 'PASSWORD_ENCRYPTOR')]").exists());
    }

}
