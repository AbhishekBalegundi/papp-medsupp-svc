package com.anthem.cartman.toolaccess.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.FakeLoginRepository;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.toolaccess.domain.CartmanTool;
import com.anthem.cartman.toolaccess.dto.ToolAccessView;
import com.anthem.cartman.toolaccess.dto.ToolStateView;
import com.anthem.cartman.toolaccess.dto.UserSummary;
import com.anthem.cartman.toolaccess.service.ToolAccessAuditor;
import com.anthem.cartman.toolaccess.service.ToolAccessService;

/**
 * Behaviour tests for module 1, using an in-memory repository so no database or
 * mocking framework is required.
 */
class ToolAccessServiceTest {

    private FakeLoginRepository repo;
    private ToolAccessService service;

    @BeforeEach
    void setUp() {
        repo = new FakeLoginRepository();
        service = new ToolAccessService(repo, new ToolAccessAuditor());

        repo.add(login("ZULU",    "Zoe",  "Zulu",  "Sales", null));
        repo.add(login("ALPHA",   "Ann",  "Alpha", "Admin", allOff()));
        // 20 is Zipcode Editor; 23 was RX Editor, descoped on 26 Aug 2026 — so MIKE carries a stored
        // bit CARTMAN no longer understands and must not disturb.
        repo.add(login("MIKE",    "Moe",  "Mike",  "Under", with(20, 23)));
        repo.add(login("SHORTY",  "Sam",  "Short", "Keyer", "1".repeat(31)));  // R3
    }

    // ── US-1.1 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("US-1.1 users are ordered by login name and never duplicated")
    void listsUsersOrdered() {
        List<UserSummary> users = service.listUsers();
        assertEquals(List.of("ALPHA", "MIKE", "SHORTY", "ZULU"),
                users.stream().map(UserSummary::loginName).toList());
        assertEquals(users.size(), users.stream().map(UserSummary::loginName).distinct().count());
    }

    @Test
    @DisplayName("US-1.1 a null Log_CartmanTools shows everything unticked")
    void nullMaskShowsNothingGranted() {
        ToolAccessView view = service.getToolAccess("ZULU");
        assertEquals(0, view.grantedCount());
        assertEquals("0".repeat(32), view.storedValue());
    }

    @Test
    @DisplayName("US-1.1 a 31-character value revokes everything (R3)")
    void shortMaskShowsNothingGranted() {
        assertEquals(0, service.getToolAccess("SHORTY").grantedCount());
    }

    @Test
    @DisplayName("US-1.1 every tool is returned with its drift flag")
    void returnsTheCatalogue() {
        ToolAccessView view = service.getToolAccess("MIKE");

        // 32 positions less the eleven CARTMAN has no screen for.
        assertEquals(21, view.tools().size());
        // MIKE holds 20 and 23, and only 20 is still a tool this screen knows about.
        assertEquals(1, view.grantedCount());

        var employerModifier = tool(view, CartmanTool.EMPLOYER_MODIFIER);
        assertEquals("Employer Modifier", employerModifier.label());
        assertEquals("ICD Manager", employerModifier.legacyLabel());
        assertTrue(employerModifier.labelDrifted());
    }

    @Test
    @DisplayName("US-1.1 nothing unusable is on the checklist")
    void unusablePositionsAreNotListed() {
        ToolAccessView view = service.getToolAccess("MIKE");
        var indices = view.tools().stream().map(ToolStateView::index).toList();

        // Six out of scope (0, 1, 8, 10, 17, 23) and five dead in the Delphi (11, 13, 14, 15, 21):
        // every row on this checklist is one an administrator can actually tick.
        for (int index : List.of(0, 1, 8, 10, 11, 13, 14, 15, 17, 21, 23)) {
            assertFalse(indices.contains(index), "index " + index + " must not be listed");
        }
    }

    /** Look a row up by tool, since the list is no longer indexed by mask position. */
    private static ToolStateView tool(ToolAccessView view, CartmanTool wanted) {
        return view.tools().stream()
                .filter(t -> t.tool().equals(wanted.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(wanted + " is not on the checklist"));
    }

    // ── US-1.2 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("US-1.2 saving writes exactly 32 characters for that user only")
    void savesThirtyTwoCharactersForOneUser() {
        service.updateToolAccess("ALPHA", EnumSet.of(CartmanTool.ZIPCODE_EDITOR), "admin1");

        assertEquals(32, repo.get("ALPHA").getCartmanTools().length());
        assertEquals('1', repo.get("ALPHA").getCartmanTools().charAt(20));
        assertEquals(with(20, 23), repo.get("MIKE").getCartmanTools(), "other users untouched");
    }

    @Test
    @DisplayName("US-1.2 the saved state is returned, not the administrator's own tools")
    void returnsWhatWasSaved() {
        ToolAccessView saved = service.updateToolAccess(
                "ALPHA", EnumSet.of(CartmanTool.FILE_MOVER, CartmanTool.GROUP_MANAGER), "admin1");

        assertEquals(2, saved.grantedCount());
        assertTrue(tool(saved, CartmanTool.FILE_MOVER).granted());
        assertTrue(tool(saved, CartmanTool.GROUP_MANAGER).granted());
    }

    @Test
    @DisplayName("US-1.2 a wired tile can be granted, even one whose function may move later")
    void acceptsWiredTile() {
        ToolAccessView saved = service.updateToolAccess(
                "ALPHA", EnumSet.of(CartmanTool.ENVIRONMENT_SYNC), "admin1");

        assertEquals(1, saved.grantedCount());
        assertTrue(tool(saved, CartmanTool.ENVIRONMENT_SYNC).granted());
    }

    /**
     * The guard the owner asked for, against a mask shaped like the real ones.
     *
     * <p>`HammonsA` holds `11111111111111111110101110000000` in the live database — bits set at six
     * of the nine positions CARTMAN no longer shows. Saving that user's tools from the checklist must
     * change only what the checklist covers and leave those six characters untouched, so the Delphi
     * goes on reading the access it granted.
     */
    @Test
    @DisplayName("a real mask keeps every position CARTMAN does not show")
    void aRealMaskKeepsWhatCartmanDoesNotShow() {
        String stored = "11111111111111111110101110000000";
        repo.add(login("HAMMONSA", "Ann", "Hammons", "Admin", stored));

        service.updateToolAccess("HAMMONSA", EnumSet.of(CartmanTool.DATA_DICTIONARY), "admin1");

        String after = repo.get("HAMMONSA").getCartmanTools();
        assertEquals(32, after.length());
        for (int index : List.of(0, 1, 8, 11, 13, 14, 15, 17, 21)) {
            assertEquals(stored.charAt(index), after.charAt(index),
                    "position " + index + " is not CARTMAN's to write");
        }
        // And the positions it does show are exactly what was ticked.
        assertEquals('1', after.charAt(CartmanTool.DATA_DICTIONARY.index()));
        assertEquals('0', after.charAt(CartmanTool.WORK_QUEUES.index()));
    }

    @Test
    @DisplayName("a bit at an out-of-scope position is left exactly as found")
    void outOfScopeBitsAreLeftExactlyAsFound() {
        // ZED holds 1 — User Alias Manager — and 23, RX Editor: two positions CARTMAN no longer has.
        repo.add(login("ZED", "Zoe", "Zed", "Admin", with(1, 20, 23)));

        service.updateToolAccess("ZED", EnumSet.of(CartmanTool.DATA_DICTIONARY), "admin1");

        String stored = repo.get("ZED").getCartmanTools();
        assertEquals('1', stored.charAt(1), "a position CARTMAN does not own survives the save");
        assertEquals('1', stored.charAt(23), "and so does one descoped after the fact");
        assertEquals('0', stored.charAt(20), "the screen's own positions still change");
        assertEquals('1', stored.charAt(CartmanTool.DATA_DICTIONARY.index()));
        assertEquals(32, stored.length());
    }

    @Test
    @DisplayName("US-1.2 an empty selection revokes everything")
    void emptySelectionRevokesAll() {
        service.updateToolAccess("MIKE", EnumSet.noneOf(CartmanTool.class), "admin1");

        // Every position the checklist owns, and only those: MIKE's bit 23 is not the screen's to
        // revoke, so clearing the list leaves it exactly where it was.
        assertEquals(with(23), repo.get("MIKE").getCartmanTools());
    }

    @Test
    @DisplayName("US-1.2 a blank or unknown user is rejected (R8)")
    void rejectsMissingUser() {
        assertThrows(ValidationException.class,
                () -> service.updateToolAccess("  ", EnumSet.noneOf(CartmanTool.class), "a"));
        assertThrows(NotFoundException.class,
                () -> service.updateToolAccess("NOBODY", EnumSet.noneOf(CartmanTool.class), "a"));
    }

    // ── US-1.3 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("US-1.3 copy makes the target match the source")
    void copiesToolSet() {
        service.copyToolAccess("MIKE", "ALPHA", "admin1");

        // What the checklist owns travels; MIKE's bit 23 does not, because CARTMAN no longer knows
        // what it means and must not go handing it to somebody who did not have it.
        assertEquals(with(20), repo.get("ALPHA").getCartmanTools());
        assertEquals(with(20, 23), repo.get("MIKE").getCartmanTools(), "and the source is unchanged");
    }

    @Test
    @DisplayName("US-1.3 copying onto the same user is rejected")
    void rejectsSelfCopy() {
        assertThrows(ValidationException.class, () -> service.copyToolAccess("MIKE", "mike", "a"));
    }

    // ── US-1.4 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("US-1.4 effective tiles come straight from the mask")
    void resolvesEffectiveTools() {
        // MIKE's other stored bit, 23, is no longer a tool, so it opens nothing.
        assertEquals(EnumSet.of(CartmanTool.ZIPCODE_EDITOR), service.effectiveTools("MIKE"));
        assertTrue(service.effectiveTools("SHORTY").isEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String allOff() {
        return "0".repeat(32);
    }

    private static String with(int... indexes) {
        char[] c = new char[32];
        java.util.Arrays.fill(c, '0');
        for (int i : indexes) {
            c[i] = '1';
        }
        return new String(c);
    }

    private static Login login(String name, String first, String last, String type, String tools) {
        Login l = new Login();
        set(l, "loginName", name);
        set(l, "firstName", first);
        set(l, "lastName", last);
        set(l, "loginType", type);
        l.setCartmanTools(tools);
        return l;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

}
