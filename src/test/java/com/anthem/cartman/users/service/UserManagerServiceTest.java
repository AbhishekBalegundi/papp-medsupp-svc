package com.anthem.cartman.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.login.repository.LoginPasswordRepository;
import com.anthem.cartman.users.service.UserManagerService.NewUser;
import com.anthem.cartman.users.dto.UserViews.HistoryPage;
import com.anthem.cartman.users.dto.UserViews.LoginAttempt;
import com.anthem.cartman.users.dto.UserViews.UserDetail;
import com.anthem.cartman.users.repository.UserAliasRepository;
import com.anthem.cartman.users.service.UserManagerService;

/**
 * Module 2 against the seeded H2 schema. Uses the database rather than fakes because the
 * behaviour under test is mostly the queries themselves — ordering, paging and cascade.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class UserManagerServiceTest {

    @Autowired
    private UserManagerService service;

    @Autowired
    private LoginLookup logins;

    @Autowired
    private LoginPasswordRepository passwords;

    @Autowired
    private UserAliasRepository aliases;

    @Nested
    @DisplayName("US-2.1 — the user list")
    class Listing {

        @Test
        @DisplayName("returns every login, ordered by name")
        void listsInOrder() {
            List<String> names = service.listUsers().stream()
                    .map(u -> u.loginName())
                    .toList();

            assertEquals(7, names.size());
            assertEquals(names.stream().sorted().toList(), names, "the list must be ordered");
        }

        @Test
        @DisplayName("carries the columns the Delphi list defined but never filled")
        void carriesNameColumns() {
            var darron = service.listUsers().stream()
                    .filter(u -> u.loginName().equals("DStante")).findFirst().orElseThrow();

            assertEquals("Admin", darron.loginType());
            assertEquals("Darron", darron.firstName());
            assertEquals("Stante", darron.lastName());
            assertTrue(darron.enabled());
        }
    }

    @Nested
    @DisplayName("US-2.2 — the properties panel")
    class Properties {

        @Test
        @DisplayName("shows every profile field, trimmed")
        void showsProfile() {
            UserDetail d = service.getUser("DStante");

            assertEquals("DStante", d.loginName());
            assertEquals("Darron Stante", d.displayName());
            assertEquals("Admin", d.loginType());
            assertEquals("U", d.department());
            assertEquals("A Manager", d.manager());
            assertEquals("3175551000", d.phone());
            assertEquals("101", d.extension());
            assertEquals("darron.stante@elevancehealth.com", d.email());
            assertEquals("AB12345", d.lanId());
            assertEquals("Cosmo", d.lastSystem());
            assertEquals("Systems Analyst", d.title());
        }

        @Test
        @DisplayName("shows password dates from TM_LoginPW")
        void showsPasswordDates() {
            UserDetail d = service.getUser("DStante");

            assertNotNull(d.passwordExpiresOn());
            assertNotNull(d.passwordLastChangedOn());
        }

        @Test
        @DisplayName("lists the five most recent attempts, newest first")
        void showsFiveMostRecent() {
            List<LoginAttempt> recent = service.getUser("DStante").recentHistory();

            assertEquals(5, recent.size(), "the panel shows five, though the user has six");
            for (int i = 1; i < recent.size(); i++) {
                assertTrue(recent.get(i - 1).attemptedAt().isAfter(recent.get(i).attemptedAt()),
                        "attempts must be newest first");
            }
        }

        @Test
        @DisplayName("last good login skips the newer failure")
        void lastGoodLoginSkipsFailures() {
            UserDetail d = service.getUser("DStante");

            assertNotNull(d.lastGoodLogin());
            assertEquals(d.recentHistory().get(1).attemptedAt(), d.lastGoodLogin(),
                    "the newest row is a failure, so the second row is the last good login");
        }

        @Test
        @DisplayName("last good login is empty when none of the five succeeded")
        void lastGoodLoginEmptyWhenNoSuccess() {
            assertNull(service.getUser("CosmoAdm").lastGoodLogin());
        }

        @Test
        @DisplayName("lists the aliases the user owns, not the ones pointing at them")
        void listsOwnedAliases() {
            assertEquals(List.of("DSTANTE2", "DStante"), service.getUser("DStante").aliases());

            // CosmoAdm owns an alias of TenCharUsr; TenCharUsr must not see it as their own.
            assertEquals(List.of("TenCharUsr"), service.getUser("TenCharUsr").aliases());
        }

        @Test
        @DisplayName("an unknown user is a not-found, not an empty panel")
        void unknownUser() {
            assertThrows(NotFoundException.class, () -> service.getUser("nobody"));
        }
    }

    @Nested
    @DisplayName("US-2.6 — creating a user")
    class Creating {

        private NewUser request(String id, String lanId) {
            return new NewUser(id, "Welcome1", "New", "Joiner", "Under - Underwriter",
                    "U - Under 65", "A Manager", "3175559999", "202",
                    "new.joiner@elevancehealth.com", lanId);
        }

        @Test
        @DisplayName("writes the login, the password row and the self-alias together")
        void createsAllThree() {
            UserDetail created = service.createUser(request("NewJoiner", "LAN99999"), false);

            assertEquals("NewJoiner", created.loginName());
            assertTrue(passwords.findByLoginNameIgnoreCase("NewJoiner").isPresent());
            assertEquals(List.of("NewJoiner"), created.aliases());
        }

        @Test
        @DisplayName("stores Role truncated to five characters and Department to one")
        void truncatesPickerLabels() {
            UserDetail created = service.createUser(request("Trunc1", "LAN88888"), false);

            assertEquals("Under", created.loginType());
            assertEquals("U", created.department());
        }

        @Test
        @DisplayName("a duplicate LAN ID is refused until confirmed, then accepted")
        void duplicateLanIdNeedsConfirmation() {
            assertThrows(UserManagerService.DuplicateLanIdException.class,
                    () -> service.createUser(request("Dup1", "LAN00001"), false));

            UserDetail created = service.createUser(request("Dup1", "LAN00001"), true);
            assertEquals("LAN00001", created.lanId());
        }

        @Test
        @DisplayName("a duplicate login name is refused outright")
        void duplicateLoginName() {
            assertThrows(ValidationException.class,
                    () -> service.createUser(request("DStante", "LAN77777"), false));
        }

        @Test
        @DisplayName("a login name longer than the column is refused")
        void loginNameTooLong() {
            assertThrows(ValidationException.class,
                    () -> service.createUser(request("ElevenChars", "LAN66666"), false));
        }

        @Test
        @DisplayName("missing required fields are refused")
        void requiredFields() {
            assertThrows(ValidationException.class, () -> service.createUser(
                    new NewUser("", "pw", "First", null, null, null, null, null, null, null, null), false));
            assertThrows(ValidationException.class, () -> service.createUser(
                    new NewUser("Someone", "", "First", null, null, null, null, null, null, null, null), false));
            assertThrows(ValidationException.class, () -> service.createUser(
                    new NewUser("Someone", "pw", " ", null, null, null, null, null, null, null, null), false));
        }
    }

    @Nested
    @DisplayName("US-2.9 — deleting a user")
    class Deleting {

        @Test
        @DisplayName("removes the login and everything keyed to it")
        void cascades() {
            var report = service.deleteUser("TenCharUsr");

            assertEquals("TenCharUsr", report.loginName());
            assertEquals(1, report.permissions());
            assertEquals(1, report.signatures());
            assertEquals(2, report.notes()); // TenCharUsr now seeds two note lines (US-2.15)
            assertTrue(logins.findByLoginNameIgnoreCase("TenCharUsr").isEmpty());
            assertTrue(passwords.findByLoginNameIgnoreCase("TenCharUsr").isEmpty());
        }

        @Test
        @DisplayName("clears aliases the user owns AND aliases pointing at them")
        void clearsBothAliasSides() {
            // Seed: TenCharUsr owns one alias, and CosmoAdm holds TenCharUsr as an alias.
            assertEquals(2, service.deleteUser("TenCharUsr").aliases());

            assertTrue(aliases.findOwnedBy("TenCharUsr").isEmpty());
            assertTrue(aliases.findOwnedBy("CosmoAdm").isEmpty(),
                    "the row where CosmoAdm pointed at the deleted user must be gone too");
        }

        @Test
        @DisplayName("deleting an unknown user is a not-found, and changes nothing")
        void unknownUser() {
            assertThrows(NotFoundException.class, () -> service.deleteUser("nobody"));
            assertEquals(7, service.listUsers().size());
        }
    }

    @Nested
    @DisplayName("US-2.11 / US-2.12 — history")
    class History {

        @Test
        @DisplayName("only users who have signed in appear")
        void onlySignedInUsers() {
            HistoryPage page = service.historyPage(null, 50);

            assertEquals(List.of("CosmoAdm", "DStante", "NoTools", "SITUW", "TenCharUsr"),
                    page.loginNames());
            assertFalse(page.hasNext());
        }

        @Test
        @DisplayName("a page is limited to the requested size and reports more to come")
        void pagesForward() {
            HistoryPage first = service.historyPage(null, 2);

            assertEquals(List.of("CosmoAdm", "DStante"), first.loginNames());
            assertTrue(first.hasNext());
        }

        @Test
        @DisplayName("a letter jumps to the first name at or after it")
        void jumpsToLetter() {
            assertEquals(List.of("SITUW", "TenCharUsr"), service.historyPage("S", 5).loginNames());
        }

        @Test
        @DisplayName("scrolling on continues past the last name shown, without repeating it")
        void pagesAfter() {
            HistoryPage first = service.historyPage(null, 2);
            assertEquals(List.of("CosmoAdm", "DStante"), first.loginNames());

            HistoryPage next = service.historyPageAfter("DStante", 2);

            assertEquals(List.of("NoTools", "SITUW"), next.loginNames(),
                    "the name at the foot of the list must not appear again at the head of the next");
            assertTrue(next.hasNext());
        }

        @Test
        @DisplayName("a letter jump stays inclusive, so it lands on the letter itself")
        void jumpAndScrollDifferInInclusiveness() {
            // The distinction the two methods exist for: a jump to "S" shows SITUW, whereas
            // scrolling on from "SITUW" shows what follows it.
            assertEquals(List.of("SITUW", "TenCharUsr"), service.historyPage("SITUW", 5).loginNames());
            assertEquals(List.of("TenCharUsr"), service.historyPageAfter("SITUW", 5).loginNames());
        }

        @Test
        @DisplayName("scrolling from nothing starts at the beginning")
        void pagesAfterNothing() {
            assertEquals(service.historyPage(null, 2).loginNames(),
                    service.historyPageAfter("  ", 2).loginNames());
        }

        @Test
        @DisplayName("the page size is clamped, so a bad client value cannot ask for everything")
        void clampsPageSize() {
            assertEquals(1, service.historyPage(null, 0).pageSize());
            assertEquals(200, service.historyPage(null, 5000).pageSize());
        }

        @Test
        @DisplayName("a user's full history is newest first, with S rendered as Successful")
        void fullHistory() {
            List<LoginAttempt> all = service.loginHistory("DStante");

            assertEquals(6, all.size(), "the full list is not capped at five");
            assertEquals("Failed", all.get(0).status());
            assertEquals("Successful", all.get(1).status());
            assertTrue(all.get(1).successful());
        }

        @Test
        @DisplayName("lower-case s still counts as a success")
        void statusIsCaseInsensitive() {
            assertTrue(service.loginHistory("NoTools").get(0).successful());
        }
    }
}
