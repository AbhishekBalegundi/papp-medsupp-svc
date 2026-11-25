package com.anthem.cartman.login.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.login.entity.Login;

/**
 * Spring Data JPA access to {@code TM_Login}.
 *
 * <p>Replaces the Delphi's inline, string-concatenated SQL. Every lookup is parameterised;
 * no {@code nolock} hint is used.
 */
public interface LoginRepository extends JpaRepository<Login, String>, LoginLookup {

    /**
     * All logins ordered by name — rule R6, matching
     * {@code Select Log_Nm From Tm_Login Order By Log_Nm} ({@code frmToolsAdmin.pas:63}).
     */
    @Override
    List<Login> findAllByOrderByLoginNameAsc();

    /**
     * One page of logins matching the Find box, ordered by login name.
     *
     * <p>The Delphi read the whole table because it drew the whole list. We draw a page, so the
     * page is what we read: filtering in Java meant every keystroke pulled all 321 rows, and at
     * five figures that stops being free.
     *
     * <p>The needle is matched against the login name and the two name columns, which is what the
     * grid shows.
     */
    @Query("""
           select l from Login l
           where :needle = '' or lower(trim(l.loginName)) like concat('%', :needle, '%')
              or lower(trim(coalesce(l.firstName, ''))) like concat('%', :needle, '%')
              or lower(trim(coalesce(l.lastName, ''))) like concat('%', :needle, '%')
           order by l.loginName
           """)
    List<Login> findPage(@Param("needle") String needle, Pageable pageable);

    /** How many logins the same filter matches, for the page totals. */
    @Query("""
           select count(l) from Login l
           where :needle = '' or lower(trim(l.loginName)) like concat('%', :needle, '%')
              or lower(trim(coalesce(l.firstName, ''))) like concat('%', :needle, '%')
              or lower(trim(coalesce(l.lastName, ''))) like concat('%', :needle, '%')
           """)
    long countPage(@Param("needle") String needle);

    /**
     * Case-insensitive lookup: the Delphi combo box matched on the displayed text.
     *
     * <p>{@code Log_Nm} is {@code char(10)}, so the stored value is blank padded. The comparison
     * trims explicitly rather than relying on the database to ignore trailing blanks — SQL Server
     * and PostgreSQL do, H2 does not, and depending on that difference makes the same query
     * behave differently per environment.
     */
    @Override
    @Query("select l from Login l where upper(trim(l.loginName)) = upper(trim(:loginName))")
    Optional<Login> findByLoginNameIgnoreCase(@Param("loginName") String loginName);
}
