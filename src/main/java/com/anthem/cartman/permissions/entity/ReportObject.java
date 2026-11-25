package com.anthem.cartman.permissions.entity;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_Reports} — the objects permissions are granted on.
 *
 * <p>Only the three columns the Permissions screen reads are mapped. The table also holds the
 * report SQL and body, which belong to the reporting module, not to this one.
 */
@Entity
@Table(name = "TM_Reports")
public class ReportObject {

    @Id
    @Column(name = "RPT_Name", length = 64, nullable = false)
    private String name;

    /** Shown in the By User grid's "Type" column (US-3.4.2). */
    @Column(name = "RPT_Style", length = 16, nullable = false)
    @Convert(converter = TrimmedStringConverter.class)
    private String style;

    @Column(name = "RPT_Folder", length = 32)
    @Convert(converter = TrimmedStringConverter.class)
    private String folder;

    public ReportObject() {
        // for JPA
    }

    public String getName() {
        return name == null ? null : name.trim();
    }

    public String getStyle() {
        return style;
    }

    public String getFolder() {
        return folder;
    }
}
