package com.anthem.cartman.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesUpdate;
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesView;
import com.anthem.cartman.users.repository.UserPreferencesRepository;
import com.anthem.cartman.users.service.UserPreferencesService;

/**
 * US-2.15 against the seeded H2 schema. DStante carries the full preference row: flags with
 * position 1 set (which no dialog control owns — the A36 witness), view minimized-top and panels
 * Applicant+Macros. The two seeded common notes belong to TenCharUsr.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class UserPreferencesServiceTest {

    @Autowired
    private UserPreferencesService service;

    @Autowired
    private UserPreferencesRepository repository;

    /** An update built from the current view, so single-field tests change one thing at a time. */
    private static PreferencesUpdate updateFrom(PreferencesView v) {
        return new PreferencesUpdate(
                v.firstName(), v.lastName(), v.nickName(), v.phone(), v.phone800(),
                v.extension(), v.email(), v.manager(), v.title(), v.role(), v.department(),
                v.fontName(), v.fontSize(),
                v.hideQueueOnQuery(), v.notesOnQuery(), v.queueOnClear(), v.navigationKeys(),
                v.allCodes(), v.printSignatures(), v.showFieldHints(), v.runQueueOnStart(),
                v.zipLookup(), v.keywordSearch(),
                v.codeManager(), v.allowPrintManager(), v.allowReportCreation(),
                v.allowReportExecution(), v.allowLetterEditing(), v.allowLetterCreation(),
                v.dropPan(), v.writeAccess(), v.strictCodes(), v.lockNotes(),
                v.rateClosedProducts(), v.editUnderwriter(), v.editHcid(), v.allowMacros(),
                v.newCallQueues(), v.doubleKey(), v.allowSavingLetters(), v.allowNonOwned(),
                v.medBOverride(),
                v.startMacro(), v.endMacro(), v.queryMacro(), v.clearMacro(), v.printMacro(),
                v.pfAppInfo(), v.pfSelDesired(), v.pfBillingInfo(), v.pfDepInfo(), v.pfMedQuest(),
                v.pfMedDetail(), v.pfOthCov(), v.pfBdInfo(), v.pfIsgReq(), v.pfAppSig(),
                v.pfAgtSig(), v.pfCheckInfo(), v.pfConf(), v.pfUnderwriting(), v.pfLetters(),
                v.pfUnderConfirm(), v.pfAudit(), v.pfMedicare(),
                v.commonNotes(),
                v.panelApplicant(), v.panelLetters(), v.panelMacros(), v.panelEdit(),
                v.optionalLetters(), v.popupInfo(), v.showXml(), v.navBar(), v.primaryView(),
                v.grantRevoke(), v.cachedObjects());
    }

    @Nested
    @DisplayName("US-2.15 — loading the dialog")
    class Load {

        @Test
        @DisplayName("the user information fields come from their TM_Login columns")
        void loadsUserInfo() {
            PreferencesView v = service.get("DStante");

            assertEquals("Darron", v.firstName());
            assertEquals("Daz", v.nickName());
            assertEquals("800-555-0000", v.phone800());
            assertEquals("Arial", v.fontName());
            assertEquals(8, v.fontSize());
            assertEquals("StartUp", v.startMacro());
        }

        @Test
        @DisplayName("flag-backed options read their Delphi positions")
        void loadsFlags() {
            PreferencesView v = service.get("DStante");

            assertTrue(v.queueOnClear());       // position 4
            assertTrue(v.runQueueOnStart());    // position 20
            assertTrue(v.showFieldHints());     // position 88
            assertTrue(v.allowSavingLetters()); // position 117
            assertTrue(v.navBar());             // position 121
            assertFalse(v.doubleKey());         // position 112
            assertEquals(1, v.primaryView());   // position 120 = '1', minimized-top
            assertTrue(v.panelApplicant());     // nibble A = 1010
            assertFalse(v.panelLetters());
            assertTrue(v.panelMacros());
            assertFalse(v.panelEdit());
            assertTrue(v.optionalLetters());    // position 128
        }

        @Test
        @DisplayName("a user with NULL flags reads as everything off")
        void nullFlagsAreAllOff() {
            PreferencesView v = service.get("TenCharUsr");

            assertFalse(v.runQueueOnStart());
            assertEquals(0, v.primaryView());
        }

        @Test
        @DisplayName("the notes memo carries the TM_Common_Notes lines in order")
        void loadsNotes() {
            assertEquals(List.of("Existing note one.", "Existing note two."),
                    service.get("TenCharUsr").commonNotes());
        }

        @Test
        @DisplayName("an unknown user is a 404")
        void unknownUser() {
            assertThrows(NotFoundException.class, () -> service.get("NotAUser"));
        }
    }

    @Nested
    @DisplayName("US-2.15 — OK saves everything at once")
    class Save {

        @Test
        @DisplayName("fields, flags, macros and notes all round-trip")
        void roundTrips() {
            PreferencesView before = service.get("DStante");
            PreferencesUpdate u = new PreferencesUpdate(
                    "Darron", "Stante", "DZ", "3175551000", "800-555-9999", "102",
                    "darron@example.com", "New Manager", "Analyst", "Admin", "U",
                    "Verdana", 10,
                    true, true, false, false, true, false, false, false, true, true,
                    false, true, false, true, false, true, false, true, false, true,
                    false, true, false, true, false, true, false, true, false,
                    "NewStart", "", "NewQuery", "", "",
                    true, false, true, false, true, false, true, false, true, false,
                    true, false, true, false, true, false, true, false,
                    List.of("Line one.", "Line two.", "Line three."),
                    false, true, false, true, true, false, true, false, 2, true, false);

            service.save("DStante", u);
            PreferencesView after = service.get("DStante");

            assertEquals("DZ", after.nickName());
            assertEquals("800-555-9999", after.phone800());
            assertEquals("Verdana", after.fontName());
            assertEquals(10, after.fontSize());
            assertEquals("NewStart", after.startMacro());
            assertTrue(after.hideQueueOnQuery());
            assertFalse(after.queueOnClear());
            assertEquals(2, after.primaryView());
            assertFalse(after.panelApplicant());
            assertTrue(after.panelLetters());
            assertEquals(List.of("Line one.", "Line two.", "Line three."), after.commonNotes());
            // Untouched identity fields survive.
            assertEquals(before.abbrevId(), after.abbrevId());
        }

        @Test
        @DisplayName("A36 — a flag position no dialog control owns survives the save")
        void preservesForeignFlagBits() {
            PreferencesView v = service.get("DStante");
            service.save("DStante", updateFrom(v));

            String stored = repository.findByName("DStante").orElseThrow()
                    .flags().toStoredValue();
            // Position 1 was set in the seed and belongs to no dialog control. The Delphi's save
            // would have zeroed it (SetOptionsFromControls starts from 128 zeros).
            assertEquals('1', stored.charAt(0));
        }

        @Test
        @DisplayName("the e-mail address is validated on OK, as Options2.pas:199 refused to save")
        void validatesEmail() {
            PreferencesView v = service.get("DStante");
            PreferencesUpdate bad = new PreferencesUpdate(
                    v.firstName(), v.lastName(), v.nickName(), v.phone(), v.phone800(),
                    v.extension(), "not-an-address", v.manager(), v.title(), v.role(),
                    v.department(), v.fontName(), v.fontSize(),
                    v.hideQueueOnQuery(), v.notesOnQuery(), v.queueOnClear(), v.navigationKeys(),
                    v.allCodes(), v.printSignatures(), v.showFieldHints(), v.runQueueOnStart(),
                    v.zipLookup(), v.keywordSearch(),
                    v.codeManager(), v.allowPrintManager(), v.allowReportCreation(),
                    v.allowReportExecution(), v.allowLetterEditing(), v.allowLetterCreation(),
                    v.dropPan(), v.writeAccess(), v.strictCodes(), v.lockNotes(),
                    v.rateClosedProducts(), v.editUnderwriter(), v.editHcid(), v.allowMacros(),
                    v.newCallQueues(), v.doubleKey(), v.allowSavingLetters(), v.allowNonOwned(),
                    v.medBOverride(),
                    v.startMacro(), v.endMacro(), v.queryMacro(), v.clearMacro(), v.printMacro(),
                    v.pfAppInfo(), v.pfSelDesired(), v.pfBillingInfo(), v.pfDepInfo(),
                    v.pfMedQuest(), v.pfMedDetail(), v.pfOthCov(), v.pfBdInfo(), v.pfIsgReq(),
                    v.pfAppSig(), v.pfAgtSig(), v.pfCheckInfo(), v.pfConf(), v.pfUnderwriting(),
                    v.pfLetters(), v.pfUnderConfirm(), v.pfAudit(), v.pfMedicare(),
                    v.commonNotes(),
                    v.panelApplicant(), v.panelLetters(), v.panelMacros(), v.panelEdit(),
                    v.optionalLetters(), v.popupInfo(), v.showXml(), v.navBar(), v.primaryView(),
                    v.grantRevoke(), v.cachedObjects());

            ValidationException thrown =
                    assertThrows(ValidationException.class, () -> service.save("DStante", bad));
            assertEquals("E-mail address is not valid.", thrown.getMessage());
        }

        @Test
        @DisplayName("saving notes replaces the old rows and touches nobody else's")
        void notesAreScopedToTheUser() {
            PreferencesView v = service.get("DStante");
            PreferencesUpdate u = updateFrom(new PreferencesView(
                    v.loginName(), v.firstName(), v.lastName(), v.nickName(), v.phone(),
                    v.phone800(), v.extension(), v.email(), v.office(), v.manager(), v.title(),
                    v.role(), v.department(), v.fontName(), v.fontSize(),
                    v.hideQueueOnQuery(), v.notesOnQuery(), v.queueOnClear(), v.navigationKeys(),
                    v.allCodes(), v.printSignatures(), v.showFieldHints(), v.runQueueOnStart(),
                    v.zipLookup(), v.keywordSearch(),
                    v.codeManager(), v.allowPrintManager(), v.allowReportCreation(),
                    v.allowReportExecution(), v.allowLetterEditing(), v.allowLetterCreation(),
                    v.dropPan(), v.writeAccess(), v.strictCodes(), v.lockNotes(),
                    v.rateClosedProducts(), v.editUnderwriter(), v.editHcid(), v.allowMacros(),
                    v.newCallQueues(), v.doubleKey(), v.allowSavingLetters(), v.allowNonOwned(),
                    v.medBOverride(),
                    v.startMacro(), v.endMacro(), v.queryMacro(), v.clearMacro(), v.printMacro(),
                    v.pfAppInfo(), v.pfSelDesired(), v.pfBillingInfo(), v.pfDepInfo(),
                    v.pfMedQuest(), v.pfMedDetail(), v.pfOthCov(), v.pfBdInfo(), v.pfIsgReq(),
                    v.pfAppSig(), v.pfAgtSig(), v.pfCheckInfo(), v.pfConf(), v.pfUnderwriting(),
                    v.pfLetters(), v.pfUnderConfirm(), v.pfAudit(), v.pfMedicare(),
                    List.of("DStante only."),
                    v.panelApplicant(), v.panelLetters(), v.panelMacros(), v.panelEdit(),
                    v.optionalLetters(), v.popupInfo(), v.showXml(), v.navBar(), v.primaryView(),
                    v.grantRevoke(), v.cachedObjects(), v.createdOn(), v.abbrevId()));

            service.save("DStante", u);

            assertEquals(List.of("DStante only."), service.get("DStante").commonNotes());
            assertEquals(List.of("Existing note one.", "Existing note two."),
                    service.get("TenCharUsr").commonNotes());
        }
    }
}
