package com.anthem.cartman.tatreports.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.tatreports.dto.TatViews.Pend;
import com.anthem.cartman.tatreports.dto.TatViews.ReportKind;
import com.anthem.cartman.tatreports.dto.TatViews.ReportResult;
import com.anthem.cartman.tatreports.dto.TatViews.ReportRow;
import com.anthem.cartman.tatreports.repository.TatReportRepository;

/** Module 10 — assembling the file (US-10.4, US-10.5, US-10.7, US-10.8). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TatReportServiceTest {

    @Mock
    private TatReportRepository repository;

    private TatReportService service;

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        service = new TatReportService(repository, new CarveOutCalculator());
    }

    /** A General row: 26 query columns, then the four carried dates. */
    private static ReportRow generalRow(int appId, String keying, String keyingSent, String complete) {
        List<String> values = new ArrayList<>();
        values.add(String.valueOf(appId));
        for (int i = 1; i < 26; i++) {
            values.add("v" + i);
        }
        return new ReportRow(appId, values, date(keying), null, date(keyingSent), date(complete), null);
    }

    private static ReportRow agentRow(int appId, String underwriter, String transfer,
            String keying, String keyingSent, String complete) {
        List<String> values = new ArrayList<>();
        values.add(String.valueOf(appId));
        values.add(underwriter);
        for (int i = 2; i < 27; i++) {
            values.add(i == 21 ? transfer : "v" + i);
        }
        return new ReportRow(appId, values, date(keying), null, date(keyingSent), date(complete),
                usDate(transfer));
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    /** The transfer date reaches the row as mm/dd/yyyy, which is what convert(char(10), .., 101) gives. */
    private static LocalDate usDate(String text) {
        if (text == null) {
            return null;
        }
        String[] p = text.split("/");
        return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    private static List<String> lines(String csv) {
        return List.of(csv.split("\r\n"));
    }

    @Nested
    @DisplayName("US-10.8.1 — the header")
    class Header {

        @Test
        @DisplayName("is the 33 columns GetHeaderRow writes, spelling and all")
        void general() {
            when(repository.general(FROM, TO)).thenReturn(List.of());
            ReportResult out = service.run(ReportKind.GENERAL, FROM, TO, "t.csv", TODAY);

            String header = lines(out.csv()).get(0);
            assertEquals(33, header.split(",").length);
            assertTrue(header.startsWith("App ID,App Type,App Source,App Rcvd Date"));
            assertTrue(header.contains("Effecitve Date"), "the Delphi's spelling is kept");
            assertTrue(header.endsWith("Total Working,Total,Range"));
            assertEquals(0, out.rowCount());
        }

        /*
         * A62 — CARTMAN writes the 33-column header over 41 columns of Agent data, so everything from
         * App Type rightwards is shifted and eight values trail off with no heading. This header names
         * what is written.
         */
        @Test
        @DisplayName("A62 — the Agent report names its own columns")
        void agent() {
            when(repository.agentDetail(FROM, TO)).thenReturn(List.of());
            ReportResult out = service.run(ReportKind.AGENT, FROM, TO, "t.csv", TODAY);

            List<String> header = List.of(lines(out.csv()).get(0).split(","));
            assertEquals(34, header.size());
            assertEquals("Underwriter", header.get(1), "not App Type: this column holds a person");
            assertEquals("TransferDt", header.get(21));
            assertTrue(header.stream().noneMatch(h -> h.startsWith("App_")),
                    "the internal date columns are used, not written");
        }
    }

    @Nested
    @DisplayName("US-10.7 — the band")
    class Band {

        @Test
        @DisplayName("covers each of the five ranges")
        void ranges() {
            assertEquals("Low (1-7)", TatReportService.band(1));
            assertEquals("Low (1-7)", TatReportService.band(7));
            assertEquals("Moderate (8-15)", TatReportService.band(8));
            assertEquals("Hi (16-30)", TatReportService.band(16));
            assertEquals("Elevated (31-60)", TatReportService.band(31));
            assertEquals("Outlier (>60)", TatReportService.band(61));
        }

        /*
         * A56 — the Delphi's Case covers 1..9999 and its CORange variable is never re-initialised, so
         * a total of 0 or over 9999 carries whichever band an earlier row last matched.
         */
        @Test
        @DisplayName("A56 — and does not leave 0 or a very large total carrying another row's band")
        void outsideTheCase() {
            assertEquals("", TatReportService.band(0));
            assertEquals("Outlier (>60)", TatReportService.band(10_000),
                    "above the Delphi's last band, which already says '>60'");
        }
    }

    @Nested
    @DisplayName("US-10.5.3 — the Agent report's Enroll row")
    class EnrollRow {

        @Test
        @DisplayName("is written once per application, before its transfers")
        void onePerApplication() {
            when(repository.agentDetail(FROM, TO)).thenReturn(List.of(
                    agentRow(4603, "SITUW", "03/01/2026", "2026-02-08", "2026-02-08", "2026-03-21"),
                    agentRow(4603, "JCardin", "03/10/2026", "2026-02-08", "2026-02-08", "2026-03-21"),
                    agentRow(4608, "SITUW", "04/01/2026", "2026-03-27", "2026-04-21", "2026-04-21")));
            when(repository.pends(anyList())).thenReturn(List.of());

            List<String> out = lines(service.run(ReportKind.AGENT, FROM, TO, "t.csv", TODAY).csv());

            // header, then Enroll + 2 transfers for 4603, then Enroll + 1 transfer for 4608
            assertEquals(6, out.size());
            assertEquals("Enroll", out.get(1).split(",")[1]);
            assertEquals("SITUW", out.get(2).split(",")[1]);
            assertEquals("JCardin", out.get(3).split(",")[1]);
            assertEquals("Enroll", out.get(4).split(",")[1]);
            assertEquals(5, service.run(ReportKind.AGENT, FROM, TO, "t.csv", TODAY).rowCount());
        }
    }

    @Nested
    @DisplayName("A55 — each row's figures are its own")
    class FiguresAreIndependent {

        /*
         * The Delphi's GetCarveOutTime exits before setting its result when an area has no window, and
         * the caller only copies the figures when the call returns true — so the row kept the previous
         * row's numbers. Here row two has no underwriting window and must report zeros, not row one's.
         */
        @Test
        @DisplayName("so a row with no underwriting window does not inherit the previous row's")
        void noInheritance() {
            when(repository.general(FROM, TO)).thenReturn(List.of(
                    generalRow(4603, "2026-02-08", "2026-02-08", "2026-03-21"),
                    generalRow(4596, "2026-02-23", null, null)));
            when(repository.pends(anyList())).thenReturn(List.of(
                    new Pend(4603, LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 17)),
                    new Pend(4603, LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 24)),
                    new Pend(4603, LocalDate.of(2026, 2, 24), LocalDate.of(2026, 3, 3)),
                    new Pend(4603, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16))));

            List<String> out = lines(service.run(ReportKind.GENERAL, FROM, TO, "t.csv", TODAY).csv());

            String[] first = out.get(1).split(",");
            String[] second = out.get(2).split(",");
            // Enroll carveout, UW carveout, Enroll working, UW working, Total working, Total, Range
            assertEquals("15", first[27], "4603's UW carve-out, as traced against the live rows");
            assertEquals("26", first[29]);
            assertEquals("0", second[27], "4596 has no keying-sent date, so no UW window");
            assertEquals("0", second[29], "and not 26, which is what CARTMAN carries over");
        }
    }

    @Nested
    @DisplayName("A57 — the CSV")
    class Csv {

        @Test
        @DisplayName("quotes a comma, quotes and doubles a quote, and quotes a line break")
        void quoting() {
            assertEquals("plain", CsvWriter.escape("plain"));
            assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
            assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
            assertEquals("\"two\nlines\"", CsvWriter.escape("two\nlines"));
            assertEquals("trimmed", CsvWriter.escape("  trimmed  "), "the Delphi trims; kept");
            assertEquals("", CsvWriter.escape(null));
        }

        @Test
        @DisplayName("and a comma in a value does not shift the columns")
        void commaKeepsAlignment() {
            List<String> values = new ArrayList<>();
            values.add("4603");
            for (int i = 1; i < 26; i++) {
                values.add(i == 13 ? "Stante, Dorothy" : "v" + i);
            }
            when(repository.general(FROM, TO)).thenReturn(List.of(
                    new ReportRow(4603, values, LocalDate.of(2026, 2, 8), null, null, null, null)));
            when(repository.pends(anyList())).thenReturn(List.of());

            String line = lines(service.run(ReportKind.GENERAL, FROM, TO, "t.csv", TODAY).csv()).get(1);
            assertTrue(line.contains("\"Stante, Dorothy\""));
            // 33 columns still, because the comma is inside quotes
            assertEquals(33, line.replaceAll("\"[^\"]*\"", "x").split(",").length);
        }
    }

    @Nested
    @DisplayName("A60 — the range is checked before anything runs")
    class Validation {

        @Test
        @DisplayName("both dates are required")
        void required() {
            assertEquals("Both From Date and To Date are required.",
                    assertThrows(ValidationException.class,
                            () -> service.run(ReportKind.GENERAL, null, TO, "t.csv", TODAY))
                            .getMessage());
            assertThrows(ValidationException.class,
                    () -> service.run(ReportKind.GENERAL, FROM, null, "t.csv", TODAY));
        }

        @Test
        @DisplayName("and From must not be after To")
        void ordered() {
            assertEquals("From Date must not be after To Date.",
                    assertThrows(ValidationException.class,
                            () -> service.run(ReportKind.GENERAL, TO, FROM, "t.csv", TODAY))
                            .getMessage());
        }
    }
}
