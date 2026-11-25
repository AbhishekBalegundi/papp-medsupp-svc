package com.anthem.cartman.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One line of a user's common notes ({@code TM_Common_Notes}) — the Notes tab of the Options
 * dialog shows them as a memo, one row per line. The Delphi's save deletes every row and inserts
 * the memo back line by line; the same happens here, inside one transaction.
 */
@Entity
@Table(name = "TM_Common_Notes")
public class CommonNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CNotes_ID", nullable = false)
    private Integer id;

    @Column(name = "CNotes_Nm", length = 10, nullable = false)
    private String loginName;

    @Column(name = "CNotes_Note", length = 500)
    private String note;

    protected CommonNote() {
        // for JPA
    }

    public static CommonNote of(String loginName, String note) {
        CommonNote n = new CommonNote();
        n.loginName = loginName;
        n.note = note;
        return n;
    }

    public Integer getId() {
        return id;
    }

    public String getLoginName() {
        return loginName == null ? null : loginName.trim();
    }

    public String getNote() {
        return note == null ? "" : note;
    }
}
