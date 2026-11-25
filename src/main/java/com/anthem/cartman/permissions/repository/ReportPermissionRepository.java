package com.anthem.cartman.permissions.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.permissions.entity.ReportObject;
import com.anthem.cartman.permissions.entity.ReportPermission;

/**
 * Repository for {@code TM_Report_Permission}.
 *
 * <p>Both key columns are fixed-width or padded in places, so every comparison trims explicitly
 * rather than relying on the database's blank-padding rules.
 */
public interface ReportPermissionRepository extends JpaRepository<ReportPermission, ReportPermission.Key> {

    @Query("select p from ReportPermission p "
            + "where upper(trim(p.objectName)) = upper(trim(:object)) "
            + "and upper(trim(p.granteeName)) = upper(trim(:grantee))")
    Optional<ReportPermission> find(@Param("object") String objectName,
                                    @Param("grantee") String granteeName);

    /** Every permission held by one user or group (US-3.4). */
    @Query("select p from ReportPermission p "
            + "where upper(trim(p.granteeName)) = upper(trim(:grantee))")
    List<ReportPermission> findByGrantee(@Param("grantee") String granteeName);

    /** Everyone holding a permission on one object (US-3.3). */
    @Query("select p from ReportPermission p "
            + "where upper(trim(p.objectName)) = upper(trim(:object))")
    List<ReportPermission> findByObject(@Param("object") String objectName);

    /** US-3.7.5 — Replace clears the target's permissions before copying. */
    @Modifying
    @Query("delete from ReportPermission p where upper(trim(p.granteeName)) = upper(trim(:grantee))")
    int deleteByGrantee(@Param("grantee") String granteeName);
}
