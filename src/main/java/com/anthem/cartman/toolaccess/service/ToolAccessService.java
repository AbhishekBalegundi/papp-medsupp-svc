package com.anthem.cartman.toolaccess.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.toolaccess.domain.CartmanTool;
import com.anthem.cartman.toolaccess.domain.ToolAccessMask;
import com.anthem.cartman.toolaccess.dto.ToolAccessView;
import com.anthem.cartman.toolaccess.dto.ToolStateView;
import com.anthem.cartman.toolaccess.dto.UserSummary;

/**
 * Module 1 — Utilities. Grants and revokes CARTMAN tile access.
 *
 * <p>Implements US-1.1 to US-1.5. Behaviour is verified against {@code frmToolsAdmin.pas}
 * and {@code frmMain1.SetAdminTools}; deliberate departures from the Delphi are marked
 * {@code FIX Dn} against the defect numbers in the M01 spec.
 */
@Service
public class ToolAccessService {

    private final LoginLookup loginRepository;
    private final ToolAccessAuditor auditor;

    public ToolAccessService(LoginLookup loginRepository, ToolAccessAuditor auditor) {
        this.loginRepository = loginRepository;
        this.auditor = auditor;
    }

    /**
     * US-1.1 — the users a tool set can be maintained for, ordered by login name (R6).
     * The list is built fresh each call, so it cannot accumulate duplicates (FIX D4).
     */
    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        return loginRepository.findAllByOrderByLoginNameAsc().stream()
                .map(l -> new UserSummary(
                        l.getLoginName().trim(),
                        l.displayName(),
                        l.getLoginType() == null ? null : l.getLoginType().trim(),
                        l.isEnabled()))
                .toList();
    }

    /** US-1.1 — the full tool list for one user, with every tool's state and status. */
    @Transactional(readOnly = true)
    public ToolAccessView getToolAccess(String loginName) {
        Login login = require(loginName);
        ToolAccessMask mask = ToolAccessMask.parse(login.getCartmanTools());

        List<ToolStateView> tools = CartmanTool.ordered().stream()
                .map(t -> new ToolStateView(
                        t.name(),
                        t.index(),
                        t.label(),
                        t.legacyLabel(),
                        t.labelDrifted(),
                        mask.isGranted(t)))
                .toList();

        return new ToolAccessView(
                login.getLoginName().trim(),
                login.displayName(),
                mask.toStoredValue(),
                mask.count(),
                tools);
    }

    /**
     * US-1.2 — replace a user's tool set.
     *
     * <p>Writes exactly 32 characters (R5) for the selected user only (R7). The saved state is
     * returned, so the caller shows what was actually stored rather than reverting to the
     * administrator's own tools as the Delphi did (FIX Trace C step 5). A failure rolls the
     * transaction back and changes nothing (FIX the swallowed exception).
     *
     * @param loginName    user being changed; must exist (R8 made explicit)
     * @param grantedTools tools to grant; every tool in the catalogue may be granted
     * @param actor        who is making the change, for the audit record (US-1.5)
     */
    @Transactional
    public ToolAccessView updateToolAccess(String loginName, Set<CartmanTool> grantedTools, String actor) {
        Login login = require(loginName);

        Set<CartmanTool> requested = grantedTools == null
                ? EnumSet.noneOf(CartmanTool.class)
                : EnumSet.copyOf(grantedTools.isEmpty() ? EnumSet.noneOf(CartmanTool.class) : grantedTools);

        ToolAccessMask before = ToolAccessMask.parse(login.getCartmanTools());
        // Only the positions the catalogue covers may change; the rest are carried through as read.
        ToolAccessMask after = before.regrant(requested);

        login.setCartmanTools(after.toStoredValue());
        loginRepository.save(login);

        auditor.recordChange(login.getLoginName().trim(), before, after, actor);

        return getToolAccess(login.getLoginName());
    }

    /** US-1.3 — copy a tool set from one user to another as a single atomic write. */
    @Transactional
    public ToolAccessView copyToolAccess(String fromLoginName, String toLoginName, String actor) {
        validateDistinct(fromLoginName, toLoginName);
        ToolAccessMask source = ToolAccessMask.parse(require(fromLoginName).getCartmanTools());
        return updateToolAccess(toLoginName, source.granted(), actor);
    }

    /**
     * US-1.4 — the tiles a user may open. This is the query the CARTMAN shell asks at sign-in;
     * it is the equivalent of {@code SetAdminTools(..., False)} and, unlike the Delphi, it
     * cannot corrupt the caller's own state (FIX D3).
     */
    @Transactional(readOnly = true)
    public Set<CartmanTool> effectiveTools(String loginName) {
        return ToolAccessMask.parse(require(loginName).getCartmanTools()).granted();
    }

    private Login require(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new ValidationException("A user must be selected.");   // R8
        }
        return loginRepository.findByLoginNameIgnoreCase(loginName.trim())
                .orElseThrow(() -> new NotFoundException("No such user: " + loginName));
    }

    private void validateDistinct(String from, String to) {
        if (from != null && from.equalsIgnoreCase(to)) {
            throw new ValidationException("Source and target user must be different.");
        }
    }

    private static List<String> labels(Set<CartmanTool> tools) {
        return tools.stream().sorted(java.util.Comparator.comparingInt(CartmanTool::index))
                .map(CartmanTool::label).toList();
    }

}
