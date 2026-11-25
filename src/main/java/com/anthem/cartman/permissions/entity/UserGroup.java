package com.anthem.cartman.permissions.entity;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_User_Group} — which reports belong to which named group.
 *
 * <p>The table has no primary key and both columns are nullable. Only distinct group names are
 * ever read, so {@code UGP_Group_Name} carries the {@code @Id} purely to satisfy JPA; nothing
 * loads a single row by it.
 */
@Entity
@Table(name = "TM_User_Group")
public class UserGroup {

    @Id
    @Column(name = "UGP_Group_Name", length = 50)
    private String groupName;

    @Column(name = "UGP_Report", length = 50)
    @Convert(converter = TrimmedStringConverter.class)
    private String reportName;

    public UserGroup() {
        // for JPA
    }

    public String getGroupName() {
        return groupName == null ? null : groupName.trim();
    }

    public String getReportName() {
        return reportName;
    }
}
