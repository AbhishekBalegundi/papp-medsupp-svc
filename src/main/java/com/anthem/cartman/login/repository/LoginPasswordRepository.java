package com.anthem.cartman.login.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.login.entity.LoginPassword;

public interface LoginPasswordRepository
        extends JpaRepository<LoginPassword, String>, LoginPasswordLookup {

    /**
     * {@code Lpw_Nm} is {@code char(10)}, so the comparison trims explicitly rather than relying
     * on the database to ignore blank padding. Same reasoning as
     * {@link LoginRepository#findByLoginNameIgnoreCase}.
     */
    @Override
    @Query("select p from LoginPassword p where upper(trim(p.loginName)) = upper(trim(:loginName))")
    Optional<LoginPassword> findByLoginNameIgnoreCase(@Param("loginName") String loginName);
}
