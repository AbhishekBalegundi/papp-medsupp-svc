package com.anthem.cartman.workqueues.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.anthem.cartman.workqueues.dto.WorkQueueViews.AssignedQueue;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.CopyResult;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueRow;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserRow;
import com.anthem.cartman.workqueues.entity.CallQueue;
import com.anthem.cartman.workqueues.entity.CallQueuePermission;
import com.anthem.cartman.workqueues.repository.CallQueuePermissionRepository;
import com.anthem.cartman.workqueues.service.WorkQueueService;

/**
 * Module 5 against the seeded H2 schema.
 *
 * <p>Seed state: six queues, two of them both named {@code over 65} ({@code Cal_ID} 7 and 40).
 * DStante holds Birthday, over 65, IHPFULLYQ (no such queue) and one row with a null queue;
 * TenCharUsr holds Birthday; CosmoAdm holds Referral; GoneUser holds Birthday but is not a login.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class WorkQueueServiceTest {

    private static final int BIRTHDAY = 80;
    private static final int OVER_65_FIRST = 7;
    private static final int OVER_65_SECOND = 40;
    private static final int REFERRAL = 95;
    private static final int UNTYPED = 99;

    @Autowired
    private WorkQueueService service;

    @Autowired
    private CallQueuePermissionRepository permissions;

    private static List<String> names(List<UserRow> rows) {
        return rows.stream().map(UserRow::name).toList();
    }

    @Nested
    @DisplayName("US-5.1 — the lists")
    class Lists {

        @Test
        @DisplayName("queues come back ordered by name")
        void queuesOrdered() {
            List<String> queueNames = service.allQueues().stream().map(QueueRow::name).toList();

            assertEquals(6, queueNames.size());
            assertEquals(queueNames.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(),
                    queueNames);
        }

        @Test
        @DisplayName("A31 — the type shown is the stored Cal_Type, not the literal 'Sales'")
        void showsRealType() {
            List<QueueRow> rows = service.allQueues();

            assertEquals("Sales", rows.stream().filter(r -> r.id() == BIRTHDAY)
                    .findFirst().orElseThrow().type());
            // 67 live rows hold NULL and one is blank; both must read as blank, not as "Sales".
            assertEquals("", rows.stream().filter(r -> r.id() == OVER_65_FIRST)
                    .findFirst().orElseThrow().type());
            assertEquals("", rows.stream().filter(r -> r.id() == UNTYPED)
                    .findFirst().orElseThrow().type());
        }

        @Test
        @DisplayName("a queue with no SQL is flagged as empty")
        void flagsEmptyQueue() {
            List<QueueRow> rows = service.allQueues();

            assertTrue(rows.stream().filter(r -> r.id() == OVER_65_SECOND)
                    .findFirst().orElseThrow().empty());
            assertFalse(rows.stream().filter(r -> r.id() == BIRTHDAY)
                    .findFirst().orElseThrow().empty());
        }

        @Test
        @DisplayName("A32 — a duplicate name is two distinct rows, each addressable")
        void duplicateNameStaysAddressable() {
            List<QueueRow> over65 = service.allQueues().stream()
                    .filter(r -> r.name().equals("over 65")).toList();

            assertEquals(2, over65.size());
            assertNotEquals(over65.get(0).id(), over65.get(1).id());
        }

        @Test
        @DisplayName("A21 — the user list is ordered")
        void usersOrdered() {
            List<String> userNames = names(service.allUsers());

            assertEquals(7, userNames.size());
            assertEquals(userNames.stream().sorted().toList(), userNames);
        }
    }

    @Nested
    @DisplayName("US-5.2 / US-5.3 — the By Queue tab")
    class ByQueue {

        @Test
        @DisplayName("the definition comes from the selected row, not the name")
        void readsDefinition() {
            assertEquals("The Birthday List.", service.board(BIRTHDAY).queue().description());
            // The two over-65 rows share a name and differ in content.
            assertTrue(service.board(OVER_65_FIRST).queue().sql().startsWith("Pro_DOB"));
            assertEquals("", service.board(OVER_65_SECOND).queue().sql());
        }

        @Test
        @DisplayName("permitted and available together account for every login exactly once")
        void listsPartitionLogins() {
            QueueBoard board = service.board(BIRTHDAY);

            assertEquals(List.of("DStante", "GoneUser", "TenCharUsr"), names(board.permitted()));
            assertFalse(names(board.available()).contains("DStante"));
            assertFalse(names(board.available()).contains("TenCharUsr"));
            assertTrue(names(board.available()).contains("CosmoAdm"));
            // GoneUser is not a login, so it is not one of the seven the available list draws from.
            assertEquals(7, board.available().size() + 2);
        }

        @Test
        @DisplayName("A35 — a permitted user who is no longer a login is listed and marked")
        void keepsUnknownUser() {
            UserRow gone = service.board(BIRTHDAY).permitted().stream()
                    .filter(u -> u.name().equals("GoneUser")).findFirst().orElseThrow();

            assertFalse(gone.known());
        }

        @Test
        @DisplayName("a null user row is skipped rather than listed as blank")
        void skipsNullUser() {
            assertTrue(service.board(REFERRAL).permitted().stream()
                    .noneMatch(u -> u.name() == null || u.name().isBlank()));
        }

        @Test
        @DisplayName("adding permits the users and moves them out of available")
        void grants() {
            QueueBoard board = service.grant(BIRTHDAY, List.of("CosmoAdm", "NoTools"));

            assertTrue(names(board.permitted()).containsAll(List.of("CosmoAdm", "NoTools")));
            assertFalse(names(board.available()).contains("CosmoAdm"));
        }

        @Test
        @DisplayName("adding a user who already holds the queue adds no second row")
        void grantIsIdempotent() {
            int before = permissions.findByQueue("Birthday").size();

            service.grant(BIRTHDAY, List.of("DStante"));

            assertEquals(before, permissions.findByQueue("Birthday").size());
        }

        @Test
        @DisplayName("A35 — a new grant must name a real login")
        void grantRejectsUnknownUser() {
            assertThrows(NotFoundException.class,
                    () -> service.grant(BIRTHDAY, List.of("NotAUser")));
        }

        @Test
        @DisplayName("adding nothing is rejected rather than silently doing nothing")
        void grantRejectsEmptySelection() {
            assertThrows(ValidationException.class, () -> service.grant(BIRTHDAY, List.of()));
        }

        @Test
        @DisplayName("A18 — a removed user returns to the available list")
        void revokeReturnsUserToAvailable() {
            QueueBoard board = service.revoke(BIRTHDAY, List.of("TenCharUsr"));

            assertFalse(names(board.permitted()).contains("TenCharUsr"));
            assertTrue(names(board.available()).contains("TenCharUsr"));
        }

        @Test
        @DisplayName("A35 — a user who is no longer a login can still be revoked")
        void revokesUnknownUser() {
            QueueBoard board = service.revoke(BIRTHDAY, List.of("GoneUser"));

            assertFalse(names(board.permitted()).contains("GoneUser"));
        }

        @Test
        @DisplayName("revoking touches only the named queue")
        void revokeIsQueueScoped() {
            service.revoke(BIRTHDAY, List.of("DStante"));

            assertTrue(permissions.findByUser("DStante").stream()
                    .anyMatch(p -> "over 65".equals(p.getQueueName())));
        }

        @Test
        @DisplayName("an unknown queue id is a 404, not an empty board")
        void unknownQueueIsNotFound() {
            assertThrows(NotFoundException.class, () -> service.board(999_999));
        }
    }

    @Nested
    @DisplayName("US-5.4 — editing a definition")
    class Definition {

        @Test
        @DisplayName("save writes the description and the SQL")
        void savesBoth() {
            service.saveDefinition(BIRTHDAY, "Reworded.", "App_Route_To = 'X'");

            assertEquals("Reworded.", service.board(BIRTHDAY).queue().description());
            assertEquals("App_Route_To = 'X'", service.board(BIRTHDAY).queue().sql());
        }

        @Test
        @DisplayName("save leaves the name and the type alone")
        void leavesNameAndTypeAlone() {
            service.saveDefinition(BIRTHDAY, "Reworded.", "App_Route_To = 'X'");

            assertEquals("Birthday", service.board(BIRTHDAY).queue().name());
            assertEquals("Sales", service.board(BIRTHDAY).queue().type());
        }

        @Test
        @DisplayName("saving one of two same-named queues leaves the other untouched")
        void savesOnlyTheSelectedRow() {
            service.saveDefinition(OVER_65_SECOND, "Second row.", "Pro_DOB is null");

            assertEquals("", service.board(OVER_65_FIRST).queue().description());
            assertEquals("Second row.", service.board(OVER_65_SECOND).queue().description());
        }
    }

    @Nested
    @DisplayName("US-5.5 — creating a queue")
    class Create {

        @Test
        @DisplayName("a new queue is empty and typed Sales, as the Delphi created them")
        void createsQueue() {
            QueueBoard board = service.createQueue("NewQ");

            assertEquals("NewQ", board.queue().name());
            assertEquals("", board.queue().sql());
            assertEquals("", board.queue().description());
            assertEquals(CallQueue.DEFAULT_TYPE, board.queue().type());
            assertTrue(board.permitted().isEmpty());
        }

        @Test
        @DisplayName("A32 — a duplicate name is refused, in any casing")
        void refusesDuplicate() {
            assertThrows(ValidationException.class, () -> service.createQueue("Birthday"));
            assertThrows(ValidationException.class, () -> service.createQueue("BIRTHDAY"));
        }

        @Test
        @DisplayName("A32 — a name longer than Cal_Name char(10) is refused, not truncated")
        void refusesOverLongName() {
            ValidationException thrown = assertThrows(ValidationException.class,
                    () -> service.createQueue("Prospect call queue"));

            assertTrue(thrown.getMessage().contains("10"));
        }

        @Test
        @DisplayName("a blank name is refused")
        void refusesBlankName() {
            assertThrows(ValidationException.class, () -> service.createQueue("   "));
        }
    }

    @Nested
    @DisplayName("US-5.7 — the By User tab")
    class ByUser {

        private static List<String> assignedNames(UserBoard board) {
            return board.assigned().stream().map(AssignedQueue::name).toList();
        }

        @Test
        @DisplayName("assigned and available are disjoint")
        void listsAreDisjoint() {
            UserBoard board = service.userBoard("DStante");

            assertTrue(assignedNames(board).containsAll(List.of("Birthday", "over 65")));
            List<String> available = board.available().stream().map(QueueRow::name).toList();
            assertFalse(available.contains("Birthday"));
            assertFalse(available.contains("over 65"));
            assertTrue(available.contains("Referral"));
        }

        @Test
        @DisplayName("A35 — an assigned queue that no longer exists is listed with no id")
        void keepsUnknownQueue() {
            AssignedQueue orphan = service.userBoard("DStante").assigned().stream()
                    .filter(q -> q.name().equals("IHPFULLYQ")).findFirst().orElseThrow();

            assertFalse(orphan.known());
            assertNull(orphan.id());
        }

        @Test
        @DisplayName("a null queue row is skipped rather than listed as blank")
        void skipsNullQueue() {
            assertTrue(service.userBoard("DStante").assigned().stream()
                    .noneMatch(q -> q.name() == null || q.name().isBlank()));
        }

        @Test
        @DisplayName("assigning moves the queue across")
        void assigns() {
            UserBoard board = service.assignQueues("CosmoAdm", List.of(BIRTHDAY, UNTYPED));

            assertTrue(assignedNames(board).containsAll(List.of("Birthday", "Untyped")));
            assertFalse(board.available().stream().map(QueueRow::name).toList()
                    .contains("Birthday"));
        }

        @Test
        @DisplayName("assigning a queue the user already holds adds no second row")
        void assignIsIdempotent() {
            int before = permissions.findByUser("DStante").size();

            service.assignQueues("DStante", List.of(BIRTHDAY));

            assertEquals(before, permissions.findByUser("DStante").size());
        }

        @Test
        @DisplayName("A32 — the second of two same-named queues adds nothing, the name being what is stored")
        void duplicateNamedQueueAddsOneRow() {
            service.assignQueues("CosmoAdm", List.of(OVER_65_FIRST, OVER_65_SECOND));

            assertEquals(1, permissions.findByUser("CosmoAdm").stream()
                    .filter(p -> "over 65".equals(p.getQueueName())).count());
        }

        @Test
        @DisplayName("A29 — unassigning actually removes the row; the Delphi's CQP_Usr never could")
        void unassignsForReal() {
            UserBoard board = service.unassignQueues("DStante", List.of("Birthday"));

            assertFalse(assignedNames(board).contains("Birthday"));
            assertTrue(permissions.findByUser("DStante").stream()
                    .noneMatch(p -> "Birthday".equals(p.getQueueName())));
        }

        @Test
        @DisplayName("A18 — an unassigned queue returns to the available list")
        void unassignReturnsQueueToAvailable() {
            UserBoard board = service.unassignQueues("DStante", List.of("Birthday"));

            assertTrue(board.available().stream().map(QueueRow::name).toList().contains("Birthday"));
        }

        @Test
        @DisplayName("A35 — an orphaned assignment can be cleared by name")
        void clearsOrphanedAssignment() {
            UserBoard board = service.unassignQueues("DStante", List.of("IHPFULLYQ"));

            assertFalse(assignedNames(board).contains("IHPFULLYQ"));
        }

        @Test
        @DisplayName("unassigning touches only the named user")
        void unassignIsUserScoped() {
            service.unassignQueues("DStante", List.of("Birthday"));

            assertTrue(permissions.findByUser("TenCharUsr").stream()
                    .anyMatch(p -> "Birthday".equals(p.getQueueName())));
        }

        @Test
        @DisplayName("an unknown user is a 404")
        void unknownUserIsNotFound() {
            assertThrows(NotFoundException.class, () -> service.userBoard("NotAUser"));
        }
    }

    @Nested
    @DisplayName("US-5.8 — copying queue permissions")
    class Copy {

        @Test
        @DisplayName("A30 — append copies what the target lacks and skips what it holds")
        void appendCopies() {
            CopyResult result = service.copy("DStante", "TenCharUsr", false);

            // DStante holds Birthday, over 65 and IHPFULLYQ; TenCharUsr already has Birthday.
            assertEquals(2, result.copied());
            assertEquals(0, result.removed());
            List<String> held = permissions.findByUser("TenCharUsr").stream()
                    .map(CallQueuePermission::getQueueName).toList();
            assertTrue(held.containsAll(List.of("Birthday", "over 65", "IHPFULLYQ")));
            assertEquals(1, held.stream().filter("Birthday"::equals).count());
        }

        @Test
        @DisplayName("replace clears the target first, in the same transaction")
        void replaceClearsTarget() {
            CopyResult result = service.copy("DStante", "CosmoAdm", true);

            assertEquals(1, result.removed());
            assertEquals(3, result.copied());
            List<String> held = permissions.findByUser("CosmoAdm").stream()
                    .map(CallQueuePermission::getQueueName).toList();
            assertFalse(held.contains("Referral"));
            assertTrue(held.containsAll(List.of("Birthday", "over 65", "IHPFULLYQ")));
        }

        @Test
        @DisplayName("copying to oneself is refused")
        void refusesSelfCopy() {
            assertThrows(ValidationException.class,
                    () -> service.copy("DStante", "DSTANTE", false));
        }

        @Test
        @DisplayName("an unknown source or target is a 404")
        void refusesUnknownParties() {
            assertThrows(NotFoundException.class,
                    () -> service.copy("NotAUser", "DStante", false));
            assertThrows(NotFoundException.class,
                    () -> service.copy("DStante", "NotAUser", false));
        }
    }
}
