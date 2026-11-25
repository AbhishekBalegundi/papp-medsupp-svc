package com.anthem.cartman.login.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.entity.LoginPassword;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.login.repository.LoginPasswordLookup;

/**
 * Interim sign-in against {@code TM_Login}, replacing the Delphi login form.
 *
 * <p><strong>This is scaffolding, not a security design.</strong> It exists so the app has a
 * current user before Okta arrives, and it authenticates the only way the existing data allows:
 * {@code TM_LoginPW.Lpw_Pw} holds passwords in clear (verified — all 314 populated rows are
 * printable ASCII), so the check is a comparison, exactly as {@code LoginForm.pas:347} did.
 * Hashing the column would lock every existing user out, so phase 1 leaves the storage alone
 * and Okta removes it entirely.
 *
 * <p>Note the password does <em>not</em> live in {@code TM_Login.Log_Pw}: that column is empty
 * in all 320 live rows. The Delphi read a field named {@code Log_Pw} returned by the
 * {@code spLogin2} procedure, which is an alias, not that column.
 *
 * <p>Two Delphi behaviours are deliberately <em>not</em> reproduced:
 * <ul>
 *   <li>the master password {@code 'ras1j127'}, which let anyone sign in as any user
 *       ({@code LoginForm.pas:347}) — a back door, not a feature;</li>
 *   <li>mailing a forgotten password back to the user in plain text.</li>
 * </ul>
 *
 * <p>Kept: the lockout at five consecutive failures ({@code Log_Count}), which is a real
 * control, and the disabled-account check.
 */
@Service
public class AuthService {

    /** Delphi locked the account on the fifth consecutive failure. */
    static final int MAX_FAILURES = 5;

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    private final LoginLookup logins;
    private final LoginPasswordLookup passwords;

    public AuthService(LoginLookup logins, LoginPasswordLookup passwords) {
        this.logins = logins;
        this.passwords = passwords;
    }

    /**
     * Verifies credentials and returns the authenticated user.
     *
     * <p>Failure messages are deliberately identical for "no such user" and "wrong password"
     * so the response cannot be used to enumerate login names.
     */
    @Transactional
    public Login authenticate(String loginName, String password) {
        if (loginName == null || loginName.isBlank() || password == null || password.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        Login login = logins.findByLoginNameIgnoreCase(loginName.trim())
                .orElseThrow(() -> {
                    AUDIT.info("login failed: unknown user {}", loginName.trim());
                    return new InvalidCredentialsException();
                });

        if (login.getFailedCount() >= MAX_FAILURES) {
            AUDIT.info("login blocked: {} is locked out", login.getLoginName());
            throw new AccountLockedException();
        }
        if (!login.isEnabled()) {
            AUDIT.info("login blocked: {} is disabled", login.getLoginName());
            throw new AccountDisabledException();
        }

        String stored = passwords.findByLoginNameIgnoreCase(login.getLoginName())
                .map(LoginPassword::getPassword)
                .orElse(null);

        if (!matches(stored, password)) {
            int failures = login.getFailedCount() + 1;
            login.setFailedCount(failures);
            logins.save(login);
            AUDIT.info("login failed: {} ({} consecutive)", login.getLoginName(), failures);
            throw failures >= MAX_FAILURES ? new AccountLockedException() : new InvalidCredentialsException();
        }

        if (login.getFailedCount() != 0) {
            login.setFailedCount(0);
            logins.save(login);
        }
        AUDIT.info("login ok: {}", login.getLoginName());
        return login;
    }

    /** Constant-time comparison; a null or blank stored password can never be matched. */
    private static boolean matches(String stored, String candidate) {
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                candidate.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Incorrect login entered.");
        }
    }

    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException() {
            super("Five consecutive login failures. Contact Cosmo support for assistance.");
        }
    }

    public static class AccountDisabledException extends RuntimeException {
        public AccountDisabledException() {
            super("This account is disabled.");
        }
    }
}
