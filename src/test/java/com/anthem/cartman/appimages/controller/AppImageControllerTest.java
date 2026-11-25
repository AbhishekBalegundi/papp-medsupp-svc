package com.anthem.cartman.appimages.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import com.anthem.cartman.appimages.dto.AppImageViews.PageContent;
import com.anthem.cartman.appimages.dto.AppImageViews.PageFilter;
import com.anthem.cartman.appimages.dto.AppImageViews.PageRow;
import com.anthem.cartman.appimages.dto.AppImageViews.PendingPage;
import com.anthem.cartman.appimages.dto.AppImageViews.StateLists;
import com.anthem.cartman.appimages.dto.AppImageViews.StateOption;
import com.anthem.cartman.appimages.dto.AppImageViews.UploadResult;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.appimages.service.AppImageService;

/** Module 11 — the endpoint. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppImageControllerTest {

    @Mock
    private AppImageService service;

    @InjectMocks
    private AppImageController controller;

    private static final PageRow ROW = new PageRow(1, "VA", "2026", "v1", "01", true);

    /*
     * A75 — the two lists are not the same list. The filter offers fifteen bare codes; the dialog offers
     * seventeen as "Name - CODE", two of which can be stored and then never filtered to.
     */
    @Test
    @DisplayName("US-11.2.1 / US-11.5.1 — two state lists, and they differ")
    void states() {
        StateLists lists = controller.states();

        assertEquals(15, lists.filter().size());
        assertEquals(17, lists.upload().size());
        assertEquals("Arizona - AZ", lists.upload().get(0).label());
        assertEquals("AZ", lists.upload().get(0).code());

        List<String> codes = lists.upload().stream().map(StateOption::code).toList();
        assertTrue(codes.contains("LA") && codes.contains("NH"), "Louisiana and New Hampshire");
        assertTrue(!lists.filter().contains("LA") && !lists.filter().contains("NH"),
                "and the filter cannot narrow to either");
    }

    @Test
    @DisplayName("US-11.5.3 — the pages of a document can be listed without storing anything")
    void pages() {
        MultipartFile file = new MockMultipartFile("file", "app.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        when(service.pagesOf(eq("app.pdf"), any()))
                .thenReturn(List.of(new PendingPage("Full", "app.pdf")));

        assertEquals(1, controller.pages(file).size());
        verify(service).pagesOf(eq("app.pdf"), any());
    }

    @Test
    @DisplayName("US-11.6 — an upload passes the file's bytes, the three fields and the page numbers")
    void upload() {
        MultipartFile file = new MockMultipartFile("file", "app.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        when(service.upload(any(), any(), any(), any(), any()))
                .thenReturn(new UploadResult(2, List.of(ROW)));

        controller.upload("VA", "2026", "v1", List.of("Full", "cover"), file);

        verify(service).upload(eq("VA"), eq("2026"), eq("v1"), any(), eq(List.of("Full", "cover")));
    }

    @Test
    @DisplayName("and no file at all is refused before the service is asked")
    void noFile() {
        MultipartFile empty = new MockMultipartFile("file", new byte[0]);

        assertEquals("A file is required.", assertThrows(ValidationException.class,
                () -> controller.upload("VA", "2026", "v1", null, empty)).getMessage());
        assertEquals("A file is required.", assertThrows(ValidationException.class,
                () -> controller.pages(empty)).getMessage());
    }

    @Test
    @DisplayName("the list passes the three filter values through")
    void list() {
        when(service.list(any())).thenReturn(List.of(ROW));

        assertEquals(List.of(ROW), controller.list("VA", "2026", "v1"));

        verify(service).list(new PageFilter("VA", "2026", "v1"));
    }

    @Test
    @DisplayName("and an absent filter is absent, not empty text")
    void listUnfiltered() {
        when(service.list(any())).thenReturn(List.of(ROW));

        controller.list(null, null, null);

        verify(service).list(new PageFilter(null, null, null));
    }

    /*
     * A69 — CARTMAN writes the page to a fixed tmp1.pdf in the user's own profile and never removes it.
     * This streams it, names it for what it holds, and tells the browser not to keep it.
     */
    @Test
    @DisplayName("US-11.3 — a page comes back as a PDF to show, named and not cached")
    void content() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        when(service.content(7)).thenReturn(new PageContent("VA_2026_v1_01.pdf", pdf));

        ResponseEntity<byte[]> out = controller.content(7);

        assertEquals(200, out.getStatusCode().value());
        assertEquals("application/pdf", out.getHeaders().getContentType().toString());
        assertTrue(out.getHeaders().getFirst("Content-Disposition").contains("VA_2026_v1_01.pdf"));
        assertTrue(out.getHeaders().getFirst("Content-Disposition").startsWith("inline"),
                "shown in the browser rather than written to the disk");
        assertEquals("no-store", out.getHeaders().getCacheControl());
        assertEquals(pdf, out.getBody());
    }

    @Test
    @DisplayName("US-11.4 — a delete answers 204 with no body")
    void delete() {
        ResponseEntity<Void> out = controller.delete(7);

        assertEquals(204, out.getStatusCode().value());
        assertNull(out.getBody());
        verify(service).delete(7);
    }
}
