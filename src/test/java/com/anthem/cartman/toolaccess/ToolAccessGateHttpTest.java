package com.anthem.cartman.toolaccess;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import com.anthem.cartman.toolaccess.controller.ToolAccessController;

/**
 * The HTTP contract of the administrator-password gate, end to end through the controller.
 *
 * <p>This is the check the Delphi never had: it compared the password in the click handler
 * and ran the UPDATE unprotected. Here a mutation without the password must die at the door.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "cartman.admin-password=test-admin-pw")
@Sql(scripts = "classpath:tm-login-h2.sql")
class ToolAccessGateHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("unlock: the right password answers 204, no body")
    void unlockRight() throws Exception {
        mvc.perform(post("/api/tool-access/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-pw\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("unlock: a wrong password answers 401 with a problem detail")
    void unlockWrong() throws Exception {
        mvc.perform(post("/api/tool-access/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ras1j127\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong administrator password."));
    }

    @Test
    @DisplayName("a mutation without the password header dies at the door")
    void updateWithoutHeader() throws Exception {
        mvc.perform(put("/api/tool-access/users/TenCharUsr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tools\":[\"WORK_QUEUES\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a mutation with the right header goes through")
    void updateWithHeader() throws Exception {
        mvc.perform(put("/api/tool-access/users/TenCharUsr")
                        .header(ToolAccessController.ADMIN_PASSWORD_HEADER, "test-admin-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tools\":[\"WORK_QUEUES\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCount").value(1));
    }

    @Test
    @DisplayName("copy is gated the same way")
    void copyWithoutHeader() throws Exception {
        mvc.perform(post("/api/tool-access/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"DStante\",\"to\":\"TenCharUsr\"}"))
                .andExpect(status().isUnauthorized());
    }
}
