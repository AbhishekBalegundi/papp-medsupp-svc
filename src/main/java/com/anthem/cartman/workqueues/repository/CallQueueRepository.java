package com.anthem.cartman.workqueues.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.workqueues.entity.CallQueue;

/**
 * Repository for {@code TM_Call_Queue}.
 *
 * <p>{@code CAL_Name} is {@code char(10)}, so every comparison on it trims explicitly rather than
 * relying on the database's blank-padding rules — SQL Server ignores trailing blanks when comparing
 * and H2 does not.
 */
public interface CallQueueRepository extends JpaRepository<CallQueue, Integer> {

    /** US-5.1.1 — the queue list, ordered by name as the Delphi ordered it. */
    List<CallQueue> findAllByOrderByNameAsc();

    /**
     * Every queue carrying a given name. Returns a list, not an {@code Optional}: the name is not
     * unique (A32), and {@code over 65} really does match two rows.
     */
    @Query("select q from CallQueue q where upper(trim(q.name)) = upper(trim(:name)) order by q.id")
    List<CallQueue> findByName(@Param("name") String name);
}
