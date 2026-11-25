package com.anthem.cartman.appdata.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anthem.cartman.appdata.entity.UwAction;

/** US-7.2.5, US-7.4, US-7.7 — the underwriting actions for one application. */
public interface UwActionRepository extends JpaRepository<UwAction, Integer> {

    /** Ordered by action id, which is how the Delphi's grid happens to arrive but never states. */
    List<UwAction> findByApplicationIdOrderByActionIdAsc(Integer applicationId);

    /** US-7.3, step 5 — every action for an application. Returns how many rows went. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UwAction a where a.applicationId = :applicationId")
    int deleteForApplication(@Param("applicationId") Integer applicationId);
}
