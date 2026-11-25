package com.anthem.cartman.permissions.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import com.anthem.cartman.permissions.service.PermissionsService;

/**
 * Maps {@code TM_Report_Permission}. Key is ({@code PERM_Name}, {@code PERM_Nm}) — the object and
 * the grantee.
 *
 * <p>{@code PERM_Nm} holds either a login name or a group name. The Delphi's UI wrapped group
 * names in parentheses for display and was supposed to strip them before writing; the By User tab
 * did not (anomaly A11). Nothing here ever stores a parenthesised name — see
 * {@link PermissionsService#bareName}.
 */
@Entity
@Table(name = "TM_Report_Permission")
@IdClass(ReportPermission.Key.class)
public class ReportPermission {

    /** {@code Perm_Level} 0 revokes, 1 grants execute, 2 grants edit. */
    public static final int NONE = 0;
    public static final int EXECUTE = 1;
    public static final int EDIT = 2;

    @Id
    @Column(name = "PERM_Name", length = 64, nullable = false)
    private String objectName;

    @Id
    @Column(name = "PERM_Nm", length = 50, nullable = false)
    private String granteeName;

    @Column(name = "PERM_Level", nullable = false)
    private int level;

    @Column(name = "PERM_Folder", length = 64)
    private String folder;

    /**
     * {@code smalldatetime}. The Delphi wrote an empty string here, which SQL Server coerced to
     * 1900-01-01 (anomaly A12); this writes {@code NULL} when there is no run date.
     */
    @Column(name = "PERM_Run_Date")
    private LocalDateTime runDate;

    public ReportPermission() {
        // for JPA
    }

    public static ReportPermission of(String objectName, String granteeName, int level) {
        ReportPermission p = new ReportPermission();
        p.objectName = objectName;
        p.granteeName = granteeName;
        p.level = level;
        return p;
    }

    public String getObjectName() {
        return objectName == null ? null : objectName.trim();
    }

    public String getGranteeName() {
        return granteeName == null ? null : granteeName.trim();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getFolder() {
        return folder == null ? null : folder.trim();
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public LocalDateTime getRunDate() {
        return runDate;
    }

    /** US-3.3 / US-3.4 — the text the grid shows for a level. */
    public static String describe(int level) {
        return switch (level) {
            case EXECUTE -> "Execute Only";
            case EDIT -> "Edit";
            default -> "";
        };
    }

    public record Key(String objectName, String granteeName) {
        public Key() {
            this(null, null);
        }
    }
}
