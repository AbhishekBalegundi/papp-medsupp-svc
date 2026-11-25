package com.anthem.cartman.toolaccess.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The 32-character tool-access flag string held in {@code TM_Login.Log_CartmanTools}.
 *
 * <p>Immutable. All parsing and rendering rules verified against the Delphi are enforced here
 * so no caller can reproduce them inconsistently:
 *
 * <ul>
 *   <li><b>R3</b> — a stored value shorter than 32 characters is treated as <em>all zeros</em>.
 *       Delphi discarded it rather than padding ({@code frmToolsAdmin.pas:149},
 *       {@code frmMain1.pas:185}), so a 31-character value revokes every tool.</li>
 *   <li><b>R4</b> — {@code null} or blank is all zeros.</li>
 *   <li><b>R5</b> — rendering always produces exactly 32 characters.</li>
 *   <li>Only positions the catalogue covers can be set — {@link CartmanTool#byIndex(int)}
 *       answering {@code null} is what makes a position untouchable.</li>
 * </ul>
 *
 * <p>Anything longer than 32 characters is truncated to the first 32 — Delphi read only
 * positions 1-32 and its own default literal was 33 characters long (defect D5).
 */
public final class ToolAccessMask {

    private static final char ON = '1';
    private static final char OFF = '0';

    private static final String ALL_OFF = String.valueOf(OFF).repeat(CartmanTool.MASK_LENGTH);

    private final Set<CartmanTool> granted;
    /** The stored value this mask was read from — the source of every bit we do not own. */
    private final String baseline;

    private ToolAccessMask(Set<CartmanTool> granted, String baseline) {
        this.granted = Collections.unmodifiableSet(granted);
        this.baseline = baseline;
    }

    /** A mask with nothing granted. */
    public static ToolAccessMask empty() {
        return new ToolAccessMask(EnumSet.noneOf(CartmanTool.class), ALL_OFF);
    }

    /**
     * Parse a stored {@code Log_CartmanTools} value.
     *
     * @param stored raw column value; may be {@code null}, blank, short, or over-long
     * @return the granted set, never {@code null}
     */
    public static ToolAccessMask parse(String stored) {
        if (stored == null) {
            return empty();                                   // R4
        }
        String value = stored.trim();
        if (value.length() < CartmanTool.MASK_LENGTH) {
            return empty();                                   // R3 — short means none, not padded
        }
        String baseline = value.substring(0, CartmanTool.MASK_LENGTH);
        EnumSet<CartmanTool> set = EnumSet.noneOf(CartmanTool.class);
        for (int i = 0; i < CartmanTool.MASK_LENGTH; i++) {
            CartmanTool tool = CartmanTool.byIndex(i);
            if (baseline.charAt(i) == ON && tool != null) {
                set.add(tool);
            }
        }
        return new ToolAccessMask(set, baseline);
    }

    /** Build a mask from a set of tools. */
    public static ToolAccessMask of(Set<CartmanTool> tools) {
        EnumSet<CartmanTool> set = EnumSet.noneOf(CartmanTool.class);
        if (tools != null) {
            set.addAll(tools);
        }
        return new ToolAccessMask(set, ALL_OFF);
    }

    /**
     * This mask's untouchable bits, with the catalogue's positions replaced by {@code tools}.
     *
     * <p>What a save writes: the screen decides the positions CARTMAN knows and nothing else, so a
     * position it has no tool for keeps whatever was already stored there.
     */
    public ToolAccessMask regrant(Set<CartmanTool> tools) {
        EnumSet<CartmanTool> set = EnumSet.noneOf(CartmanTool.class);
        if (tools != null) {
            set.addAll(tools);
        }
        return new ToolAccessMask(set, baseline);
    }

    /** Render for storage — always exactly 32 characters (R5). */
    public String toStoredValue() {
        StringBuilder sb = new StringBuilder(baseline);
        for (int i = 0; i < CartmanTool.MASK_LENGTH; i++) {
            CartmanTool tool = CartmanTool.byIndex(i);
            // Ours to write only where the catalogue has a tool; everything else keeps what was stored.
            if (tool != null) {
                sb.setCharAt(i, granted.contains(tool) ? ON : OFF);
            }
        }
        return sb.toString();
    }

    public boolean isGranted(CartmanTool tool) {
        return granted.contains(tool);
    }

    /** Unmodifiable view of the granted tools. */
    public Set<CartmanTool> granted() {
        return granted;
    }

    public int count() {
        return granted.size();
    }

    /** Tools granted here but not in {@code other}. */
    public Set<CartmanTool> addedComparedTo(ToolAccessMask other) {
        EnumSet<CartmanTool> diff = EnumSet.noneOf(CartmanTool.class);
        diff.addAll(granted);
        diff.removeAll(other.granted);
        return diff;
    }

    /** Tools granted in {@code other} but not here. */
    public Set<CartmanTool> removedComparedTo(ToolAccessMask other) {
        return other.addedComparedTo(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ToolAccessMask m && granted.equals(m.granted);
    }

    @Override
    public int hashCode() {
        return granted.hashCode();
    }

    @Override
    public String toString() {
        return toStoredValue();
    }
}
