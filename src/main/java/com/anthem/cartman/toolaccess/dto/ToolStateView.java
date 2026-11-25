package com.anthem.cartman.toolaccess.dto;

/**
 * One row of the Utilities checklist.
 *
 * @param tool         stable enum name — what the client sends back on save
 * @param index        position in {@code Log_CartmanTools}; equals the legacy toolbar Tag
 * @param label        the tile this index actually opens
 * @param legacyLabel  the caption the Delphi checklist showed
 * @param labelDrifted true when the legacy caption named a different tile (defect D1)
 * @param granted      whether the user currently holds it
 */
public record ToolStateView(
        String tool,
        int index,
        String label,
        String legacyLabel,
        boolean labelDrifted,
        boolean granted) {
}
