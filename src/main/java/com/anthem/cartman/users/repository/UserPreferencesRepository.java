package com.anthem.cartman.users.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.users.entity.UserPreferences;

/**
 * The Options dialog's slice of {@code TM_Login}. {@code Log_Nm} is {@code char(10)}, so the
 * lookup trims explicitly — SQL Server ignores trailing blanks when comparing, H2 does not.
 */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, String> {

    @Query("select p from UserPreferences p "
            + "where upper(trim(p.loginName)) = upper(trim(:loginName))")
    Optional<UserPreferences> findByName(@Param("loginName") String loginName);
}
