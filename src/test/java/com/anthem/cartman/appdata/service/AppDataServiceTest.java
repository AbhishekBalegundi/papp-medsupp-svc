package com.anthem.cartman.appdata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationDates;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationEdit;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationOwners;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationSummary;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationUpdate;
import com.anthem.cartman.appdata.dto.AppDataViews.DeletionResult;
import com.anthem.cartman.appdata.dto.AppDataViews.Outcome;
import com.anthem.cartman.appdata.dto.AppDataViews.Pickers;
import com.anthem.cartman.appdata.dto.AppDataViews.TableOutcome;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionRow;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionUpdate;
import com.anthem.cartman.common.exception.NotFoundException;

/** Module 7 — Application Data Manager (US-7.1 to US-7.9). */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class AppDataServiceTest {

    @Autowired
    private AppDataService service;

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String table, String where) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where " + where,
                Integer.class);
        return n == null ? 0 : n;
    }

    private static TableOutcome outcomeFor(DeletionResult result, String table) {
        return result.tables().stream()
                .filter(t -> t.table().equals(table))
                .findFirst()
                .orElseThrow(() -> new AssertionError(table + " is not in the result"));
    }

    @Nested
    @DisplayName("US-7.2 — finding a record")
    class Finding {

        @Test
        @DisplayName("shows the eleven identifying fields, each trimmed")
        void showsIdentifyingFields() {
            ApplicationSummary a = service.findApplication(3455);

            assertEquals(3455, a.applicationId());
            assertEquals("Stante", a.lastName(), "char columns arrive blank-padded");
            assertEquals("Dorothy", a.firstName());
            assertEquals("IHP", a.type());
            assertEquals("14 Fore Street", a.address1());
            assertEquals("Truro", a.city());
            assertEquals("VA", a.state());
            assertEquals("23219", a.zip());
            assertEquals("804-555-0100", a.phone1());
            assertEquals(LocalDate.of(1974, 3, 2), a.dateOfBirth());
            assertEquals("HC0001", a.hcid());
            assertEquals("DCN0001", a.dcn());
        }

        @Test
        @DisplayName("US-7.2.4 — an unknown key is refused with the Delphi's wording")
        void unknownKeyIsRefused() {
            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> service.findApplication(999999));

            assertEquals("Application Record Not Found. Verify the Key Value is Correct.",
                    ex.getMessage());
        }

        @Test
        @DisplayName("US-7.2.5 — the underwriting actions for one application, in action order")
        void listsUwActions() {
            List<UwActionRow> rows = service.uwActions(3455);

            assertEquals(2, rows.size());
            assertEquals(List.of(1, 2), rows.stream().map(UwActionRow::actionId).toList());
            assertEquals("Requested medical records", rows.get(0).action());
            assertEquals(LocalDate.of(2026, 2, 2), rows.get(0).start());
            assertNull(rows.get(0).close(), "an empty column is a blank box, not a zero date");
        }

        @Test
        @DisplayName("an application with no actions is an empty list, not an error")
        void noActionsIsEmpty() {
            assertTrue(service.uwActions(3456).isEmpty());
        }
    }

    @Nested
    @DisplayName("US-7.3 — deleting an application")
    class DeletingApplication {

        @Test
        @DisplayName("removes the application and every dependent row, reporting each count")
        void removesEverythingAndCounts() {
            DeletionResult result = service.deleteApplication(3455);

            assertEquals(0, count("TM_Application", "App_ID = 3455"));
            assertEquals(0, count("TM_UW_Actions", "UW_App_ID = 3455"));
            assertEquals(0, count("TM_App_Floating_Note", "FN_Application = 3455"));
            assertEquals(0, count("TM_Record_History", "rtrim(RHist_ID) = '3455'"));

            assertEquals(1, outcomeFor(result, "TM_Application").rows());
            assertEquals(2, outcomeFor(result, "TM_UW_Actions").rows());
            assertEquals(2, outcomeFor(result, "TM_App_Floating_Note").rows());
            assertEquals(2, outcomeFor(result, "TM_Record_History").rows());
            assertEquals(7, result.totalRows());
        }

        @Test
        @DisplayName("takes nothing belonging to another application")
        void leavesOtherApplicationsAlone() {
            service.deleteApplication(3455);

            assertEquals(1, count("TM_Application", "App_ID = 3457"));
            assertEquals(1, count("TM_UW_Actions", "UW_App_ID = 3457"));
            assertEquals(1, count("TM_App_Floating_Note", "FN_Application = 3457"));
            assertEquals(1, count("TM_Record_History", "rtrim(RHist_ID) = '3457'"));
        }

        /*
         * A45 — the Delphi turned a square green whenever the statement ran, which cannot be told
         * apart from "it matched nothing". 3456 has no dependants at all, so every table but the
         * application itself must report NONE rather than DELETED.
         */
        @Test
        @DisplayName("A45 — a table that matched nothing says so, rather than looking successful")
        void nothingMatchedIsNotSuccess() {
            DeletionResult result = service.deleteApplication(3456);

            assertEquals(Outcome.DELETED, outcomeFor(result, "TM_Application").outcome());
            assertEquals(Outcome.NONE, outcomeFor(result, "TM_UW_Actions").outcome());
            assertEquals(Outcome.NONE, outcomeFor(result, "TM_App_Floating_Note").outcome());
            assertEquals(Outcome.NONE, outcomeFor(result, "TM_Record_History").outcome());
            assertEquals(1, result.totalRows());
        }

        /*
         * A46 — TM_Application_Dependants is named by the Delphi and is absent from this database,
         * so its statement raised every time and its square was always red. Absence is reported as
         * absence, and it does not fail the deletion.
         */
        @Test
        @DisplayName("A46 — a table the database does not have is reported as absent")
        void absentTableIsReportedAsAbsent() {
            TableOutcome dependants =
                    outcomeFor(service.deleteApplication(3455), "TM_Application_Dependants");

            assertEquals(Outcome.TABLE_ABSENT, dependants.outcome());
            assertEquals(0, dependants.rows());
        }

        @Test
        @DisplayName("all five tables are reported, in the Delphi's order")
        void reportsAllFiveInOrder() {
            DeletionResult result = service.deleteApplication(3455);

            assertEquals(List.of("TM_Application", "TM_Application_Dependants",
                            "TM_App_Floating_Note", "TM_Record_History", "TM_UW_Actions"),
                    result.tables().stream().map(TableOutcome::table).toList());
        }

        @Test
        @DisplayName("an unknown application is refused and nothing is deleted")
        void unknownApplicationDeletesNothing() {
            assertThrows(NotFoundException.class, () -> service.deleteApplication(999999));

            assertEquals(3, count("TM_Application", "1 = 1"));
        }
    }

    @Nested
    @DisplayName("US-7.4 — deleting one underwriting action")
    class DeletingAction {

        @Test
        @DisplayName("takes that row alone, leaving the application and its other actions")
        void takesOneRow() {
            service.deleteUwAction(9001);

            assertEquals(1, count("TM_UW_Actions", "UW_App_ID = 3455"));
            assertEquals(1, count("TM_UW_Actions", "UW_ID = 9002"));
            assertEquals(1, count("TM_Application", "App_ID = 3455"));
        }

        @Test
        @DisplayName("an unknown row is refused")
        void unknownRowIsRefused() {
            assertThrows(NotFoundException.class, () -> service.deleteUwAction(9999));
        }
    }

    @Nested
    @DisplayName("US-7.6 and US-7.8 — editing an application")
    class EditingApplication {

        @Test
        @DisplayName("US-7.6 — the dates, the owners and the identifying fields come back together")
        void editViewHasEverything() {
            ApplicationEdit edit = service.editView(3455);

            assertEquals("Stante", edit.identity().lastName());
            assertEquals(LocalDate.of(2026, 2, 1), edit.dates().enrollDate());
            assertNull(edit.dates().uwDate(), "an empty column is a blank box");
            assertEquals("Pending", edit.owners().uwStatus(), "trimmed");
            assertEquals("SITUW", edit.owners().underwriter());
        }

        @Test
        @DisplayName("US-7.8.1 — a save writes the dates and the owners")
        void savesDatesAndOwners() {
            ApplicationEdit saved = service.saveApplication(3455, new ApplicationUpdate(
                    new ApplicationDates(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), null,
                            null, null, null, null, LocalDate.of(2026, 5, 8)),
                    new ApplicationOwners("Approved", "SITUW", "EKeyer", "CEllison", "ENunn")));

            assertEquals(LocalDate.of(2026, 5, 1), saved.dates().enrollCompleteDate());
            assertEquals(LocalDate.of(2026, 5, 8), saved.dates().uwDate());
            assertEquals("Approved", saved.owners().uwStatus());
            assertEquals(LocalDate.of(2026, 5, 1),
                    service.editView(3455).dates().enrollCompleteDate(), "and it is stored");
        }

        @Test
        @DisplayName("US-7.8.1 — a blank date box clears the column rather than storing a zero date")
        void blankDateClearsTheColumn() {
            service.saveApplication(3455, new ApplicationUpdate(
                    new ApplicationDates(null, null, null, null, null, null, null, null),
                    new ApplicationOwners("Pending", "SITUW", "EKeyer", "CEllison", "ENunn")));

            assertNull(service.editView(3455).dates().enrollDate());
            assertEquals(0, count("TM_Application", "App_ID = 3455 and App_Enroll_Dt is not null"));
        }

        @Test
        @DisplayName("editing one application leaves the others untouched")
        void leavesOthersAlone() {
            service.saveApplication(3455, new ApplicationUpdate(
                    new ApplicationDates(null, null, null, null, null, null, null, null),
                    new ApplicationOwners("Approved", null, null, null, null)));

            assertEquals("", service.editView(3456).owners().uwStatus());
        }

        @Test
        @DisplayName("an unknown application cannot be saved")
        void unknownCannotBeSaved() {
            assertThrows(NotFoundException.class, () -> service.saveApplication(999999,
                    new ApplicationUpdate(
                            new ApplicationDates(null, null, null, null, null, null, null, null),
                            new ApplicationOwners(null, null, null, null, null))));
        }
    }

    @Nested
    @DisplayName("US-7.8.3 — editing one underwriting action")
    class EditingAction {

        @Test
        @DisplayName("writes the three dates on that row only")
        void writesThatRowOnly() {
            UwActionRow saved = service.saveUwAction(9001, new UwActionUpdate(
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3)));

            assertEquals(LocalDate.of(2026, 6, 1), saved.start());
            assertEquals(LocalDate.of(2026, 6, 2), saved.close());
            assertEquals(LocalDate.of(2026, 6, 3), saved.applicationDate());

            List<UwActionRow> rows = service.uwActions(3455);
            assertEquals(LocalDate.of(2026, 2, 9), rows.get(1).start(), "the sibling row is untouched");
        }

        /*
         * A49 — the Delphi pre-fills the three boxes from the first row of the grid, then saves
         * against whichever row happens to be highlighted, so pressing Save without clicking writes
         * row one's dates onto another row. Addressing the row by its own key is what prevents it.
         */
        @Test
        @DisplayName("A49 — two rows of one application are addressed separately")
        void rowsAreAddressedSeparately() {
            service.saveUwAction(9002, new UwActionUpdate(LocalDate.of(2026, 7, 7), null, null));

            List<UwActionRow> rows = service.uwActions(3455);
            assertEquals(LocalDate.of(2026, 2, 2), rows.get(0).start(), "row 1 unchanged");
            assertEquals(LocalDate.of(2026, 7, 7), rows.get(1).start());
            assertNull(rows.get(1).close(), "and a blank box clears the column");
        }
    }

    @Nested
    @DisplayName("US-7.9 — the pickers")
    class PickerLists {

        @Test
        @DisplayName("UW Status comes from the code table, type 23 only")
        void uwStatusesComeFromCodeType23() {
            Pickers pickers = service.pickers();

            assertEquals(List.of("Approved", "Declined", "Pending"), pickers.uwStatuses());
            assertFalse(pickers.uwStatuses().contains("Not a UW status"));
        }

        @Test
        @DisplayName("each login list holds only the logins of its own type")
        void loginListsAreSplitByType() {
            Pickers pickers = service.pickers();

            assertNotNull(pickers.underwriters());
            assertTrue(pickers.underwriters().stream().allMatch(n -> !n.isBlank()));
            assertFalse(pickers.underwriters().contains("CosmoAdm"),
                    "an Admin login belongs to none of the four lists");
        }
    }

    /*
     * US-7.10 — the wording is not interchangeable. CARTMAN words a find, a save and a delete
     * differently, and each message is a distinct row of the message catalogue; the results square
     * says green or red, so the words are the only thing that says which action failed and why.
     */
    @Nested
    @DisplayName("US-7.10 — each action words its own failure")
    class Wording {

        @Test
        @DisplayName("a find asks the user to check the key value")
        void findWording() {
            assertEquals("Application Record Not Found. Verify the Key Value is Correct.",
                    assertThrows(NotFoundException.class,
                            () -> service.findApplication(999999)).getMessage());
            assertEquals("Application Record Not Found. Verify the Key Value is Correct.",
                    assertThrows(NotFoundException.class,
                            () -> service.editView(999999)).getMessage());
            // A key with no actions is an empty grid, not an error: the screen says so itself, in
            // the two wordings the form uses — plural on the deletion tab, singular on the editing
            // tab, which is how CARTMAN words those two ShowMessage calls.
            assertTrue(service.uwActions(999999).isEmpty());
        }

        @Test
        @DisplayName("a save says it could not update")
        void saveWording() {
            assertEquals("Application Record Not Found, Could Not Update!",
                    assertThrows(NotFoundException.class, () -> service.saveApplication(999999,
                            new ApplicationUpdate(new ApplicationDates(null, null, null, null,
                                    null, null, null, null),
                                    new ApplicationOwners("", "", "", "", "")))).getMessage());
            assertEquals("UW Action Record Not Found, Could Not Update!",
                    assertThrows(NotFoundException.class, () -> service.saveUwAction(9999,
                            new UwActionUpdate(null, null, null))).getMessage());
        }

        @Test
        @DisplayName("a delete says it could not delete")
        void deleteWording() {
            assertEquals("Application record not found, Could Not Delete!",
                    assertThrows(NotFoundException.class,
                            () -> service.deleteApplication(999999)).getMessage());
            assertEquals("UW Action Record not found, Could Not Delete!",
                    assertThrows(NotFoundException.class,
                            () -> service.deleteUwAction(9999)).getMessage());
        }
    }
}
