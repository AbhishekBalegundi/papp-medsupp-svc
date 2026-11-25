package com.anthem.cartman.common.util;

import org.springframework.stereotype.Component;

import com.anthem.cartman.login.controller.AuthController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Who is signed in, for code that needs the name rather than the whole session.
 *
 * <p>The interim sign-in keeps the login name in the HTTP session; it does not populate a
 * {@link java.security.Principal}, because there is no Spring Security filter chain yet.
 * Controllers that asked for a {@code Principal} therefore always saw {@code null} — which is
 * how {@code /api/tool-access/me} came to answer 401 for a perfectly valid session.
 *
 * <p>This is the single place that knows where the current user lives, so when Okta replaces the
 * session with a JWT only this class changes.
 */
@Component
public class CurrentUser {

    /** The signed-in login name, or {@code null} when there is no session. */
    public String nameOrNull(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object name = session == null ? null : session.getAttribute(AuthController.SESSION_USER);
        return name == null ? null : name.toString();
    }

    /** The signed-in login name, or {@code "SYSTEM"} for audit lines when nobody is. */
    public String nameOrSystem(HttpServletRequest request) {
        String name = nameOrNull(request);
        return name == null ? "SYSTEM" : name;
    }
}
