package com.anthem.cartman.login.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "classpath:tm-login-h2.sql")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    private MvcResult signIn(String user, String password, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    @Test
    @DisplayName("a valid sign-in returns the user and never the password")
    void signsIn() throws Exception {
        MvcResult result = signIn("TenCharUsr", "realpw", 200);

        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("realpw"),
                "the response must not carry the password: " + body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("TenCharUsr"));
    }

    @Test
    @DisplayName("a wrong password is 401 and starts no session")
    void wrongPassword() throws Exception {
        MvcResult result = signIn("TenCharUsr", "nope", 401);

        org.junit.jupiter.api.Assertions.assertNull(result.getRequest().getSession(false),
                "a failed sign-in must not create a session");
    }

    @Test
    @DisplayName("a locked account is refused with 403")
    void lockedAccount() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"LockedUser\",\"password\":\"s3cret\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Five consecutive")));
    }

    @Test
    @DisplayName("a disabled account is refused with 403")
    void disabledAccount() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"DisabledUs\",\"password\":\"s3cret\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/me is 401 without a session and returns the user with one")
    void meReflectsTheSession() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        HttpSession session = signIn("TenCharUsr", "realpw", 200).getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        mvc.perform(get("/api/auth/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginName").value("TenCharUsr"));
    }

    @Test
    @DisplayName("logout ends the session, so /me is 401 again")
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session =
                (MockHttpSession) signIn("TenCharUsr", "realpw", 200).getRequest().getSession(false);

        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TM_Login.Log_Pw is ignored — only TM_LoginPW.Lpw_Pw authenticates")
    void ignoresTheEmptyLogPwColumn() throws Exception {
        // The seed sets Log_Pw='s3cret' and Lpw_Pw='realpw' for this user.
        signIn("TenCharUsr", "s3cret", 401);
    }

    @Test
    @DisplayName("a blank login name is rejected before any lookup")
    void rejectsBlankInput() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginName\":\"\",\"password\":\"s3cret\"}"))
                .andExpect(status().is4xxClientError());
    }
}
