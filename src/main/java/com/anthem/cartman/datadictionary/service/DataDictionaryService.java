package com.anthem.cartman.datadictionary.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.datadictionary.dto.DataDictionaryViews;
import com.anthem.cartman.datadictionary.entity.DataDictionaryEntry;
import com.anthem.cartman.datadictionary.repository.DataDictionaryRepository;

/**
 * Module 6 — Cosmo Data Dictionary (US-6.1 to US-6.4).
 *
 * <p>Three deliberate corrections, all recorded as anomalies:
 * <ul>
 *   <li><b>A22</b> — Save was a bare {@code UPDATE}, so a column with no dictionary row could
 *       never be documented; the statement affected nothing and reported nothing. Save is an
 *       upsert here.</li>
 *   <li><b>A23</b> — the {@code DD} checkbox column no code ever read is not reproduced.</li>
 *   <li><b>A24</b> — values longer than the column are rejected rather than silently truncated.</li>
 * </ul>
 *
 * <p>US-6.5, the data-flow bitmap, is not implemented: {@code CosmoDataLayout.bmp} is not in the
 * source tree, so there is nothing to render and no way to know what it showed.
 */
@Service
public class DataDictionaryService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    private final SchemaCatalogue catalogue;
    private final DataDictionaryRepository entries;

    public DataDictionaryService(SchemaCatalogue catalogue, DataDictionaryRepository entries) {
        this.catalogue = catalogue;
        this.entries = entries;
    }

    /** US-6.1 — every non-system table in the database. */
    @Transactional(readOnly = true)
    public List<String> tables() {
        return catalogue.tableNames();
    }

    /**
     * US-6.2 / US-6.3 — every column of one table with its dictionary entry attached.
     *
     * <p>One catalogue read plus one query, rather than the Delphi's query per selected field.
     */
    @Transactional(readOnly = true)
    public List<DataDictionaryViews.FieldRow> fields(String tableName) {
        String table = requireTable(tableName);

        Map<String, DataDictionaryEntry> documented = new LinkedHashMap<>();
        for (DataDictionaryEntry e : entries.findForTable(table)) {
            documented.put(key(e.getFieldName()), e);
        }

        return catalogue.columnNames(table).stream()
                .map(column -> {
                    DataDictionaryEntry e = documented.get(key(column));
                    return new DataDictionaryViews.FieldRow(
                            column,
                            e != null,
                            e == null ? null : e.getPrettyName(),
                            e == null ? null : e.getFieldType(),
                            e == null ? null : e.getFieldLength(),
                            e == null ? null : e.getDescription());
                })
                .toList();
    }

    /**
     * US-6.4 — store the four described values for one column.
     *
     * <p>Upsert, so a column that has never been documented can be. The Delphi's bare
     * {@code UPDATE} silently did nothing in that case (A22).
     */
    @Transactional
    public DataDictionaryViews.FieldRow saveEntry(String tableName, String fieldName,
                                                  DataDictionaryViews.EntryEdit edit) {
        String table = requireTable(tableName);
        String field = requireColumn(table, fieldName);

        String prettyName = checkLength("Simple Name", edit.prettyName(),
                DataDictionaryEntry.PRETTY_NAME_MAX);
        String fieldType = checkLength("Type", edit.fieldType(),
                DataDictionaryEntry.FIELD_TYPE_MAX);
        String fieldLength = checkLength("Length", edit.fieldLength(),
                DataDictionaryEntry.FIELD_LENGTH_MAX);
        String description = checkLength("Description", edit.description(),
                DataDictionaryEntry.DESCRIPTION_MAX);

        DataDictionaryEntry entry = entries.findEntry(table, field)
                .orElseGet(() -> DataDictionaryEntry.of(table, field));
        boolean isNew = entry.getPrettyName() == null && entry.getDescription() == null
                && entry.getFieldType() == null && entry.getFieldLength() == null;

        entry.describe(prettyName, fieldType, fieldLength, description);
        entries.save(entry);

        AUDIT.info("data dictionary {}: {}.{}", isNew ? "created" : "updated", table, field);

        return new DataDictionaryViews.FieldRow(field, true, prettyName, fieldType, fieldLength,
                description);
    }

    // ── helpers ───────────────────────────────────────────────────

    private String requireTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new ValidationException("A table is required.");
        }
        // Resolve to the catalogue's own spelling: JDBC metadata lookups are case-sensitive.
        return catalogue.resolveTableName(tableName)
                .orElseThrow(() -> new NotFoundException("No table named " + tableName.trim() + "."));
    }

    private String requireColumn(String table, String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new ValidationException("A field is required.");
        }
        String field = fieldName.trim();
        return catalogue.columnNames(table).stream()
                .filter(c -> c.equalsIgnoreCase(field))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        table + " has no column named " + field + "."));
    }

    /** A24 — the Delphi let the database truncate; say so instead. */
    private static String checkLength(String label, String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw new ValidationException(
                    label + " may be at most " + max + " characters (given " + trimmed.length() + ").");
        }
        return trimmed;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
