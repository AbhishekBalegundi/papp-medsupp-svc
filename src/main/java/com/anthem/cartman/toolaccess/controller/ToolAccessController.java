package com.anthem.cartman.toolaccess.controller;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.common.util.CurrentUser;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.toolaccess.domain.CartmanTool;
import com.anthem.cartman.toolaccess.dto.ToolAccessView;
import com.anthem.cartman.toolaccess.dto.UserSummary;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.anthem.cartman.toolaccess.service.AdminPasswordGate;
import com.anthem.cartman.toolaccess.service.ToolAccessService;

/**
 * Module 1 — Utilities. REST surface for CARTMAN tile access.
 *
 * <p>The Delphi gated this screen on the Cosmo System Administrator password typed into the
 * dialog ({@code frmToolsAdmin.pas:52}) — but only on the client; the UPDATE itself was open.
 * Here the same password ritual is kept for the UI ({@link #unlock}) and, unlike the Delphi,
 * every mutation re-checks it server-side via the {@code X-Admin-Password} header. The gate
 * retires when Okta lands and these methods gain
 * {@code @PreAuthorize("hasAuthority('CARTMAN_TOOL_ADMIN')")}.
 */
@RestController
@RequestMapping("/api/tool-access")
public class ToolAccessController {

    /** Carries the administrator password on mutations until Okta replaces the gate. */
    public static final String ADMIN_PASSWORD_HEADER = "X-Admin-Password";

    private final ToolAccessService service;
    private final AdminPasswordGate gate;
    private final CurrentUser currentUser;

    public ToolAccessController(ToolAccessService service, AdminPasswordGate gate,
                                CurrentUser currentUser) {
        this.service = service;
        this.gate = gate;
        this.currentUser = currentUser;
    }

    /**
     * The dialog's password check — the web form of typing into {@code edPassword}.
     * 204 when the password is right; 401 when wrong; 503 when none is configured.
     */
    @PostMapping("/unlock")
    public ResponseEntity<Void> unlock(@Valid @RequestBody UnlockRequest request) {
        gate.require(request.password());
        return ResponseEntity.noContent().build();
    }

    /** US-1.1 — users available in the picker, ordered by login name, no duplicates. */
    @GetMapping("/users")
    public List<UserSummary> listUsers() {
        return service.listUsers();
    }

    /** US-1.1 — one user's full tool list. */
    @GetMapping("/users/{loginName}")
    public ToolAccessView getToolAccess(@PathVariable String loginName) {
        return service.getToolAccess(loginName);
    }

    /** US-1.2 — replace a user's tool set. Returns what was actually stored. */
    @PutMapping("/users/{loginName}")
    public ToolAccessView updateToolAccess(@PathVariable String loginName,
                                           @Valid @RequestBody UpdateToolAccessRequest request,
                                           @RequestHeader(name = ADMIN_PASSWORD_HEADER, required = false)
                                           String adminPassword,
                                           HttpServletRequest http) {
        gate.require(adminPassword);
        return service.updateToolAccess(loginName, toTools(request.tools()),
                currentUser.nameOrSystem(http));
    }

    /** US-1.3 — what a copy would change, without writing anything. */
    /** US-1.3 — copy a tool set between users. */
    @PostMapping("/copy")
    public ToolAccessView copyToolAccess(@Valid @RequestBody CopyToolAccessRequest request,
                                         @RequestHeader(name = ADMIN_PASSWORD_HEADER, required = false)
                                         String adminPassword,
                                         HttpServletRequest http) {
        gate.require(adminPassword);
        return service.copyToolAccess(request.from(), request.to(),
                currentUser.nameOrSystem(http));
    }

    /**
     * US-1.4 / US-1.7 — the signed-in user's effective tiles, which drive the shell's toolbar.
     *
     * <p>Reads the session rather than a {@code Principal}: the interim sign-in sets no
     * principal, so asking for one made this endpoint answer 401 for every valid session.
     */
    @GetMapping("/me")
    public ResponseEntity<List<String>> myTools(HttpServletRequest http) {
        String name = currentUser.nameOrNull(http);
        if (name == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(service.effectiveTools(name).stream()
                .sorted(java.util.Comparator.comparingInt(CartmanTool::index))
                .map(CartmanTool::name)
                .toList());
    }

    /** The full catalogue — lets the UI render the checklist before a user is chosen. */
    @GetMapping("/catalogue")
    public List<CatalogueEntry> catalogue() {
        return CartmanTool.ordered().stream()
                .map(t -> new CatalogueEntry(t.name(), t.index(), t.label(), t.legacyLabel(),
                        t.labelDrifted()))
                .toList();
    }

    private static Set<CartmanTool> toTools(List<String> names) {
        Set<CartmanTool> tools = EnumSet.noneOf(CartmanTool.class);
        if (names == null) {
            return tools;
        }
        for (String n : names) {
            tools.add(CartmanTool.byName(n)
                    .orElseThrow(() -> new ValidationException("Unknown tool: " + n)));
        }
        return tools;
    }

    /** @param tools stable enum names of the tools to grant; everything else is revoked */
    public record UpdateToolAccessRequest(List<String> tools) {
    }

    public record UnlockRequest(@NotBlank String password) {
    }

    public record CopyToolAccessRequest(@NotBlank String from, @NotBlank String to) {
    }

    public record CatalogueEntry(String tool, int index, String label, String legacyLabel,
                                 boolean labelDrifted) {
    }
}
