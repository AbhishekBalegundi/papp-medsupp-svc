package com.anthem.cartman.login.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import com.anthem.cartman.login.entity.Login;

/**
 * The narrow slice of login persistence that the Tool Access module needs.
 *
 * <p>Services depend on this rather than on {@link LoginRepository} directly, so they are not
 * coupled to the whole {@code JpaRepository} surface and can be tested without a database.
 * {@link LoginRepository} extends it, so the Spring Data proxy satisfies it at runtime.
 */
public interface LoginLookup {

    /** All logins ordered by name — rule R6. */
    List<Login> findAllByOrderByLoginNameAsc();

    /** Case-insensitive lookup by {@code Log_Nm}. */
    Optional<Login> findByLoginNameIgnoreCase(String loginName);

    /** Persist a change. */
    <S extends Login> S save(S entity);

    /**
     * One page of logins matching the Find box, ordered by login name.
     *
     * <p>Declared here rather than only on {@link LoginRepository} because the services depend on
     * this narrow port; a query they call has to be part of it. {@link LoginRepository} carries
     * the JPQL.
     */
    List<Login> findPage(String needle, Pageable pageable);

    /** How many logins the same filter matches. */
    long countPage(String needle);
}
