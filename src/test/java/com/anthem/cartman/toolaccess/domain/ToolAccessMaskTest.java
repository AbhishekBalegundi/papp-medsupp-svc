package com.anthem.cartman.toolaccess.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Parity tests for the {@code Log_CartmanTools} rules, each tied to a rule in the M01 spec.
 */
class ToolAccessMaskTest {

    private static String maskWith(int... indexes) {
        char[] c = new char[CartmanTool.MASK_LENGTH];
        java.util.Arrays.fill(c, '0');
        for (int i : indexes) {
            c[i] = '1';
        }
        return new String(c);
    }

    @Nested
    @DisplayName("R3 — a value shorter than 32 characters means no tools, not padding")
    class ShortValues {

        @Test
        void thirtyOneCharactersGrantsNothing() {
            String thirtyOne = "1".repeat(31);
            assertEquals(0, ToolAccessMask.parse(thirtyOne).count(),
                    "Delphi discarded short values outright (frmToolsAdmin.pas:149)");
        }

        @Test
        void exactlyThirtyTwoIsHonoured() {
            // Every position CARTMAN owns is read, and every other one is copied through, so a
            // 32-character run of ones comes back unchanged.
            assertEquals("1".repeat(32), ToolAccessMask.parse("1".repeat(32)).toStoredValue(),
                    "all 32 positions are accounted for");
            assertEquals(CartmanTool.ordered().size(), ToolAccessMask.parse("1".repeat(32)).count());
        }
    }

    @Nested
    @DisplayName("R4 — null or blank means no tools")
    class EmptyValues {

        @Test
        void nullGrantsNothing() {
            assertEquals(0, ToolAccessMask.parse(null).count());
        }

        @Test
        void blankGrantsNothing() {
            assertEquals(0, ToolAccessMask.parse("   ").count());
        }
    }

    @Nested
    @DisplayName("R1 — index is the toolbar tag")
    class IndexMapping {

        @Test
        void positionMapsToTheCorrectTool() {
            // index 20 is Zipcode Editor; index 24 is Product Manager
            ToolAccessMask mask = ToolAccessMask.parse(maskWith(20, 24));
            assertTrue(mask.isGranted(CartmanTool.ZIPCODE_EDITOR));
            assertTrue(mask.isGranted(CartmanTool.PRODUCT_MANAGER));
            assertEquals(2, mask.count());
        }

        @Test
        void driftedLabelsStillMapByIndexNotCaption() {
            // Defect D1: index 6 was captioned "ICD Manager" but controls the Employer Modifier
            ToolAccessMask mask = ToolAccessMask.parse(maskWith(6));
            assertTrue(mask.isGranted(CartmanTool.EMPLOYER_MODIFIER));
            assertFalse(mask.isGranted(CartmanTool.ICD_10));
            assertEquals("ICD Manager", CartmanTool.EMPLOYER_MODIFIER.legacyLabel());
            assertTrue(CartmanTool.EMPLOYER_MODIFIER.labelDrifted());
        }
    }

    @Nested
    @DisplayName("R5 — rendering is always exactly 32 characters")
    class Rendering {

        @Test
        void emptyRendersThirtyTwoZeros() {
            assertEquals("0".repeat(32), ToolAccessMask.empty().toStoredValue());
        }

        @Test
        void roundTripIsStable() {
            String stored = maskWith(1, 5, 20, 31);
            assertEquals(stored, ToolAccessMask.parse(stored).toStoredValue());
        }

        @Test
        void overLongInputIsTruncatedToThirtyTwo() {
            // Delphi's own default literal was 33 characters (defect D5)
            String thirtyThree = "1".repeat(33);
            assertEquals(32, ToolAccessMask.parse(thirtyThree).toStoredValue().length());
        }
    }

    @Nested
    @DisplayName("Positions CARTMAN has no tool for")
    class NoTool {

        /**
         * Eleven of the thirty-two positions are not CARTMAN's.
         *
         * <p>Five are dead in the Delphi — 11, 13, 14, 15 and 21, whose `case` branches in
         * `frmMain1.UtilityClick` are commented out (defect D2), so the tile opens nothing. Six are
         * out of scope — 0, 1, 8, 17, then 10 Admin Users Properties (21 Aug 2026) and 23 RX Editor
         * (26 Aug 2026). Either way the catalogue has no entry, so a bit set there grants nothing and
         * cannot be granted.
         */
        @Test
        void elevenIndicesHaveNoToolAtAll() {
            assertEquals(List.of(0, 1, 8, 10, 11, 13, 14, 15, 17, 21, 23),
                    java.util.stream.IntStream.range(0, CartmanTool.MASK_LENGTH)
                            .filter(i -> CartmanTool.byIndex(i) == null)
                            .boxed()
                            .toList());
            assertEquals(21, CartmanTool.ordered().size());
        }

        @Test
        void aBitAtSuchAPositionGrantsNothing() {
            // index 11 (Report Reset) was never wired — defect D2
            ToolAccessMask mask = ToolAccessMask.parse(maskWith(11));

            assertEquals(0, mask.count());
            assertEquals('1', mask.toStoredValue().charAt(11), "and it is still carried through");
        }

        /*
         * Admin Users Properties, index 10, was taken out of scope on 21 Aug 2026 — its tile was never
         * built. Users who hold that bit keep it: a screen that no longer offers something must not
         * revoke it, least of all silently and for everyone at once.
         */
        @Test
        void theDescopedAdminUsersBitSurvivesASave() {
            ToolAccessMask held = ToolAccessMask.parse(maskWith(10, 30));

            assertEquals(1, held.count(), "only User Manager at 30 is a tool this screen knows");
            assertEquals('1', held.toStoredValue().charAt(10), "and 10 is carried through untouched");

            // Even a save that grants nothing at all leaves it alone.
            String afterRevokingEverything = held.regrant(EnumSet.noneOf(CartmanTool.class))
                    .toStoredValue();
            assertEquals('1', afterRevokingEverything.charAt(10));
            assertEquals('0', afterRevokingEverything.charAt(30), "while a known tool is revoked");
        }

        @Test
        void everyCatalogueToolCanBeGranted() {
            // Nothing is listed that cannot be ticked; the checklist offers exactly what it can set.
            String rendered = ToolAccessMask.of(EnumSet.allOf(CartmanTool.class)).toStoredValue();
            for (CartmanTool t : CartmanTool.ordered()) {
                assertEquals('1', rendered.charAt(t.index()),
                        t.label() + " at index " + t.index());
            }
        }

        /*
         * The other half of that rule, and the one that matters more: a position this screen cannot
         * set is also one it must not clear. An earlier build rendered '0' for every non-grantable
         * position, so saving any user silently revoked whatever was stored there — CosmoAdm's
         * position 15 was observed cleared exactly that way.
         */
        @Test
        void aSaveLeavesUntouchablePositionsExactlyAsTheyWereFound() {
            char[] stored = "0".repeat(CartmanTool.MASK_LENGTH).toCharArray();
            stored[0] = '1';   // out of scope — CARTMAN has no tool here at all
            stored[15] = '1';  // dead in the Delphi — CARTMAN has no tool here either
            stored[3] = '1';   // WORK_QUEUES — this one is the screen's to change
            ToolAccessMask before = ToolAccessMask.parse(new String(stored));

            String after = before.regrant(EnumSet.of(CartmanTool.DATA_DICTIONARY)).toStoredValue();

            assertEquals('1', after.charAt(0), "an out-of-scope bit must survive a save");
            assertEquals('1', after.charAt(15), "a dead position must survive a save");
            assertEquals('0', after.charAt(3), "the screen's own positions do change");
            assertEquals('1', after.charAt(CartmanTool.DATA_DICTIONARY.index()));
        }

        @Test
        void anOutOfScopeBitIsCarriedThroughButNeverReported() {
            char[] stored = "0".repeat(CartmanTool.MASK_LENGTH).toCharArray();
            stored[1] = '1';  // User Alias Manager — no longer a CARTMAN module
            ToolAccessMask mask = ToolAccessMask.parse(new String(stored));

            // Nothing to report: CARTMAN has no such tool. The bit still survives a round trip.
            assertEquals(0, mask.count());
            assertEquals('1', mask.toStoredValue().charAt(1));
        }
    }

    @Nested
    @DisplayName("Diffing supports the copy preview (US-1.3)")
    class Diffing {

        @Test
        void reportsAddedAndRemoved() {
            ToolAccessMask target = ToolAccessMask.parse(maskWith(20));         // Zipcode Editor
            ToolAccessMask source = ToolAccessMask.parse(maskWith(24, 25));     // Product, File Mover

            assertEquals(EnumSet.of(CartmanTool.PRODUCT_MANAGER, CartmanTool.FILE_MOVER),
                    source.addedComparedTo(target));
            assertEquals(EnumSet.of(CartmanTool.ZIPCODE_EDITOR),
                    source.removedComparedTo(target));
        }
    }

    @Test
    @DisplayName("The catalogue holds 21 tools: 32 positions less the eleven with no screen")
    void catalogueIsComplete() {
        assertEquals(21, CartmanTool.values().length);
        for (int i = 0; i < CartmanTool.MASK_LENGTH; i++) {
            CartmanTool tool = CartmanTool.byIndex(i);
            if (tool != null) {
                assertEquals(i, tool.index(), "a tool must sit at the index it claims");
            }
        }
        assertEquals(11, (int) java.util.stream.IntStream.range(0, CartmanTool.MASK_LENGTH)
                .filter(i -> CartmanTool.byIndex(i) == null)
                .count());
        // 23 was RX Editor until 26 Aug 2026. A stored bit there must now resolve to no tool at all,
        // which is what keeps ToolAccessMask copying it through a save untouched.
        assertNull(CartmanTool.byIndex(23));
    }
}
