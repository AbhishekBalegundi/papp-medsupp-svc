package com.anthem.cartman.login;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.anthem.cartman.login.entity.LoginPassword;
import com.anthem.cartman.login.repository.LoginPasswordLookup;

/** In-memory stand-in for {@code TM_LoginPW}, so the auth tests need no database. */
public final class FakeLoginPasswordRepository implements LoginPasswordLookup {

    private final Map<String, LoginPassword> store = new LinkedHashMap<>();

    /** Records a password for a login. A null password models a row with none. */
    public void put(String loginName, String password) {
        LoginPassword entry = new LoginPassword();
        set(entry, "loginName", loginName);
        set(entry, "password", password);
        set(entry, "changeDue", LocalDateTime.now().plusDays(30));
        store.put(loginName.toUpperCase(), entry);
    }

    @Override
    public Optional<LoginPassword> findByLoginNameIgnoreCase(String loginName) {
        return Optional.ofNullable(store.get(loginName.toUpperCase()));
    }

    private static void set(LoginPassword entry, String field, Object value) {
        try {
            var f = LoginPassword.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(entry, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no such field on LoginPassword: " + field, e);
        }
    }
}
