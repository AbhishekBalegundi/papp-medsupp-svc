package com.anthem.cartman.letterhistory.dto;

import java.time.LocalDateTime;

/** Module 13 — Letter History Deleter. What the screen reads and what a delete reports back. */
public final class LetterHistoryViews {

    private LetterHistoryViews() {
    }

    /**
     * US-13.5 / US-13.6 — one letter recorded against a key: the grid's Date, Letter and ID.
     *
     * <p>{@code RHist_Date} is a {@code smalldatetime}, so the time carries no seconds. The Delphi
     * writes {@code FieldByName('RHist_Date').AsString} into the cell, which is whatever the
     * workstation's locale renders; the timestamp is sent as it is stored and the screen decides how to
     * draw it.
     *
     * <p>{@code letterName} is {@code char(64)} in the table and therefore space-padded. It is trimmed
     * here — the padding is the column's, not the letter's.
     */
    public record LetterRow(LocalDateTime date, String letterName, int letterId) {
    }

    /**
     * US-13.7 — what a delete actually removed.
     *
     * <p>A79: the Delphi's grid loses the row whenever the second statement raises nothing, whether or
     * not the first did anything. Both counts come back instead, so the screen can say what happened.
     * {@code letters} of zero is not a failure — three of the nine event-2 rows in the live database
     * point at a letter that is not there, one of them at id {@code 0}.
     */
    public record DeleteResult(int letters, int events) {
    }
}
