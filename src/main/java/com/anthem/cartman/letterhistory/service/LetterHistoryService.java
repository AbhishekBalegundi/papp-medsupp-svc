package com.anthem.cartman.letterhistory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.DeleteResult;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.LetterRow;
import com.anthem.cartman.letterhistory.repository.LetterHistoryRepository;

/**
 * Module 13 — Letter History Deleter ({@code frmLetterHistDel}).
 *
 * <p>Two things happen on this screen: a key value is looked up, and one of the letters it found is
 * removed from both tables. The Delphi does the second as two independent statements with an empty
 * handler each (A78); here it is one transaction, so a letter and the event that records it cannot
 * outlive each other.
 */
@Service
public class LetterHistoryService {

    private final LetterHistoryRepository repository;

    public LetterHistoryService(LetterHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * US-13.5 — the letters recorded against a key, oldest first.
     *
     * <p>US-13.5.1: a key of nothing but spaces reads nothing, as {@code FindLetters} refuses to run
     * without a trimmed value. It answers an empty list rather than an error — the Delphi simply leaves
     * the grid as it is and hides Delete.
     */
    @Transactional(readOnly = true)
    public List<LetterRow> list(String keyValue) {
        String key = keyValue == null ? "" : keyValue.trim();
        if (key.isEmpty()) {
            return List.of();
        }
        return repository.findLetters(key);
    }

    /**
     * US-13.7 — remove one letter and the event that says it was sent.
     *
     * <p>Both statements run inside one transaction. A78: in the Delphi the first can fail, say so, and
     * the second still run — leaving a letter nothing points at, or an event whose letter is gone. Here
     * either both rows go or neither does.
     *
     * <p>The letter count may legitimately be zero: an event can point at a letter that is not there,
     * which three of the nine event-2 rows in the live database do. That is reported, not treated as a
     * failure. An event count of zero means the key and letter id matched nothing at all, which is a
     * failure — there was nothing to delete.
     */
    @Transactional
    public DeleteResult delete(String keyValue, int letterId) {
        String key = keyValue == null ? "" : keyValue.trim();
        if (key.isEmpty()) {
            throw new ValidationException("A key value is required.");
        }
        int letters = repository.deleteLetter(letterId);
        int events = repository.deleteEvent(key, letterId);
        if (events == 0) {
            // Nothing was removed, so nothing should be committed — including the letter, if the
            // statement above happened to match one.
            throw new ValidationException("That letter is no longer in the record's history.");
        }
        return new DeleteResult(letters, events);
    }
}
