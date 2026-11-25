package com.anthem.cartman.datadictionary.dto;

/** The shapes the Cosmo Data Dictionary screen reads and writes. */
public final class DataDictionaryViews {

    private DataDictionaryViews() {
    }

    /**
     * One row of the Fields grid, carrying its dictionary entry (US-6.2, US-6.3).
     *
     * @param documented whether a {@code TM_Data_Dictionary} row exists for this column; the four
     *                   described values are null when it does not
     */
    public record FieldRow(String fieldName, boolean documented, String prettyName,
                           String fieldType, String fieldLength, String description) {
    }

    /** The four values Save writes (US-6.4.1). Any of them may be blank. */
    public record EntryEdit(String prettyName, String fieldType, String fieldLength,
                            String description) {
    }
}
