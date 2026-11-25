package com.anthem.cartman.users.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.anthem.cartman.login.entity.Login;

/**
 * The parts of a user delete (US-2.9) that reach tables no other CARTMAN module owns.
 *
 * <p>{@code TM_Signature} and {@code TM_Common_Notes} are not modelled as entities — CARTMAN
 * only ever deletes from them, and mapping a whole table to delete by one column would be more
 * code with more to get wrong. These are parameter-bound native statements, not procedures.
 */
public interface UserDeletionRepository extends Repository<Login, String> {

    @Modifying
    @Query(value = "DELETE FROM TM_Signature WHERE RTRIM(Sig_Nm) = :loginName", nativeQuery = true)
    int deleteSignatures(@Param("loginName") String loginName);

    @Modifying
    @Query(value = "DELETE FROM TM_Common_Notes WHERE RTRIM(CNotes_Nm) = :loginName", nativeQuery = true)
    int deleteCommonNotes(@Param("loginName") String loginName);

    @Modifying
    @Query(value = "DELETE FROM TM_Report_Permission WHERE RTRIM(Perm_Nm) = :loginName", nativeQuery = true)
    int deleteReportPermissions(@Param("loginName") String loginName);

    /** US-2.6.2 — the duplicate-LAN-ID check the Add User dialog runs before inserting. */
    @Query(value = "SELECT COUNT(*) FROM TM_Login WHERE RTRIM(Log_LAN_ID) = :lanId", nativeQuery = true)
    long countByLanId(@Param("lanId") String lanId);
}
