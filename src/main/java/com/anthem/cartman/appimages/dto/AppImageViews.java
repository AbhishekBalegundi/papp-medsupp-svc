package com.anthem.cartman.appimages.dto;

import java.util.List;

/** Module 11 — Record Cleaner. The shapes crossing the wire. */
public final class AppImageViews {

    private AppImageViews() {
    }

    /**
     * One row of the list. The document itself is never part of it (US-11.1.3).
     *
     * <p>{@code hasDocument} is here because A67 leaves rows behind with nothing stored against them —
     * they list, they offer a View link, and there is nothing to show. The screen can say so instead.
     */
    public record PageRow(int id, String state, String year, String version, String pageNumber,
                          boolean hasDocument) {}

    /** The filter the list is narrowed by, all three optional (US-11.2). */
    public record PageFilter(String state, String year, String version) {}

    /** One entry of the dialog's state list: the code stored, and the label shown. */
    public record StateOption(String code, String label) {}

    /**
     * The two state lists, which are not the same list.
     *
     * <p>`frmAppUpload`'s filter offers fifteen bare codes; `frmAddAppPage`'s combo offers seventeen as
     * "Name - CODE", including Louisiana and New Hampshire — which the filter cannot then narrow to.
     * See A75.
     */
    public record StateLists(List<String> filter, List<StateOption> upload) {}

    /** One row of the dialog's grid, before it is stored (US-11.5.3). */
    public record PendingPage(String pageNumber, String fileName) {}

    /** What an upload produced. */
    public record UploadResult(int stored, List<PageRow> pages) {}

    /** A stored document, on its way to the person who asked for it. */
    public record PageContent(String fileName, byte[] content) {}
}
