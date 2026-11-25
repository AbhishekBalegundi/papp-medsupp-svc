package com.anthem.cartman.letterhistory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.DeleteResult;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.LetterRow;
import com.anthem.cartman.letterhistory.service.LetterHistoryService;

/** Module 13 — Letter History Deleter. */
@RestController
@RequestMapping("/api/letter-history")
public class LetterHistoryController {

    private final LetterHistoryService service;

    public LetterHistoryController(LetterHistoryService service) {
        this.service = service;
    }

    /** US-13.4 / US-13.5 — the lightning-bolt button: the event-2 letters for that key. */
    @GetMapping
    public List<LetterRow> list(@RequestParam(required = false) String keyValue) {
        return service.list(keyValue);
    }

    /** US-13.7 — the Delete button: both rows, in one transaction, with the counts back. */
    @DeleteMapping
    public DeleteResult delete(@RequestParam String keyValue, @RequestParam int letterId) {
        return service.delete(keyValue, letterId);
    }
}
