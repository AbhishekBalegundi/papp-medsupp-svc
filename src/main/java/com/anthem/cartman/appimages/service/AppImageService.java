package com.anthem.cartman.appimages.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.appimages.dto.AppImageViews.PageContent;
import com.anthem.cartman.appimages.dto.AppImageViews.PageFilter;
import com.anthem.cartman.appimages.dto.AppImageViews.PageRow;
import com.anthem.cartman.appimages.dto.AppImageViews.PendingPage;
import com.anthem.cartman.appimages.dto.AppImageViews.StateOption;
import com.anthem.cartman.appimages.dto.AppImageViews.UploadResult;
import com.anthem.cartman.appimages.entity.AppImage;
import com.anthem.cartman.appimages.repository.AppImageRepository;
import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;

/**
 * Module 11 — Record Cleaner: the stored pages of scanned paper applications.
 *
 * <p>Corrections to the legacy screen, each recorded in the requirements:
 *
 * <ul>
 *   <li><b>A65 / A66 / A67</b> — an upload is one transaction, and each page's row carries its own
 *       document. CARTMAN inserts a row with no document and then writes the document into whichever row
 *       holds the highest key, continuing past a failed insert and abandoning the run part way through a
 *       missing file. Those three between them let two users exchange documents, let a failed page
 *       overwrite a good one, and left rows with nothing behind them.</li>
 *   <li><b>A68</b> — a delete that removes nothing says so, rather than being swallowed.</li>
 *   <li><b>A70</b> — nothing is concatenated into a statement.</li>
 *   <li><b>A72</b> — a state must be one of the fifteen codes; text is not truncated to its last two
 *       characters.</li>
 *   <li><b>A73</b> — the filter is in the query.</li>
 * </ul>
 */
@Service
public class AppImageService {

    private static final Logger AUDIT = LoggerFactory.getLogger("cartman.audit");

    /** US-11.2.1 — the fifteen codes the *filter* offers, in `frmAppUpload.dfm`'s order. */
    public static final List<String> STATES = List.of(
            "AZ", "CA", "CO", "CT", "GA", "IN", "KY", "ME", "MO", "NV", "NY", "OH", "TX", "VA", "WI");

    /**
     * US-11.5.1 — the seventeen the dialog offers, as `frmAddAppPage.dfm` writes them.
     *
     * <p>A75 — this is not the same list as the filter's. Louisiana and New Hampshire can be stored and
     * then never filtered to. Kept as the forms have them, and recorded.
     */
    public static final List<StateOption> UPLOAD_STATES = List.of(
            new StateOption("AZ", "Arizona - AZ"),
            new StateOption("CA", "California - CA"),
            new StateOption("CO", "Colorado - CO"),
            new StateOption("CT", "Connecticut - CT"),
            new StateOption("GA", "Georgia - GA"),
            new StateOption("IN", "Indiana - IN"),
            new StateOption("KY", "Kentucky - KY"),
            new StateOption("LA", "Louisiana - LA"),
            new StateOption("ME", "Maine - ME"),
            new StateOption("MO", "Missouri - MO"),
            new StateOption("NV", "Nevada - NV"),
            new StateOption("NH", "New Hampshire - NH"),
            new StateOption("NY", "New York - NY"),
            new StateOption("OH", "Ohio - OH"),
            new StateOption("TX", "Texas - TX"),
            new StateOption("VA", "Virginia - VA"),
            new StateOption("WI", "Wisconsin - WI"));

    private static final List<String> UPLOAD_CODES =
            UPLOAD_STATES.stream().map(StateOption::code).toList();

    private final AppImageRepository repository;
    private final PdfSplitter splitter;

    public AppImageService(AppImageRepository repository, PdfSplitter splitter) {
        this.repository = repository;
        this.splitter = splitter;
    }

    /** US-11.1 / US-11.2 — the list, narrowed by whatever the three boxes hold. */
    @Transactional(readOnly = true)
    public List<PageRow> list(PageFilter filter) {
        return repository.list(blankToNull(filter.state()), blankToNull(filter.year()),
                blankToNull(filter.version()));
    }

    /** US-11.3 — one stored document. */
    @Transactional(readOnly = true)
    public PageContent content(int id) {
        AppImage image = repository.findWithPage(id)
                .orElseThrow(() -> new NotFoundException("That application page no longer exists."));
        if (image.getPage() == null || image.getPage().length == 0) {
            // A67 leaves rows like this behind. They list, and they have nothing to show.
            throw new NotFoundException("That application page has no document stored against it.");
        }
        return new PageContent(fileNameFor(image), image.getPage());
    }

    /** US-11.4 — remove one page. */
    @Transactional
    public void delete(int id) {
        // A68 — the Delphi swallows every failure here, so a delete that removed nothing looked the same
        // as one that worked.
        if (!repository.existsById(id)) {
            throw new NotFoundException("That application page no longer exists.");
        }
        repository.deleteById(id);
        AUDIT.info("application page {} deleted", id);
    }

    /**
     * US-11.5.3 — what the dialog's grid will hold, without storing anything.
     *
     * <p>The Delphi fills its grid from the files pdftk left on the disk. Here the document is split and
     * the pages are listed, and nothing is written until OK.
     */
    public List<PendingPage> pagesOf(String fileName, byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ValidationException("A file is required.");
        }
        String name = fileName == null || fileName.isBlank() ? "application.pdf" : fileName.trim();
        List<PendingPage> pending = new ArrayList<>();
        pending.add(new PendingPage(PdfSplitter.FULL, name));
        for (PdfSplitter.Page page : splitter.split(pdf)) {
            pending.add(new PendingPage(page.number(), "pg_" + page.number() + ".pdf"));
        }
        return pending;
    }

    /**
     * US-11.5 / US-11.6 — store an uploaded application: the whole document, then one row per page.
     *
     * <p>One transaction. Either every row and its document are stored, or none is.
     *
     * @param pageNumbers the numbers the dialog's grid holds, which may have been edited by hand — the
     *                    grid carries {@code goEditing} and says so, and the Delphi stores the cell's
     *                    value rather than recomputing it. Null or empty means derive them.
     */
    @Transactional
    public UploadResult upload(String state, String year, String version, byte[] pdf,
            List<String> pageNumbers) {
        String code = requireState(state);
        String theYear = require(year, "A Year is required.");
        String theVersion = require(version, "A Version is required.");
        if (pdf == null || pdf.length == 0) {
            throw new ValidationException("A file is required.");
        }

        List<byte[]> documents = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        documents.add(pdf);
        numbers.add(PdfSplitter.FULL);
        for (PdfSplitter.Page page : splitter.split(pdf)) {
            documents.add(page.content());
            numbers.add(page.number());
        }

        // The grid's own values win where they were sent, because the grid can be edited by hand.
        if (pageNumbers != null && !pageNumbers.isEmpty()) {
            if (pageNumbers.size() != numbers.size()) {
                throw new ValidationException("That does not match the pages of the document: "
                        + pageNumbers.size() + " given for " + numbers.size() + " pages.");
            }
            for (int i = 0; i < pageNumbers.size(); i++) {
                String given = pageNumbers.get(i) == null ? "" : pageNumbers.get(i).trim();
                if (given.isEmpty()) {
                    throw new ValidationException("Every page needs a number.");
                }
                if (given.length() > 8) {
                    throw new ValidationException("A page number may be at most 8 characters: " + given);
                }
                numbers.set(i, given);
            }
        }

        List<AppImage> stored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            AppImage image = new AppImage();
            image.setState(code);
            image.setYear(theYear);
            image.setVersion(theVersion);
            image.setPageNumber(numbers.get(i));
            image.setPage(documents.get(i));
            stored.add(image);
        }

        List<AppImage> saved = repository.saveAll(stored);
        AUDIT.info("application pages stored: {} {} {} — {} rows", code, theYear, theVersion,
                saved.size());
        List<PageRow> rows = saved.stream()
                .map(i -> new PageRow(i.getId(), i.getState(), i.getYear(), i.getVersion(),
                        i.getPageNumber(), i.getPage() != null && i.getPage().length > 0))
                .toList();
        return new UploadResult(rows.size(), rows);
    }

    /**
     * A72 — the state is one of the seventeen the dialog offers, or it is refused.
     *
     * <p>The Delphi cuts anything longer than two characters down to its <em>last two</em>, which is how
     * it takes the code off "Louisiana - LA" — and which turns a typed "Virginia" into "ia".
     */
    private static String requireState(String state) {
        String trimmed = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            throw new ValidationException("A State is required.");
        }
        if (!UPLOAD_CODES.contains(trimmed)) {
            throw new ValidationException("That is not a state this application holds pages for: "
                    + state.trim());
        }
        return trimmed;
    }

    private static String require(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException(message);
        }
        return trimmed;
    }

    /** A69 — the file is named for what it is, rather than written to a fixed `tmp1.pdf` on disk. */
    private static String fileNameFor(AppImage image) {
        return String.join("_", trim(image.getState()), trim(image.getYear()),
                trim(image.getVersion()), trim(image.getPageNumber())) + ".pdf";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }


    private static String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
