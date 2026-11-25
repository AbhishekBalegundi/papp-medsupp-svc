package com.anthem.cartman.tatreports.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.tatreports.dto.TatViews.CarveOut;
import com.anthem.cartman.tatreports.dto.TatViews.Pend;
import com.anthem.cartman.tatreports.dto.TatViews.ReportKind;
import com.anthem.cartman.tatreports.dto.TatViews.ReportResult;
import com.anthem.cartman.tatreports.dto.TatViews.ReportRow;
import com.anthem.cartman.tatreports.repository.TatReportRepository;
import com.anthem.cartman.tatreports.service.CarveOutCalculator.Area;
import com.anthem.cartman.tatreports.service.CarveOutCalculator.Window;

/**
 * Module 10 — build a turn-around-time report.
 *
 * <p>The shape is CARTMAN's: read the rows for the date range, compute the enrolment and underwriting
 * figures for each, band the total, and write a CSV line per row (US-10.4 to US-10.8).
 */
@Service
public class TatReportService {

    private static final Logger AUDIT = LoggerFactory.getLogger("cartman.audit");

    /** US-10.8.1 — `GetHeaderRow`, character for character including `Effecitve Date`. */
    static final List<String> GENERAL_HEADER = List.of(
            "App ID", "App Type", "App Source", "App Rcvd Date", "DCN", "GI", "Tobacco_Ind",
            "Effecitve Date", "State", "Sales Region", "Ops Region", "Product", "Plan", "Last Name",
            "First Name", "DOB", "HCID", "Agent Num", "Medicare Number", "Final Status", "Status Dt",
            "Pend Source", "Clean App", "Enrolled Date", "Diag Codes", "Decline Reason",
            "Enroll Carveout", "UW Carveout", "Enroll Working", "UW Working", "Total Working",
            "Total", "Range");

    /**
     * A62 — the Agent report gets a header naming what it actually writes: column 2 is the underwriter,
     * not the application type, and there is a transfer date after Status Dt. CARTMAN writes the header
     * above under 41 columns of data.
     */
    static final List<String> AGENT_HEADER = withAgentColumns();

    private final TatReportRepository repository;
    private final CarveOutCalculator carveOut;

    public TatReportService(TatReportRepository repository, CarveOutCalculator carveOut) {
        this.repository = repository;
        this.carveOut = carveOut;
    }

    /**
     * US-10.4 / US-10.5 — run a report.
     *
     * @param today the day an unfinished application is measured to; passed in so the figures can be
     *              tested against a fixed date
     */
    @Transactional(readOnly = true)
    public ReportResult run(ReportKind kind, LocalDate from, LocalDate to, String fileName,
            LocalDate today) {
        // A60 — CARTMAN validates none of this and pastes whatever is in the boxes into the statement.
        if (from == null || to == null) {
            throw new ValidationException("Both From Date and To Date are required.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("From Date must not be after To Date.");
        }

        List<ReportRow> rows = kind == ReportKind.GENERAL
                ? repository.general(from, to)
                : repository.agentDetail(from, to);

        Map<Integer, List<Pend>> pendsByApplication = pendsFor(rows);

        StringBuilder csv = new StringBuilder();
        CsvWriter.appendLine(csv, kind == ReportKind.GENERAL ? GENERAL_HEADER : AGENT_HEADER);

        int written = 0;
        Integer lastApplication = null;
        for (ReportRow row : rows) {
            List<Pend> pends = pendsByApplication.getOrDefault(row.applicationId(), List.of());

            // US-10.5.3 — a synthetic Enroll line before the first row of each application.
            if (kind == ReportKind.AGENT
                    && (lastApplication == null || lastApplication != row.applicationId())) {
                CsvWriter.appendLine(csv, line(row, pends, today, true));
                written++;
            }
            CsvWriter.appendLine(csv, line(row, pends, today, false));
            written++;
            lastApplication = row.applicationId();
        }

        AUDIT.info("TAT {} report: {} to {}, {} rows", kind, from, to, written);
        return new ReportResult(fileName, written, csv.toString());
    }

    /** One CSV line: the query's values, then the six computed columns. */
    private List<String> line(ReportRow row, List<Pend> pends, LocalDate today, boolean enrollRow) {
        Window dates = new Window(row.keyingDate(), row.applicationDate(), row.keyingSentDate(),
                row.completeDate());

        CarveOut enroll = carveOut.compute(dates, Area.ENROLL, pends, today);

        // US-10.5.4 — an Agent line measures underwriting from its own transfer date; the synthetic
        // Enroll line has no underwriting of its own to report.
        CarveOut uw;
        if (enrollRow) {
            uw = CarveOut.NONE;
        } else if (row.transferDate() != null) {
            Window fromTransfer = new Window(row.keyingDate(), row.applicationDate(),
                    row.transferDate(), row.completeDate());
            uw = carveOut.compute(fromTransfer, Area.UW, pends, today);
        } else {
            uw = carveOut.compute(dates, Area.UW, pends, today);
        }

        int totalWorking = enroll.working() + uw.working();
        int total = enroll.carveOut() + enroll.working() + uw.carveOut() + uw.working();

        List<String> out = new ArrayList<>(row.values());
        if (enrollRow) {
            // Column 2 carries the literal word, in place of the underwriter.
            out.set(1, "Enroll");
        }
        out.add(String.valueOf(enroll.carveOut()));
        out.add(String.valueOf(uw.carveOut()));
        out.add(String.valueOf(enroll.working()));
        out.add(String.valueOf(uw.working()));
        out.add(String.valueOf(totalWorking));
        out.add(String.valueOf(total));
        out.add(band(total));
        return out;
    }

    /**
     * US-10.7.2 — the band for a total.
     *
     * <p>A56 — CARTMAN's `Case COT of` covers 1 to 9999 only, and its `CORange` variable is never
     * re-initialised, so a total of 0 or above 9999 carries whichever band an earlier row last matched.
     * Zero is reported as no band, and everything above 60 as the top band, which is what that band
     * already says in words.
     */
    static String band(int total) {
        if (total <= 0) {
            return "";
        }
        if (total <= 7) {
            return "Low (1-7)";
        }
        if (total <= 15) {
            return "Moderate (8-15)";
        }
        if (total <= 30) {
            return "Hi (16-30)";
        }
        if (total <= 60) {
            return "Elevated (31-60)";
        }
        return "Outlier (>60)";
    }

    private Map<Integer, List<Pend>> pendsFor(List<ReportRow> rows) {
        List<Integer> ids = rows.stream().map(ReportRow::applicationId).distinct().toList();
        Map<Integer, List<Pend>> byApplication = new HashMap<>();
        for (Pend pend : repository.pends(ids)) {
            byApplication.computeIfAbsent(pend.applicationId(), k -> new ArrayList<>()).add(pend);
        }
        return byApplication;
    }

    private static List<String> withAgentColumns() {
        List<String> header = new ArrayList<>(GENERAL_HEADER);
        header.set(1, "Underwriter");
        header.add(21, "TransferDt");
        return List.copyOf(header);
    }
}
