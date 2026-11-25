package com.anthem.cartman.users.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.anthem.cartman.users.util.PreferenceFlags;

/**
 * The shapes of the Options dialog (US-2.15). One flat record both ways: every field is a control
 * on one of the eight tabs of {@code Options2.dfm}, named for what the control does rather than
 * for its flag position — the position mapping lives in {@code PreferenceFlags}.
 */
public final class PreferencesViews {

    private PreferencesViews() {
    }

    /** What GET returns; {@code createdOn}/{@code abbrevId} are the Admin tab's read-only labels. */
    public record PreferencesView(
            String loginName,
            // Admin — User Information
            String firstName, String lastName, String nickName, String phone, String phone800,
            String extension, String email, Integer office, String manager, String title,
            String role, String department,
            // Font
            String fontName, Integer fontSize,
            // Edit — behaviour options
            boolean hideQueueOnQuery, boolean notesOnQuery, boolean queueOnClear,
            boolean navigationKeys, boolean allCodes, boolean printSignatures,
            boolean showFieldHints, boolean runQueueOnStart, boolean zipLookup,
            boolean keywordSearch,
            // Security
            boolean codeManager, boolean allowPrintManager, boolean allowReportCreation,
            boolean allowReportExecution, boolean allowLetterEditing, boolean allowLetterCreation,
            boolean dropPan, boolean writeAccess, boolean strictCodes, boolean lockNotes,
            boolean rateClosedProducts, boolean editUnderwriter, boolean editHcid,
            boolean allowMacros, boolean newCallQueues, boolean doubleKey,
            boolean allowSavingLetters, boolean allowNonOwned, boolean medBOverride,
            // Macro
            String startMacro, String endMacro, String queryMacro, String clearMacro,
            String printMacro,
            // Tab Stops — application sections opened expanded
            boolean pfAppInfo, boolean pfSelDesired, boolean pfBillingInfo, boolean pfDepInfo,
            boolean pfMedQuest, boolean pfMedDetail, boolean pfOthCov, boolean pfBdInfo,
            boolean pfIsgReq, boolean pfAppSig, boolean pfAgtSig, boolean pfCheckInfo,
            boolean pfConf, boolean pfUnderwriting, boolean pfLetters, boolean pfUnderConfirm,
            boolean pfAudit, boolean pfMedicare,
            // Notes
            List<String> commonNotes,
            // Display ("NUMEn" page)
            boolean panelApplicant, boolean panelLetters, boolean panelMacros, boolean panelEdit,
            boolean optionalLetters, boolean popupInfo, boolean showXml, boolean navBar,
            int primaryView, boolean grantRevoke, boolean cachedObjects,
            // Read-only labels
            LocalDateTime createdOn, String abbrevId) {
    }

    /** What PUT accepts — the same fields minus the read-only ones. */
    public record PreferencesUpdate(
            String firstName, String lastName, String nickName, String phone, String phone800,
            String extension, String email, String manager, String title, String role,
            String department,
            String fontName, Integer fontSize,
            boolean hideQueueOnQuery, boolean notesOnQuery, boolean queueOnClear,
            boolean navigationKeys, boolean allCodes, boolean printSignatures,
            boolean showFieldHints, boolean runQueueOnStart, boolean zipLookup,
            boolean keywordSearch,
            boolean codeManager, boolean allowPrintManager, boolean allowReportCreation,
            boolean allowReportExecution, boolean allowLetterEditing, boolean allowLetterCreation,
            boolean dropPan, boolean writeAccess, boolean strictCodes, boolean lockNotes,
            boolean rateClosedProducts, boolean editUnderwriter, boolean editHcid,
            boolean allowMacros, boolean newCallQueues, boolean doubleKey,
            boolean allowSavingLetters, boolean allowNonOwned, boolean medBOverride,
            String startMacro, String endMacro, String queryMacro, String clearMacro,
            String printMacro,
            boolean pfAppInfo, boolean pfSelDesired, boolean pfBillingInfo, boolean pfDepInfo,
            boolean pfMedQuest, boolean pfMedDetail, boolean pfOthCov, boolean pfBdInfo,
            boolean pfIsgReq, boolean pfAppSig, boolean pfAgtSig, boolean pfCheckInfo,
            boolean pfConf, boolean pfUnderwriting, boolean pfLetters, boolean pfUnderConfirm,
            boolean pfAudit, boolean pfMedicare,
            List<String> commonNotes,
            boolean panelApplicant, boolean panelLetters, boolean panelMacros, boolean panelEdit,
            boolean optionalLetters, boolean popupInfo, boolean showXml, boolean navBar,
            int primaryView, boolean grantRevoke, boolean cachedObjects) {
    }

    /** What the account-refresh e-mail endpoint answers (US-2.14). */
    public record MailResult(String toAddress, String lastSuccessfulLogin, boolean attachmentSent,
                             String relayResponse) {
    }
}
