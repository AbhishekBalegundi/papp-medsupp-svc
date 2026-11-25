package com.anthem.cartman.datadictionary.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.EntryEdit;
import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.FieldRow;

import jakarta.validation.Valid;
import com.anthem.cartman.datadictionary.service.DataDictionaryService;

/** Module 6 — Cosmo Data Dictionary. */
@RestController
@RequestMapping("/api/data-dictionary")
public class DataDictionaryController {

    private final DataDictionaryService service;

    public DataDictionaryController(DataDictionaryService service) {
        this.service = service;
    }

    /** US-6.1 — the Table combo. */
    @GetMapping("/tables")
    public List<String> tables() {
        return service.tables();
    }

    /** US-6.2 / US-6.3 — the Fields grid, each row carrying its dictionary entry. */
    @GetMapping("/tables/{tableName}/fields")
    public List<FieldRow> fields(@PathVariable String tableName) {
        return service.fields(tableName);
    }

    /** US-6.4 — Save. Creates the dictionary row when the column has none. */
    @PutMapping("/tables/{tableName}/fields/{fieldName}")
    public FieldRow save(@PathVariable String tableName, @PathVariable String fieldName,
                        @Valid @RequestBody EntryEdit edit) {
        return service.saveEntry(tableName, fieldName, edit);
    }
}
