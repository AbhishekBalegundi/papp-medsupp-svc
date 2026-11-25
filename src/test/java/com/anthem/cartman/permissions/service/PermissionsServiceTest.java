package com.anthem.cartman.permissions.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import com.anthem.cartman.common.exception.NotFoundException;
import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.permissions.service.PermissionsService.GrantChange;
import com.anthem.cartman.permissions.dto.PermissionViews.Grantee;
import com.anthem.cartman.permissions.dto.PermissionViews.ObjectGrantRow;
import com.anthem.cartman.permissions.dto.PermissionViews.UserGrantRow;
import com.anthem.cartman.permissions.service.PermissionsService;

@SpringBootTest
@Sql(scripts = "classpath:tm-login-h2.sql")
class PermissionsServiceTest {

    private static final String DAILY = "Daily Application Report";
    private static final String MONTHLY = "Monthly Premium Summary";
    private static final String ZIP = "Zip Code Extract";

    @Autowired
    private PermissionsService service;

    private UserGrantRow rowFor(String grantee, String object) {
        return service.byUser(grantee).stream()
                .filter(r -> r.objectName().equals(object))
                .findFirst().orElseThrow();
    }

    @Nested
    @DisplayName("US-3.1 — the pickers")
    class Pickers {

        @Test
        @DisplayName("lists logins then groups, groups shown in parentheses")
        void listsBoth() {
            List<Grantee> all = service.grantees();

            Grantee group = all.stream().filter(Grantee::group).findFirst().orElseThrow();
            assertEquals("Underwriters", group.name(), "the stored name has no parentheses");
            assertEquals("(Underwriters)", group.display(), "the display name has them");

            assertTrue(all.stream().anyMatch(g -> g.name().equals("DStante") && !g.group()));
        }

        @Test
        @DisplayName("objects are ordered by name")
        void objectsOrdered() {
            List<String> names = service.objectNames();

            assertEquals(List.of(DAILY, MONTHLY, ZIP), names);
        }
    }

    @Nested
    @DisplayName("US-3.3 — By Object")
    class ByObject {

        @Test
        @DisplayName("shows every login, permitted or not")
        void showsEveryLogin() {
            List<ObjectGrantRow> rows = service.byObject(DAILY);

            assertEquals(7, rows.stream().filter(r -> !r.group()).count(),
                    "all seven logins appear, like the Delphi's outer join");
            ObjectGrantRow unpermitted = rows.stream()
                    .filter(r -> r.granteeName().equals("SITUW")).findFirst().orElseThrow();
            assertEquals("", unpermitted.permission());
            assertEquals(0, unpermitted.level());
        }

        @Test
        @DisplayName("renders levels as the Delphi text")
        void rendersLevels() {
            List<ObjectGrantRow> rows = service.byObject(DAILY);

            assertEquals("Edit", rows.stream()
                    .filter(r -> r.granteeName().equals("DStante")).findFirst().orElseThrow()
                    .permission());
            assertEquals("Execute Only", rows.stream()
                    .filter(r -> r.granteeName().equals("TenCharUsr")).findFirst().orElseThrow()
                    .permission());
        }

        @Test
        @DisplayName("a group appears only when it holds a permission on that object")
        void groupsOnlyWhenPermitted() {
            assertTrue(service.byObject(DAILY).stream().anyMatch(ObjectGrantRow::group),
                    "Underwriters hold Daily");
            assertFalse(service.byObject(ZIP).stream().anyMatch(ObjectGrantRow::group),
                    "no group holds Zip Code Extract");
        }
    }

    @Nested
    @DisplayName("US-3.4 — By User")
    class ByUser {

        @Test
        @DisplayName("shows every object, permitted or not, with its type")
        void showsEveryObject() {
            List<UserGrantRow> rows = service.byUser("DStante");

            assertEquals(3, rows.size());
            assertEquals("Report", rows.get(0).type());
            assertEquals("Edit", rowFor("DStante", DAILY).permission());
            assertEquals("Execute Only", rowFor("DStante", MONTHLY).permission());
            assertEquals("", rowFor("DStante", ZIP).permission());
        }

        @Test
        @DisplayName("a group can be read with or without its display parentheses")
        void acceptsParenthesisedGroup() {
            assertEquals("Execute Only", rowFor("Underwriters", DAILY).permission());
            assertEquals("Execute Only", rowFor("(Underwriters)", DAILY).permission());
        }
    }

    @Nested
    @DisplayName("US-3.5 — grant and revoke")
    class Granting {

        @Test
        @DisplayName("applies one level across a whole selection")
        void appliesToSelection() {
            int changed = service.apply(List.of(
                    new GrantChange(DAILY, "SITUW"),
                    new GrantChange(MONTHLY, "SITUW"),
                    new GrantChange(ZIP, "SITUW")), 2);

            assertEquals(3, changed);
            assertTrue(service.byUser("SITUW").stream().allMatch(r -> r.permission().equals("Edit")));
        }

        @Test
        @DisplayName("updates a row that already exists rather than duplicating it")
        void updatesExisting() {
            service.apply(List.of(new GrantChange(DAILY, "DStante")), 1);

            assertEquals("Execute Only", rowFor("DStante", DAILY).permission());
        }

        @Test
        @DisplayName("revoking clears the permission")
        void revokes() {
            service.apply(List.of(new GrantChange(DAILY, "DStante")), 0);

            assertEquals("", rowFor("DStante", DAILY).permission());
        }

        @Test
        @DisplayName("a group is stored under its bare name, whichever tab the change came from")
        void stripsParenthesesOnWrite() {
            // This is anomaly A11: the Delphi's By User tab stored "(Underwriters)".
            service.apply(List.of(new GrantChange(ZIP, "(Underwriters)")), 2);

            assertEquals("Edit", rowFor("Underwriters", ZIP).permission(),
                    "the row must be findable by the bare name");
            assertTrue(service.byObject(ZIP).stream()
                            .anyMatch(r -> r.granteeName().equals("Underwriters") && r.group()),
                    "and it must show up on the By Object tab too");
        }

        @Test
        @DisplayName("an unknown level or an empty selection is refused")
        void validates() {
            assertThrows(ValidationException.class,
                    () -> service.apply(List.of(new GrantChange(DAILY, "DStante")), 7));
            assertThrows(ValidationException.class, () -> service.apply(List.of(), 1));
            assertThrows(ValidationException.class,
                    () -> service.apply(List.of(new GrantChange("", "DStante")), 1));
        }

        @Test
        @DisplayName("a granted row carries no invented run date")
        void noInventedRunDate() {
            // Anomaly A12: the Delphi wrote '' into a smalldatetime, giving 1900-01-01.
            service.apply(List.of(new GrantChange(ZIP, "DStante")), 1);

            assertNull(rowFor("DStante", ZIP).runDate());
        }
    }

    @Nested
    @DisplayName("US-3.7 — copy between users")
    class Copying {

        @Test
        @DisplayName("the folder travels with the permission but the run date does not")
        void copiesFolderNotRunDate() {
            service.copy("DStante", "SITUW", false);

            assertEquals("Daily", rowFor("SITUW", DAILY).folder());
            assertNull(rowFor("SITUW", DAILY).runDate());
        }

        @Test
        @DisplayName("copying a user onto themselves is refused before anything is deleted")
        void refusesSelfCopy() {
            assertThrows(ValidationException.class,
                    () -> service.copy("DStante", "DStante", true));

            assertEquals("Edit", rowFor("DStante", DAILY).permission(), "nothing was removed");
        }

        @Test
        @DisplayName("an unknown user or group is refused")
        void refusesUnknown() {
            assertThrows(NotFoundException.class, () -> service.copy("DStante", "ghost", false));
            assertThrows(NotFoundException.class, () -> service.copy("ghost", "DStante", false));
        }

        @Test
        @DisplayName("a group is a valid copy target")
        void copiesToGroup() {
            service.copy("DStante", "(Underwriters)", false);

            assertEquals("Execute Only", rowFor("Underwriters", DAILY).permission(),
                    "already held, so unchanged");
            assertEquals("Execute Only", rowFor("Underwriters", MONTHLY).permission());
        }
    }
}
