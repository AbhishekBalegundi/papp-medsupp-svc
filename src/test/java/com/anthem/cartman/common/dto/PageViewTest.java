package com.anthem.cartman.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.anthem.cartman.common.dto.PageView;

/** The page arithmetic every paged endpoint depends on. */
class PageViewTest {

    private static final List<String> HUNDRED =
            IntStream.range(0, 100).mapToObj(i -> "row-" + i).toList();

    @Test
    @DisplayName("the first page carries the size asked for and knows there is more")
    void firstPage() {
        PageView<String> page = PageView.slice(HUNDRED, 0, 20);

        assertEquals(20, page.items().size());
        assertEquals("row-0", page.items().get(0));
        assertEquals(100, page.totalItems());
        assertEquals(5, page.totalPages());
        assertFalse(page.hasPrevious());
        assertTrue(page.hasNext());
    }

    @Test
    @DisplayName("the last page is short and knows it is last")
    void lastPage() {
        // 100 rows at 30 a page is four pages: 0-29, 30-59, 60-89, then a short 90-99.
        PageView<String> page = PageView.slice(HUNDRED, 3, 30);

        assertEquals(10, page.items().size());
        assertEquals("row-90", page.items().get(0));
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    @DisplayName("a page past the end is empty rather than an error")
    void pastTheEnd() {
        PageView<String> page = PageView.slice(HUNDRED, 99, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(100, page.totalItems());
    }

    @Test
    @DisplayName("an empty list is one empty page, not zero pages")
    void emptyList() {
        PageView<String> page = PageView.slice(List.of(), 0, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    @DisplayName("a negative page reads as the first")
    void negativePage() {
        assertEquals("row-0", PageView.slice(HUNDRED, -3, 10).items().get(0));
    }

    @Test
    @DisplayName("size is clamped, so no caller can pull a whole table back")
    void sizeIsClamped() {
        assertEquals(PageView.MAX_SIZE, PageView.clampSize(100_000));
        assertEquals(PageView.DEFAULT_SIZE, PageView.clampSize(0));
        assertEquals(PageView.DEFAULT_SIZE, PageView.clampSize(-5));
        assertEquals(25, PageView.clampSize(25));
    }
}
