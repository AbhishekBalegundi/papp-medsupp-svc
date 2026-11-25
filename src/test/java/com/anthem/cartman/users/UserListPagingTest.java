package com.anthem.cartman.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.users.dto.UserViews.UserListItem;
import com.anthem.cartman.users.service.UserManagerService;

/** US-2.1 paged: the grid draws an icon per user, so the page bounds the DOM as well as the wire. */
@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class UserListPagingTest {

    @Autowired
    private UserManagerService service;

    @Test
    @DisplayName("a page carries the size asked for and the full total")
    void pages() {
        PageView<UserListItem> page = service.listUsers(null, 0, 3);

        assertEquals(3, page.items().size());
        assertEquals(7, page.totalItems()); // the seed's seven logins
        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
    }

    @Test
    @DisplayName("pages stay in the ordered sequence, with nothing dropped or repeated")
    void pagesTileTheWholeList() {
        var all = service.listUsers().stream().map(UserListItem::loginName).toList();

        var paged = new java.util.ArrayList<String>();
        for (int p = 0; p < 4; p++) {
            service.listUsers(null, p, 2).items().forEach(u -> paged.add(u.loginName()));
        }

        assertEquals(all, paged);
    }

    @Test
    @DisplayName("search matches the login name")
    void searchesLoginName() {
        PageView<UserListItem> page = service.listUsers("tenchar", 0, 50);

        assertEquals(1, page.totalItems());
        assertEquals("TenCharUsr", page.items().get(0).loginName());
    }

    @Test
    @DisplayName("search matches the real name too, so a user is findable as people say it")
    void searchesRealName() {
        PageView<UserListItem> page = service.listUsers("darron", 0, 50);

        assertEquals(1, page.totalItems());
        assertEquals("DStante", page.items().get(0).loginName());
    }

    @Test
    @DisplayName("a search matching nothing is an empty page, not an error")
    void searchWithNoMatch() {
        PageView<UserListItem> page = service.listUsers("nobody-here", 0, 50);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalItems());
    }
}
