package com.anthem.cartman.users.service;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesUpdate;
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesView;
import com.anthem.cartman.users.entity.CommonNote;
import com.anthem.cartman.users.entity.UserPreferences;
import com.anthem.cartman.users.repository.CommonNoteRepository;
import com.anthem.cartman.users.repository.UserPreferencesRepository;
import com.anthem.cartman.users.util.PreferenceFlags;

/**
 * US-2.15 — the Options dialog (*User Preferences*, {@code Options2.pas}).
 *
 * <p>Loads the preference slice of {@code TM_Login} plus the {@code TM_Common_Notes} lines; saves
 * everything in one transaction (the Delphi ran an unwrapped delete + per-line inserts + stored
 * procedure — a mid-save failure left the notes gone and the row half-written).
 *
 * <p>Deliberately corrected, recorded as anomalies:
 * <ul>
 *   <li><b>A36</b> — flag positions the dialog does not own are preserved, not zeroed.</li>
 *   <li><b>A37</b> — the user's password is neither shown nor round-tripped.</li>
 *   <li><b>A38</b> — {@code Log_Office} stays read-only rather than being overwritten with 0.</li>
 * </ul>
 */
@Service
public class UserPreferencesService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    /**
     * The Delphi's checker ({@code isValidAddress}) accepted letters, digits and {@code @-_.} with
     * an {@code @} and a dot present; the same idea, stated as one expression.
     */
    private static final Pattern EMAIL = Pattern.compile("[\\w.-]+@[\\w.-]+\\.[\\w-]+");

    private final UserPreferencesRepository preferences;
    private final CommonNoteRepository notes;

    public UserPreferencesService(UserPreferencesRepository preferences,
                                  CommonNoteRepository notes) {
        this.preferences = preferences;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public PreferencesView get(String loginName) {
        UserPreferences p = require(loginName);
        return view(p);
    }

    /** OK — everything the dialog holds, written at once. */
    @Transactional
    public PreferencesView save(String loginName, PreferencesUpdate u) {
        // The Delphi validated the address on OK and refused to save (Options2.pas:199).
        if (u.email() != null && !u.email().isBlank() && !EMAIL.matcher(u.email().trim()).matches()) {
            throw new ValidationException("E-mail address is not valid.");
        }

        UserPreferences p = require(loginName);

        // A36 — start from the stored flags so unmapped positions survive the save.
        PreferenceFlags flags = p.flags()
                .set(PreferenceFlags.QUEUE_ON_CLEAR, u.queueOnClear())
                .set(PreferenceFlags.NOTES_ON_QUERY, u.notesOnQuery())
                .set(PreferenceFlags.ALLOW_QUEUES, u.newCallQueues())
                .set(PreferenceFlags.ALLOW_PRINT_MANAGER, u.allowPrintManager())
                .set(PreferenceFlags.ALLOW_REPORT_CREATION, u.allowReportCreation())
                .set(PreferenceFlags.ALLOW_REPORT_EXECUTION, u.allowReportExecution())
                .set(PreferenceFlags.DROP_PAN, u.dropPan())
                .set(PreferenceFlags.WRITE_ACCESS, u.writeAccess())
                .set(PreferenceFlags.PRINT_SIGNATURES, u.printSignatures())
                .set(PreferenceFlags.RUN_QUEUE_ON_START, u.runQueueOnStart())
                .set(PreferenceFlags.MED_B_OVERRIDE, u.medBOverride())
                .set(PreferenceFlags.ALL_CODES, u.allCodes())
                .set(PreferenceFlags.GRANT_REVOKE, u.grantRevoke())
                .set(PreferenceFlags.CACHED_OBJECTS, u.cachedObjects())
                .set(PreferenceFlags.SHOW_FIELD_HINTS, u.showFieldHints())
                .set(PreferenceFlags.PF_APP_INFO, u.pfAppInfo())
                .set(PreferenceFlags.PF_SEL_DESIRED, u.pfSelDesired())
                .set(PreferenceFlags.PF_BILLING_INFO, u.pfBillingInfo())
                .set(PreferenceFlags.PF_DEP_INFO, u.pfDepInfo())
                .set(PreferenceFlags.PF_MED_QUEST, u.pfMedQuest())
                .set(PreferenceFlags.PF_MED_DETAIL, u.pfMedDetail())
                .set(PreferenceFlags.PF_OTH_COV, u.pfOthCov())
                .set(PreferenceFlags.PF_BD_INFO, u.pfBdInfo())
                .set(PreferenceFlags.PF_ISG_REQ, u.pfIsgReq())
                .set(PreferenceFlags.PF_APP_SIG, u.pfAppSig())
                .set(PreferenceFlags.PF_AGT_SIG, u.pfAgtSig())
                .set(PreferenceFlags.PF_CHECK_INFO, u.pfCheckInfo())
                .set(PreferenceFlags.PF_CONF, u.pfConf())
                .set(PreferenceFlags.PF_AUDIT, u.pfAudit())
                .set(PreferenceFlags.PF_MEDICARE, u.pfMedicare())
                .set(PreferenceFlags.PF_UNDERWRITING, u.pfUnderwriting())
                .set(PreferenceFlags.PF_LETTERS, u.pfLetters())
                .set(PreferenceFlags.PF_UNDER_CONFIRM, u.pfUnderConfirm())
                .set(PreferenceFlags.RATE_CLOSED_PRODUCTS, u.rateClosedProducts())
                .set(PreferenceFlags.EDIT_UW, u.editUnderwriter())
                .set(PreferenceFlags.HIDE_QUEUE_ON_QUERY, u.hideQueueOnQuery())
                .set(PreferenceFlags.EDIT_HCID, u.editHcid())
                .set(PreferenceFlags.DOUBLE_KEY, u.doubleKey())
                .set(PreferenceFlags.KEYWORD_SEARCH, u.keywordSearch())
                .set(PreferenceFlags.ALLOW_SAVING_LETTERS, u.allowSavingLetters())
                .set(PreferenceFlags.NAV_BAR, u.navBar())
                .set(PreferenceFlags.SHOW_XML, u.showXml())
                .set(PreferenceFlags.POPUP_INFO, u.popupInfo())
                .set(PreferenceFlags.OPTIONAL_LETTERS, u.optionalLetters())
                .setPrimaryView(u.primaryView())
                .setStartupPanels(u.panelApplicant(), u.panelLetters(), u.panelMacros(),
                        u.panelEdit());

        p.applyUpdate(u.firstName(), u.lastName(), u.nickName(), u.phone(), u.phone800(),
                u.extension(), u.email(), u.manager(), u.title(), u.role(), u.department(),
                u.fontName(), u.fontSize() == null ? 8 : u.fontSize(), u.zipLookup(),
                u.navigationKeys(), u.allowMacros(), u.allowNonOwned(), u.lockNotes(),
                u.allowLetterEditing(), u.strictCodes(), u.allowLetterCreation(), u.codeManager(),
                u.startMacro(), u.endMacro(), u.queryMacro(), u.clearMacro(), u.printMacro(),
                flags);
        preferences.save(p);

        // The memo replaces the notes wholesale, as the Delphi's delete + insert-per-line did —
        // but inside this same transaction.
        String name = p.getLoginName();
        notes.deleteByUser(name);
        for (String line : u.commonNotes() == null ? List.<String>of() : u.commonNotes()) {
            notes.save(CommonNote.of(name, line.trim()));
        }

        AUDIT.info("preferences saved for {}", name);
        return view(require(loginName));
    }

    private UserPreferences require(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new ValidationException("A user is required.");
        }
        return preferences.findByName(loginName.trim())
                .orElseThrow(() -> new NotFoundException("No user named " + loginName.trim() + "."));
    }

    private PreferencesView view(UserPreferences p) {
        PreferenceFlags f = p.flags();
        boolean[] panels = f.startupPanels();
        List<String> noteLines = notes.findByUser(p.getLoginName()).stream()
                .map(n -> n.getNote().trim())
                .toList();
        return new PreferencesView(
                p.getLoginName(),
                p.getFirstName(), p.getLastName(), p.getNickName(), p.getPhone(), p.getPhone800(),
                p.getExtension(), p.getEmail(), p.getOffice(), p.getManager(), p.getTitle(),
                p.getRole(), p.getDepartment(),
                p.getFontName(), p.getFontSize(),
                f.get(PreferenceFlags.HIDE_QUEUE_ON_QUERY), f.get(PreferenceFlags.NOTES_ON_QUERY),
                f.get(PreferenceFlags.QUEUE_ON_CLEAR), p.isNavigationKeys(),
                f.get(PreferenceFlags.ALL_CODES), f.get(PreferenceFlags.PRINT_SIGNATURES),
                f.get(PreferenceFlags.SHOW_FIELD_HINTS), f.get(PreferenceFlags.RUN_QUEUE_ON_START),
                p.isZipLookup(), f.get(PreferenceFlags.KEYWORD_SEARCH),
                p.isCodeManager(), f.get(PreferenceFlags.ALLOW_PRINT_MANAGER),
                f.get(PreferenceFlags.ALLOW_REPORT_CREATION),
                f.get(PreferenceFlags.ALLOW_REPORT_EXECUTION), p.isAllowLetterEditing(),
                p.isAllowLetterCreation(), f.get(PreferenceFlags.DROP_PAN),
                f.get(PreferenceFlags.WRITE_ACCESS), p.isStrictCodes(), p.isLockNotes(),
                f.get(PreferenceFlags.RATE_CLOSED_PRODUCTS), f.get(PreferenceFlags.EDIT_UW),
                f.get(PreferenceFlags.EDIT_HCID), p.isAllowMacros(),
                f.get(PreferenceFlags.ALLOW_QUEUES), f.get(PreferenceFlags.DOUBLE_KEY),
                f.get(PreferenceFlags.ALLOW_SAVING_LETTERS), p.isAllowNonOwned(),
                f.get(PreferenceFlags.MED_B_OVERRIDE),
                p.getStartMacro(), p.getEndMacro(), p.getQueryMacro(), p.getClearMacro(),
                p.getPrintMacro(),
                f.get(PreferenceFlags.PF_APP_INFO), f.get(PreferenceFlags.PF_SEL_DESIRED),
                f.get(PreferenceFlags.PF_BILLING_INFO), f.get(PreferenceFlags.PF_DEP_INFO),
                f.get(PreferenceFlags.PF_MED_QUEST), f.get(PreferenceFlags.PF_MED_DETAIL),
                f.get(PreferenceFlags.PF_OTH_COV), f.get(PreferenceFlags.PF_BD_INFO),
                f.get(PreferenceFlags.PF_ISG_REQ), f.get(PreferenceFlags.PF_APP_SIG),
                f.get(PreferenceFlags.PF_AGT_SIG), f.get(PreferenceFlags.PF_CHECK_INFO),
                f.get(PreferenceFlags.PF_CONF), f.get(PreferenceFlags.PF_UNDERWRITING),
                f.get(PreferenceFlags.PF_LETTERS), f.get(PreferenceFlags.PF_UNDER_CONFIRM),
                f.get(PreferenceFlags.PF_AUDIT), f.get(PreferenceFlags.PF_MEDICARE),
                noteLines,
                panels[0], panels[1], panels[2], panels[3],
                f.get(PreferenceFlags.OPTIONAL_LETTERS), f.get(PreferenceFlags.POPUP_INFO),
                f.get(PreferenceFlags.SHOW_XML), f.get(PreferenceFlags.NAV_BAR),
                f.primaryView(), f.get(PreferenceFlags.GRANT_REVOKE),
                f.get(PreferenceFlags.CACHED_OBJECTS),
                p.getCreatedOn(), p.getAbbrevId());
    }
}
