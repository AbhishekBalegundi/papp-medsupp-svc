package com.anthem.cartman.toolaccess.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Cosmo System Administrator password gate for the Tools Administrator screen.
 *
 * <p>The Delphi compared the typed password against a literal in the source
 * ({@code frmToolsAdmin.pas:52}, {@code 'ras1j127'}) and only on the client — the UPDATE that
 * followed was not protected at all. Here the password comes from the {@code CARTMAN_ADMIN_PASSWORD}
 * environment variable and is enforced on the server: the UI unlock is cosmetic, the check on
 * every mutation is what actually protects the data.
 *
 * <p>When the variable is not set, every check fails — there is no default and no back door.
 * Comparison is constant-time so timing cannot leak how much of a guess was right.
 *
 * <p>This whole class retires when Okta lands: the gate becomes a
 * {@code CARTMAN_TOOL_ADMIN} authority.
 */
@Component
public class AdminPasswordGate {

    private final byte[] expected;

    public AdminPasswordGate(@Value("${cartman.admin-password:}") String adminPassword) {
        this.expected = adminPassword == null || adminPassword.isBlank()
                ? null
                : adminPassword.getBytes(StandardCharsets.UTF_8);
    }

    /** True when a password is configured, i.e. the gate can be passed at all. */
    public boolean configured() {
        return expected != null;
    }

    /** Constant-time match; always false when no password is configured. */
    public boolean matches(String candidate) {
        if (expected == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gate for mutations: passes silently or throws the failure the API reports.
     *
     * @throws AdminGateNotConfiguredException when no password is set on the server
     * @throws WrongAdminPasswordException     when the candidate does not match
     */
    public void require(String candidate) {
        if (!configured()) {
            throw new AdminGateNotConfiguredException();
        }
        if (!matches(candidate)) {
            throw new WrongAdminPasswordException();
        }
    }

    public static class AdminGateNotConfiguredException extends RuntimeException {
        public AdminGateNotConfiguredException() {
            super("The administrator password is not configured on the server. "
                    + "Set the CARTMAN_ADMIN_PASSWORD environment variable.");
        }
    }

    public static class WrongAdminPasswordException extends RuntimeException {
        public WrongAdminPasswordException() {
            super("Wrong administrator password.");
        }
    }
}
