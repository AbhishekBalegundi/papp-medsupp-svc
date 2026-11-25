package com.anthem.cartman.users.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.entity.LoginPassword;
import com.anthem.cartman.login.repository.LoginPasswordRepository;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.login.repository.LoginRepository;
import com.anthem.cartman.users.dto.UserViews.HistoryPage;
import com.anthem.cartman.users.dto.UserViews.LoginAttempt;
import com.anthem.cartman.users.dto.UserViews.UserDetail;
import com.anthem.cartman.users.dto.UserViews.UserListItem;
import com.anthem.cartman.users.entity.LoginHistory;
import com.anthem.cartman.users.entity.UserAlias;
import com.anthem.cartman.users.repository.LoginHistoryRepository;
import com.anthem.cartman.users.repository.UserAliasRepository;
import com.anthem.cartman.users.repository.UserDeletionRepository;

/**
 * Module 2 — User Manager (US-2.1 to US-2.14).
 *
 * <p>Two places where this deliberately differs from the Delphi, both recorded in the
 * requirements as anomalies rather than behaviour worth keeping:
 *
 * <ul>
 *   <li><b>A6</b> — create and delete each ran five or six unwrapped statements. Both are
 *       {@code @Transactional} here, so a mid-way failure cannot leave a half-built user.</li>
 *   <li><b>A5</b> — the Delphi deleted aliases by {@code UA_Alias} while the screen read them by
 *       {@code UA_Log_Id}, leaving rows owned by a login that no longer existed. Delete now
 *       clears both sides.</li>
 * </ul>
 */
@Service
public class UserManagerService {

    /** US-2.2.3 — the properties panel shows five attempts. */
    private static final Limit RECENT_HISTORY = Limit.of(5);

    /** Guard on the History tab's client-supplied page size. */
    private static final int MAX_PAGE_SIZE = 200;

    /** US-2.6.3 — the Delphi wrote this literal into a NOT NULL column. */
    static final String PLACEHOLDER_ABBREV_ID = "xxx";

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    /** Reads and writes go through the port; {@link LoginRepository} adds delete. */
    private final LoginLookup logins;
    private final LoginRepository loginStore;
    private final LoginPasswordRepository passwords;
    private final LoginHistoryRepository history;
    private final UserAliasRepository aliases;
    private final UserDeletionRepository deletions;

    public UserManagerService(LoginRepository logins, LoginPasswordRepository passwords,
                              LoginHistoryRepository history, UserAliasRepository aliases,
                              UserDeletionRepository deletions) {
        this.logins = logins;
        this.loginStore = logins;
        this.passwords = passwords;
        this.history = history;
        this.aliases = aliases;
        this.deletions = deletions;
    }

    // ── Current Users tab ─────────────────────────────────────────

    /** US-2.1 — every login, ordered by name. */
    @Transactional(readOnly = true)
    public List<UserListItem> listUsers() {
        return logins.findAllByOrderByLoginNameAsc().stream()
                .map(UserManagerService::listItem)
                .toList();
    }

    /**
     * US-2.1, paged. The grid draws an icon per user, so a full 320-row load is 2,200 SVG nodes;
     * a page keeps the DOM proportional to what is on screen. `search` matches the login name or
     * either part of the real name.
     */
    @Transactional(readOnly = true)
    public PageView<UserListItem> listUsers(String search, int page, int size) {
        String needle = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        int clamped = PageView.clampSize(size);
        int index = Math.max(0, page);
        long total = logins.countPage(needle);
        List<UserListItem> rows = logins.findPage(needle, PageRequest.of(index, clamped)).stream()
                .map(UserManagerService::listItem)
                .toList();
        return PageView.of(rows, index, clamped, total);
    }

    private static UserListItem listItem(Login l) {
        return new UserListItem(l.getLoginName(), l.getLoginType(),
                l.getFirstName() == null ? null : l.getFirstName().trim(),
                l.getLastName() == null ? null : l.getLastName().trim(),
                l.isEnabled());
    }

    /** US-2.2 — everything the properties panel shows. */
    @Transactional(readOnly = true)
    public UserDetail getUser(String loginName) {
        Login login = require(loginName);
        String name = login.getLoginName();

        Optional<LoginPassword> password = passwords.findByLoginNameIgnoreCase(name);

        List<LoginAttempt> recent = history.findAttempts(name, RECENT_HISTORY).stream()
                .map(UserManagerService::toAttempt)
                .toList();

        // US-2.2.4 — the newest success *among those five*, not across the whole table.
        LocalDateTime lastGood = recent.stream()
                .filter(LoginAttempt::successful)
                .map(LoginAttempt::attemptedAt)
                .findFirst()
                .orElse(null);

        List<String> aliasNames = aliases.findOwnedBy(name).stream()
                .map(UserAlias::getAlias)
                .toList();

        return new UserDetail(
                name,
                login.displayName(),
                trimmed(login.getFirstName()),
                trimmed(login.getLastName()),
                login.getLoginType(),
                login.getDepartment(),
                login.getManager(),
                login.getPhone(),
                login.getExtension(),
                login.getEmail(),
                login.getLanId(),
                login.getLastSystem(),
                login.getTitle(),
                login.isEnabled(),
                login.getFailedCount(),
                password.map(LoginPassword::getChangeDue).orElse(null),
                password.map(LoginPassword::getLastChangedAt).orElse(null),
                lastGood,
                recent,
                aliasNames);
    }

    // ── Add and delete ────────────────────────────────────────────

    /** US-2.6.2 — does any other login already hold this LAN ID? */
    @Transactional(readOnly = true)
    public boolean lanIdInUse(String lanId) {
        return lanId != null && !lanId.isBlank() && deletions.countByLanId(lanId.trim()) > 0;
    }

    /**
     * US-2.6 — create the login, its password row and its self-alias, in one transaction.
     *
     * @param confirmedDuplicateLanId the caller has shown the "LAN ID already exists" prompt and
     *                                the administrator chose to continue
     */
    @Transactional
    public UserDetail createUser(NewUser request, boolean confirmedDuplicateLanId) {
        String loginName = requireText(request.loginName(), "A user ID is required.").trim();
        if (loginName.length() > 10) {
            throw new ValidationException("A user ID may be at most 10 characters.");
        }
        requireText(request.password(), "An initial password is required.");
        requireText(request.firstName(), "A first name is required.");

        if (logins.findByLoginNameIgnoreCase(loginName).isPresent()) {
            throw new ValidationException("A user named " + loginName + " already exists.");
        }
        if (!confirmedDuplicateLanId && lanIdInUse(request.lanId())) {
            throw new DuplicateLanIdException(request.lanId());
        }

        Login login = Login.create(loginName,
                request.firstName().trim(),
                trimmed(request.lastName()),
                // US-2.6.3 — Role and Department are stored truncated, exactly as the Delphi did.
                first(request.role(), 5),
                first(request.department(), 1),
                trimmed(request.manager()),
                trimmed(request.phone()),
                trimmed(request.extension()),
                trimmed(request.email()),
                trimmed(request.lanId()),
                PLACEHOLDER_ABBREV_ID);
        logins.save(login);

        passwords.save(LoginPassword.initial(loginName, request.password(), LocalDateTime.now()));
        aliases.save(UserAlias.self(loginName));

        AUDIT.info("user created: {} by request", loginName);
        return getUser(loginName);
    }

    /**
     * US-2.9 — remove the user and everything keyed to them, in one transaction.
     *
     * @return a short report of what was removed, so the caller can show it
     */
    @Transactional
    public DeletionReport deleteUser(String loginName) {
        Login login = require(loginName);
        String name = login.getLoginName();

        int permissions = deletions.deleteReportPermissions(name);
        int signatures = deletions.deleteSignatures(name);
        int notes = deletions.deleteCommonNotes(name);
        int owned = aliases.deleteOwnedBy(name);
        int referencing = aliases.deleteReferencing(name);
        passwords.findByLoginNameIgnoreCase(name).ifPresent(passwords::delete);
        loginStore.delete(login);

        AUDIT.info("user deleted: {} (permissions={}, signatures={}, notes={}, aliases={})",
                name, permissions, signatures, notes, owned + referencing);

        return new DeletionReport(name, permissions, signatures, notes, owned + referencing);
    }

    // ── History tab ───────────────────────────────────────────────

    /**
     * US-2.11 — one page of names that appear in {@code TM_Login_History}.
     *
     * @param startAt  first name to include, or null to start at the beginning
     * @param pageSize how many rows fit the client's grid; clamped to a sane range
     */
    @Transactional(readOnly = true)
    public HistoryPage historyPage(String startAt, int pageSize) {
        int size = clampPageSize(pageSize);
        String start = startAt == null || startAt.isBlank() ? "" : startAt.trim();

        // Read one extra to learn whether another page exists, without a second query.
        List<String> found = history.pageForward(start, Limit.of(size + 1));
        boolean hasNext = found.size() > size;
        List<String> page = found.stream().limit(size).map(String::trim).toList();

        return new HistoryPage(page, size, hasNext);
    }

    /**
     * The names that follow {@code after}, for a list that is being scrolled rather than paged.
     *
     * <p>Exclusive of {@code after}, so appending continues the list instead of repeating the name
     * already at the bottom of it.
     */
    @Transactional(readOnly = true)
    public HistoryPage historyPageAfter(String after, int pageSize) {
        int size = clampPageSize(pageSize);
        if (after == null || after.isBlank()) {
            return historyPage(null, size);
        }

        // One extra row answers "is there another page" without a second query.
        List<String> found = history.pageAfter(after.trim(), Limit.of(size + 1));
        boolean hasNext = found.size() > size;
        List<String> page = found.stream().limit(size).map(String::trim).toList();

        return new HistoryPage(page, size, hasNext);
    }

    /** US-2.12 — every sign-in attempt for one user, newest first. */
    @Transactional(readOnly = true)
    public List<LoginAttempt> loginHistory(String loginName) {
        return history.findAttempts(loginName.trim()).stream()
                .map(UserManagerService::toAttempt)
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────

    private Login require(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new ValidationException("A login name is required.");
        }
        return logins.findByLoginNameIgnoreCase(loginName.trim())
                .orElseThrow(() -> new NotFoundException("No user named " + loginName.trim() + "."));
    }

    private static LoginAttempt toAttempt(LoginHistory h) {
        return new LoginAttempt(h.getAttemptedAt(), h.getSystem(),
                h.successful() ? "Successful" : "Failed", h.successful());
    }

    private static int clampPageSize(int requested) {
        return Math.max(1, Math.min(requested, MAX_PAGE_SIZE));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    /** The Delphi stored only the leading characters of the Role and Department picker labels. */
    private static String first(String value, int length) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= length ? trimmed : trimmed.substring(0, length).trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value;
    }

    /** The fields the Add User dialog collects (US-2.4). */
    public record NewUser(String loginName, String password, String firstName, String lastName,
                          String role, String department, String manager, String phone,
                          String extension, String email, String lanId) {
    }

    public record DeletionReport(String loginName, int permissions, int signatures, int notes,
                                 long aliases) {
    }

    /** Raised when the LAN ID is already taken and the administrator has not yet confirmed. */
    public static class DuplicateLanIdException extends RuntimeException {
        private final String lanId;

        public DuplicateLanIdException(String lanId) {
            super("This LAN ID already exists. Are you sure you want to continue?");
            this.lanId = lanId;
        }

        public String getLanId() {
            return lanId == null ? "" : lanId.trim().toUpperCase(Locale.ROOT);
        }
    }
}
