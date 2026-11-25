package com.anthem.cartman.appimages.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.anthem.cartman.appimages.dto.AppImageViews.PageRow;
import com.anthem.cartman.appimages.entity.AppImage;

/**
 * Module 11 — the stored pages.
 *
 * <p><b>A73.</b> CARTMAN reads every row of the table on every refresh and then filters the result in
 * the grid. The filter belongs in the query, so the list projection takes it — and the projection
 * selects five short columns and the document's <em>length</em>, never the document, so listing a
 * thousand pages does not move a thousand PDFs.
 *
 * <p><b>US-11.1.2 — the ordering is the Delphi's, including the part that looks odd:</b> page number is
 * ordered by its <em>length</em> before its value, so page 2 comes before page 10 rather than after it.
 */
@Repository
public interface AppImageRepository extends JpaRepository<AppImage, Integer> {

    /**
     * US-11.1 / US-11.2 — the list, narrowed. A null or blank part of the filter matches everything,
     * which is how the Delphi's three filter boxes behave when left empty.
     */
    @Query("""
            select new com.anthem.cartman.appimages.dto.AppImageViews$PageRow(
                    i.id, i.state, i.year, i.version, i.pageNumber,
                    case when i.page is null then false else true end)
            from AppImage i
            where (:state is null or i.state = :state)
              and (:year is null or i.year = :year)
              and (:version is null or i.version = :version)
            order by i.state, i.year, i.version, length(i.pageNumber), i.pageNumber
            """)
    List<PageRow> list(@Param("state") String state,
                       @Param("year") String year,
                       @Param("version") String version);

    /** US-11.3 — one document, read only when somebody asks to see it. */
    @Query("select i from AppImage i where i.id = :id")
    Optional<AppImage> findWithPage(@Param("id") int id);
}
