package com.anthem.cartman.appimages.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.anthem.cartman.appimages.dto.AppImageViews.PageContent;
import com.anthem.cartman.appimages.dto.AppImageViews.PageFilter;
import com.anthem.cartman.appimages.dto.AppImageViews.PageRow;
import com.anthem.cartman.appimages.dto.AppImageViews.PendingPage;
import com.anthem.cartman.appimages.dto.AppImageViews.StateLists;
import com.anthem.cartman.appimages.dto.AppImageViews.UploadResult;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.appimages.service.AppImageService;

/** Module 11 — Record Cleaner. */
@RestController
@RequestMapping("/api/app-images")
public class AppImageController {

    private final AppImageService service;

    public AppImageController(AppImageService service) {
        this.service = service;
    }

    /**
     * US-11.2.1 / US-11.5.1 — the two state lists, which are not the same list.
     *
     * <p>The filter offers fifteen bare codes; the dialog offers seventeen as "Name - CODE", including
     * two the filter cannot narrow to (A75). Both come back in one call because the screen needs both.
     */
    @GetMapping("/states")
    public StateLists states() {
        return new StateLists(AppImageService.STATES, AppImageService.UPLOAD_STATES);
    }

    /**
     * US-11.5.3 — what the dialog's grid will hold: the whole document and each of its pages, numbered.
     * Nothing is stored.
     */
    @PostMapping("/pages")
    public List<PendingPage> pages(@RequestParam("file") MultipartFile file) {
        return service.pagesOf(originalName(file), bytesOf(file));
    }

    /** US-11.1 / US-11.2 — the list, narrowed in the query rather than on screen. */
    @GetMapping
    public List<PageRow> list(@RequestParam(required = false) String state,
                              @RequestParam(required = false) String year,
                              @RequestParam(required = false) String version) {
        return service.list(new PageFilter(state, year, version));
    }

    /**
     * US-11.3 — show one page.
     *
     * <p>Sent inline as a PDF so the browser opens it in a tab. A69 — CARTMAN writes it to a fixed
     * `tmp1.pdf` in the user's own profile and never removes it, leaving an applicant's paperwork on the
     * disk of every machine it was viewed from.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable int id) {
        PageContent page = service.content(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // `inline`, so the browser shows it. setContentDispositionFormData would emit `form-data`,
        // which is for a multipart body and which no browser treats as something to display.
        headers.setContentDisposition(ContentDisposition.inline().filename(page.fileName()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(page.content());
    }

    /** US-11.4 — remove one page. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** US-11.5 / US-11.6 — take a scanned application and store the document and every page. */
    @PostMapping
    public UploadResult upload(@RequestParam String state,
                               @RequestParam String year,
                               @RequestParam String version,
                               @RequestParam(required = false) List<String> pageNumbers,
                               @RequestParam("file") MultipartFile file) {
        return service.upload(state, year, version, bytesOf(file), pageNumbers);
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("A file is required.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("That file could not be read: " + e.getMessage());
        }
    }

    private static String originalName(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }
}
