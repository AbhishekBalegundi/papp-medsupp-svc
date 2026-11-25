package com.anthem.cartman.users.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.users.entity.CommonNote;

/** The Notes tab's rows, in insertion order — the order the Delphi memo showed them. */
public interface CommonNoteRepository extends JpaRepository<CommonNote, Integer> {

    @Query("select n from CommonNote n "
            + "where upper(trim(n.loginName)) = upper(trim(:loginName)) order by n.id")
    List<CommonNote> findByUser(@Param("loginName") String loginName);

    /** The save replaces every row; bulk JPQL bypasses the persistence context, hence the flushes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CommonNote n where upper(trim(n.loginName)) = upper(trim(:loginName))")
    int deleteByUser(@Param("loginName") String loginName);
}
