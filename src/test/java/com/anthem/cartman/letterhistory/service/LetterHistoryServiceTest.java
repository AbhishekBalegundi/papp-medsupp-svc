package com.anthem.cartman.letterhistory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.DeleteResult;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.LetterRow;

/** Module 13 — Letter History Deleter (US-13.5 and US-13.7). */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class LetterHistoryServiceTest {

    @Autowired
    private LetterHistoryService service;

    @Autowired
    private JdbcTemplate jdbc;

    private int letters(int letterId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from TM_Letter_History where LH_ID = ?", Integer.class, letterId);
        return n == null ? 0 : n;
    }

    private int events(String keyValue) {
        Integer n = jdbc.queryForObject(
                "select count(*) from TM_Record_History where rtrim(RHist_ID) = ? and RHist_Event = 2",
                Integer.class, keyValue);
        return n == null ? 0 : n;
    }

    @Nested
    @DisplayName("US-13.5 — finding the letters")
    class Finding {

        @Test
        @DisplayName("US-13.5.6 — oldest first, whatever order they were stored in")
        void ordered() {
            List<LetterRow> rows = service.list("7100");

            assertEquals(List.of(1, 2, 3), rows.stream().map(LetterRow::letterId).toList());
            assertEquals(LocalDateTime.of(2026, 4, 21, 5, 53), rows.get(0).date());
        }

        /*
         * US-13.5.2 — the query asks for RHist_Event = 2 and nothing else. The seed puts an event-1 row
         * on the same key, which is a field change, not a letter: it must not appear. Without the event
         * filter this list would be four long.
         */
        @Test
        @DisplayName("US-13.5.2 — only event 2 is a letter")
        void onlyLetters() {
            assertEquals(3, service.list("7100").size());
            assertTrue(service.list("7100").stream().noneMatch(r -> "not a letter".equals(r.letterName())));
        }

        /** RHist_Letter_Name is char(64); the padding belongs to the column, not the letter. */
        @Test
        @DisplayName("the letter's name comes back trimmed")
        void trimmed() {
            assertEquals("SM Ltr Denial 2A 24", service.list("7100").get(0).letterName());
        }

        /*
         * US-13.5.1 — FindLetters refuses to run on a trimmed-empty box. It is not an error: the Delphi
         * leaves the grid as it is and hides Delete.
         */
        @Test
        @DisplayName("US-13.5.1 — a key of nothing but spaces reads nothing")
        void blankKey() {
            assertEquals(List.of(), service.list("   "));
            assertEquals(List.of(), service.list(""));
            assertEquals(List.of(), service.list(null));
        }

        /** RHist_ID is char(15), so both sides are trimmed before they are compared. */
        @Test
        @DisplayName("a key typed with spaces around it still finds its letters")
        void paddedKey() {
            assertEquals(3, service.list("  7100  ").size());
        }

        @Test
        @DisplayName("a key with no letters answers an empty list")
        void noLetters() {
            assertEquals(List.of(), service.list("3455"));
        }
    }

    @Nested
    @DisplayName("US-13.7 — deleting a letter")
    class Deleting {

        @Test
        @DisplayName("US-13.7.2 / US-13.7.3 — both rows go, and the counts say so")
        void deletesBoth() {
            DeleteResult result = service.delete("7100", 1);

            assertEquals(1, result.letters());
            assertEquals(1, result.events());
            assertEquals(0, letters(1));
            assertEquals(2, events("7100"));
            assertEquals(List.of(2, 3), service.list("7100").stream().map(LetterRow::letterId).toList());
        }

        /*
         * An event can point at a letter that is not there — three of the nine event-2 rows in the live
         * database do, one of them at id 0. Removing no letter is not a failure; the event still goes and
         * the count reports the truth.
         */
        @Test
        @DisplayName("a letter that is already absent is reported, not treated as a failure")
        void missingLetter() {
            DeleteResult result = service.delete("7100", 3);

            assertEquals(0, result.letters());
            assertEquals(1, result.events());
            assertEquals(2, events("7100"));
        }

        /*
         * A83 — the statement is keyed on the id, the event code and the letter id, because
         * TM_Record_History has no key to address one row by. Two rows sharing all three are both
         * removed by one press.
         */
        @Test
        @DisplayName("A83 — two events sharing a letter id both go, and the count says two")
        void twoEventsOneLetter() {
            DeleteResult result = service.delete("7101", 4);

            assertEquals(1, result.letters());
            assertEquals(2, result.events());
            assertEquals(0, events("7101"));
        }

        /*
         * A78 — the transaction, proved from the side that can actually leave damage. Letter 9 exists but
         * no event points at it, so the first statement removes a row and the second removes none. The
         * Delphi would have deleted the letter and said nothing; here the whole thing rolls back.
         */
        @Test
        @DisplayName("A78 — nothing is committed when the event delete matches nothing")
        void rollsBack() {
            assertEquals(1, letters(9));

            ValidationException thrown =
                    assertThrows(ValidationException.class, () -> service.delete("7999", 9));

            assertTrue(thrown.getMessage().contains("no longer in the record's history"));
            assertEquals(1, letters(9), "the letter must survive a delete that removed no event");
        }

        @Test
        @DisplayName("a key of nothing but spaces is refused")
        void blankKey() {
            assertThrows(ValidationException.class, () -> service.delete("  ", 1));
            assertThrows(ValidationException.class, () -> service.delete(null, 1));
            assertEquals(1, letters(1));
        }
    }
}
