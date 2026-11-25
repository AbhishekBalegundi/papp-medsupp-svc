package com.anthem.cartman.common.dto;

import java.util.List;

/**
 * One page of a list, with what a pager needs to draw itself.
 *
 * <p>The screens that need this are the ones standing on large tables: `TM_Reports` holds 5,187
 * rows, so *By User* — every object against one grantee — was sending and rendering 5,187 rows at
 * once. Paging keeps the payload and the DOM proportional to what is on screen.
 *
 * @param items      the rows on this page
 * @param page       zero-based page number
 * @param size       page size asked for
 * @param totalItems how many rows match, across every page
 */
public record PageView<T>(List<T> items, int page, int size, long totalItems, int totalPages,
                          boolean hasPrevious, boolean hasNext) {

    /** Largest page a client may ask for, so a rogue `size` cannot pull the whole table back. */
    public static final int MAX_SIZE = 500;
    public static final int DEFAULT_SIZE = 50;

    public static <T> PageView<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size <= 0 ? 1 : (int) Math.max(1, Math.ceil((double) totalItems / size));
        return new PageView<>(items, page, size, totalItems, totalPages, page > 0,
                page + 1 < totalPages);
    }

    /**
     * Slice an in-memory list. Used where the rows are assembled in Java rather than by the
     * database — the permission grids merge a table of rows with a table of grants, so the merge
     * has to happen before the page can be cut.
     */
    public static <T> PageView<T> slice(List<T> all, int page, int size) {
        int effectiveSize = clampSize(size);
        int from = Math.min(Math.max(page, 0) * effectiveSize, all.size());
        int to = Math.min(from + effectiveSize, all.size());
        return of(all.subList(from, to), Math.max(page, 0), effectiveSize, all.size());
    }

    public static int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
