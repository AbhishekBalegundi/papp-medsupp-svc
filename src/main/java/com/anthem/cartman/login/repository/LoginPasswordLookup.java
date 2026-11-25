package com.anthem.cartman.login.repository;

import java.util.Optional;
import com.anthem.cartman.login.entity.LoginPassword;

/** The slice of {@code TM_LoginPW} the interim sign-in needs. Retires with Okta. */
public interface LoginPasswordLookup {

    Optional<LoginPassword> findByLoginNameIgnoreCase(String loginName);
}
