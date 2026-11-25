package com.anthem.cartman.permissions.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.permissions.dto.PermissionViews.CopyResult;
import com.anthem.cartman.permissions.dto.PermissionViews.Grantee;
import com.anthem.cartman.permissions.dto.PermissionViews.ObjectGrantRow;
import com.anthem.cartman.permissions.dto.PermissionViews.UserGrantRow;
import com.anthem.cartman.permissions.entity.ReportObject;
import com.anthem.cartman.permissions.entity.ReportPermission;
import com.anthem.cartman.permissions.repository.UserGroupRepository;
import com.anthem.cartman.permissions.repository.ReportObjectRepository;
import com.anthem.cartman.permissions.repository.ReportPermissionRepository;

/**
 * Module 3 — Object Permissions (US-3.1 to US-3.8).
 *
 * <p>Notable departures from the Delphi, all recorded as anomalies in the requirements:
 * <ul>
 *   <li><b>A11</b> — the By User tab wrote {@code Perm_Nm = '(Group)'} with the parentheses,
 *       producing rows nothing could match. {@link #bareName} strips them on every write, no
 *       matter which tab the change came from.</li>
 *   <li><b>A12</b> — an empty string was written into the {@code smalldatetime} run date, which
 *       SQL Server turned into 1900-01-01. Unknown run dates are {@code NULL} here.</li>
 *   <li><b>A13</b> — Replace deleted the target's permissions and then copied, unwrapped. The
 *       copy is one transaction.</li>
 * </ul>
 *
 * <p>{@code dsGetUserPerm}, the procedure behind the By User grid, has no body in the source
 * tree. {@link #byUser} reimplements it from the columns the screen consumed: every object,
 * left-joined to that grantee's permission. Confirm against the live procedure before release.
 */
@Service
public class PermissionsService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    private final ReportPermissionRepository permissions;
    private final ReportObjectRepository objects;
    private final UserGroupRepository groups;
    private final LoginLookup logins;

    PermissionsService(ReportPermissionRepository permissions, ReportObjectRepository objects,
                       UserGroupRepository groups, LoginLookup logins) {
        this.permissions = permissions;
        this.objects = objects;
        this.groups = groups;
        this.logins = logins;
    }

    // ── Pickers ───────────────────────────────────────────────────

    /** US-3.1 — logins first, then groups shown parenthesised. Both ordered. */
    @Transactional(readOnly = true)
    public List<Grantee> grantees() {
        List<Grantee> all = new ArrayList<>(logins.findAllByOrderByLoginNameAsc().stream()
                .map(Login::getLoginName)
                .map(Grantee::user)
                .toList());
        groups.findDistinctGroupNames().stream().map(Grantee::group).forEach(all::add);
        return all;
    }

    /** US-3.1.3 — the objects, ordered by name. */
    @Transactional(readOnly = true)
    public List<String> objectNames() {
        return objects.findAllByOrderByNameAsc().stream().map(ReportObject::getName).toList();
    }

    /**
     * US-3.1.3, paged — filtered, ordered and sliced by the database.
     *
     * <p>Prefix matches lead, so the picker can complete from the first letter; names merely
     * containing the text follow, so a search still finds them. See {@code findNames} for why
     * both the ordering and the paging belong in the query.
     */
    @Transactional(readOnly = true)
    public PageView<String> objectNames(String search, int page, int size) {
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        int clamped = PageView.clampSize(size);
        int index = Math.max(0, page);
        long total = objects.countNames(needle);
        List<String> names = objects.findNames(needle, PageRequest.of(index, clamped));
        return PageView.of(names, index, clamped, total);
    }

    // ── By Object ─────────────────────────────────────────────────

    /**
     * US-3.3 — every login, plus any group holding a permission, against one object.
     * Logins with no permission appear with a blank level, as the Delphi's outer join did.
     */
    @Transactional(readOnly = true)
    public List<ObjectGrantRow> byObject(String objectName) {
        String object = requireObject(objectName);

        Map<String, ReportPermission> held = permissions.findByObject(object).stream()
                .collect(Collectors.toMap(p -> key(p.getGranteeName()), p -> p, (a, b) -> a,
                        LinkedHashMap::new));

        List<ObjectGrantRow> rows = new ArrayList<>();
        for (Login login : logins.findAllByOrderByLoginNameAsc()) {
            rows.add(row(Grantee.user(login.getLoginName()), held.get(key(login.getLoginName()))));
        }
        // Groups appear only when they hold a permission — the Delphi's UNION was an inner join.
        for (String group : groups.findDistinctGroupNames()) {
            ReportPermission p = held.get(key(group));
            if (p != null) {
                rows.add(row(Grantee.group(group), p));
            }
        }
        return rows;
    }

    /**
     * US-3.3, paged. The rows are a merge of every grantee with the permissions held on this
     * object, so the merge happens first and the page is cut from the result.
     */
    @Transactional(readOnly = true)
    public PageView<ObjectGrantRow> byObject(String objectName, String search, boolean onlyPermitted,
                                             int page, int size) {
        List<ObjectGrantRow> rows = byObject(objectName).stream()
                .filter(r -> !onlyPermitted || r.level() > 0)
                .filter(r -> matches(r.display(), search))
                .toList();
        return PageView.slice(rows, page, size);
    }

    private static ObjectGrantRow row(Grantee grantee, ReportPermission permission) {
        int level = permission == null ? ReportPermission.NONE : permission.getLevel();
        return new ObjectGrantRow(grantee.name(), grantee.display(), grantee.group(), level,
                ReportPermission.describe(level),
                permission == null ? null : permission.getRunDate(),
                permission == null ? null : permission.getFolder());
    }

    // ── By User ───────────────────────────────────────────────────

    /** US-3.4 — every object, with this grantee's level where one exists. */
    @Transactional(readOnly = true)
    public List<UserGrantRow> byUser(String granteeName) {
        String grantee = bareName(granteeName);
        if (grantee.isEmpty()) {
            throw new ValidationException("A user or group is required.");
        }

        Map<String, ReportPermission> held = permissions.findByGrantee(grantee).stream()
                .collect(Collectors.toMap(p -> key(p.getObjectName()), p -> p, (a, b) -> a,
                        LinkedHashMap::new));

        return objects.findAllByOrderByNameAsc().stream()
                .map(object -> {
                    ReportPermission p = held.get(key(object.getName()));
                    int level = p == null ? ReportPermission.NONE : p.getLevel();
                    return new UserGrantRow(object.getName(), object.getStyle(), level,
                            ReportPermission.describe(level),
                            p == null ? null : p.getRunDate(),
                            p == null ? object.getFolder() : p.getFolder());
                })
                .toList();
    }

    /**
     * US-3.4, paged — the screen that most needed it: every one of the 5,187 objects was being
     * sent and drawn for a single grantee.
     */
    @Transactional(readOnly = true)
    public PageView<UserGrantRow> byUser(String granteeName, String search, boolean onlyPermitted,
                                         int page, int size) {
        List<UserGrantRow> rows = byUser(granteeName).stream()
                .filter(r -> !onlyPermitted || r.level() > 0)
                .filter(r -> matches(r.objectName(), search))
                .toList();
        return PageView.slice(rows, page, size);
    }

    /** Case-insensitive contains; a blank search matches everything. */
    private static boolean matches(String value, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }

    // ── Grant and revoke ──────────────────────────────────────────

    /**
     * US-3.5 — set one level across a whole selection, in one transaction.
     *
     * <p>Revoking removes the row rather than storing level 0. The Delphi stored the zero, which
     * left a permission row that grants nothing; both read paths already treat a missing row and
     * a zero row identically, so removing it keeps the table honest without changing behaviour.
     *
     * @param pairs object-and-grantee pairs to change
     * @return how many rows were written or removed
     */
    @Transactional
    public int apply(List<GrantChange> pairs, int level) {
        if (level < ReportPermission.NONE || level > ReportPermission.EDIT) {
            throw new ValidationException("Permission level must be 0, 1 or 2.");
        }
        if (pairs == null || pairs.isEmpty()) {
            throw new ValidationException("Select at least one row.");
        }

        int changed = 0;
        for (GrantChange change : pairs) {
            String object = requireObject(change.objectName());
            String grantee = bareName(change.granteeName());
            if (grantee.isEmpty()) {
                throw new ValidationException("A user or group is required.");
            }

            Optional<ReportPermission> existing = permissions.find(object, grantee);
            if (level == ReportPermission.NONE) {
                existing.ifPresent(permissions::delete);
                changed += existing.isPresent() ? 1 : 0;
            } else if (existing.isPresent()) {
                existing.get().setLevel(level);
                permissions.save(existing.get());
                changed++;
            } else {
                permissions.save(ReportPermission.of(object, grantee, level));
                changed++;
            }
        }

        AUDIT.info("permissions set to {} on {} row(s)", ReportPermission.describe(level), changed);
        return changed;
    }

    // ── Copy ──────────────────────────────────────────────────────

    /** US-3.7 — perform the copy. Replace and copy share one transaction. */
    @Transactional
    public CopyResult copy(String from, String to, boolean replace) {
        String source = requireGrantee(from);
        String target = requireGrantee(to);
        if (source.equalsIgnoreCase(target)) {
            throw new ValidationException("Choose two different users.");
        }

        int removed = 0;
        if (replace) {
            removed = permissions.deleteByGrantee(target);
        }

        Set<String> targetHolds = permissions.findByGrantee(target).stream()
                .map(p -> key(p.getObjectName()))
                .collect(Collectors.toSet());

        int copied = 0;
        for (ReportPermission source_ : permissions.findByGrantee(source)) {
            if (targetHolds.contains(key(source_.getObjectName()))) {
                continue; // US-3.7.6 — never overwrite what the target already holds
            }
            ReportPermission copy =
                    ReportPermission.of(source_.getObjectName(), target, source_.getLevel());
            copy.setFolder(source_.getFolder()); // US-3.7.7 — folder travels, run date does not
            permissions.save(copy);
            copied++;
        }

        AUDIT.info("permissions copied from {} to {} (replace={}, copied={}, removed={})",
                source, target, replace, copied, removed);
        return new CopyResult(source, target, copied, removed);
    }

    // ── helpers ───────────────────────────────────────────────────

    /**
     * Strips the display parentheses a group name carries in the UI, so {@code Perm_Nm} always
     * holds the bare name. This is the fix for anomaly A11.
     */
    static String bareName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length() > 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String requireObject(String objectName) {
        String name = objectName == null ? "" : objectName.trim();
        if (name.isEmpty()) {
            throw new ValidationException("An object is required.");
        }
        return name;
    }

    private String requireGrantee(String name) {
        String bare = bareName(name);
        if (bare.isEmpty()) {
            throw new ValidationException("A user or group is required.");
        }
        boolean known = logins.findByLoginNameIgnoreCase(bare).isPresent()
                || groups.findDistinctGroupNames().stream().anyMatch(g -> g.equalsIgnoreCase(bare));
        if (!known) {
            throw new NotFoundException("No user or group named " + bare + ".");
        }
        return bare;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    /** One cell of the grid the administrator selected. */
    public record GrantChange(String objectName, String granteeName) {
    }
}
