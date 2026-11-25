package com.anthem.cartman.workqueues.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.login.repository.LoginLookup;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.AssignedQueue;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.CopyResult;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueDetail;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueRow;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserRow;
import com.anthem.cartman.workqueues.entity.CallQueue;
import com.anthem.cartman.workqueues.entity.CallQueuePermission;
import com.anthem.cartman.workqueues.repository.CallQueuePermissionRepository;
import com.anthem.cartman.workqueues.repository.CallQueueRepository;

/**
 * Module 5 — Work Queue Manager (US-5.1 to US-5.8).
 *
 * <p>A work queue is a named saved query producing a worklist; {@code TM_Call_Queue_Permission} says
 * which users may open which queue. The same relationship is editable from either end, which is what
 * the Delphi's two tabs were for.
 *
 * <p>Reuses {@link LoginLookup} for both user lists, as Modules 3 and 4 do.
 *
 * <p>Delphi behaviours deliberately corrected, each recorded as an anomaly in the requirements:
 * <ul>
 *   <li><b>A29</b> — the By User tab's unassign deleted on {@code CQP_Usr}, which is not a column,
 *       so it always failed while the UI moved the row anyway. The column is {@code CQP_User}.</li>
 *   <li><b>A30</b> — Export's insert named two columns and supplied three values, so it always
 *       failed — after Replace had already deleted the target's rows. Fixed, and transactional.</li>
 *   <li><b>A31</b> — the queue list printed the literal {@code Sales} as the type for every row.
 *       {@link CallQueue#getType()} returns what is stored.</li>
 *   <li><b>A32</b> — New Queue checked neither for a duplicate name nor against
 *       {@code Cal_Name char(10)}. Both are checked. Queues are addressed by {@code Cal_ID}, the
 *       real primary key, so the duplicates already in the data stay individually editable.</li>
 *   <li><b>A34</b> — every multi-row operation ran unwrapped. All are transactional.</li>
 *   <li><b>A35</b> — a permitted user who is no longer a login, or a permission naming a queue that
 *       no longer exists, is listed and marked rather than dropped. New grants must name both.</li>
 * </ul>
 */
@Service
public class WorkQueueService {

    private static final Logger AUDIT = LoggerFactory.getLogger("CARTMAN.AUDIT");

    private final CallQueueRepository queues;
    private final CallQueuePermissionRepository permissions;
    private final LoginLookup logins;

    public WorkQueueService(CallQueueRepository queues, CallQueuePermissionRepository permissions,
                            LoginLookup logins) {
        this.queues = queues;
        this.permissions = permissions;
        this.logins = logins;
    }

    // ── Lists ─────────────────────────────────────────────────────

    /** US-5.1.1 — the queue list, ordered by name, showing the type each queue actually holds. */
    @Transactional(readOnly = true)
    public List<QueueRow> allQueues() {
        return orderedQueues().stream().map(WorkQueueService::row).toList();
    }

    /** US-5.1.4 — every login, for both user lists. Ordered, unlike one of the Delphi's queries. */
    @Transactional(readOnly = true)
    public List<UserRow> allUsers() {
        return logins.findAllByOrderByLoginNameAsc().stream()
                .map(l -> UserRow.login(l.getLoginName(), l.getLoginType()))
                .toList();
    }

    // ── By Queue tab ──────────────────────────────────────────────

    /**
     * US-5.2 / US-5.3.1 — one queue's definition and both user lists.
     *
     * <p>The available list is every login not already permitted, computed here rather than mutated
     * in the UI. The permitted list keeps users who are no longer logins, marked unknown (A35).
     */
    @Transactional(readOnly = true)
    public QueueBoard board(int queueId) {
        CallQueue queue = requireQueue(queueId);
        String name = queue.getName();

        Map<String, Login> byName = logins.findAllByOrderByLoginNameAsc().stream()
                .collect(Collectors.toMap(l -> key(l.getLoginName()), Function.identity(),
                        (a, b) -> a, java.util.LinkedHashMap::new));

        Set<String> permittedKeys = new LinkedHashSet<>();
        List<UserRow> permitted = new ArrayList<>();
        for (CallQueuePermission p : permissions.findByQueue(name)) {
            String user = p.getUserName();
            if (user == null || user.isBlank() || !permittedKeys.add(key(user))) {
                continue; // one live row has a null user; duplicates collapse to one entry
            }
            Login login = byName.get(key(user));
            permitted.add(login == null
                    ? UserRow.unknown(user)
                    : UserRow.login(login.getLoginName(), login.getLoginType()));
        }

        List<UserRow> available = byName.values().stream()
                .filter(l -> !permittedKeys.contains(key(l.getLoginName())))
                .map(l -> UserRow.login(l.getLoginName(), l.getLoginType()))
                .toList();

        return new QueueBoard(detail(queue), permitted, available);
    }

    /** US-5.5 — create a queue, then hand back its board so the client can open the editor. */
    @Transactional
    public QueueBoard createQueue(String name) {
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty()) {
            throw new ValidationException("A queue name is required.");
        }
        if (wanted.length() > CallQueue.NAME_LENGTH) {
            throw new ValidationException("A queue name may be at most "
                    + CallQueue.NAME_LENGTH + " characters; \"" + wanted + "\" is "
                    + wanted.length() + ".");
        }
        if (!queues.findByName(wanted).isEmpty()) {
            throw new ValidationException("A work queue named " + wanted + " already exists.");
        }

        CallQueue created = queues.save(CallQueue.create(wanted));
        AUDIT.info("work queue created: {} (Cal_ID {})", created.getName(), created.getId());
        return board(created.getId());
    }

    /** US-5.4.2 — Save Queue writes the description and the SQL, and nothing else. */
    @Transactional
    public QueueBoard saveDefinition(int queueId, String description, String sql) {
        CallQueue queue = requireQueue(queueId);
        queue.updateDefinition(description == null ? "" : description, sql == null ? "" : sql);
        queues.save(queue);

        AUDIT.info("work queue {} definition saved (Cal_ID {})", queue.getName(), queue.getId());
        return board(queueId);
    }

    /** US-5.3.3 — permit one or more logins to open this queue. */
    @Transactional
    public QueueBoard grant(int queueId, List<String> userNames) {
        CallQueue queue = requireQueue(queueId);
        List<String> wanted = distinctNonBlank(userNames);
        if (wanted.isEmpty()) {
            throw new ValidationException("Select at least one user to add.");
        }

        Set<String> held = permissions.findByQueue(queue.getName()).stream()
                .map(p -> key(p.getUserName())).collect(Collectors.toSet());

        int added = 0;
        for (String candidate : wanted) {
            // A35 — a new grant must name a real login, unlike the rows already in the table.
            String user = requireLogin(candidate).getLoginName();
            if (!held.add(key(user))) {
                continue;
            }
            permissions.save(CallQueuePermission.of(user, queue.getName()));
            added++;
        }

        AUDIT.info("work queue {}: {} user(s) permitted", queue.getName(), added);
        return board(queueId);
    }

    /**
     * US-5.3.5 — revoke one or more users.
     *
     * <p>No login check: the point of revoking is often to clear a user who no longer exists.
     */
    @Transactional
    public QueueBoard revoke(int queueId, List<String> userNames) {
        CallQueue queue = requireQueue(queueId);
        List<String> wanted = distinctNonBlank(userNames);
        if (wanted.isEmpty()) {
            throw new ValidationException("Select at least one user to remove.");
        }

        int removed = 0;
        for (String user : wanted) {
            removed += permissions.deletePair(user, queue.getName());
        }

        AUDIT.info("work queue {}: {} permission row(s) removed", queue.getName(), removed);
        return board(queueId);
    }

    // ── By User tab ───────────────────────────────────────────────

    /**
     * US-5.7.1 — one user's assigned queues and the queues still available.
     *
     * <p>An assigned entry whose queue no longer exists is kept and marked, so the row can be seen
     * and cleared rather than quietly disappearing (A35).
     */
    @Transactional(readOnly = true)
    public UserBoard userBoard(String loginName) {
        Login user = requireLogin(loginName);
        String name = user.getLoginName();

        List<CallQueue> all = orderedQueues();
        Map<String, CallQueue> byName = new java.util.LinkedHashMap<>();
        for (CallQueue q : all) {
            byName.putIfAbsent(key(q.getName()), q); // duplicate names resolve to the lower Cal_ID
        }

        Set<String> assignedKeys = new LinkedHashSet<>();
        List<AssignedQueue> assigned = new ArrayList<>();
        for (CallQueuePermission p : permissions.findByUser(name)) {
            String queueName = p.getQueueName();
            if (queueName == null || queueName.isBlank() || !assignedKeys.add(key(queueName))) {
                continue; // one live row for KMilo has a null queue
            }
            CallQueue queue = byName.get(key(queueName));
            assigned.add(queue == null
                    ? new AssignedQueue(null, queueName, false)
                    : new AssignedQueue(queue.getId(), queue.getName(), true));
        }

        List<QueueRow> available = all.stream()
                .filter(q -> !assignedKeys.contains(key(q.getName())))
                .map(WorkQueueService::row)
                .toList();

        return new UserBoard(name, assigned, available);
    }

    /** US-5.7.2 — assign one or more queues to a user, addressed by {@code Cal_ID}. */
    @Transactional
    public UserBoard assignQueues(String loginName, List<Integer> queueIds) {
        Login user = requireLogin(loginName);
        String name = user.getLoginName();
        if (queueIds == null || queueIds.isEmpty()) {
            throw new ValidationException("Select at least one queue to assign.");
        }

        Set<String> held = permissions.findByUser(name).stream()
                .map(p -> key(p.getQueueName())).collect(Collectors.toSet());

        int added = 0;
        for (Integer queueId : queueIds) {
            if (queueId == null) {
                continue;
            }
            CallQueue queue = requireQueue(queueId);
            // Two queues can share a name, and the permission stores only the name, so the second
            // of a duplicate pair would add nothing.
            if (!held.add(key(queue.getName()))) {
                continue;
            }
            permissions.save(CallQueuePermission.of(name, queue.getName()));
            added++;
        }

        AUDIT.info("{}: {} work queue(s) assigned", name, added);
        return userBoard(name);
    }

    /**
     * US-5.7.3 — unassign one or more queues, by name.
     *
     * <p>By name rather than by id because an assigned row may name a queue that no longer exists,
     * and clearing exactly those rows is the reason to reach for this. This is the operation the
     * Delphi could not perform at all (A29).
     */
    @Transactional
    public UserBoard unassignQueues(String loginName, List<String> queueNames) {
        Login user = requireLogin(loginName);
        String name = user.getLoginName();
        List<String> wanted = distinctNonBlank(queueNames);
        if (wanted.isEmpty()) {
            throw new ValidationException("Select at least one queue to unassign.");
        }

        int removed = 0;
        for (String queueName : wanted) {
            removed += permissions.deletePair(name, queueName);
        }

        AUDIT.info("{}: {} work queue permission row(s) removed", name, removed);
        return userBoard(name);
    }

    // ── Copy ──────────────────────────────────────────────────────

        /**
     * US-5.8 — copy one user's queue permissions to another.
     *
     * <p>Replace and copy share one transaction, so the target can never be left with nothing
     * because the copy failed after the delete — which is exactly what the Delphi did every time,
     * its insert being invalid (A30, A13).
     */
    @Transactional
    public CopyResult copy(String from, String to, boolean replace) {
        String source = requireLogin(from).getLoginName();
        String target = requireLogin(to).getLoginName();
        requireDistinct(source, target);

        List<String> sourceQueues = heldQueueNames(source);

        int removed = 0;
        Set<String> targetKeys;
        if (replace) {
            removed = permissions.deleteByUser(target);
            targetKeys = new LinkedHashSet<>();
        } else {
            targetKeys = heldQueueNames(target).stream().map(WorkQueueService::key)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        int copied = 0;
        for (String queueName : sourceQueues) {
            if (!targetKeys.add(key(queueName))) {
                continue; // US-5.8.3 — skip what the target already holds
            }
            permissions.save(CallQueuePermission.of(target, queueName));
            copied++;
        }

        AUDIT.info("work queue permissions copied from {} to {}: {} added, {} removed, replace={}",
                source, target, copied, removed, replace);
        return new CopyResult(source, target, copied, removed);
    }

    // ── helpers ───────────────────────────────────────────────────

    /**
     * Every queue in the order the screen lists them.
     *
     * <p>The Delphi said {@code ORDER BY Cal_Name} and got SQL Server's case-insensitive collation.
     * Ordering here rather than in the query keeps the list identical on any database — H2, for one,
     * sorts {@code over 65} after {@code Untyped} — and breaks the tie between two queues sharing a
     * name by {@code Cal_ID}, so the list is stable rather than in server order.
     */
    private List<CallQueue> orderedQueues() {
        return queues.findAllByOrderByNameAsc().stream()
                .sorted(java.util.Comparator
                        .comparing(CallQueue::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CallQueue::getId))
                .toList();
    }

    private static QueueRow row(CallQueue q) {
        String sql = q.getQuery();
        return new QueueRow(q.getId(), q.getName(), q.getType(), sql == null || sql.isBlank());
    }

    private static QueueDetail detail(CallQueue q) {
        return new QueueDetail(q.getId(), q.getName(), q.getType(),
                q.getDescription() == null ? "" : q.getDescription(),
                q.getQuery() == null ? "" : q.getQuery());
    }

    private List<String> heldQueueNames(String userName) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (CallQueuePermission p : permissions.findByUser(userName)) {
            String queueName = p.getQueueName();
            if (queueName == null || queueName.isBlank()) {
                continue;
            }
            if (seen.add(key(queueName))) {
                out.add(queueName);
            }
        }
        return out;
    }

    private CallQueue requireQueue(int queueId) {
        return queues.findById(queueId)
                .orElseThrow(() -> new NotFoundException("No work queue with id " + queueId + "."));
    }

    private Login requireLogin(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            throw new ValidationException("A user is required.");
        }
        return logins.findByLoginNameIgnoreCase(loginName.trim())
                .orElseThrow(() -> new NotFoundException("No user named " + loginName.trim() + "."));
    }

    private static void requireDistinct(String source, String target) {
        if (key(source).equals(key(target))) {
            throw new ValidationException("The source and target users must be different.");
        }
    }

    private static List<String> distinctNonBlank(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (seen.add(key(value))) {
                out.add(value.trim());
            }
        }
        return out;
    }

    /** Names are fixed-width and case-insensitive; compare them trimmed and upper. */
    private static String key(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
