package com.anthem.cartman.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.toolaccess.domain.ToolAccessMask;
import com.anthem.cartman.common.config.PersistenceConfig;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;

/**
 * Proves the {@link Login} entity binds to the Delphi column names exactly as written.
 *
 * <p>Runs against H2 seeded from {@code tm-login-h2.sql}, which spells the columns the way
 * SQL Server spells them. That makes it a standing regression test for a real defect: Spring
 * Boot's default physical naming strategy rewrote {@code Log_CartmanTools} to
 * {@code log_cartman_tools}, and every query against the genuine table failed with
 * <em>Invalid column name</em>. It went unnoticed because the only integration test ran
 * against a table Hibernate had itself created, using its own naming.
 *
 * <p>{@link com.anthem.cartman.common.config.PersistenceConfig} is the fix. If it is removed this
 * test fails in the normal build, without needing the shared database.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class LoginMappingTest {

    /** The port, not the Spring Data interface — {@code save} is ambiguous across the two. */
    @Autowired
    private LoginLookup repository;

    /**
     * Looks a row up by name without going through {@code findByLoginNameIgnoreCase}.
     *
     * <p>{@code Log_Nm} is {@code char(10)}, so the stored value is blank padded. SQL Server and
     * PostgreSQL both treat trailing blanks as insignificant when comparing {@code char}, so the
     * derived query matches there — {@link LoginSchemaIT} confirms it against the real database.
     * H2 compares the padded value literally and finds nothing. That difference is H2's, not a
     * defect in the query, and this test is about column binding, so it sidesteps it.
     */
    private Login byName(String name) {
        return repository.findAllByOrderByLoginNameAsc().stream()
                .filter(l -> l.getLoginName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("seed row missing: " + name));
    }

    @Test
    @DisplayName("Every mapped column resolves against Delphi's spelling")
    void allColumnsResolve() {
        List<Login> all = repository.findAllByOrderByLoginNameAsc();

        assertEquals(7, all.size(), "the seed script inserts seven rows");
    }

    @Test
    @DisplayName("Log_CartmanTools is read from the column of that exact name")
    void readsToolMask() {
        Login darron = byName("DStante");

        assertEquals("11111111111111111111111111111111", darron.getCartmanTools().trim());
    }

    @Test
    @DisplayName("char(10) padding is stripped on the way out")
    void trimsPaddedLoginName() {
        for (Login l : repository.findAllByOrderByLoginNameAsc()) {
            assertEquals(l.getLoginName(), l.getLoginName().trim());
        }
    }

    @Test
    @DisplayName("char(5) Log_Typ is trimmed by the converter")
    void trimsLoginType() {
        Login darron = byName("DStante");

        assertEquals("Admin", darron.getLoginType());
    }

    @Test
    @DisplayName("A NULL mask means no tools, which is the common case in production")
    void nullMaskGrantsNothing() {
        Login none = byName("NoTools");

        assertNull(none.getCartmanTools());
        assertEquals(0, ToolAccessMask.parse(none.getCartmanTools()).count());
    }

    @Test
    @DisplayName("A mask of 32 spaces also means no tools — SITUW and SITCORSP hold exactly that")
    void blankMaskGrantsNothing() {
        Login blank = byName("SITUW");

        assertFalse(blank.getCartmanTools() == null, "the column holds spaces, not NULL");
        assertEquals(0, ToolAccessMask.parse(blank.getCartmanTools()).count());
    }

    @Test
    @DisplayName("Log_Enabled of 0 is the only disabled state")
    void readsEnabledFlag() {
        assertTrue(byName("DStante").isEnabled());
        assertFalse(byName("DisabledUs").isEnabled());
    }

    @Test
    @DisplayName("A write reaches the same column it was read from")
    void maskRoundTripsThroughTheDatabase() {
        Login login = byName("NoTools");
        login.setCartmanTools("10000000000000000000000000000000");
        repository.save(login);

        assertEquals("10000000000000000000000000000000", byName("NoTools").getCartmanTools().trim());
    }
}
