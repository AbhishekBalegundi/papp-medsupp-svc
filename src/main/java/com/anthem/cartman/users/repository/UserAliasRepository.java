package com.anthem.cartman.users.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.users.entity.UserAlias;

/**
 * {@code TM_User_Alias}.
 *
 * <p>Both key columns are {@code char(10)}, so every comparison trims explicitly rather than
 * relying on the database to ignore blank padding — SQL Server and PostgreSQL do, H2 does not.
 *
 * <p>Every {@code @Modifying} query below flushes before and clears after. A bulk JPQL update or
 * delete bypasses the persistence context, so without this a read in the same transaction returns
 * the stale entity — which is exactly how {@code setDefaultAlias} first appeared to do nothing.
 */
public interface UserAliasRepository extends JpaRepository<UserAlias, UserAlias.Key> {

    /** US-2.2.5 — the aliases a user owns. */
    @Query("select a from UserAlias a "
            + "where upper(trim(a.ownerLoginName)) = upper(trim(:owner)) "
            + "order by trim(a.alias)")
    List<UserAlias> findOwnedBy(@Param("owner") String ownerLoginName);

    /** Rows where the user owns the alias. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserAlias a where upper(trim(a.ownerLoginName)) = upper(trim(:owner))")
    int deleteOwnedBy(@Param("owner") String ownerLoginName);

    /** Rows where the user <em>is</em> someone else's alias — the Delphi deleted only these. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserAlias a where upper(trim(a.alias)) = upper(trim(:alias))")
    int deleteReferencing(@Param("alias") String alias);
}
