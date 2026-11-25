package com.anthem.cartman.appdata.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationDates;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationEdit;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationOwners;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationSummary;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationUpdate;
import com.anthem.cartman.appdata.dto.AppDataViews.DeletionResult;
import com.anthem.cartman.appdata.dto.AppDataViews.Pickers;
import com.anthem.cartman.appdata.dto.AppDataViews.TableOutcome;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionRow;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionUpdate;
import com.anthem.cartman.appdata.entity.Application;
import com.anthem.cartman.appdata.entity.UwAction;
import com.anthem.cartman.appdata.repository.ApplicationRepository;
import com.anthem.cartman.appdata.repository.DependentRecordRepository;
import com.anthem.cartman.appdata.repository.UwActionRepository;
import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;

/**
 * Module 7 — Application Data Manager (US-7.1 to US-7.9).
 *
 * <p>Four places where this deliberately differs from the Delphi, each recorded in the requirements
 * as a correction rather than behaviour worth keeping:
 *
 * <ul>
 *   <li><b>A43</b> — deleting an application ran five statements with no transaction, each swallowing
 *       its own error, so the parent could go while the children stayed. It is one transaction
 *       here: all of it, or none of it.</li>
 *   <li><b>A45</b> — a green square meant "the statement ran", which cannot be told apart from "it
 *       matched nothing". Every table reports the rows it actually removed.</li>
 *   <li><b>A46</b> — {@code TM_Application_Dependants} is absent from this database, so that
 *       statement failed every time. Its absence is reported as such instead of as a failure.</li>
 *   <li><b>A48</b> — dates were written as the client's {@code mm/dd/yyyy} text inside a
 *       concatenated statement. They are dates here, bound as parameters.</li>
 * </ul>
 */
@Service
public class AppDataService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    /** The dependants table the Delphi names, which this database does not have. */
    private static final String DEPENDANTS = "TM_Application_Dependants";

    private final ApplicationRepository applications;
    private final UwActionRepository uwActions;
    private final DependentRecordRepository dependents;

    public AppDataService(ApplicationRepository applications, UwActionRepository uwActions,
                          DependentRecordRepository dependents) {
        this.applications = applications;
        this.uwActions = uwActions;
        this.dependents = dependents;
    }

    // ── finding ──────────────────────────────────────────────────────

    /** US-7.2.3 — the identifying fields for one application. */
    @Transactional(readOnly = true)
    public ApplicationSummary findApplication(int applicationId) {
        return summaryOf(require(applicationId));
    }

    public ApplicationSummary findApplication(int applicationId, String name) {
        return summaryOf(require(applicationId));
    }
    /** US-7.6 — everything the editing tab shows. */
    @Transactional(readOnly = true)
    public ApplicationEdit editView(int applicationId) {
        Application app = require(applicationId);
        return new ApplicationEdit(summaryOf(app), datesOf(app), ownersOf(app));
    }

    /**
     * US-7.2.5 / US-7.7.1 — every underwriting action for an application.
     *
     * <p>An application with no actions is an empty list, not an error: the screen shows an empty
     * grid and says so itself.
     */
    @Transactional(readOnly = true)
    public List<UwActionRow> uwActions(int applicationId) {
        return uwActions.findByApplicationIdOrderByActionIdAsc(applicationId).stream()
                .map(AppDataService::rowOf)
                .toList();
    }

    /** US-7.9 — the status codes and the four login lists. */
    @Transactional(readOnly = true)
    public Pickers pickers() {
        return new Pickers(dependents.uwStatusCodes(),
                dependents.loginsOfType("under"),
                dependents.loginsOfType("keyer"),
                dependents.loginsOfType("audit"),
                dependents.loginsOfType("coord"));
    }

    // ── deleting ─────────────────────────────────────────────────────

    /**
     * US-7.3 — remove an application and everything hanging off it, in one transaction.
     *
     * <p>The order is the Delphi's, and it matters only in that the parent goes first; nothing here
     * depends on the order because the whole thing commits or rolls back together.
     */
    @Transactional
    public DeletionResult deleteApplication(int applicationId) {
        require(applicationId, APP_NOT_DELETED);

        List<TableOutcome> outcomes = new ArrayList<>();

        int history = dependents.countRecordHistory(applicationId);
        int notes = dependents.deleteFloatingNotes(applicationId);
        int actions = uwActions.deleteForApplication(applicationId);
        dependents.deleteRecordHistory(applicationId);
        applications.deleteById(applicationId);

        outcomes.add(TableOutcome.deleted("TM_Application", "TM_Application record", 1));
        if (dependents.tableExists(DEPENDANTS)) {
            outcomes.add(TableOutcome.deleted(DEPENDANTS, "TM_Application_Dependants records",
                    dependents.deleteDependants(applicationId)));
        } else {
            outcomes.add(TableOutcome.absent(DEPENDANTS, "TM_Application_Dependants records"));
        }
        outcomes.add(TableOutcome.deleted("TM_App_Floating_Note", "TM_App_Floating_Note records", notes));
        outcomes.add(TableOutcome.deleted("TM_Record_History", "TM_Record_History records", history));
        outcomes.add(TableOutcome.deleted("TM_UW_Actions", "TM_UW_Actions records", actions));

        AUDIT.info("application {} deleted with {} dependent row(s)", applicationId,
                notes + actions + history);
        return new DeletionResult(applicationId, outcomes);
    }

    /** US-7.4 — remove one underwriting action, addressed by its own key. */
    @Transactional
    public void deleteUwAction(int uwId) {
        UwAction action = requireAction(uwId, UW_NOT_DELETED);
        uwActions.delete(action);
        AUDIT.info("UW action {} (application {}, action id {}) deleted",
                uwId, action.getApplicationId(), action.getActionId());
    }

    // ── saving ───────────────────────────────────────────────────────

    /** US-7.8.1 — the eight dates and the five ownership fields, in one statement. */
    @Transactional
    public ApplicationEdit saveApplication(int applicationId, ApplicationUpdate update) {
        if (update == null || update.dates() == null || update.owners() == null) {
            throw new ValidationException("Dates and owners are both required.");
        }
        Application app = require(applicationId, APP_NOT_UPDATED);
        ApplicationDates dates = update.dates();
        app.setEnrollCompleteDate(dates.enrollCompleteDate());
        app.setEnrollDate(dates.enrollDate());
        app.setEnrollSentDate(dates.enrollSentDate());
        app.setKeyingDate(dates.keyingDate());
        app.setKeyingSentDate(dates.keyingSentDate());
        app.setUwSentDate(dates.uwSentDate());
        app.setUwStatusDate(dates.uwStatusDate());
        app.setUwDate(dates.uwDate());

        ApplicationOwners owners = update.owners();
        app.setUwStatus(trimmedOrNull(owners.uwStatus()));
        app.setUnderwriter(trimmedOrNull(owners.underwriter()));
        app.setKeyer(trimmedOrNull(owners.keyer()));
        app.setAuditor(trimmedOrNull(owners.auditor()));
        app.setCoordinator(trimmedOrNull(owners.coordinator()));

        applications.save(app);
        AUDIT.info("application {} updated", applicationId);
        return new ApplicationEdit(summaryOf(app), datesOf(app), ownersOf(app));
    }

    /** US-7.8.3 — one underwriting action's three dates, on that row alone. */
    @Transactional
    public UwActionRow saveUwAction(int uwId, UwActionUpdate update) {
        if (update == null) {
            throw new ValidationException("The dates are required.");
        }
        UwAction action = requireAction(uwId, UW_NOT_UPDATED);
        action.setStart(update.start());
        action.setClose(update.close());
        action.setApplicationDate(update.applicationDate());
        uwActions.save(action);
        AUDIT.info("UW action {} updated", uwId);
        return rowOf(action);
    }

    // ── helpers ──────────────────────────────────────────────────────

    /**
     * The Delphi has a different message for each of the three things that can be asked of a record,
     * and the difference is worth keeping: a find is told to check the key value, while a save or a
     * delete that finds nothing is telling the user their record has gone since they read it.
     * `FindRecord`/`FindEditRecord`, `btnSaveClick` and `btnDeleteClick` each word it their own way.
     */
    private static final String APP_NOT_FOUND = "Application Record Not Found. Verify the Key Value is Correct.";
    private static final String APP_NOT_UPDATED = "Application Record Not Found, Could Not Update!";
    private static final String APP_NOT_DELETED = "Application record not found, Could Not Delete!";
    private static final String UW_NOT_FOUND = "UW Actions Record Not Found. Verify the Key Value is Correct.";
    private static final String UW_NOT_UPDATED = "UW Action Record Not Found, Could Not Update!";
    private static final String UW_NOT_DELETED = "UW Action Record not found, Could Not Delete!";

    private Application require(int applicationId) {
        return require(applicationId, APP_NOT_FOUND);
    }

    private Application require(int applicationId, String message) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException(message));
    }

    private UwAction requireAction(int uwId) {
        return requireAction(uwId, UW_NOT_FOUND);
    }

    private UwAction requireAction(int uwId, String message) {
        return uwActions.findById(uwId).orElseThrow(() -> new NotFoundException(message));
    }

    private static ApplicationSummary summaryOf(Application a) {
        return new ApplicationSummary(a.getId(), trimmed(a.getLastName()), trimmed(a.getFirstName()),
                trimmed(a.getType()), trimmed(a.getAddress1()), trimmed(a.getCity()),
                trimmed(a.getState()), trimmed(a.getZip()), trimmed(a.getPhone1()),
                a.getDateOfBirth() == null ? null : a.getDateOfBirth().toLocalDate(),
                trimmed(a.getHcid()), trimmed(a.getDcn()));
    }

    private static ApplicationDates datesOf(Application a) {
        return new ApplicationDates(a.getEnrollCompleteDate(), a.getEnrollDate(),
                a.getEnrollSentDate(), a.getKeyingDate(), a.getKeyingSentDate(), a.getUwSentDate(),
                a.getUwStatusDate(), a.getUwDate());
    }

    private static ApplicationOwners ownersOf(Application a) {
        return new ApplicationOwners(trimmed(a.getUwStatus()), trimmed(a.getUnderwriter()),
                trimmed(a.getKeyer()), trimmed(a.getAuditor()), trimmed(a.getCoordinator()));
    }

    private static UwActionRow rowOf(UwAction a) {
        return new UwActionRow(a.getId(), a.getApplicationId() == null ? 0 : a.getApplicationId(),
                a.getActionId(), trimmed(a.getType()), trimmed(a.getAction()), a.getStart(),
                a.getClose(), a.getApplicationDate());
    }

    /** Every column here is {@code char}, so every value arrives blank-padded. */
    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimmedOrNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
