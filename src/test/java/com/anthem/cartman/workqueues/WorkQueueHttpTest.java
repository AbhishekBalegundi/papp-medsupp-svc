package com.anthem.cartman.workqueues;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract of the Work Queue Manager.
 *
 * <p>Queue ids come from the seed: Birthday is 80, the two {@code over 65} rows are 7 and 40.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = "classpath:tm-login-h2.sql")
class WorkQueueHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("the queue list carries the real Cal_Type, blank where there is none")
    void listsQueues() throws Exception {
        mvc.perform(get("/api/work-queues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.id == 80)].type").value("Sales"))
                .andExpect(jsonPath("$[?(@.id == 99)].type").value(""));
    }

    @Test
    @DisplayName("a queue board carries the definition and both user lists")
    void readsBoard() throws Exception {
        mvc.perform(get("/api/work-queues/80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.name").value("Birthday"))
                .andExpect(jsonPath("$.queue.description").value("The Birthday List."))
                .andExpect(jsonPath("$.permitted.length()").value(3))
                .andExpect(jsonPath("$.permitted[?(@.name == 'GoneUser')].known").value(false));
    }

    @Test
    @DisplayName("an unknown queue answers 404 with a problem detail")
    void unknownQueue() throws Exception {
        mvc.perform(get("/api/work-queues/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No work queue with id 999999."));
    }

    @Test
    @DisplayName("creating a queue answers its board")
    void createsQueue() throws Exception {
        mvc.perform(post("/api/work-queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HttpQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.name").value("HttpQ"))
                .andExpect(jsonPath("$.queue.type").value("Sales"));
    }

    @Test
    @DisplayName("a duplicate name answers 400, not a second row")
    void refusesDuplicateName() throws Exception {
        mvc.perform(post("/api/work-queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Birthday\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("A work queue named Birthday already exists."));
    }

    @Test
    @DisplayName("a blank name is rejected by validation")
    void refusesBlankName() throws Exception {
        mvc.perform(post("/api/work-queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("saving a definition answers the redrawn board")
    void savesDefinition() throws Exception {
        mvc.perform(put("/api/work-queues/80")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Edited.\",\"sql\":\"App_Route_To = 'B'\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.description").value("Edited."))
                .andExpect(jsonPath("$.queue.sql").value("App_Route_To = 'B'"));
    }

    @Test
    @DisplayName("granting moves the user from available to permitted")
    void grants() throws Exception {
        mvc.perform(post("/api/work-queues/80/permissions/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"users\":[\"CosmoAdm\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitted[?(@.name == 'CosmoAdm')]").exists())
                .andExpect(jsonPath("$.available[?(@.name == 'CosmoAdm')]").doesNotExist());
    }

    @Test
    @DisplayName("granting to a name that is not a login answers 404")
    void refusesUnknownGrantee() throws Exception {
        mvc.perform(post("/api/work-queues/80/permissions/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"users\":[\"NotAUser\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the By User tab carries assigned queues, including one that no longer exists")
    void readsUserBoard() throws Exception {
        mvc.perform(get("/api/work-queues/by-user/DStante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("DStante"))
                .andExpect(jsonPath("$.assigned[?(@.name == 'IHPFULLYQ')].known").value(false))
                .andExpect(jsonPath("$.assigned[?(@.name == 'Birthday')].id").value(80));
    }

    @Test
    @DisplayName("A29 — unassigning by name removes the row the Delphi never could")
    void unassigns() throws Exception {
        mvc.perform(post("/api/work-queues/by-user/DStante/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queues\":[\"Birthday\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned[?(@.name == 'Birthday')]").doesNotExist())
                .andExpect(jsonPath("$.available[?(@.name == 'Birthday')]").exists());
    }

    @Test
    @DisplayName("assigning by id moves the queue across")
    void assigns() throws Exception {
        mvc.perform(post("/api/work-queues/by-user/CosmoAdm/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queueIds\":[80]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigned[?(@.name == 'Birthday')].id").value(80));
    }

    @Test
    @DisplayName("A30 — the copy the Delphi could never complete answers what it wrote")
    void copies() throws Exception {
        mvc.perform(post("/api/work-queues/copy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"DStante\",\"to\":\"CosmoAdm\",\"replace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copied").value(3))
                .andExpect(jsonPath("$.removed").value(1));
    }

    @Test
    @DisplayName("there is no delete endpoint; the Delphi button had no handler (A33)")
    void hasNoDelete() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/work-queues/80"))
                .andExpect(status().isMethodNotAllowed());
    }
}
