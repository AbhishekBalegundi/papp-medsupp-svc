package com.anthem.cartman.datadictionary.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.anthem.cartman.datadictionary.entity.DataDictionaryEntry;

/**
 * {@code TM_Data_Dictionary}. Both key columns are {@code char(32)}, so comparisons trim
 * explicitly rather than relying on the database's blank-padding rules.
 */
public interface DataDictionaryRepository extends JpaRepository<DataDictionaryEntry, DataDictionaryEntry.Key> {

    @Query("select e from DataDictionaryEntry e "
            + "where upper(trim(e.tableName)) = upper(trim(:table)) "
            + "and upper(trim(e.fieldName)) = upper(trim(:field))")
    Optional<DataDictionaryEntry> findEntry(@Param("table") String tableName,
                                            @Param("field") String fieldName);

    /** Every documented column of one table, so a whole grid loads in one query. */
    @Query("select e from DataDictionaryEntry e "
            + "where upper(trim(e.tableName)) = upper(trim(:table))")
    List<DataDictionaryEntry> findForTable(@Param("table") String tableName);
}
