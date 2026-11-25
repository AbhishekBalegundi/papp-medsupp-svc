package com.anthem.cartman.workqueues.dto;

import java.util.List;

/** The shapes the Work Queue Manager screen reads. */
public final class WorkQueueViews {

    private WorkQueueViews() {
    }

    /**
     * A row of the queue list (US-5.1.1).
     *
     * @param type  the stored {@code Cal_Type}, blank where there is none — the Delphi printed the
     *              literal {@code Sales} for all 113 rows (A31)
     * @param empty the queue has no SQL, so it produces nothing; 13 live rows are in that state
     */
    public record QueueRow(int id, String name, String type, boolean empty) {
    }

    /** A queue's definition (US-5.2). */
    public record QueueDetail(int id, String name, String type, String description, String sql) {
    }

    /**
     * An entry in either user list (US-5.3.1).
     *
     * @param known the name still exists in {@code TM_Login}. 72 of the 110 distinct permitted users
     *              are neither a login nor an alias (A35); they are shown, marked, not hidden
     */
    public record UserRow(String name, String loginType, boolean known) {

        public static UserRow login(String name, String loginType) {
            return new UserRow(name, loginType, true);
        }

        public static UserRow unknown(String name) {
            return new UserRow(name, "", false);
        }
    }

    /**
     * Everything the By Queue tab shows for one selected queue.
     *
     * <p>Both lists are derived on every read from what is actually stored, so the client never has
     * to mutate its own lists to stay correct.
     *
     * @param permitted users holding a permission on this queue
     * @param available every login that does not
     */
    public record QueueBoard(QueueDetail queue, List<UserRow> permitted, List<UserRow> available) {
    }

    /**
     * A queue a user holds (US-5.7.1).
     *
     * @param id    {@code null} when the named queue no longer exists — 31 live rows (A35)
     * @param known there is a queue of this name
     */
    public record AssignedQueue(Integer id, String name, boolean known) {
    }

    /** Everything the By User tab shows for one selected user. */
    public record UserBoard(String userName, List<AssignedQueue> assigned, List<QueueRow> available) {
    }

    public record CopyResult(String fromName, String toName, int copied, int removed) {
    }
}
