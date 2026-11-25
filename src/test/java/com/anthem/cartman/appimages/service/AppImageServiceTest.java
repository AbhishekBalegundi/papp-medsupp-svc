package com.anthem.cartman.appimages.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.appimages.dto.AppImageViews.PageContent;
import com.anthem.cartman.appimages.dto.AppImageViews.PageFilter;
import com.anthem.cartman.appimages.dto.AppImageViews.PageRow;
import com.anthem.cartman.appimages.dto.AppImageViews.UploadResult;
import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;

/** Module 11 — Record Cleaner (US-11.1 to US-11.6). */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class AppImageServiceTest {

    @Autowired
    private AppImageService service;

    @Autowired
    private JdbcTemplate jdbc;

    private static final PageFilter ALL = new PageFilter(null, null, null);

    private static byte[] pdfOf(int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private int rows() {
        Integer n = jdbc.queryForObject("select count(*) from TM_App_Images", Integer.class);
        return n == null ? 0 : n;
    }

    @Nested
    @DisplayName("US-11.1 — the list")
    class Listing {

        /*
         * US-11.1.2 — ordered by the *length* of the page number before its value. That is what makes 2
         * come before 10 rather than after it, and it also puts "Full" last, because four characters sort
         * after two. So the whole document appears at the end of its own set of pages, not the start.
         */
        @Test
        @DisplayName("US-11.1.2 — page 2 comes before page 10, and Full sorts after both")
        void naturalSort() {
            List<PageRow> va = service.list(new PageFilter("VA", "2026", "v1"));

            assertEquals(List.of("02", "10", "Full"),
                    va.stream().map(PageRow::pageNumber).toList());
        }

        @Test
        @DisplayName("ordered by state, then year, then version")
        void ordering() {
            List<PageRow> all = service.list(ALL);

            assertEquals("CA", all.get(0).state(), "CA before VA");
            assertEquals(6, all.size());
        }

        @Test
        @DisplayName("US-11.1.3 — a row says whether a document is there, and never carries one")
        void saysWhetherStored() {
            PageRow row = service.list(new PageFilter("CA", "2025", "v1")).get(0);

            assertTrue(row.hasDocument());
        }

        @Test
        @DisplayName("and a row with nothing stored against it says so rather than failing")
        void missingDocument() {
            PageRow row = service.list(new PageFilter("CA", "2025", "v1")).get(1);

            assertEquals("02", row.pageNumber());
            assertTrue(!row.hasDocument(), "A67 leaves rows like this behind");
        }
    }

    @Nested
    @DisplayName("US-11.2 — the filter, applied in the query (A73)")
    class Filtering {

        @Test
        @DisplayName("by state")
        void byState() {
            assertEquals(2, service.list(new PageFilter("CA", null, null)).size());
            assertEquals(4, service.list(new PageFilter("VA", null, null)).size());
        }

        @Test
        @DisplayName("by version, across states")
        void byVersion() {
            assertEquals(1, service.list(new PageFilter(null, null, "v2")).size());
        }

        @Test
        @DisplayName("all three together")
        void allThree() {
            assertEquals(3, service.list(new PageFilter("VA", "2026", "v1")).size());
        }

        @Test
        @DisplayName("an empty box matches everything, as the Delphi's empty filter does")
        void blanksMatchEverything() {
            assertEquals(6, service.list(new PageFilter("", "  ", null)).size());
        }

        @Test
        @DisplayName("and a filter matching nothing is an empty list, not an error")
        void noMatch() {
            assertEquals(List.of(), service.list(new PageFilter("TX", null, null)));
        }
    }

    @Nested
    @DisplayName("US-11.3 — viewing a page")
    class Viewing {

        @Test
        @DisplayName("names the file for what it holds, rather than a fixed tmp1.pdf (A69)")
        void names() {
            int id = service.list(new PageFilter("VA", "2026", "v2")).get(0).id();

            PageContent page = service.content(id);

            assertEquals("VA_2026_v2_01.pdf", page.fileName());
            assertEquals(4, page.content().length);
        }

        @Test
        @DisplayName("a page that has gone says so")
        void gone() {
            assertEquals("That application page no longer exists.",
                    assertThrows(NotFoundException.class, () -> service.content(999999)).getMessage());
        }

        @Test
        @DisplayName("and one with no document behind it says that instead")
        void noDocument() {
            int id = service.list(new PageFilter("CA", "2025", "v1")).get(1).id();

            assertEquals("That application page has no document stored against it.",
                    assertThrows(NotFoundException.class, () -> service.content(id)).getMessage());
        }
    }

    @Nested
    @DisplayName("US-11.4 — deleting a page")
    class Deleting {

        @Test
        @DisplayName("removes that page and leaves the rest")
        void removesOne() {
            int before = rows();
            int id = service.list(new PageFilter("VA", "2026", "v2")).get(0).id();

            service.delete(id);

            assertEquals(before - 1, rows());
            assertEquals(0, service.list(new PageFilter("VA", "2026", "v2")).size());
        }

        /*
         * A68 — the Delphi wraps both delete routes in an empty `except`, so a delete that removed
         * nothing looked exactly like one that worked.
         */
        @Test
        @DisplayName("A68 — and a page that is already gone is reported, not swallowed")
        void alreadyGone() {
            assertEquals("That application page no longer exists.",
                    assertThrows(NotFoundException.class, () -> service.delete(999999)).getMessage());
            assertEquals(6, rows(), "nothing was touched");
        }
    }

    @Nested
    @DisplayName("US-11.5.3 — listing the pages before storing them")
    class Previewing {

        @Test
        @DisplayName("the whole document and each page, and nothing is stored")
        void lists() throws IOException {
            int before = rows();

            var pending = service.pagesOf("application.pdf", pdfOf(3));

            assertEquals(List.of("Full", "01", "02", "03"),
                    pending.stream().map(p -> p.pageNumber()).toList());
            assertEquals("application.pdf", pending.get(0).fileName(), "the document keeps its name");
            assertEquals("pg_01.pdf", pending.get(1).fileName(), "as pdftk's burst names them");
            assertEquals(before, rows(), "nothing was written");
        }

        @Test
        @DisplayName("and something that is not a PDF is refused")
        void notAPdf() {
            assertThrows(ValidationException.class, () -> service.pagesOf("x.pdf", "no".getBytes()));
            assertThrows(ValidationException.class, () -> service.pagesOf("x.pdf", new byte[0]));
        }
    }

    @Nested
    @DisplayName("US-11.6 — storing an upload")
    class Uploading {

        @Test
        @DisplayName("stores the whole document and one row per page, in one go")
        void storesAll() throws IOException {
            UploadResult result = service.upload("TX", "2026", "v9", pdfOf(3), null);

            assertEquals(4, result.stored(), "the document, then three pages");
            assertEquals(List.of("Full", "01", "02", "03"),
                    result.pages().stream().map(PageRow::pageNumber).toList());
            assertEquals(10, rows());
        }

        /* A65 — the Delphi writes into Max(APG_ID), so two uploads at once exchange documents. */
        @Test
        @DisplayName("A65 — every row carries its own document, keyed to itself")
        void ownDocument() throws IOException {
            UploadResult result = service.upload("TX", "2026", "v9", pdfOf(2), null);

            for (PageRow row : result.pages()) {
                assertTrue(row.hasDocument(), "row " + row.id() + " has a document");
            }
            assertEquals(3, result.pages().stream().map(PageRow::id).distinct().count());
        }

        /* A66 / A67 — the Delphi carries on past a failure and leaves rows with nothing behind them. */
        @Test
        @DisplayName("A67 — a file that cannot be read stores nothing at all")
        void nothingHalfStored() {
            int before = rows();

            assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", "v9", "not a pdf".getBytes(), null));

            assertEquals(before, rows(), "not even the Full row");
        }

        @Test
        @DisplayName("the three identifying fields are required")
        void required() throws IOException {
            byte[] pdf = pdfOf(1);
            assertEquals("A State is required.", assertThrows(ValidationException.class,
                    () -> service.upload("  ", "2026", "v1", pdf, null)).getMessage());
            assertEquals("A Year is required.", assertThrows(ValidationException.class,
                    () -> service.upload("TX", "", "v1", pdf, null)).getMessage());
            assertEquals("A Version is required.", assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", null, pdf, null)).getMessage());
            assertEquals("A file is required.", assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", "v1", new byte[0], null)).getMessage());
        }

        /* A72 — the Delphi cuts anything longer than two characters to its last two. */
        @Test
        @DisplayName("A72 — a state must be one of the seventeen the dialog offers")
        void stateMustBeReal() throws IOException {
            byte[] pdf = pdfOf(1);

            assertTrue(assertThrows(ValidationException.class,
                    () -> service.upload("Virginia", "2026", "v1", pdf, null))
                    .getMessage().endsWith("Virginia"));
            assertEquals(6, rows(), "and nothing was stored under 'ia'");
        }

        @Test
        @DisplayName("and a code is accepted in any case")
        void anyCase() throws IOException {
            assertEquals("TX", service.upload("tx", "2026", "v1", pdfOf(1), null)
                    .pages().get(0).state());
        }
    }

    @Nested
    @DisplayName("US-11.6 — the page numbers the grid holds")
    class EditedNumbers {

        /*
         * The grid carries goEditing and says "* Edit grid by clicking.", and the Delphi stores the
         * cell's value rather than recomputing it — so a number corrected by hand is what is stored.
         */
        @Test
        @DisplayName("are what gets stored, not a recomputed number")
        void honoured() throws IOException {
            UploadResult result = service.upload("TX", "2026", "v9", pdfOf(2),
                    List.of("Full", "cover", "sigs"));

            assertEquals(List.of("Full", "cover", "sigs"),
                    result.pages().stream().map(PageRow::pageNumber).toList());
        }

        @Test
        @DisplayName("and are derived when the grid sends none")
        void derived() throws IOException {
            UploadResult result = service.upload("TX", "2026", "v9", pdfOf(2), List.of());

            assertEquals(List.of("Full", "01", "02"),
                    result.pages().stream().map(PageRow::pageNumber).toList());
        }

        @Test
        @DisplayName("a list that does not match the document is refused")
        void wrongCount() throws IOException {
            byte[] pdf = pdfOf(3);

            assertTrue(assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", "v9", pdf, List.of("Full", "01")))
                    .getMessage().contains("2 given for 4 pages"));
        }

        @Test
        @DisplayName("a blank number is refused, and stores nothing")
        void blankNumber() throws IOException {
            byte[] pdf = pdfOf(1);
            int before = rows();

            assertEquals("Every page needs a number.", assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", "v9", pdf, List.of("Full", "  "))).getMessage());
            assertEquals(before, rows());
        }

        @Test
        @DisplayName("and one longer than the column is refused rather than silently cut")
        void tooLong() throws IOException {
            byte[] pdf = pdfOf(1);

            assertTrue(assertThrows(ValidationException.class,
                    () -> service.upload("TX", "2026", "v9", pdf, List.of("Full", "123456789")))
                    .getMessage().startsWith("A page number may be at most 8 characters"));
        }
    }

    @Nested
    @DisplayName("A75 — the two state lists are not the same list")
    class StateListsDiffer {

        @Test
        @DisplayName("seventeen for the dialog, fifteen for the filter")
        void differ() {
            assertEquals(15, AppImageService.STATES.size());
            assertEquals(17, AppImageService.UPLOAD_STATES.size());
        }

        @Test
        @DisplayName("and a state only the dialog offers can be stored, then never filtered to")
        void louisiana() throws IOException {
            assertEquals("LA", service.upload("LA", "2026", "v1", pdfOf(1), null)
                    .pages().get(0).state());
            assertTrue(!AppImageService.STATES.contains("LA"),
                    "so the filter's picker cannot narrow to it");
        }
    }
}
