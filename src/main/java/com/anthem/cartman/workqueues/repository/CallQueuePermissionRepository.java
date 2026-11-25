package com.anthem.cartman.workqueues.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.workqueues.entity.CallQueuePermission;

/**
 * Repository for {@code TM_Call_Queue_Permission}.
 *
 * <p>{@code CQP_Queue} is {@code char(16)} and {@code CQP_User} a {@code varchar(50)} fed from
 * {@code char(10)} names, so both are compared trimmed and upper-cased.
 *
 * <p>The bulk deletes flush and clear, because a bulk JPQL statement bypasses the persistence
 * context and a read afterwards would otherwise return rows the database no longer has.
 */
public interface CallQueuePermissionRepository extends JpaRepository<CallQueuePermission, Integer> {

    /** US-5.3.1 — everyone permitted on one queue. */
    @Query("select p from CallQueuePermission p "
            + "where upper(trim(p.queueName)) = upper(trim(:queue)) "
            + "order by upper(trim(p.userName))")
    List<CallQueuePermission> findByQueue(@Param("queue") String queueName);

    /** US-5.7.1 — every queue one user holds. */
    @Query("select p from CallQueuePermission p "
            + "where upper(trim(p.userName)) = upper(trim(:user)) "
            + "order by upper(trim(p.queueName))")
    List<CallQueuePermission> findByUser(@Param("user") String userName);

    /**
     * US-5.3.5 / US-5.7.3 — revoke one pair.
     *
     * <p>This is the statement the Delphi's By User tab got wrong, deleting on {@code CQP_Usr}
     * (anomaly A29). Duplicate pairs would all go, which is what the Delphi's SQL intended.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CallQueuePermission p "
            + "where upper(trim(p.userName)) = upper(trim(:user)) "
            + "and upper(trim(p.queueName)) = upper(trim(:queue))")
    int deletePair(@Param("user") String userName, @Param("queue") String queueName);

    /** US-5.8.2 — Replace clears everything the target holds before copying. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CallQueuePermission p where upper(trim(p.userName)) = upper(trim(:user))")
    int deleteByUser(@Param("user") String userName);
}
