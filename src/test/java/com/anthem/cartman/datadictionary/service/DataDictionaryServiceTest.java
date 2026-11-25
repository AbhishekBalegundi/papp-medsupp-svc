package com.anthem.cartman.datadictionary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.EntryEdit;
import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.FieldRow;
import com.anthem.cartman.datadictionary.service.DataDictionaryService;
import com.anthem.cartman.login.entity.Login;

@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class DataDictionaryServiceTest {

    @Autowired
    private DataDictionaryService service;

    private FieldRow row(String table, String field) {
        return service.fields(table).stream()
                .filter(f -> f.fieldName().equalsIgnoreCase(field))
                .findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("US-6.1 — the table list")
    class Tables {

        @Test
        @DisplayName("comes from the live catalogue, so seeded tables appear")
        void listsSeededTables() {
            List<String> tables = service.tables();

            assertTrue(tables.stream().anyMatch(t -> t.equalsIgnoreCase("TM_Login")));
            assertTrue(tables.stream().anyMatch(t -> t.equalsIgnoreCase("TM_Data_Dictionary")));
        }

        @Test
        @DisplayName("excludes the database's own system tables")
        void excludesSystemTables() {
            assertFalse(service.tables().stream()
                            .anyMatch(t -> t.toUpperCase().startsWith("INFORMATION_SCHEMA")),
                    "GetTableNames(..., False) suppressed system tables");
        }

        @Test
        @DisplayName("is ordered by name")
        void ordered() {
            List<String> tables = service.tables();

            assertEquals(tables.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), tables);
        }
    }

    @Nested
    @DisplayName("US-6.2 / US-6.3 — the field grid")
    class Fields {

        @Test
        @DisplayName("lists every column of the table, documented or not")
        void listsEveryColumn() {
            List<FieldRow> rows = service.fields("TM_Login");

            assertEquals(38, rows.size(), "the seeded TM_Login has 38 columns");
            assertTrue(rows.stream().anyMatch(r -> r.fieldName().equalsIgnoreCase("Log_CartmanTools")));
        }

        @Test
        @DisplayName("carries the dictionary entry where one exists")
        void carriesEntry() {
            FieldRow documented = row("TM_Login", "Log_Nm");

            assertTrue(documented.documented());
            assertEquals("Login Name", documented.prettyName());
            assertEquals("The user's COSMO login", documented.description());
            assertEquals("10", documented.fieldLength());
            assertEquals("char", documented.fieldType());
        }

        @Test
        @DisplayName("shows an undocumented column with empty values, not an error")
        void undocumentedColumn() {
            FieldRow undocumented = row("TM_Login", "Log_Title");

            assertFalse(undocumented.documented());
            assertNull(undocumented.prettyName());
            assertNull(undocumented.description());
        }

        @Test
        @DisplayName("an unknown table is a not-found, not an empty grid")
        void unknownTable() {
            assertThrows(NotFoundException.class, () -> service.fields("TM_Nope"));
            assertThrows(ValidationException.class, () -> service.fields(" "));
        }
    }

    @Nested
    @DisplayName("US-6.4 — saving an entry")
    class Saving {

        private EntryEdit edit(String pretty, String type, String length, String description) {
            return new EntryEdit(pretty, type, length, description);
        }

        @Test
        @DisplayName("updates an entry that already exists")
        void updatesExisting() {
            service.saveEntry("TM_Login", "Log_Nm",
                    edit("User ID", "char", "10", "Primary key of TM_Login"));

            FieldRow saved = row("TM_Login", "Log_Nm");
            assertEquals("User ID", saved.prettyName());
            assertEquals("Primary key of TM_Login", saved.description());
        }

        @Test
        @DisplayName("creates the entry when the column has never been documented")
        void createsMissing() {
            // Anomaly A22: the Delphi's bare UPDATE affected 0 rows here and said nothing.
            assertFalse(row("TM_Login", "Log_Title").documented());

            service.saveEntry("TM_Login", "Log_Title",
                    edit("Job Title", "varchar", "50", "The user's job title"));

            FieldRow saved = row("TM_Login", "Log_Title");
            assertTrue(saved.documented());
            assertEquals("Job Title", saved.prettyName());
        }

        @Test
        @DisplayName("a blank value clears the field rather than storing whitespace")
        void blankClears() {
            service.saveEntry("TM_Login", "Log_Nm", edit("Login Name", "char", "10", "   "));

            assertNull(row("TM_Login", "Log_Nm").description());
        }

        @Test
        @DisplayName("a value longer than its column is refused, not truncated")
        void refusesOverLongValues() {
            // Anomaly A24: the Delphi let the database silently truncate.
            ValidationException e = assertThrows(ValidationException.class,
                    () -> service.saveEntry("TM_Login", "Log_Nm",
                            edit("x".repeat(33), "char", "10", "ok")));
            assertTrue(e.getMessage().contains("32"), e.getMessage());

            assertThrows(ValidationException.class, () -> service.saveEntry("TM_Login", "Log_Nm",
                    edit("ok", "char", "10", "y".repeat(65))));
            assertThrows(ValidationException.class, () -> service.saveEntry("TM_Login", "Log_Nm",
                    edit("ok", "z".repeat(11), "10", "ok")));
            assertThrows(ValidationException.class, () -> service.saveEntry("TM_Login", "Log_Nm",
                    edit("ok", "char", "123456", "ok")));

            assertEquals("Login Name", row("TM_Login", "Log_Nm").prettyName(),
                    "a refused save changes nothing");
        }

        @Test
        @DisplayName("an unknown column of a real table is a not-found")
        void unknownColumn() {
            assertThrows(NotFoundException.class, () -> service.saveEntry("TM_Login", "Nope",
                    edit("a", "b", "1", "c")));
            assertThrows(ValidationException.class, () -> service.saveEntry("TM_Login", " ",
                    edit("a", "b", "1", "c")));
        }

        @Test
        @DisplayName("the column name is matched case-insensitively, as the grid supplies it")
        void columnNameIsCaseInsensitive() {
            service.saveEntry("tm_login", "log_nm", edit("Login Name", "char", "10", "ok"));

            assertEquals("ok", row("TM_Login", "Log_Nm").description());
        }
    }
}
