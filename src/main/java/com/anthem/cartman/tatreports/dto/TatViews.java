package com.anthem.cartman.tatreports.dto;

import java.time.LocalDate;
import java.util.List;

/** Module 10 — TAT Reports. The shapes crossing the wire and the service boundary. */
public final class TatViews {

    private TatViews() {
    }

    /** Which report. The two tabs of `RzPageControl1`. */
    public enum ReportKind { GENERAL, AGENT }

    /**
     * US-10.6 — one pend, as the carve-out walk reads it. `UW_Start` and `UW_Close` of a
     * `TM_UW_Actions` row that is not a task and is neither an approval nor a denial.
     */
    public record Pend(int applicationId, LocalDate start, LocalDate close) {}

    /** US-10.6 — the three figures for one area of one application. */
    public record CarveOut(int total, int carveOut, int working) {

        /** No window to measure: A55 makes this a value rather than a failure. */
        public static final CarveOut NONE = new CarveOut(0, 0, 0);
    }

    /**
     * One line of the report, before the computed columns are filled in.
     *
     * <p>`values` is the query's columns in order, already in the order the header names them. The
     * dates the carve-out needs are carried separately because the Delphi carries them too — the Agent
     * statement selects six trailing date columns for exactly this reason (US-10.5.1).
     */
    public record ReportRow(int applicationId,
                            List<String> values,
                            LocalDate keyingDate,
                            LocalDate applicationDate,
                            LocalDate keyingSentDate,
                            LocalDate completeDate,
                            LocalDate transferDate) {}

    /** What a run produced, for the screen to report. */
    public record ReportResult(String fileName, int rowCount, String csv) {}
}
