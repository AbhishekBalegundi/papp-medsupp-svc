package com.anthem.cartman.tatreports.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.anthem.cartman.tatreports.dto.TatViews.CarveOut;
import com.anthem.cartman.tatreports.dto.TatViews.Pend;

/**
 * US-10.6 — of the days an application was open, how many was it waiting on someone else?
 *
 * <p>Transcribed from {@code GetCarveOutTime} and {@code GetCarveOutTimeAgent}
 * ({@code frmTATReport.pas} lines 111-353), which are two near-copies of one algorithm: walk the days
 * of a window, count the pends open on each, and call a day carved out when any pend is open on it.
 *
 * <p>Two corrections, both recorded in the requirements and both measured against the live rows:
 *
 * <ul>
 *   <li><b>A54</b> — a close date outside the window is ignored. The Delphi writes it into a
 *       {@code TStringList} by name, and {@code Values[]} on an absent name <em>appends</em> a line
 *       rather than failing, which moves the entry the walk treats as the last day and lets the
 *       phantom day be counted. It turned application 4608 into 25 carved-out days of 25 — no working
 *       days at all for an application that was worked.</li>
 *   <li><b>A55</b> — an area with no start date returns zeros as a value, not as a failure. The Delphi
 *       returns false there, and its caller only copies the figures when the call returns true, so the
 *       row silently kept the previous row's numbers.</li>
 * </ul>
 *
 * <p>Everything else is kept exactly, including the parts that look like mistakes: the total is an
 * absolute day count, the end date is not one of the days walked, the last day decrements the open
 * count once more, and a pend that never closes carves out every day after it opens.
 */
@Component
public class CarveOutCalculator {

    /**
     * Which window of an application's life is being measured.
     *
     * <p>The Delphi also implements an `All` area — keying date to completion — and never calls it. It
     * is not implemented here for the same reason A59's duplicate statement is not: nothing reaches it.
     */
    public enum Area { ENROLL, UW }

    /**
     * The dates an application carries, as the calculation reads them.
     *
     * @param keyingDate     App_Keying_Dt
     * @param applicationDate App_Dt
     * @param keyingSentDate App_KeyingSnt_Dt — the Delphi comments call this the enrol-sent date
     * @param completeDate   App_Complete_Dt
     */
    public record Window(LocalDate keyingDate, LocalDate applicationDate,
                         LocalDate keyingSentDate, LocalDate completeDate) {}

    /**
     * US-10.6.1 — the start and end of the window for an area.
     *
     * @param today the day an open application is measured to; passed in so the rules can be tested
     * @return the window, or null when the area has no start date (UW without a keying-sent date)
     */
    public LocalDate[] bounds(Window dates, Area area, LocalDate today) {
        LocalDate start;
        LocalDate end;
        if (area == Area.UW) {
            // US-10.6.2 — no keying-sent date, no underwriting window.
            if (dates.keyingSentDate() == null) {
                return null;
            }
            start = dates.keyingSentDate();
            end = dates.completeDate() != null ? dates.completeDate() : today;
        } else {
            start = dates.keyingDate() != null ? dates.keyingDate() : dates.applicationDate();
            end = dates.keyingSentDate() != null ? dates.keyingSentDate()
                    : dates.completeDate() != null ? dates.completeDate() : today;
        }
        if (start == null) {
            return null;
        }
        // US-10.6.3 — an end before 1900 is treated as absent.
        if (end == null || end.isBefore(LocalDate.of(1900, 1, 1))) {
            end = today;
        }
        return new LocalDate[] { start, end };
    }

    /**
     * Count the days.
     *
     * @param pends every non-task pend on the application; filtered to the window here, so one query
     *              can serve every area and every row rather than one query per row per area
     */
    public CarveOut compute(Window dates, Area area, List<Pend> pends, LocalDate today) {
        LocalDate[] bounds = bounds(dates, area, today);
        if (bounds == null) {
            return CarveOut.NONE;
        }
        LocalDate start = bounds[0];
        LocalDate end = bounds[1];

        // US-10.6.4 — DaysBetween is absolute, so a window that runs backwards still counts forward.
        int total = (int) Math.abs(ChronoUnit.DAYS.between(start, end));

        // US-10.6.5 — one entry per day from the start, for `total` days: the end day is not included.
        Map<LocalDate, StringBuilder> days = new LinkedHashMap<>();
        for (int i = 0; i < total; i++) {
            days.put(start.plusDays(i), new StringBuilder());
        }

        for (Pend pend : inWindow(pends, start, end)) {
            // US-10.6.7 — S on the start day when that day is in the window.
            StringBuilder onStart = pend.start() == null ? null : days.get(pend.start());
            if (onStart != null) {
                onStart.append('S');
            }
            if (pend.close() != null) {
                StringBuilder onClose = days.get(pend.close());
                // A54 — absent means outside the window, and is ignored rather than appended.
                if (onClose != null && onClose.indexOf("S") < 0) {
                    onClose.append('E');
                }
            }
        }

        // US-10.6.8 — walk the days, counting the pends left open on each.
        int open = 0;
        int carvedOut = 0;
        int index = 0;
        int last = days.size() - 1;
        for (StringBuilder marks : days.values()) {
            if (marks.indexOf("S") >= 0) {
                open++;
            }
            if (marks.indexOf("E") >= 0) {
                open--;
            }
            if (open == -1) {
                open = 0;
            }
            if (index == last) {
                open--;
            }
            if (open > 0) {
                carvedOut++;
            }
            index++;
        }

        // US-10.6.9 — the Delphi floors both figures at zero; kept.
        int carve = Math.max(carvedOut, 0);
        int working = Math.max(total - carve, 0);
        return new CarveOut(total, carve, working);
    }

    /**
     * US-10.6.6 — the window's own pend filter: started on or after the start, and either still open
     * or closed by the day after the end.
     */
    private List<Pend> inWindow(List<Pend> pends, LocalDate start, LocalDate end) {
        List<Pend> kept = new ArrayList<>();
        LocalDate closeBy = end.plusDays(1);
        for (Pend pend : pends) {
            if (pend.start() == null || pend.start().isBefore(start)) {
                continue;
            }
            if (pend.close() != null && pend.close().isAfter(closeBy)) {
                continue;
            }
            kept.add(pend);
        }
        // The Delphi orders by UW_Start; the repository does the same, and this preserves it.
        return kept;
    }
}
