package com.anthem.cartman.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthem.cartman.common.dto.PageView;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import com.anthem.cartman.permissions.service.PermissionsService;

/**
 * The order a page of object names comes back in.
 *
 * The Delphi combo holds every name and completes against the first one beginning with what has
 * been typed. A page of ours has to carry those same names or there is nothing to complete
 * against — and ordered purely by name it did not: a search for "m" returned "2009 Med Supp",
 * "877 My Insurance…" and forty-eight more like them, every one a match and not one of them
 * starting with an M, so the picker suggested nothing until enough had been typed to make a
 * substring match rare.
 */
@SpringBootTest
@Sql(scripts = { "classpath:tm-login-h2.sql", "classpath:objects-prefix-h2.sql" })
class ObjectPickerOrderTest {

    @Autowired
    private PermissionsService service;

    @Test
    @DisplayName("names beginning with what was typed come first")
    void prefixMatchesLead() {
        PageView<String> page = service.objectNames("m", 0, 50);

        assertEquals(List.of("Monthly Premium Summary", "AAA Member List"), page.items(),
                "the name starting with M leads, though it sorts after the one merely containing it");
    }

    @Test
    @DisplayName("names merely containing the text are still found")
    void containsStillMatches() {
        PageView<String> page = service.objectNames("member", 0, 50);

        assertEquals(List.of("AAA Member List"), page.items());
    }

    @Test
    @DisplayName("the first page carries the match, so a one-letter search can complete")
    void firstPageCarriesTheMatch() {
        // The picker only ever sees page 0 while typing; a match on page 2 may as well not exist.
        PageView<String> page = service.objectNames("m", 0, 1);

        assertEquals(List.of("Monthly Premium Summary"), page.items());
        assertTrue(page.hasNext());
    }

    @Test
    @DisplayName("with nothing typed the list is plain alphabetical, as the combo holds it")
    void unsearchedStaysAlphabetical() {
        PageView<String> page = service.objectNames("", 0, 50);

        assertEquals(
                List.of("AAA Member List", "Daily Application Report", "Monthly Premium Summary",
                        "Zip Code Extract"),
                page.items());
    }
}
