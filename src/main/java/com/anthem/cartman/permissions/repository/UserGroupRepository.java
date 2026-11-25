package com.anthem.cartman.permissions.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.anthem.cartman.permissions.entity.UserGroup;

/**
 * Read-only access to {@code TM_User_Group}, which supplies the group names the pickers show
 * in parentheses (US-3.1.2).
 *
 * <p>The table has no primary key; only the distinct group name is ever read, so the entity is
 * keyed on the group name for JPA's benefit alone.
 */
public interface UserGroupRepository extends JpaRepository<UserGroup, String> {

    /** US-3.1.2 — the distinct group names, ordered. */
    @Query("select distinct trim(g.groupName) from UserGroup g "
            + "where g.groupName is not null and trim(g.groupName) <> '' "
            + "order by trim(g.groupName)")
    List<String> findDistinctGroupNames();
}
