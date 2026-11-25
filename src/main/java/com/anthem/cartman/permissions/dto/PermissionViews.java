package com.anthem.cartman.permissions.dto;

import java.time.LocalDateTime;
import java.util.List;

/** The shapes the Object Permissions screen reads. */
public final class PermissionViews {

    private PermissionViews() {
    }

    /**
     * An entry in either picker (US-3.1). Groups are marked so the UI can show them the way the
     * Delphi did — wrapped in parentheses — without the parentheses ever reaching the database.
     *
     * @param name    the bare name, which is what {@code Perm_Nm} stores
     * @param display the name as the picker shows it: {@code (Group)} or just the login name
     */
    public record Grantee(String name, String display, boolean group) {
        public static Grantee user(String name) {
            return new Grantee(name, name, false);
        }

        public static Grantee group(String name) {
            return new Grantee(name, "(" + name + ")", true);
        }
    }

    /**
     * A row of the By Object grid (US-3.3): every login, permitted or not.
     *
     * @param permission rendered text — blank, "Execute Only" or "Edit"
     */
    public record ObjectGrantRow(String granteeName, String display, boolean group, int level,
                                 String permission, LocalDateTime runDate, String folder) {
    }

    /** A row of the By User grid (US-3.4): every object, permitted or not. */
    public record UserGrantRow(String objectName, String type, int level, String permission,
                               LocalDateTime runDate, String folder) {
    }

    public record CopyResult(String fromName, String toName, int copied, int removed) {
    }
}
