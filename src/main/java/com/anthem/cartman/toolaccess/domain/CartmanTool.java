package com.anthem.cartman.toolaccess.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The 32 CARTMAN tools, indexed exactly as the Delphi toolbar was.
 *
 * <p>The index is the contract. In Delphi, {@code frmMain1.SetAdminTools} showed a toolbar
 * button when {@code clTools.ItemState[button.Tag] = 1} — so the position in the checklist
 * <em>is</em> the button Tag, and character {@code index + 1} of
 * {@code TM_Login.Log_CartmanTools} is that tool's flag.
 *
 * <p><b>Label correction.</b> The legacy checklist captions had drifted from the tiles they
 * actually control. Ticking "ICD Manager" (index 6) granted the Employer Modifier. Each
 * constant therefore carries both the corrected {@code label} — the tile the index really
 * opens — and the {@code legacyLabel} it used to display, so the drift stays visible.
 *
 * @see <a href="file:../../../../../../../../COSMO/cosmo-backend/architecture/CARTMAN_M01_Utilities_Spec.md">M01 spec, defect D1</a>
 */
public enum CartmanTool {

    /*
     * Twenty-two of the column's thirty-two positions. Every one of them opens a screen; a
     * position that does not is simply absent, and there are two reasons for that.
     *
     * Dead in the Delphi (defect D2) — 11, 13, 14, 15 and 21. Their `case` branches in
     * `frmMain1.UtilityClick` are commented out, so the tile opens nothing. They were listed and
     * struck through for a while, on the grounds that the row was a true statement about the legacy
     * application; the owner's call is that a checklist should offer only what can be granted.
     *
     * Out of CARTMAN's scope (12 Aug 2026) — 0 Password Administrator / Lockout, 1 User Alias
     * Manager, 8 Insert Claims, 17 Signature Loader. And 10 Admin Users Properties (21 Aug 2026),
     * whose tile was never built; the separate User Manager at 30 is a different tool and stays.
     * And 23 RX Editor (26 Aug 2026), whose tile was never built either.
     *
     * The positions still exist in `Log_CartmanTools`, a 32-character column the legacy application
     * also reads, so `byIndex` answers null for all eleven and `ToolAccessMask` copies their stored
     * bits through a save untouched. Dropping something from CARTMAN must not rewrite data CARTMAN
     * no longer understands.
     */

    // index, corrected label (the tile it really opens), legacy checklist caption
    FLOW_CONTROL         ( 2, "Flow Control",             "Enviornment Flow"),
    WORK_QUEUES          ( 3, "Work Queues",              "Work Queue Manager"),
    DATA_DICTIONARY      ( 4, "Data Dictionary",          "Cosmo Data Dictionary"),
    PERMISSIONS          ( 5, "Permissions",              "Object Permissions Manager"),
    EMPLOYER_MODIFIER    ( 6, "Employer Modifier",        "ICD Manager"),
    APP_DATA_MGR         ( 7, "App Data Mgr",             "Employer Modifier"),
    ENVIRONMENT_SYNC     ( 9, "Environment Sync",         "Environment Sync"),
    TAT_REPORTS          (12, "TAT Reports",              "Zip Insert"),
    APP_PAGE_UPLOAD      (16, "App Page Upload",          "App Page Uploader"),
    ICD_10               (18, "ICD 10",                   "App Tracking Editor"),
    LETTER_HISTORY_DEL   (19, "Letter History Deleter",   "Letter History Deleter"),
    ZIPCODE_EDITOR       (20, "Zipcode Editor",           "Zipcode Editor"),
    APP_TRACKER          (22, "App Tracker",              "App Tracker"),
    PRODUCT_MANAGER      (24, "Product Manager",          "Product Manager"),
    FILE_MOVER           (25, "File Mover",               "File Mover"),
    REOPEN_APPLICATION   (26, "ReOpen Application",       "App ReOpen"),
    BROADCAST_MESSAGE    (27, "Broadcast Message",        "Broadcast Message"),
    PASSWORD_ENCRYPTOR   (28, "Password Encryptor",       "Password Encryptor"),
    MEDISYS_XML_VIEWER   (29, "Medisys XML Viewer",       "Medisys XML Viewer"),
    USER_MANAGER         (30, "User Manager",             "User Manager"),
    GROUP_MANAGER        (31, "Group Manager",            "Group Manager");

    /**
    /** Number of flag positions in {@code TM_Login.Log_CartmanTools}. */
    public static final int MASK_LENGTH = 32;

    private static final CartmanTool[] BY_INDEX = new CartmanTool[MASK_LENGTH];

    static {
        for (CartmanTool t : values()) {
            BY_INDEX[t.index] = t;
        }
    }

    private final int index;
    private final String label;
    private final String legacyLabel;

    CartmanTool(int index, String label, String legacyLabel) {
        this.index = index;
        this.label = label;
        this.legacyLabel = legacyLabel;
    }

    /** Zero-based position in the mask; equals the legacy toolbar button Tag. */
    public int index() {
        return index;
    }

    /** The tile this index actually opens. */
    public String label() {
        return label;
    }

    /** The caption the legacy checklist showed — kept so label drift stays visible. */
    public String legacyLabel() {
        return legacyLabel;
    }

    /** True when the legacy caption named a different tile than the index controls. */
    public boolean labelDrifted() {
        return !label.equalsIgnoreCase(legacyLabel);
    }

    /**
     * The tool at a mask position, or {@code null} where CARTMAN has none.
     *
     * <p>Five positions are out of scope and several others are dead in the Delphi, so the column is
     * wider than the catalogue. Callers must treat {@code null} as "not ours to read or write".
     */
    public static CartmanTool byIndex(int index) {
        if (index < 0 || index >= MASK_LENGTH) {
            throw new IllegalArgumentException(
                    "Tool index must be 0-" + (MASK_LENGTH - 1) + ", got " + index);
        }
        return BY_INDEX[index];
    }

    public static Optional<CartmanTool> byName(String name) {
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** All tools in mask order. */
    public static List<CartmanTool> ordered() {
        return Arrays.stream(values())
                .sorted((a, b) -> Integer.compare(a.index, b.index))
                .toList();
    }
}
