package com.anthem.cartman.appdata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.appdata.repository.DependentRecordRepository;

/**
 * A43 — deleting an application must be one transaction.
 *
 * <p>The Delphi runs five statements, each swallowing its own error, so the parent row can go while
 * the children stay. This forces the last step to fail and checks that nothing at all was removed.
 * A test that only ever sees the happy path cannot tell a transaction from five statements in a row.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class AppDataDeleteRollbackTest {

    @Autowired
    private AppDataService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private DependentRecordRepository dependents;

    private int count(String table, String where) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where " + where,
                Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("a failure part way leaves the application and every dependent row in place")
    void failurePartWayLeavesEverything() {
        // The repository is a @Repository bean, so Spring translates what it throws; the type is
        // not the point, only that the failure propagates and the transaction rolls back.
        doThrow(new IllegalStateException("the database went away"))
                .when(dependents).deleteRecordHistory(anyInt());

        assertThrows(RuntimeException.class, () -> service.deleteApplication(3455));

        assertEquals(1, count("TM_Application", "App_ID = 3455"), "the application survives");
        assertEquals(2, count("TM_UW_Actions", "UW_App_ID = 3455"), "its actions survive");
        assertEquals(2, count("TM_App_Floating_Note", "FN_Application = 3455"), "its notes survive");
        assertEquals(2, count("TM_Record_History", "rtrim(RHist_ID) = '3455'"), "its history survives");
    }
}
