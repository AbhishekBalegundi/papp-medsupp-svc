package com.anthem.cartman.users.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.anthem.cartman.users.util.PreferenceFlags;

/** The Log_Flags codec, tied to the positions Options2.pas addresses. */
class PreferenceFlagsTest {

    @Test
    @DisplayName("a blank stored value reads as all-off, as the Delphi's zero substitute did")
    void blankIsAllOff() {
        PreferenceFlags f = PreferenceFlags.parse(null);

        assertFalse(f.get(PreferenceFlags.RUN_QUEUE_ON_START));
        assertEquals(0, f.primaryView());
        assertArrayEquals(new boolean[] {false, false, false, false}, f.startupPanels());
        assertEquals(PreferenceFlags.LENGTH, f.toStoredValue().length());
    }

    @Test
    @DisplayName("the Delphi's blank literal is 145 zeros against a 128 column; only 128 are kept")
    void overlongLiteralIsTruncated() {
        PreferenceFlags f = PreferenceFlags.parse("0".repeat(145));

        assertEquals(128, f.toStoredValue().length());
    }

    @Test
    @DisplayName("positions are 1-based, exactly as Options2.pas reads Flags[n]")
    void positionsAreOneBased() {
        StringBuilder stored = new StringBuilder("0".repeat(128));
        stored.setCharAt(20 - 1, '1'); // Flags[20] — Run Call Queue On Startup

        PreferenceFlags f = PreferenceFlags.parse(stored.toString());

        assertTrue(f.get(PreferenceFlags.RUN_QUEUE_ON_START));
        assertFalse(f.get(19));
        assertFalse(f.get(21));
    }

    @Test
    @DisplayName("A36 — setting the dialog's positions leaves every other position alone")
    void preservesUnknownPositions() {
        StringBuilder stored = new StringBuilder("0".repeat(128));
        stored.setCharAt(0, '1');  // position 1 — no dialog control owns it
        stored.setCharAt(50 - 1, '1'); // position 50 — likewise

        String result = PreferenceFlags.parse(stored.toString())
                .set(PreferenceFlags.RUN_QUEUE_ON_START, true)
                .toStoredValue();

        assertEquals('1', result.charAt(0));
        assertEquals('1', result.charAt(50 - 1));
        assertEquals('1', result.charAt(20 - 1));
    }

    @Test
    @DisplayName("position 120 carries the primary view: 0 full, 1 min-top, 2 min-bottom")
    void primaryViewRoundTrips() {
        for (int mode = 0; mode <= 2; mode++) {
            PreferenceFlags f = PreferenceFlags.parse(null).setPrimaryView(mode);
            assertEquals(mode, PreferenceFlags.parse(f.toStoredValue()).primaryView());
        }
    }

    @Test
    @DisplayName("position 127 packs the startup panels into one hex digit, HexToBinary order")
    void startupPanelsRoundTrip() {
        PreferenceFlags f = PreferenceFlags.parse(null)
                .setStartupPanels(true, false, true, false); // Applicant + Macros = 1010 = A

        assertEquals('A', f.toStoredValue().charAt(127 - 1));
        assertArrayEquals(new boolean[] {true, false, true, false},
                PreferenceFlags.parse(f.toStoredValue()).startupPanels());
    }

    @Test
    @DisplayName("all sixteen nibble values survive a round trip")
    void allNibbleValues() {
        for (int i = 0; i < 16; i++) {
            PreferenceFlags f = PreferenceFlags.parse(null)
                    .setStartupPanels((i & 8) != 0, (i & 4) != 0, (i & 2) != 0, (i & 1) != 0);
            boolean[] back = PreferenceFlags.parse(f.toStoredValue()).startupPanels();
            assertEquals((i & 8) != 0, back[0]);
            assertEquals((i & 4) != 0, back[1]);
            assertEquals((i & 2) != 0, back[2]);
            assertEquals((i & 1) != 0, back[3]);
        }
    }
}
