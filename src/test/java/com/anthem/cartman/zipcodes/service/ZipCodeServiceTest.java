package com.anthem.cartman.zipcodes.service;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.NewZip;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;

/** Module 14 — Zipcode Editor (US-14.4 to US-14.9). */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class ZipCodeServiceTest {

    @Autowired
    private ZipCodeService service;

    @Autowired
    private JdbcTemplate jdbc;

    private static final NewZip GOOD = new NewZip("40391", "Winchester", "KY", "Clark", true);

    @Nested
    @DisplayName("US-14.4 — finding a zip")
    class Finding {

        @Test
        @DisplayName("US-14.4.6 — ordered by county, then city")
        void ordered() {
            List<ZipRow> rows = service.find("41465");

            assertEquals(List.of("Wolfpen", "Bethanna", "Cyrus"),
                    rows.stream().map(ZipRow::city).toList());
            assertEquals("Breathitt", rows.get(0).county());
        }

        /** char(20) pads what it stores; the padding is the column's, not the county's. */
        @Test
        @DisplayName("the columns come back trimmed")
        void trimmed() {
            ZipRow row = service.find("45040").get(0);

            assertEquals("OH", row.state());
            assertEquals("Warren", row.county());
        }

        /*
         * A87 — Yes only when the stored value is exactly 1. Null is the live table's answer on all but
         * one row, and that row holds 0; neither may read as Yes.
         */
        @Test
        @DisplayName("A87 — Choice is Yes only for a stored 1, not for 0 and not for null")
        void choice() {
            List<ZipRow> rows = service.find("45040");
            ZipRow deerfield = rows.stream().filter(r -> "Deerfield".equals(r.city())).findFirst().orElseThrow();
            ZipRow mason = rows.stream().filter(r -> "Mason".equals(r.city())).findFirst().orElseThrow();
            ZipRow nullChoice = service.find("41465").get(0);

            assertTrue(deerfield.isInChoiceNetwork());
            assertFalse(mason.isInChoiceNetwork(), "a stored 0 is not in the network");
            assertNull(nullChoice.choice());
            assertFalse(nullChoice.isInChoiceNetwork(), "and neither is a null");
        }

        /* US-14.3.2 — `if (Length(edZipCode.Text) = 5)`: any other length reads nothing at all. */
        @Test
        @DisplayName("US-14.3.2 — only a five-digit zip reads anything")
        void mustBeFiveDigits() {
            assertEquals(List.of(), service.find("4146"));
            assertEquals(List.of(), service.find("414655"));
            assertEquals(List.of(), service.find(""));
            assertEquals(List.of(), service.find(null));
            assertEquals(List.of(), service.find("4146a"));
        }

        /*
         * A89 — a row whose zip lives only in Zip_Zip_Dup1. The search reads Zip_Zip_Cd, so 404 live
         * rows cannot be reached from this screen at all. Asserted so the consequence is visible.
         */
        @Test
        @DisplayName("A89 — a row with no Zip_Zip_Cd cannot be found, though it exists")
        void unreachableRow() {
            assertEquals(List.of(), service.find("63125"));
            assertEquals(1, count("Zip_Zip_Dup1 = '63125'"), "the row is in the table");
        }
    }

    @Nested
    @DisplayName("US-14.6 / US-14.7 — deleting and the Choice network")
    class Editing {

        @Test
        @DisplayName("US-14.6.2 — the row goes, and only that row")
        void deletes() {
            service.delete(9002);

            assertEquals(2, service.find("41465").size());
            assertEquals(0, count("Zip_ID = 9002"));
        }

        /* The Delphi reports only a raised exception, so a delete that matched nothing looked fine. */
        @Test
        @DisplayName("and a row that is already gone is reported")
        void deleteMissing() {
            assertThrows(NotFoundException.class, () -> service.delete(99999));
        }

        @Test
        @DisplayName("US-14.7.2 / US-14.7.3 — Choice in and Choice out write 1 and 0")
        void choiceBothWays() {
            service.setChoice(9001, true);
            assertEquals(1, choiceOf(9001));
            assertTrue(row(9001).isInChoiceNetwork());

            service.setChoice(9001, false);
            assertEquals(0, choiceOf(9001));
            assertFalse(row(9001).isInChoiceNetwork());
        }

        /** One row, addressed by Zip_ID — the others on the same zip are untouched. */
        @Test
        @DisplayName("and only the row asked for")
        void choiceOneRow() {
            service.setChoice(9001, true);

            assertNull(choiceOf(9002));
            assertNull(choiceOf(9003));
        }

        @Test
        @DisplayName("a Choice change to a row that is gone is reported")
        void choiceMissing() {
            assertThrows(NotFoundException.class, () -> service.setChoice(99999, true));
        }
    }

    @Nested
    @DisplayName("US-14.9 — inserting a zip")
    class Inserting {

        @Test
        @DisplayName("US-14.9.3 — the row is stored and can be found straight away")
        void inserts() {
            ZipRow saved = service.insert(GOOD);

            assertEquals("Winchester", saved.city());
            assertEquals(List.of(saved.id()), service.find("40391").stream().map(ZipRow::id).toList());
        }

        /* A89 — the Delphi writes the zip into both columns, and so does this. */
        @Test
        @DisplayName("A89 — the zip is written into both zip columns")
        void bothZipColumns() {
            service.insert(GOOD);

            assertEquals(1, count("rtrim(Zip_Zip_Cd) = '40391' and rtrim(Zip_Zip_Dup1) = '40391'"));
        }

        /* A90 — hard-coded 0: a new zip is never in the Choice network at creation. */
        @Test
        @DisplayName("A90 — Zip_Choice is 0, whatever the user wanted")
        void choiceIsZero() {
            ZipRow saved = service.insert(GOOD);

            assertEquals((short) 0, saved.choice());
            assertFalse(saved.isInChoiceNetwork());
        }

        /* A95 — a smallint, bound as a number rather than as the Delphi's quoted string. */
        @Test
        @DisplayName("A95 — Market Area is stored as 1 when ticked and 0 when not")
        void market() {
            service.insert(GOOD);
            assertEquals(1, marketOf("40391"));

            service.insert(new NewZip("40392", "Winchester", "KY", "Clark", false));
            assertEquals(0, marketOf("40392"));
        }

        /* A97 — an insert supplies seven of thirteen columns and leaves five null. */
        @Test
        @DisplayName("A97 — the five columns CARTMAN never reads are left null")
        void untouchedColumns() {
            service.insert(GOOD);

            assertEquals(1, count("rtrim(Zip_Zip_Cd) = '40391' and ZIP_Territory is null "
                    + "and ZIP_ASA_Terr is null and Zip_Sup_Area is null and Zip_Sel_Area is null "
                    + "and Zip_County_Code is null"));
        }

        @Test
        @DisplayName("US-14.9.1 — each of the form's four checks, in its own words")
        void eachCheck() {
            assertMessage(new NewZip("404", "Winchester", "KY", "Clark", false),
                    "Zip Code Length Should Be 5 Digits");
            assertMessage(new NewZip("40391", "  ", "KY", "Clark", false),
                    "Zip City Can Not Be Blank");
            assertMessage(new NewZip("40391", "Winchester", "KYX", "Clark", false),
                    "Zip State Length Should Be 2 Characters");
            assertMessage(new NewZip("40391", "Winchester", "KY", "", false),
                    "Zip County Can Not Be Blank");
        }

        /*
         * A88 — the Delphi runs all four checks and shows four message boxes one after another, leaving
         * the cursor in the last field that failed. All four faults are reported together here, in the
         * order the form declares them, so the first named is the first to fix.
         */
        @Test
        @DisplayName("A88 — all four faults are reported at once, in the form's order")
        void allFourTogether() {
            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.insert(new NewZip("", "", "", "", false)));

            String message = thrown.getMessage();
            assertTrue(message.startsWith("Zip Code Length Should Be 5 Digits"), message);
            assertTrue(message.contains("Zip City Can Not Be Blank"), message);
            assertTrue(message.contains("Zip State Length Should Be 2 Characters"), message);
            assertTrue(message.contains("Zip County Can Not Be Blank"), message);
        }

        @Test
        @DisplayName("and a refused insert stores nothing")
        void refusedStoresNothing() {
            int before = count("1 = 1");

            assertThrows(ValidationException.class,
                    () -> service.insert(new NewZip("404", "Winchester", "KY", "Clark", false)));

            assertEquals(before, count("1 = 1"));
        }

        /*
         * A92 — nothing stops the same row going in twice, and the live data already has 28 rows
         * identical in state, county and city. Asserted so the gap is recorded rather than assumed.
         */
        @Test
        @DisplayName("A92 — the same row can be inserted twice, as it can in CARTMAN")
        void duplicatesAllowed() {
            service.insert(GOOD);
            service.insert(GOOD);

            assertEquals(2, service.find("40391").size());
        }

        /* A93 — bound, not joined: a county with an apostrophe would have broken the statement. */
        @Test
        @DisplayName("A93 — an apostrophe in a name is stored, not a syntax error")
        void apostrophe() {
            service.insert(new NewZip("23875", "Prince George", "VA", "Prince George's", false));

            assertEquals("Prince George's", service.find("23875").get(0).county());
        }

        private void assertMessage(NewZip request, String expected) {
            ValidationException thrown =
                    assertThrows(ValidationException.class, () -> service.insert(request));
            assertEquals(expected, thrown.getMessage());
        }
    }

    private int count(String where) {
        Integer n = jdbc.queryForObject("select count(*) from TM_Cd_ZipCnty where " + where, Integer.class);
        return n == null ? 0 : n;
    }

    private Integer choiceOf(int id) {
        return jdbc.queryForObject("select Zip_Choice from TM_Cd_ZipCnty where Zip_ID = ?",
                Integer.class, id);
    }

    private Integer marketOf(String zip) {
        return jdbc.queryForObject("select Zip_Market from TM_Cd_ZipCnty where rtrim(Zip_Zip_Cd) = ?",
                Integer.class, zip);
    }

    private ZipRow row(int id) {
        return service.find("41465").stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }
}
