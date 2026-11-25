package com.anthem.cartman.toolaccess.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.anthem.cartman.toolaccess.service.AdminPasswordGate;

class AdminPasswordGateTest {

    @Nested
    @DisplayName("when a password is configured")
    class Configured {

        private final AdminPasswordGate gate = new AdminPasswordGate("s3cret");

        @Test
        @DisplayName("the exact password passes")
        void matchesExact() {
            assertTrue(gate.configured());
            assertTrue(gate.matches("s3cret"));
            assertDoesNotThrow(() -> gate.require("s3cret"));
        }

        @Test
        @DisplayName("anything else is rejected — including near misses and null")
        void rejectsEverythingElse() {
            assertFalse(gate.matches("s3cret "));
            assertFalse(gate.matches("S3cret"));
            assertFalse(gate.matches(""));
            assertFalse(gate.matches(null));

            assertThrows(AdminPasswordGate.WrongAdminPasswordException.class,
                    () -> gate.require("wrong"));
            assertThrows(AdminPasswordGate.WrongAdminPasswordException.class,
                    () -> gate.require(null));
        }
    }

    @Nested
    @DisplayName("when no password is configured")
    class Unconfigured {

        @Test
        @DisplayName("there is no back door — every check fails, loudly on require")
        void rejectsEverything() {
            for (AdminPasswordGate gate : new AdminPasswordGate[] {
                    new AdminPasswordGate(null), new AdminPasswordGate(""), new AdminPasswordGate("   ")}) {
                assertFalse(gate.configured());
                assertFalse(gate.matches(""));
                assertFalse(gate.matches(null));
                assertThrows(AdminPasswordGate.AdminGateNotConfiguredException.class,
                        () -> gate.require("anything"));
            }
        }
    }
}
