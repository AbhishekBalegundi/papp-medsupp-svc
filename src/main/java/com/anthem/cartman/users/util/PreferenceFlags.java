package com.anthem.cartman.users.util;

/**
 * The {@code TM_Login.Log_Flags} codec — a 128-character string of {@code '0'}/{@code '1'} the
 * Options dialog reads and writes by position. Positions here are the Delphi's, <b>1-based</b>,
 * taken verbatim from {@code Options2.pas} ({@code PopulateOptions} / {@code SetOptionsFromControls}).
 *
 * <p>Two Delphi behaviours are deliberately not reproduced:
 * <ul>
 *   <li><b>A36</b> — the Delphi rebuilds the string from 128 zeros and sets only the dialog's own
 *       positions, so any flag some other system set is silently wiped on save. {@link #apply}
 *       starts from the stored value and touches only known positions.</li>
 *   <li><b>A39</b> — position 117 is written twice ({@code AllowSavingLettersCheck1}, then
 *       overwritten by {@code ckSave}). Both checkboxes read the same bit, so here they are one
 *       field: {@code allowSavingLetters}.</li>
 * </ul>
 *
 * <p>Position 127 packs four "panel open on startup" switches into one hex digit
 * ({@code HexToBinary}/{@code BinaryToHex}): bit order Applicant, Letters, Macros, Edit.
 * Position 120 is the primary view: {@code '0'} full screen, {@code '1'} minimized-top,
 * {@code '2'} minimized-bottom.
 */
public final class PreferenceFlags {

    public static final int LENGTH = 128;

    // 1-based positions, exactly as Options2.pas addresses Flags[n].
    public static final int QUEUE_ON_CLEAR = 4;
    public static final int NOTES_ON_QUERY = 5;
    public static final int ALLOW_QUEUES = 9;
    public static final int ALLOW_PRINT_MANAGER = 10;
    public static final int ALLOW_REPORT_CREATION = 11;
    public static final int ALLOW_REPORT_EXECUTION = 12;
    public static final int DROP_PAN = 13;
    public static final int WRITE_ACCESS = 14;
    public static final int PRINT_SIGNATURES = 16;
    public static final int RUN_QUEUE_ON_START = 20;
    public static final int MED_B_OVERRIDE = 24;
    public static final int ALL_CODES = 26;
    public static final int GRANT_REVOKE = 63;
    public static final int CACHED_OBJECTS = 64;
    public static final int SHOW_FIELD_HINTS = 88;
    public static final int PF_APP_INFO = 89;
    public static final int PF_SEL_DESIRED = 90;
    public static final int PF_BILLING_INFO = 91;
    public static final int PF_DEP_INFO = 92;
    public static final int PF_MED_QUEST = 93;
    public static final int PF_MED_DETAIL = 94;
    public static final int PF_OTH_COV = 95;
    public static final int PF_BD_INFO = 96;
    public static final int PF_ISG_REQ = 97;
    public static final int PF_APP_SIG = 98;
    public static final int PF_AGT_SIG = 99;
    public static final int PF_CHECK_INFO = 100;
    public static final int PF_CONF = 101;
    public static final int PF_AUDIT = 102;
    public static final int PF_MEDICARE = 103;
    public static final int PF_UNDERWRITING = 104;
    public static final int PF_LETTERS = 105;
    public static final int PF_UNDER_CONFIRM = 106;
    public static final int RATE_CLOSED_PRODUCTS = 107;
    public static final int EDIT_UW = 108;
    public static final int HIDE_QUEUE_ON_QUERY = 109;
    public static final int EDIT_HCID = 111;
    public static final int DOUBLE_KEY = 112;
    public static final int KEYWORD_SEARCH = 116;
    public static final int ALLOW_SAVING_LETTERS = 117;
    public static final int PRIMARY_VIEW = 120;
    public static final int NAV_BAR = 121;
    public static final int SHOW_XML = 122;
    public static final int POPUP_INFO = 124;
    public static final int STARTUP_PANELS = 127;
    public static final int OPTIONAL_LETTERS = 128;

    private final char[] flags;

    private PreferenceFlags(char[] flags) {
        this.flags = flags;
    }

    /**
     * A stored value, blank-tolerant: the Delphi substitutes an all-zero string when
     * {@code Log_Flags} is blank (its literal is 145 zeros against a 128 column — defect D5's
     * sibling; only 1–128 are ever read). Shorter stored values are zero-padded.
     */
    public static PreferenceFlags parse(String stored) {
        char[] flags = new char[LENGTH];
        String value = stored == null ? "" : stored;
        for (int i = 0; i < LENGTH; i++) {
            flags[i] = i < value.length() && value.charAt(i) == '1' ? '1' : '0';
        }
        // Position 120 and 127 carry digits/hex, not just 0/1 — keep them verbatim.
        keepVerbatim(flags, value, PRIMARY_VIEW);
        keepVerbatim(flags, value, STARTUP_PANELS);
        return new PreferenceFlags(flags);
    }

    private static void keepVerbatim(char[] flags, String value, int position) {
        if (position - 1 < value.length()) {
            char c = value.charAt(position - 1);
            flags[position - 1] = c == ' ' ? '0' : c;
        }
    }

    public boolean get(int position) {
        return flags[position - 1] == '1';
    }

    public PreferenceFlags set(int position, boolean value) {
        flags[position - 1] = value ? '1' : '0';
        return this;
    }

    /** Position 120: 0 full screen, 1 minimized-top, 2 minimized-bottom. */
    public int primaryView() {
        char c = flags[PRIMARY_VIEW - 1];
        return c == '1' ? 1 : c == '2' ? 2 : 0;
    }

    public PreferenceFlags setPrimaryView(int mode) {
        flags[PRIMARY_VIEW - 1] = (char) ('0' + Math.min(Math.max(mode, 0), 2));
        return this;
    }

    /** Position 127, one hex digit: bits Applicant, Letters, Macros, Edit (HexToBinary order). */
    public boolean[] startupPanels() {
        int value = Character.digit(flags[STARTUP_PANELS - 1], 16);
        if (value < 0) {
            value = 0;
        }
        return new boolean[] {
            (value & 0b1000) != 0, // Applicant
            (value & 0b0100) != 0, // Letters
            (value & 0b0010) != 0, // Macros
            (value & 0b0001) != 0, // Edit
        };
    }

    public PreferenceFlags setStartupPanels(boolean applicant, boolean letters, boolean macros,
                                            boolean edit) {
        int value = (applicant ? 0b1000 : 0) | (letters ? 0b0100 : 0)
                | (macros ? 0b0010 : 0) | (edit ? 0b0001 : 0);
        flags[STARTUP_PANELS - 1] = Character.toUpperCase(Character.forDigit(value, 16));
        return this;
    }

    /** Exactly 128 characters, as the save writes it. */
    public String toStoredValue() {
        return new String(flags);
    }
}
