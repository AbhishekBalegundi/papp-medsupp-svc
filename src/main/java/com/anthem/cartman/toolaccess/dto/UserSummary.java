package com.anthem.cartman.toolaccess.dto;

/**
 * A user in the Utilities picker.
 *
 * @param loginName   {@code TM_Login.Log_Nm}
 * @param displayName real name, falling back to the login name
 * @param loginType   {@code Log_Typ} — Admin, Under, Keyer, Coord, Sales, Audit
 * @param enabled     {@code Log_Enabled} — null is treated as enabled, as in Delphi
 */
public record UserSummary(String loginName, String displayName, String loginType, boolean enabled) {
}
