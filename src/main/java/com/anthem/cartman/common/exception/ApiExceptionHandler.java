package com.anthem.cartman.common.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.anthem.cartman.login.service.AuthService;
import com.anthem.cartman.toolaccess.service.AdminPasswordGate;
import com.anthem.cartman.users.service.UserManagerService;

/**
 * Turns business exceptions into RFC 9457 problem details.
 *
 * <p>Replaces the Delphi's {@code ShowMessage} dialogs, which reported an error and then
 * carried on regardless ({@code frmToolsAdmin.pas:118-120}). Here a failure is a failure.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Not found");
        pd.setType(URI.create("urn:cartman:not-found"));
        return pd;
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail onValidation(ValidationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid request");
        pd.setType(URI.create("urn:cartman:validation"));
        return pd;
    }

    @ExceptionHandler(com.anthem.cartman.toolaccess.service.AdminPasswordGate.WrongAdminPasswordException.class)
    public ProblemDetail onWrongAdminPassword(
            com.anthem.cartman.toolaccess.service.AdminPasswordGate.WrongAdminPasswordException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Wrong administrator password");
        pd.setType(URI.create("urn:cartman:admin-password"));
        return pd;
    }

    @ExceptionHandler(com.anthem.cartman.toolaccess.service.AdminPasswordGate.AdminGateNotConfiguredException.class)
    public ProblemDetail onAdminGateNotConfigured(
            com.anthem.cartman.toolaccess.service.AdminPasswordGate.AdminGateNotConfiguredException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Administrator password not configured");
        pd.setType(URI.create("urn:cartman:admin-password-unconfigured"));
        return pd;
    }

    /**
     * US-2.6.2 — the LAN ID is taken. 409 rather than 400: the request is well formed and the
     * client may repeat it with {@code confirmDuplicateLanId=true}, which is what the Delphi's
     * Yes/Cancel prompt amounted to.
     */
    @ExceptionHandler(com.anthem.cartman.users.service.UserManagerService.DuplicateLanIdException.class)
    public ProblemDetail onDuplicateLanId(
            com.anthem.cartman.users.service.UserManagerService.DuplicateLanIdException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("LAN ID already exists");
        pd.setType(URI.create("urn:cartman:duplicate-lan-id"));
        pd.setProperty("lanId", ex.getLanId());
        pd.setProperty("confirmable", true);
        return pd;
    }

    @ExceptionHandler(com.anthem.cartman.login.service.AuthService.InvalidCredentialsException.class)
    public ProblemDetail onInvalidCredentials(
            com.anthem.cartman.login.service.AuthService.InvalidCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setTitle("Sign-in failed");
        pd.setType(URI.create("urn:cartman:invalid-credentials"));
        return pd;
    }

    @ExceptionHandler({
            com.anthem.cartman.login.service.AuthService.AccountLockedException.class,
            com.anthem.cartman.login.service.AuthService.AccountDisabledException.class})
    public ProblemDetail onAccountUnavailable(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Account unavailable");
        pd.setType(URI.create("urn:cartman:account-unavailable"));
        return pd;
    }
}
