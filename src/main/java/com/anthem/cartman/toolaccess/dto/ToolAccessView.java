package com.anthem.cartman.toolaccess.dto;

import java.util.List;

/**
 * A user's complete tool access.
 *
 * @param loginName    the user
 * @param displayName  real name for the header
 * @param storedValue  the exact 32-character value held in {@code Log_CartmanTools}
 * @param grantedCount how many tools are granted
 * @param tools        all 32 tools in mask order
 */
public record ToolAccessView(
        String loginName,
        String displayName,
        String storedValue,
        int grantedCount,
        List<ToolStateView> tools) {
}
