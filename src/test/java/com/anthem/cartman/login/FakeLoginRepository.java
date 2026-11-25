package com.anthem.cartman.login;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.data.domain.Pageable;
import java.util.Map;
import java.util.Optional;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;

/**
 * In-memory stand-in for the JPA repository, shared by the service tests.
 *
 * <p>It lives in the {@code login} package so it can build {@link Login} fixtures without
 * reflection into another package, and so tests of services that only need the
 * {@link LoginLookup} port can run with no Spring context and no database.
 */
public final class FakeLoginRepository implements LoginLookup {

    private final Map<String, Login> store = new LinkedHashMap<>();

    public void add(Login login) {
        store.put(key(login.getLoginName()), login);
    }

    public Login get(String loginName) {
        return store.get(key(loginName));
    }

    /** Builds a login without touching the database. Any argument may be null. */
    public static Login login(String loginName, String firstName, String lastName,
                              String loginType, String cartmanTools) {
        Login login = new Login();
        set(login, "loginName", loginName);
        set(login, "firstName", firstName);
        set(login, "lastName", lastName);
        set(login, "loginType", loginType);
        login.setCartmanTools(cartmanTools);
        return login;
    }

    /** Adds the account fields the auth tests need on top of {@link #login}. */
    public static Login account(String loginName, boolean enabled, int failures) {
        Login login = login(loginName, loginName, null, "Admin", null);
        set(login, "enabled", enabled ? 1 : 0);
        login.setFailedCount(failures);
        return login;
    }

    public static void set(Login login, String field, Object value) {
        try {
            var f = Login.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(login, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no such field on Login: " + field, e);
        }
    }

    private static String key(String loginName) {
        return loginName.toUpperCase();
    }

    @Override
    public List<Login> findAllByOrderByLoginNameAsc() {
        List<Login> all = new ArrayList<>(store.values());
        all.sort(Comparator.comparing(Login::getLoginName));
        return all;
    }

    @Override
    public Optional<Login> findByLoginNameIgnoreCase(String loginName) {
        return Optional.ofNullable(store.get(key(loginName)));
    }

    @Override
    public <S extends Login> S save(S entity) {
        store.put(key(entity.getLoginName()), entity);
        return entity;
    }

    /** The same filter the JPQL applies, so a service test sees the same rows as production. */
    @Override
    public List<Login> findPage(String needle, Pageable pageable) {
        List<Login> matched = findAllByOrderByLoginNameAsc().stream()
                .filter(l -> matches(l, needle))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), matched.size());
        int to = Math.min(from + pageable.getPageSize(), matched.size());
        return matched.subList(from, to);
    }

    @Override
    public long countPage(String needle) {
        return findAllByOrderByLoginNameAsc().stream().filter(l -> matches(l, needle)).count();
    }

    private static boolean matches(Login l, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        String n = needle.trim().toLowerCase(java.util.Locale.ROOT);
        return String.join(" ",
                        l.getLoginName() == null ? "" : l.getLoginName(),
                        l.getFirstName() == null ? "" : l.getFirstName(),
                        l.getLastName() == null ? "" : l.getLastName())
                .toLowerCase(java.util.Locale.ROOT).contains(n);
    }
}
