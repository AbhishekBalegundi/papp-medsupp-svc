package com.anthem.cartman.zipcodes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;
import com.anthem.cartman.zipcodes.entity.ZipCounty;

/** Module 14 — {@code TM_Cd_ZipCnty}. */
public interface ZipCountyRepository extends JpaRepository<ZipCounty, Integer> {

    /**
     * US-14.4.2 — the Delphi's own query, bound and projected.
     *
     * <p>{@code Zip_Zip_Cd} is {@code char(10)}, so the stored value is padded; the column is trimmed
     * rather than the search value padded. Ordered by county then city, as the form orders it.
     *
     * <p>Only the five columns the grid draws are selected: the table is 52,440 rows wide of thirteen
     * columns and none of the others is shown.
     */
    @Query("""
            select new com.anthem.cartman.zipcodes.dto.ZipCodeViews$ZipRow(
                       z.id, z.state, z.county, z.city, z.choice)
              from ZipCounty z
             where trim(z.zip) = :zip
             order by z.county, z.city
            """)
    List<ZipRow> findByZip(@Param("zip") String zip);
}
