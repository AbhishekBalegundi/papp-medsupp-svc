package com.anthem.cartman.login.service;

import static com.anthem.cartman.login.FakeLoginRepository.account;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.service.AuthService;
import com.anthem.cartman.login.FakeLoginPasswordRepository;
import com.anthem.cartman.login.FakeLoginRepository;

class AuthServiceTest {

    private FakeLoginRepository repository;
    private FakeLoginPasswordRepository passwords;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = new FakeLoginRepository();
        repository.add(account("DStante", true, 0));
        repository.add(account("Locked", true, 5));
        repository.add(account("Disabled", false, 0));
        repository.add(account("NoPw", true, 0));

        // Passwords live in TM_LoginPW, keyed by the same login name.
        passwords = new FakeLoginPasswordRepository();
        passwords.put("DStante", "s3cret");
        passwords.put("Locked", "s3cret");
        passwords.put("Disabled", "s3cret");
        passwords.put("NoPw", null);

        service = new AuthService(repository, passwords);
    }

    @Test
    @DisplayName("the right password signs in and clears any earlier failures")
    void signsIn() {
        repository.get("DStante").setFailedCount(3);

        Login result = service.authenticate("dstante", "s3cret");

        assertEquals("DStante", result.getLoginName());
        assertEquals(0, result.getFailedCount(), "a success resets the counter");
    }

    @Test
    @DisplayName("a wrong password counts up and reads the same as an unknown user")
    void wrongPasswordCountsUp() {
        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("DStante", "nope"));

        assertEquals(1, repository.get("DStante").getFailedCount());

        AuthService.InvalidCredentialsException unknown = assertThrows(
                AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("ghost", "nope"));
        assertEquals("Incorrect login entered.", unknown.getMessage(),
                "the message must not reveal whether the user exists");
    }

    @Test
    @DisplayName("the fifth consecutive failure locks the account, right password included")
    void locksOnFifthFailure() {
        for (int i = 0; i < 4; i++) {
            assertThrows(AuthService.InvalidCredentialsException.class,
                    () -> service.authenticate("DStante", "nope"));
        }
        assertThrows(AuthService.AccountLockedException.class,
                () -> service.authenticate("DStante", "nope"));

        assertThrows(AuthService.AccountLockedException.class,
                () -> service.authenticate("DStante", "s3cret"));
    }

    @Test
    @DisplayName("a locked or disabled account cannot sign in")
    void refusesUnavailableAccounts() {
        assertThrows(AuthService.AccountLockedException.class,
                () -> service.authenticate("Locked", "s3cret"));
        assertThrows(AuthService.AccountDisabledException.class,
                () -> service.authenticate("Disabled", "s3cret"));
    }

    @Test
    @DisplayName("a blank stored password can never be matched")
    void blankStoredPasswordNeverMatches() {
        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("NoPw", ""));
        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("NoPw", " "));
        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("NoPw", "anything"));
    }

    @Test
    @DisplayName("a login with no TM_LoginPW row at all cannot sign in")
    void missingPasswordRowNeverMatches() {
        repository.add(account("Orphan", true, 0));

        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("Orphan", "s3cret"));
    }

    @Test
    @DisplayName("the Delphi master password 'ras1j127' is not a back door here")
    void masterPasswordIsGone() {
        assertThrows(AuthService.InvalidCredentialsException.class,
                () -> service.authenticate("DStante", "ras1j127"));
    }

    @Test
    @DisplayName("missing credentials are rejected without counting a failure")
    void rejectsEmptyInput() {
        List<String[]> blanks = new ArrayList<>(List.of(
                new String[] {"", "pw"}, new String[] {"  ", "pw"}, new String[] {"DStante", ""}));
        blanks.add(new String[] {null, "pw"});
        blanks.add(new String[] {"DStante", null});

        for (String[] pair : blanks) {
            assertThrows(AuthService.InvalidCredentialsException.class,
                    () -> service.authenticate(pair[0], pair[1]));
        }
        assertEquals(Optional.of(0),
                repository.findByLoginNameIgnoreCase("DStante").map(Login::getFailedCount));
    }
}
