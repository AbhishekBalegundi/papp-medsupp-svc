package com.anthem.cartman.common.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import com.anthem.cartman.common.config.SchemaManagementGuard;

/**
 * The guard exists because a misconfigured {@code ddl-auto} once dropped a live table.
 * These tests are the reason it stays correct.
 */
class SchemaManagementGuardTest {

    private final SchemaManagementGuard guard = new SchemaManagementGuard();

    private void run(MockEnvironment env) {
        guard.postProcessEnvironment(env, null);
    }

    private MockEnvironment env(String ddlAuto) {
        MockEnvironment env = new MockEnvironment();
        if (ddlAuto != null) {
            env.setProperty(SchemaManagementGuard.DDL_AUTO, ddlAuto);
        }
        return env;
    }

    @Nested
    @DisplayName("ddl-auto")
    class DdlAuto {

        @ParameterizedTest(name = "{0} is rejected")
        @ValueSource(strings = {"create", "create-drop", "update", "create-only", "drop", "validate"})
        @DisplayName("anything that is not none fails the boot")
        void rejectsEverythingButNone(String value) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> run(env(value)));
            assertTrue(e.getMessage().contains(SchemaManagementGuard.DDL_AUTO), e.getMessage());
        }

        @Test
        @DisplayName("an unset value fails too — Boot defaults it to create-drop on an embedded database")
        void rejectsAbsent() {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> run(env(null)));
            assertTrue(e.getMessage().contains("not set"), e.getMessage());
        }

        @Test
        @DisplayName("a blank value counts as unset")
        void rejectsBlank() {
            assertThrows(IllegalStateException.class, () -> run(env("   ")));
        }

        @Test
        @DisplayName("none is accepted, however it is cased or padded")
        void acceptsNone() {
            assertDoesNotThrow(() -> run(env("none")));
            assertDoesNotThrow(() -> run(env("NONE")));
            assertDoesNotThrow(() -> run(env("  None  ")));
        }
    }

    @Nested
    @DisplayName("the native hibernate property")
    class Hbm2Ddl {

        @Test
        @DisplayName("cannot smuggle schema management past a correct ddl-auto")
        void rejectsOverride() {
            MockEnvironment e = env("none");
            e.setProperty(SchemaManagementGuard.HBM2DDL, "create-drop");

            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> run(e));
            assertTrue(thrown.getMessage().contains(SchemaManagementGuard.HBM2DDL), thrown.getMessage());
        }

        @Test
        @DisplayName("is fine when absent or set to none")
        void allowsNone() {
            assertDoesNotThrow(() -> run(env("none")));

            MockEnvironment e = env("none");
            e.setProperty(SchemaManagementGuard.HBM2DDL, "none");
            assertDoesNotThrow(() -> run(e));
        }
    }

    @Test
    @DisplayName("every shipped configuration file declares ddl-auto: none")
    void everyConfigFileIsSafe() throws IOException {
        List<Path> roots = List.of(Path.of("src/main/resources"), Path.of("src/test/resources"));
        List<String> offenders = new ArrayList<>();
        int checked = 0;

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".yaml") && !name.endsWith(".yml") && !name.endsWith(".properties")) {
                        continue;
                    }
                    checked++;
                    for (String line : Files.readAllLines(file)) {
                        String text = line.trim();
                        if (text.startsWith("#") || !text.contains("ddl-auto")) {
                            continue;
                        }
                        String value = text.substring(text.indexOf("ddl-auto") + "ddl-auto".length())
                                .replace(":", "").replace("=", "").trim();
                        if (!value.equalsIgnoreCase("none")) {
                            offenders.add(file + " -> " + text);
                        }
                    }
                }
            }
        }

        assertTrue(checked > 0, "expected to find configuration files to check");
        assertTrue(offenders.isEmpty(),
                "CARTMAN must never manage the Delphi schema, but found: " + offenders);
    }
}
