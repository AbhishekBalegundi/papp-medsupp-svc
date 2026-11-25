package com.anthem.cartman.tatreports.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.tatreports.dto.TatViews.ReportKind;
import com.anthem.cartman.tatreports.dto.TatViews.ReportResult;
import com.anthem.cartman.tatreports.service.TatReportService;

/** Module 10 — the endpoint: which report, what name, and how the file comes back. */
@ExtendWith(MockitoExtension.class)
class TatReportControllerTest {

    @Mock
    private TatReportService service;

    private TatReportController controller;

    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @BeforeEach
    void setUp() {
        controller = new TatReportController(service);
    }

    private void serviceReturns(String csv, int rows) {
        when(service.run(any(), any(), any(), any(), any()))
                .thenReturn(new ReportResult("TAT - 202x.csv", rows, csv));
    }

    @Test
    @DisplayName("general asks for the General report")
    void general() {
        serviceReturns("App ID\r\n", 9);

        controller.report("general", FROM, TO, "TAT - 202x.csv");

        ArgumentCaptor<ReportKind> kind = ArgumentCaptor.forClass(ReportKind.class);
        org.mockito.Mockito.verify(service)
                .run(kind.capture(), eq(FROM), eq(TO), eq("TAT - 202x.csv"), any());
        assertEquals(ReportKind.GENERAL, kind.getValue());
    }

    @Test
    @DisplayName("agent asks for the Agent report")
    void agent() {
        serviceReturns("App ID\r\n", 4);

        controller.report("agent", FROM, TO, "a.csv");

        org.mockito.Mockito.verify(service).run(eq(ReportKind.AGENT), any(), any(), any(), any());
    }

    @Test
    @DisplayName("anything else is refused")
    void unknown() {
        assertEquals("Unknown report: monthly",
                assertThrows(ValidationException.class,
                        () -> controller.report("monthly", FROM, TO, "a.csv")).getMessage());
    }

    @Test
    @DisplayName("the file comes back as a CSV attachment, with the row count on a header")
    void response() {
        serviceReturns("App ID,App Type\r\n", 9);

        ResponseEntity<byte[]> out = controller.report("general", FROM, TO, "TAT - 202x.csv");

        assertEquals(200, out.getStatusCode().value());
        assertEquals("App ID,App Type\r\n", new String(out.getBody(), StandardCharsets.UTF_8));
        assertEquals("text/csv;charset=UTF-8", out.getHeaders().getContentType().toString());
        assertEquals("9", out.getHeaders().getFirst("X-Row-Count"));
        assertTrue(out.getHeaders().getFirst("Content-Disposition").contains("TAT - 202x.csv"));
        // Without this the browser cannot read X-Row-Count off a cross-origin response.
        assertTrue(out.getHeaders().getFirst("Access-Control-Expose-Headers").contains("X-Row-Count"));
    }

    @Test
    @DisplayName("US-10.2 — a name with no extension gets one")
    void addsExtension() {
        serviceReturns("x", 1);

        controller.report("general", FROM, TO, "March");

        org.mockito.Mockito.verify(service).run(any(), any(), any(), eq("March.csv"), any());
    }

    @Test
    @DisplayName("and one that has it, in any case, keeps just the one")
    void keepsExtension() {
        serviceReturns("x", 1);

        controller.report("general", FROM, TO, "March.CSV");

        org.mockito.Mockito.verify(service).run(any(), any(), any(), eq("March.CSV"), any());
    }

    @Test
    @DisplayName("a blank name is refused")
    void blankName() {
        assertEquals("A file name is required.",
                assertThrows(ValidationException.class,
                        () -> controller.report("general", FROM, TO, "   ")).getMessage());
    }

    @Test
    @DisplayName("and a name that could climb out of its folder is refused, not trimmed")
    void pathInName() {
        for (String bad : new String[] { "../secrets.csv", "sub/report.csv", "sub\\report.csv" }) {
            assertEquals("The file name must not contain a path.",
                    assertThrows(ValidationException.class,
                            () -> controller.report("general", FROM, TO, bad)).getMessage(), bad);
        }
    }
}
