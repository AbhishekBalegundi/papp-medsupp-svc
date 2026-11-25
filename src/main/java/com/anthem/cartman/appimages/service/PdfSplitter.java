package com.anthem.cartman.appimages.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import com.anthem.cartman.common.exception.ValidationException;

/**
 * US-11.5.3 — split an uploaded application into one document per page.
 *
 * <p><b>A71.</b> CARTMAN copies the PDF into its own install directory, deletes any {@code pg_*.pdf}
 * lying there, runs {@code pdftk.exe <State>_Full.pdf burst} without waiting for it, sleeps for a fixed
 * seven seconds and then reads whatever files it finds. If pdftk is absent, slow or fails, the wait
 * passes anyway and the previous run's leftovers are picked up instead. Nothing touches the disk here,
 * nothing is left behind, and a document that cannot be read is reported rather than half-processed.
 */
@Component
public class PdfSplitter {

    /** The whole document's own row carries this in place of a number (US-11.5.3). */
    public static final String FULL = "Full";

    /** One page of a split document, numbered as the Delphi numbers its burst files. */
    public record Page(String number, byte[] content) {}

    /**
     * Split a PDF.
     *
     * @return one document per page, numbered {@code 01}, {@code 02}, … as {@code GetPageNumFromFile}
     *         numbers them: the page's position, padded to two digits
     * @throws ValidationException when the bytes are not a PDF this can read
     */
    public List<Page> split(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ValidationException("The file is empty.");
        }
        List<Page> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int count = document.getNumberOfPages();
            if (count == 0) {
                throw new ValidationException("That PDF has no pages.");
            }
            for (int i = 0; i < count; i++) {
                try (PDDocument single = new PDDocument()) {
                    single.addPage(document.getPage(i));
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    single.save(out);
                    pages.add(new Page(numberFor(i + 1), out.toByteArray()));
                }
            }
        } catch (IOException e) {
            // Not a PDF, or one PDFBox will not open. The Delphi would have carried on regardless.
            throw new ValidationException("That file could not be read as a PDF: " + e.getMessage());
        }
        return pages;
    }

    /**
     * US-11.5.4 — the page number, as the Delphi derives it: the position padded to two digits.
     *
     * <p>Beyond ninety-nine pages the Delphi's own rule takes the last two characters, so page 100
     * becomes {@code 00}. The number is kept whole here instead: the column holds eight characters, and
     * a wrong page number is worse than a long one.
     */
    static String numberFor(int position) {
        return position < 100 ? String.format("%02d", position) : String.valueOf(position);
    }
}
