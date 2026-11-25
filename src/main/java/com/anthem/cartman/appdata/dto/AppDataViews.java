package com.anthem.cartman.appdata.dto;

import java.time.LocalDate;
import java.util.List;

/** The shapes the Application Data Manager exchanges with its screen (Module 7). */
public final class AppDataViews {

    private AppDataViews() {
    }

    /**
     * US-7.2.3 — the eleven identifying fields, shown read-only on both tabs.
     *
     * @param dateOfBirth as stored; the screen formats it
     */
    public record ApplicationSummary(int applicationId, String lastName, String firstName,
                                     String type, String address1, String city, String state,
                                     String zip, String phone1, LocalDate dateOfBirth, String hcid,
                                     String dcn) {
    }

    /** US-7.6 — everything the editing tab shows for an application. */
    public record ApplicationEdit(ApplicationSummary identity, ApplicationDates dates,
                                  ApplicationOwners owners) {
    }

    /** US-7.6.2 — the eight workflow dates. A null is a blank box. */
    public record ApplicationDates(LocalDate enrollCompleteDate, LocalDate enrollDate,
                                   LocalDate enrollSentDate, LocalDate keyingDate,
                                   LocalDate keyingSentDate, LocalDate uwSentDate,
                                   LocalDate uwStatusDate, LocalDate uwDate) {
    }

    /** US-7.6.3 — the five ownership fields. */
    public record ApplicationOwners(String uwStatus, String underwriter, String keyer,
                                    String auditor, String coordinator) {
    }

    /** US-7.8.1 — what a save carries. Both halves are required; a null inside either is a blank. */
    public record ApplicationUpdate(ApplicationDates dates, ApplicationOwners owners) {
    }

    /**
     * US-7.2.5 / US-7.7 — one row of the underwriting grid.
     *
     * @param id the row's own key, which is what an edit or a delete addresses
     */
    public record UwActionRow(int id, int applicationId, int actionId, String type, String action,
                              LocalDate start, LocalDate close, LocalDate applicationDate) {
    }

    /** US-7.8.3 — the three dates an underwriting action exposes. */
    public record UwActionUpdate(LocalDate start, LocalDate close, LocalDate applicationDate) {
    }

    /** US-7.9 — the four login lists and the status codes, read once when the screen opens. */
    public record Pickers(List<String> uwStatuses, List<String> underwriters, List<String> keyers,
                          List<String> auditors, List<String> coordinators) {
    }

    /**
     * US-7.3 — what a deletion actually did, table by table.
     *
     * <p>The Delphi shows a green square when the statement ran and a red one when it raised, which
     * cannot distinguish "removed four rows" from "matched nothing" (A45). This reports the count.
     */
    public record DeletionResult(int applicationId, List<TableOutcome> tables) {

        public int totalRows() {
            return tables.stream().mapToInt(TableOutcome::rows).sum();
        }
    }

    /** One line of the deletion result. */
    public record TableOutcome(String table, String label, Outcome outcome, int rows) {

        public static TableOutcome deleted(String table, String label, int rows) {
            return new TableOutcome(table, label, rows > 0 ? Outcome.DELETED : Outcome.NONE, rows);
        }

        public static TableOutcome absent(String table, String label) {
            return new TableOutcome(table, label, Outcome.TABLE_ABSENT, 0);
        }
    }

    /** What became of one table in a deletion. */
    public enum Outcome {
        /** Rows were removed. */
        DELETED,
        /** The statement ran and matched nothing. */
        NONE,
        /** The table is not in this database at all — see A46. */
        TABLE_ABSENT
    }
}
