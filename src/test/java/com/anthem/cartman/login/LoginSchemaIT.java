package com.anthem.cartman.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.anthem.cartman.toolaccess.domain.ToolAccessMask;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginRepository;
import com.anthem.cartman.toolaccess.domain.CartmanTool;

/**
 * Verifies the entity mapping against the <em>real</em> COSMO database.
 *
 * <p>Not part of the normal build — it needs network access to the shared server. Run with:
 * <pre>mvn test -Dtest=LoginSchemaIT -Dcartman.it.enabled=true</pre>
 *
 * <p>Guards the assumptions this module depends on:
 * <ul>
 *   <li>{@code Log_Nm} is {@code char(10)} and must come back trimmed</li>
 *   <li>{@code Log_CartmanTools} is {@code varchar(32)} and is {@code NULL} for most users</li>
 *   <li>every populated mask parses cleanly and round-trips to the same 32 characters</li>
 * </ul>
 *
 * <p><strong>This test points at a shared database that other people depend on.</strong> It runs
 * under {@code schema-it}, a self-contained read-only profile — not {@code dev}, and not the
 * unit-test defaults. It reads; it must never write. An earlier version activated {@code dev}
 * and inherited {@code ddl-auto: create-drop} from the layer beneath it, which dropped
 * {@code TM_Login} and lost a row that no backup could return. Keep every assertion read-only,
 * and do not add a profile to this class.
 */
@SpringBootTest
@ActiveProfiles("schema-it")
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(
        named = "cartman.it.enabled", matches = "true")
class LoginSchemaIT {

    @Autowired
    private LoginRepository repository;

    @Test
    @DisplayName("TM_Login is readable and Log_Nm comes back without char(10) padding")
    void readsLoginsWithoutPadding() {
        List<Login> all = repository.findAllByOrderByLoginNameAsc();
        assertFalse(all.isEmpty(), "TM_Login should not be empty");

        for (Login l : all) {
            assertEquals(l.getLoginName(), l.getLoginName().trim(),
                    "Log_Nm is char(10); the converter must strip padding");
        }
    }

    @Test
    @DisplayName("Every populated Log_CartmanTools round-trips to the same 32 characters")
    void masksRoundTrip() {
        List<Login> populated = repository.findAllByOrderByLoginNameAsc().stream()
                .filter(l -> l.getCartmanTools() != null && !l.getCartmanTools().isBlank())
                .toList();

        assertFalse(populated.isEmpty(), "expected at least one user with tools configured");

        for (Login l : populated) {
            String stored = l.getCartmanTools().trim();
            assertEquals(32, stored.length(), l.getLoginName() + " should hold 32 characters");

            String rendered = ToolAccessMask.parse(stored).toStoredValue();
            assertEquals(32, rendered.length());

            /*
             * Every position round-trips, including the nine CARTMAN has no tool for. Reading a
             * value and writing it back unchanged must be a no-op on real data — this used to
             * assert the opposite, that unknown positions render '0', which is how saving a user
             * silently revoked access the legacy application had granted.
             */
            assertEquals(stored, rendered,
                    l.getLoginName() + " must round-trip unchanged, all 32 positions");
        }
    }

    @Test
    @DisplayName("A null Log_CartmanTools yields no tools — the majority case in production")
    void nullMasksAreCommonAndSafe() {
        long nulls = repository.findAllByOrderByLoginNameAsc().stream()
                .filter(l -> l.getCartmanTools() == null)
                .count();
        assertTrue(nulls > 0, "production data has many users with no tools configured");

        repository.findAllByOrderByLoginNameAsc().stream()
                .filter(l -> l.getCartmanTools() == null)
                .findFirst()
                .ifPresent(l -> assertEquals(0, ToolAccessMask.parse(l.getCartmanTools()).count()));
    }
}
