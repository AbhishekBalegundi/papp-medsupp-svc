package com.anthem.cartman.users.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.users.dto.PreferencesViews.MailResult;
import com.anthem.cartman.users.entity.LoginHistory;
import com.anthem.cartman.users.repository.LoginHistoryRepository;

/**
 * US-2.14 — the account-refresh e-mail the History tab's toolbar button sends
 * ({@code btnEMailClick}, {@code frmUserManager.pas:593-679}).
 *
 * <p>The wording is the Delphi's verbatim — including its typos ("Anthemystems"), recorded as
 * anomaly A40 so nobody mistakes them for ours. The last successful login is read from
 * {@code TM_Login_History} here rather than scraped off the on-screen grid, which is the same
 * data by a sturdier route.
 */
@Service
public class AccountRefreshMailService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    /** From address and subject exactly as compiled into the Delphi (yes, the leading space). */
    static final String FROM_ADDRESS = "CosmoSupport@anthem.com";
    static final String SUBJECT = " Cosmo account refresh";
    static final String ATTACHMENT_NAME = "New User.xlsx";

    private static final DateTimeFormatter GRID_DATE =
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss");

    private final MailRelay relay;
    private final LoginHistoryRepository history;
    private final String attachmentPath;

    public AccountRefreshMailService(MailRelay relay, LoginHistoryRepository history,
                                     @Value("${cartman.mail.new-user-form:}") String attachmentPath) {
        this.relay = relay;
        this.history = history;
        this.attachmentPath = attachmentPath;
    }

    @Transactional(readOnly = true)
    public MailResult send(String loginName, String toAddress) {
        // The Delphi's InputBox simply did nothing on an empty address; an API says so.
        if (toAddress == null || toAddress.isBlank()) {
            throw new ValidationException("An e-mail address is required.");
        }
        if (!relay.configured()) {
            throw new ValidationException(
                    "No mail relay is configured: TM_Cosmo_Config.CC_EmailHost is absent and "
                            + "CARTMAN_MAIL_RELAY_URL is not set.");
        }

        // The most recent Successful attempt — what the Delphi took from the top of the grid.
        String lastLogin = history.findAttempts(loginName).stream()
                .filter(LoginHistory::successful)
                .findFirst()
                .map(h -> GRID_DATE.format(h.getAttemptedAt()))
                .orElse("");

        String body = "Hello.  Your last successful login was on " + lastLogin
                + ".  As all Anthemystems are required to remove any login not utilized for 30 or "
                + "more days, your login has expired.  "
                + "Please complete the attached form and have your manager send it back to us with "
                + "approval and we will get you back in as soon as possible. Please make sure to "
                + "read and answer each form field.<p><p>"
                + "Thanks, <p>"
                + "CosmoSupport";

        byte[] attachment = loadAttachment();
        String response = relay.send(FROM_ADDRESS, toAddress.trim(), SUBJECT, body,
                attachment == null ? null : ATTACHMENT_NAME, attachment);

        // 'successful' anywhere in the response text is the relay's yes (frmUserManager.pas:659).
        if (response == null
                || !response.toLowerCase(Locale.ROOT).contains("successful")) {
            throw new ValidationException("The mail relay did not accept the message: "
                    + (response == null ? "(no response)" : response));
        }

        AUDIT.info("account-refresh e-mail for {} sent to {}", loginName, toAddress.trim());
        return new MailResult(toAddress.trim(), lastLogin, attachment != null, response);
    }

    /** {@code New User.xlsx} sat next to the executable; here its path is configuration. */
    private byte[] loadAttachment() {
        if (attachmentPath == null || attachmentPath.isBlank()) {
            return null;
        }
        try {
            return Files.readAllBytes(Path.of(attachmentPath));
        } catch (IOException e) {
            return null; // send without the form rather than not at all; the result says so
        }
    }
}
