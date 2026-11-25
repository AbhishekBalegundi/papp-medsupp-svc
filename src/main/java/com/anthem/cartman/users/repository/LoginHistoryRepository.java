package com.anthem.cartman.users.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.login.repository.LoginRepository;
import com.anthem.cartman.users.entity.LoginHistory;

/** {@code TM_Login_History} — read only; CARTMAN never writes sign-in rows. */
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, LoginHistory.Key> {

    /**
     * US-2.12.1 — every attempt for one user, newest first.
     *
     * <p>{@code Logh_Nm} is {@code char(10)}; the comparison trims explicitly so it does not
     * depend on the database's blank-padding semantics. Same reasoning as
     * {@code LoginRepository.findByLoginNameIgnoreCase}.
     */
    @Query("""
           select h from LoginHistory h
           where upper(trim(h.loginName)) = upper(trim(:loginName))
           order by h.attemptedAt desc
           """)
    List<LoginHistory> findAttempts(@Param("loginName") String loginName);

    /** US-2.2.3 — the five most recent attempts, for the properties panel. */
    @Query("""
           select h from LoginHistory h
           where upper(trim(h.loginName)) = upper(trim(:loginName))
           order by h.attemptedAt desc
           """)
    List<LoginHistory> findAttempts(@Param("loginName") String loginName, Limit limit);

    /**
     * US-2.11.4 — one page of distinct names at or after {@code start}, ascending.
     * Only users who have signed in appear, exactly as in the Delphi.
     */
    @Query("""
           select distinct trim(h.loginName) from LoginHistory h
           where trim(h.loginName) >= :start
           order by trim(h.loginName)
           """)
    List<String> pageForward(@Param("start") String start, Limit limit);

    /**
     * The next names strictly after {@code after}, ascending.
     *
     * <p>Distinct from {@link #pageForward}, which is inclusive of its start: inclusive is what a
     * jump to a letter wants (start the list at "d"), exclusive is what appending wants (continue
     * past the last name already shown, without repeating it).
     */
    @Query("""
           select distinct trim(h.loginName) from LoginHistory h
           where trim(h.loginName) > :after
           order by trim(h.loginName)
           """)
    List<String> pageAfter(@Param("after") String after, Limit limit);

    /**
     * US-2.11.8 — the page immediately before {@code before}. Read descending to take the
     * nearest names; the service reverses it so the page reads ascending.
     */
    @Query("""
           select distinct trim(h.loginName) from LoginHistory h
           where trim(h.loginName) < :before
           order by trim(h.loginName) desc
           """)
    List<String> pageBackward(@Param("before") String before, Limit limit);
}
