package com.anthem.cartman.tatreports.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.tatreports.dto.TatViews.ReportKind;
import com.anthem.cartman.tatreports.dto.TatViews.ReportResult;
import com.anthem.cartman.tatreports.service.TatReportService;

/**
 * Module 10 — TAT Reports.
 *
 * <p>CARTMAN writes the file to a folder the user picks, defaulting to a UNC share. A browser cannot
 * write to an arbitrary path, so the report is handed back as a download under the name the user typed
 * — see "The file, and where it goes" in the requirements. `X-Row-Count` carries the row count so the
 * screen can say what was produced, which is what CARTMAN's progress bar and *File Complete* label do.
 */
@RestController
@RequestMapping("/api/tat-reports")
public class TatReportController {

    private final TatReportService service;

    public TatReportController(TatReportService service) {
        this.service = service;
    }

    @GetMapping("/{kind}")
    public ResponseEntity<byte[]> report(
            @PathVariable String kind,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "TAT - 202x.csv") String name) {

        ReportKind which = switch (kind) {
            case "general" -> ReportKind.GENERAL;
            case "agent" -> ReportKind.AGENT;
            default -> throw new ValidationException("Unknown report: " + kind);
        };

        ReportResult result = service.run(which, from, to, fileName(name), LocalDate.now());
        byte[] body = result.csv().getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        // `attachment`, not `form-data` — the latter is for a multipart body, and the browser's save
        // dialog takes its name from a well-formed disposition header.
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(result.fileName()).build());
        headers.set("X-Row-Count", String.valueOf(result.rowCount()));
        headers.set("Access-Control-Expose-Headers", "X-Row-Count");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * US-10.2 — the name always ends in exactly one `.csv`, which the screen also enforces as it is
     * typed. Anything that could leave the folder is refused rather than trimmed: CARTMAN pastes the
     * name straight onto the path.
     */
    private static String fileName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new ValidationException("A file name is required.");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new ValidationException("The file name must not contain a path.");
        }
        return name.toLowerCase().endsWith(".csv") ? name : name + ".csv";
    }
}
