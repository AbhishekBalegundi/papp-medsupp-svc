package com.anthem.cartman.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.users.dto.PreferencesViews.MailResult;
import com.anthem.cartman.users.service.AccountRefreshMailService;
import com.anthem.cartman.users.service.MailRelay;

/**
 * US-2.14 with a fake relay, so the wire contract is asserted without a mail host: from address,
 * subject (with the Delphi's leading space), the body wording verbatim, and the 'successful'
 * acknowledgement rule.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class AccountRefreshMailServiceTest {

    /** Captures what would have gone to the relay and answers what the test tells it to. */
    static class FakeRelay implements MailRelay {
        boolean configured = true;
        String response = "Message send successful";
        String from;
        String to;
        String subject;
        String body;
        String attachmentName;

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public String send(String fromAddress, String toAddress, String subject, String htmlBody,
                           String attachmentName, byte[] attachment) {
            this.from = fromAddress;
            this.to = toAddress;
            this.subject = subject;
            this.body = htmlBody;
            this.attachmentName = attachmentName;
            return response;
        }
    }

    @TestConfiguration
    static class RelayConfig {
        @Bean
        @Primary
        FakeRelay fakeRelay() {
            return new FakeRelay();
        }
    }

    @Autowired
    private AccountRefreshMailService service;

    @Autowired
    private FakeRelay relay;

    @org.junit.jupiter.api.BeforeEach
    void resetRelay() {
        relay.configured = true;
        relay.response = "Message send successful";
    }

    @Test
    @DisplayName("the message carries the Delphi's from, subject and wording verbatim")
    void sendsTheDelphiMessage() {
        relay.response = "ok: successful";

        MailResult result = service.send("DStante", "someone@example.com");

        assertEquals("CosmoSupport@anthem.com", relay.from);
        assertEquals(" Cosmo account refresh", relay.subject); // the leading space is compiled in
        assertEquals("someone@example.com", relay.to);
        assertTrue(relay.body.startsWith("Hello.  Your last successful login was on "));
        assertTrue(relay.body.contains("Anthemystems")); // the Delphi's typo, kept verbatim (A40)
        assertFalse(result.attachmentSent()); // no form file configured in tests
    }

    @Test
    @DisplayName("US-2.14.3 — the body quotes the most recent Successful sign-in")
    void quotesLastSuccessfulLogin() {
        relay.response = "successful";

        MailResult result = service.send("DStante", "someone@example.com");

        // DStante's newest attempt is a failure; the newest success is two days ago (seed).
        assertFalse(result.lastSuccessfulLogin().isBlank());
        assertTrue(relay.body.contains(result.lastSuccessfulLogin()));
    }

    @Test
    @DisplayName("an empty address sends nothing, as the Delphi's empty InputBox did")
    void refusesEmptyAddress() {
        assertThrows(ValidationException.class, () -> service.send("DStante", "  "));
    }

    @Test
    @DisplayName("without a relay configured the failure names both configuration sources")
    void refusesWhenUnconfigured() {
        relay.configured = false;

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.send("DStante", "someone@example.com"));
        assertTrue(thrown.getMessage().contains("CC_EmailHost"));
        assertTrue(thrown.getMessage().contains("CARTMAN_MAIL_RELAY_URL"));
    }

    @Test
    @DisplayName("a relay answer without 'successful' is an error, not a silent success")
    void surfacesRelayRefusal() {
        relay.response = "queue full";

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.send("DStante", "someone@example.com"));
        assertTrue(thrown.getMessage().contains("queue full"));
    }
}
