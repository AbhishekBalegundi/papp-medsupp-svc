package com.anthem.cartman.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.permissions.dto.PermissionViews.ObjectGrantRow;
import com.anthem.cartman.permissions.dto.PermissionViews.UserGrantRow;
import com.anthem.cartman.permissions.service.PermissionsService;

/**
 * Paging and search over the permission grids — the screens that stood on `TM_Reports`, which
 * holds 5,187 rows live. The seed has three objects and seven logins, enough to prove the
 * arithmetic and the filters.
 */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class PermissionsPagingTest {

    @Autowired
    private PermissionsService service;

    @Test
    @DisplayName("the object picker returns a page, not the whole table")
    void objectPickerPages() {
        PageView<String> first = service.objectNames(null, 0, 2);

        assertEquals(2, first.items().size());
        assertEquals(3, first.totalItems());
        assertEquals(2, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(first.hasPrevious());

        PageView<String> second = service.objectNames(null, 1, 2);
        assertEquals(1, second.items().size());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
    }

    @Test
    @DisplayName("the object picker narrows by what has been typed, case-insensitively")
    void objectPickerSearches() {
        PageView<String> page = service.objectNames("monthly", 0, 50);

        assertEquals(1, page.totalItems());
        assertEquals("Monthly Premium Summary", page.items().get(0));
    }

    @Test
    @DisplayName("By Object pages, and the page is a slice of the same rows the full read gives")
    void byObjectPages() {
        int total = service.byObject("Daily Application Report").size();
        PageView<ObjectGrantRow> page = service.byObject("Daily Application Report", null, false, 0, 3);

        assertEquals(3, page.items().size());
        assertEquals(total, page.totalItems());
        assertEquals(service.byObject("Daily Application Report").get(0).granteeName(),
                page.items().get(0).granteeName());
    }

    @Test
    @DisplayName("By Object can show only the grantees that actually hold a permission")
    void byObjectFiltersToPermitted() {
        PageView<ObjectGrantRow> page =
                service.byObject("Daily Application Report", null, true, 0, 50);

        assertTrue(page.totalItems() > 0);
        assertTrue(page.items().stream().allMatch(r -> r.level() > 0));
    }

    @Test
    @DisplayName("By Object searches on the displayed name, so a group is findable as typed")
    void byObjectSearches() {
        PageView<ObjectGrantRow> page =
                service.byObject("Daily Application Report", "underwriters", false, 0, 50);

        assertEquals(1, page.totalItems());
        assertEquals("Underwriters", page.items().get(0).granteeName());
    }

    @Test
    @DisplayName("By User pages over every object — the read that was sending 5,187 rows")
    void byUserPages() {
        int total = service.byUser("DStante").size();
        PageView<UserGrantRow> page = service.byUser("DStante", null, false, 0, 2);

        assertEquals(2, page.items().size());
        assertEquals(total, page.totalItems());
        assertTrue(page.hasNext());
    }

    @Test
    @DisplayName("By User filters to the objects the grantee holds")
    void byUserFiltersToPermitted() {
        PageView<UserGrantRow> page = service.byUser("DStante", null, true, 0, 50);

        assertTrue(page.totalItems() > 0);
        assertTrue(page.items().stream().allMatch(r -> r.level() > 0));
        assertTrue(page.totalItems() < service.byUser("DStante").size());
    }

    @Test
    @DisplayName("By User searches by object name")
    void byUserSearches() {
        PageView<UserGrantRow> page = service.byUser("DStante", "zip", false, 0, 50);

        assertEquals(1, page.totalItems());
        assertEquals("Zip Code Extract", page.items().get(0).objectName());
    }
}
