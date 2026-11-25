package com.anthem.cartman.permissions.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.permissions.entity.ReportObject;
import com.anthem.cartman.permissions.entity.ReportPermission;

/** Read-only access to {@code TM_Reports}. */
public interface ReportObjectRepository extends JpaRepository<ReportObject, String> {

    List<ReportObject> findAllByOrderByNameAsc();

    /**
     * One page of object names matching what has been typed, prefix matches first.
     *
     * <p>The filter, the ordering and the page all happen in the database. Reading the whole
     * table and slicing it in Java gave the same answer but cost a full read per keystroke, and
     * the cost did not vary with the page asked for — at five figures of rows that is the
     * type-ahead undone.
     *
     * <p>The ordering is what lets the picker complete from the first letter: the Delphi combo
     * holds every name and completes against the first one *beginning* with the text, so a page
     * must carry those names or there is nothing to complete against.
     */
    @Query("""
           select trim(r.name) from ReportObject r
           where :needle = '' or lower(trim(r.name)) like concat('%', :needle, '%')
           order by
             case when lower(trim(r.name)) like concat(:needle, '%') then 0 else 1 end,
             lower(trim(r.name))
           """)
    List<String> findNames(@Param("needle") String needle, Pageable pageable);

    /** How many names the same filter matches, for the page totals. */
    @Query("""
           select count(r) from ReportObject r
           where :needle = '' or lower(trim(r.name)) like concat('%', :needle, '%')
           """)
    long countNames(@Param("needle") String needle);
}
