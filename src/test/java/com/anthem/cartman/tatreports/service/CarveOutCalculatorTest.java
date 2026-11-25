package com.anthem.cartman.tatreports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.anthem.cartman.tatreports.dto.TatViews.CarveOut;
import com.anthem.cartman.tatreports.dto.TatViews.Pend;
import com.anthem.cartman.tatreports.service.CarveOutCalculator.Area;
import com.anthem.cartman.tatreports.service.CarveOutCalculator.Window;

/**
 * US-10.6 — the carve-out calculation.
 *
 * <p>The expected figures in {@link AgainstTheLiveRows} were produced by transcribing
 * {@code GetCarveOutTime} statement by statement and running it against the live tables, so these
 * assert what CARTMAN computes rather than what the algorithm reads as if it should.
 */
class CarveOutCalculatorTest {

    private final CarveOutCalculator calculator = new CarveOutCalculator();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static Pend pend(String start, String close) {
        return new Pend(1, start == null ? null : d(start), close == null ? null : d(close));
    }

    @Nested
    @DisplayName("US-10.6.1 — the window for each area")
    class Bounds {

        @Test
        @DisplayName("Enroll runs from the keying date to the keying-sent date")
        void enroll() {
            LocalDate[] bounds = calculator.bounds(
                    new Window(d("2026-02-08"), d("2026-02-07"), d("2026-02-20"), null),
                    Area.ENROLL, TODAY);
            assertEquals(d("2026-02-08"), bounds[0]);
            assertEquals(d("2026-02-20"), bounds[1]);
        }

        @Test
        @DisplayName("and falls back to the application date when there is no keying date")
        void enrollFallsBack() {
            LocalDate[] bounds = calculator.bounds(
                    new Window(null, d("2026-02-07"), null, null), Area.ENROLL, TODAY);
            assertEquals(d("2026-02-07"), bounds[0]);
            assertEquals(TODAY, bounds[1], "an open application is measured to today");
        }

        @Test
        @DisplayName("Enroll prefers the complete date over today when there is no keying-sent date")
        void enrollPrefersComplete() {
            LocalDate[] bounds = calculator.bounds(
                    new Window(d("2026-02-08"), null, null, d("2026-03-01")), Area.ENROLL, TODAY);
            assertEquals(d("2026-03-01"), bounds[1]);
        }

        @Test
        @DisplayName("UW runs from the keying-sent date")
        void uw() {
            LocalDate[] bounds = calculator.bounds(
                    new Window(d("2026-02-08"), null, d("2026-02-08"), d("2026-03-21")),
                    Area.UW, TODAY);
            assertEquals(d("2026-02-08"), bounds[0]);
            assertEquals(d("2026-03-21"), bounds[1]);
        }

        @Test
        @DisplayName("US-10.6.2 — and there is no UW window at all without one")
        void uwNeedsKeyingSent() {
            assertNull(calculator.bounds(new Window(d("2026-02-08"), null, null, null),
                    Area.UW, TODAY));
        }

        @Test
        @DisplayName("US-10.6.3 — an end date before 1900 is treated as absent")
        void ancientEnd() {
            LocalDate[] bounds = calculator.bounds(
                    new Window(d("2026-02-08"), null, d("1899-12-31"), null), Area.ENROLL, TODAY);
            assertEquals(TODAY, bounds[1]);
        }
    }

    @Nested
    @DisplayName("US-10.6 — counting the days")
    class Counting {

        @Test
        @DisplayName("no pends means every day was worked")
        void noPends() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-11"), null),
                    Area.ENROLL, List.of(), TODAY);
            assertEquals(10, out.total());
            assertEquals(0, out.carveOut());
            assertEquals(10, out.working());
        }

        @Test
        @DisplayName("US-10.6.5 — the end day is not one of the days walked")
        void endDayExcluded() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-02"), null),
                    Area.ENROLL, List.of(), TODAY);
            assertEquals(1, out.total(), "one day, not two");
        }

        @Test
        @DisplayName("a start and end on the same day is a window of nothing")
        void sameDay() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-02-08"), null, d("2026-02-08"), null),
                    Area.ENROLL, List.of(), TODAY);
            assertEquals(0, out.total());
            assertEquals(0, out.working());
        }

        @Test
        @DisplayName("US-10.6.4 — the total is absolute, so a backwards window still counts forward")
        void backwards() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-11"), null, d("2026-03-01"), null),
                    Area.ENROLL, List.of(), TODAY);
            assertEquals(10, out.total());
        }

        @Test
        @DisplayName("a pend that opens and closes carves out the days it was open")
        void closedPend() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-11"), null),
                    Area.ENROLL, List.of(pend("2026-03-03", "2026-03-06")), TODAY);
            assertEquals(10, out.total());
            assertEquals(3, out.carveOut());
            assertEquals(7, out.working());
        }

        @Test
        @DisplayName("US-10.6.10 — a pend that never closes carves out everything after it")
        void openPend() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-11"), null),
                    Area.ENROLL, List.of(pend("2026-03-03", null)), TODAY);
            assertEquals(10, out.total());
            assertEquals(7, out.carveOut(), "from the 3rd to the day before the last");
            assertEquals(3, out.working());
        }

        @Test
        @DisplayName("a pend outside the window is not counted")
        void pendBeforeWindow() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-11"), null),
                    Area.ENROLL, List.of(pend("2026-02-01", "2026-02-05")), TODAY);
            assertEquals(0, out.carveOut());
        }

        @Test
        @DisplayName("a close on a day that already opened a pend does not also close it")
        void openAndCloseSameDay() {
            // The Delphi skips the E when the day already carries an S, which keeps a same-day
            // open-and-close counted as open.
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-01"), null, d("2026-03-11"), null),
                    Area.ENROLL, List.of(pend("2026-03-03", "2026-03-03")), TODAY);
            assertEquals(7, out.carveOut());
        }
    }

    @Nested
    @DisplayName("A54 — a close date outside the window")
    class PhantomDay {

        /*
         * The Delphi writes the close day into a TStringList by name; Values[] on a name that is not
         * there appends a line rather than failing. That lengthens the list, moving the entry the walk
         * treats as the last day and letting the phantom day be counted. Measured on application 4608:
         * 25 carved out of 25 total, so no working days at all for an application that was worked.
         */
        @Test
        @DisplayName("is ignored, so the window stays the length it was built")
        void ignored() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-27"), null, d("2026-04-21"), null),
                    Area.ENROLL,
                    List.of(pend("2026-03-27", "2026-04-21"), pend("2026-03-27", null)),
                    TODAY);

            assertEquals(25, out.total());
            assertEquals(24, out.carveOut(), "24, not the 25 the phantom day produces");
            assertEquals(1, out.working(), "and one working day, rather than none");
        }
    }

    @Nested
    @DisplayName("A55 — an area with no window")
    class NoWindow {

        /*
         * The Delphi returns false here, and its caller only copies the figures when the call returns
         * true, so the row silently kept the previous row's numbers. Zeros are returned as a value.
         */
        @Test
        @DisplayName("returns zeros rather than a failure")
        void zeros() {
            CarveOut out = calculator.compute(new Window(d("2026-02-23"), null, null, null),
                    Area.UW, List.of(pend("2026-03-01", null)), TODAY);
            assertEquals(CarveOut.NONE, out);
        }
    }

    @Nested
    @DisplayName("US-10.6 — against the live rows")
    class AgainstTheLiveRows {

        @Test
        @DisplayName("application 4603, underwriting: 41 days, 15 carved out, 26 worked")
        void app4603Uw() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-02-08"), d("2026-02-07"), d("2026-02-08"), d("2026-03-21")),
                    Area.UW,
                    List.of(pend("2026-02-09", "2026-02-17"), pend("2026-02-09", "2026-02-24"),
                            pend("2026-02-24", "2026-03-03"), pend("2026-03-09", "2026-03-16")),
                    TODAY);
            assertEquals(41, out.total());
            assertEquals(15, out.carveOut());
            assertEquals(26, out.working());
        }

        @Test
        @DisplayName("application 4601, enrolment: two pends never closed, 153 of 154 carved out")
        void app4601Enroll() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-03-17"), null, null, null), Area.ENROLL,
                    List.of(pend("2026-03-17", null), pend("2026-03-17", null)), TODAY);
            assertEquals(154, out.total());
            assertEquals(153, out.carveOut());
            assertEquals(1, out.working());
        }

        @Test
        @DisplayName("application 4603, enrolment: keyed and sent the same day, nothing to measure")
        void app4603Enroll() {
            CarveOut out = calculator.compute(
                    new Window(d("2026-02-08"), d("2026-02-07"), d("2026-02-08"), d("2026-03-21")),
                    Area.ENROLL, List.of(), TODAY);
            assertEquals(0, out.total());
            assertEquals(0, out.working());
        }
    }
}
