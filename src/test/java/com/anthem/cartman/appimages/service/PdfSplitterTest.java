package com.anthem.cartman.appimages.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anthem.cartman.common.exception.ValidationException;

/**
 * US-11.5.3 — splitting an uploaded application into pages.
 *
 * <p>Real PDFs throughout: a document is built, split, and each piece opened again, so the split is
 * proved rather than assumed. CARTMAN hands this to pdftk.exe and waits seven seconds (A71).
 */
class PdfSplitterTest {

    private final PdfSplitter splitter = new PdfSplitter();

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

    @Test
    @DisplayName("one document per page, and each one is a readable PDF")
    void splitsEveryPage() throws IOException {
        List<PdfSplitter.Page> pages = splitter.split(pdfOf(3));

        assertEquals(3, pages.size());
        for (PdfSplitter.Page page : pages) {
            try (PDDocument single = Loader.loadPDF(page.content())) {
                assertEquals(1, single.getNumberOfPages(), "each piece holds exactly one page");
            }
        }
    }

    @Test
    @DisplayName("US-11.5.4 — numbered as the Delphi numbers its burst files, padded to two digits")
    void numbers() throws IOException {
        assertEquals(List.of("01", "02", "03"),
                splitter.split(pdfOf(3)).stream().map(PdfSplitter.Page::number).toList());
    }

    @Test
    @DisplayName("a single-page application splits to one")
    void onePage() throws IOException {
        assertEquals(1, splitter.split(pdfOf(1)).size());
    }

    /*
     * The Delphi's rule takes the last two characters of the filename's tail, so page 100 becomes "00".
     * The number is kept whole here: the column holds eight characters, and a wrong page number is worse
     * than a long one.
     */
    @Test
    @DisplayName("and past ninety-nine the number stays whole rather than wrapping to 00")
    void beyondNinetyNine() {
        assertEquals("01", PdfSplitter.numberFor(1));
        assertEquals("99", PdfSplitter.numberFor(99));
        assertEquals("100", PdfSplitter.numberFor(100));
    }

    @Test
    @DisplayName("something that is not a PDF is refused, with the reason")
    void notAPdf() {
        assertTrue(assertThrows(ValidationException.class,
                () -> splitter.split("this is not a pdf".getBytes(StandardCharsets.UTF_8)))
                .getMessage().startsWith("That file could not be read as a PDF"));
    }

    @Test
    @DisplayName("and so is nothing at all")
    void empty() {
        assertEquals("The file is empty.",
                assertThrows(ValidationException.class, () -> splitter.split(new byte[0])).getMessage());
        assertEquals("The file is empty.",
                assertThrows(ValidationException.class, () -> splitter.split(null)).getMessage());
    }
}
