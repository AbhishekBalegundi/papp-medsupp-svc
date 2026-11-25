package com.anthem.cartman.workqueues.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_Call_Queue_Permission} — one row per (user, queue) pair a user may open.
 *
 * <p>The key is the identity {@code CQP_ID}. The pair itself is not constrained in any way: both
 * columns are nullable, neither is a foreign key, and neither is unique. Live data shows what that
 * allowed — 72 of the 110 distinct users are neither a login nor an alias, 31 rows name a queue that
 * does not exist, and two rows have a null queue (anomaly A35).
 *
 * <p>{@code CQP_Queue} is {@code char(16)} while {@code CAL_Name} is {@code char(10)}, so the column
 * also holds names no queue could ever have had.
 */
@Entity
@Table(name = "TM_Call_Queue_Permission")
public class CallQueuePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CQP_ID", nullable = false)
    private Integer id;

    @Column(name = "CQP_User", length = 50)
    private String userName;

    @Column(name = "CQP_Queue", length = 16)
    private String queueName;

    public CallQueuePermission() {
        // for JPA
    }

    /** US-5.3.3 / US-5.7.2 — grant one user access to one queue. */
    public static CallQueuePermission of(String userName, String queueName) {
        CallQueuePermission p = new CallQueuePermission();
        p.userName = userName;
        p.queueName = queueName;
        return p;
    }

    public Integer getId() {
        return id;
    }

    public String getUserName() {
        return userName == null ? null : userName.trim();
    }

    public String getQueueName() {
        return queueName == null ? null : queueName.trim();
    }
}
