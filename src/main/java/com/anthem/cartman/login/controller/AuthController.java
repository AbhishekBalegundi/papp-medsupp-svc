package com.anthem.cartman.login.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.anthem.cartman.common.util.CurrentUser;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.login.service.AuthService;

/**
 * Interim sign-in endpoints. The session cookie is the whole mechanism — no tokens, no
 * Spring Security — because all of this is replaced by Okta's authorization code flow.
 *
 * <p>The session holds only the login name; every request re-reads the user, so a disabled
 * or retired account stops working immediately rather than at the next sign-in.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Session attribute holding the signed-in login name. */
    public static final String SESSION_USER = "cartman.user";

    private final AuthService auth;
    private final LoginLookup logins;

    public AuthController(AuthService auth, LoginLookup logins) {
        this.auth = auth;
        this.logins = logins;
    }

    @PostMapping("/login")
    public CurrentUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        Login login = auth.authenticate(request.loginName(), request.password());

        // New session on every sign-in, so a pre-existing session id cannot be fixated.
        HttpSession existing = http.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        http.getSession(true).setAttribute(SESSION_USER, login.getLoginName());

        return CurrentUser.of(login);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    /** Who is signed in; 401 when nobody is, or when the account no longer qualifies. */
    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Object name = session == null ? null : session.getAttribute(SESSION_USER);
        if (name == null) {
            return ResponseEntity.status(401).build();
        }
        return logins.findByLoginNameIgnoreCase(name.toString())
                .filter(Login::isEnabled)
                .map(login -> ResponseEntity.ok(CurrentUser.of(login)))
                .orElseGet(() -> {
                    session.invalidate();
                    return ResponseEntity.status(401).build();
                });
    }

    public record LoginRequest(@NotBlank String loginName, @NotBlank String password) {
    }

    /** Never carries the password — only what the shell needs to show. */
    public record CurrentUser(String loginName, String displayName, String loginType) {
        static CurrentUser of(Login login) {
            return new CurrentUser(login.getLoginName(), login.displayName(), login.getLoginType());
        }
    }
}
