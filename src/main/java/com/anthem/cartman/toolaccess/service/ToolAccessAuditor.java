package com.anthem.cartman.toolaccess.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthem.cartman.toolaccess.domain.CartmanTool;
import com.anthem.cartman.toolaccess.domain.ToolAccessMask;

/**
 * US-1.5 — records every tool-access change.
 *
 * <p>The Delphi had no audit at all. This runs inside the caller's transaction, so the audit
 * entry and the change either both land or neither does.
 *
 * <p>Currently writes a structured log line. When the shared audit table is introduced (see the
 * shared write path in the architecture diagram) this becomes a repository call — the contract
 * here does not change.
 */
@Component
public class ToolAccessAuditor {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT.TOOL_ACCESS");

    /**
     * @param targetUser whose access changed
     * @param before     tools held before the change
     * @param after      tools held after the change
     * @param actor      the administrator making the change
     */
    public void recordChange(String targetUser, ToolAccessMask before, ToolAccessMask after, String actor) {
        Set<CartmanTool> added = after.addedComparedTo(before);
        Set<CartmanTool> removed = after.removedComparedTo(before);

        if (added.isEmpty() && removed.isEmpty()) {
            AUDIT.info("actor={} target={} outcome=NO_CHANGE mask={}", actor, targetUser, after);
            return;
        }
        AUDIT.info("actor={} target={} outcome=CHANGED granted={} revoked={} before={} after={}",
                actor, targetUser, names(added), names(removed), before, after);
    }

    private static java.util.List<String> names(Set<CartmanTool> tools) {
        return tools.stream()
                .sorted(java.util.Comparator.comparingInt(CartmanTool::index))
                .map(CartmanTool::label)
                .toList();
    }
}
