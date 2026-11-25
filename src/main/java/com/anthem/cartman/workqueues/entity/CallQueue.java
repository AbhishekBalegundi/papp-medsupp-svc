package com.anthem.cartman.workqueues.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_Call_Queue} — a work queue, which is a named saved query producing a worklist.
 *
 * <p>The key is {@code CAL_ID}, an identity column, and that is genuinely the table's primary key
 * ({@code PK_TM_Call_Queue}). The name is <b>not</b> unique: live data holds {@code over 65} twice
 * and {@code Test}/{@code test} as a case-only pair, because the Delphi's New Queue never checked
 * (anomaly A32). Addressing a queue by its id is what lets those rows stay individually editable.
 *
 * <p>{@code CAL_Desc} and {@code CAL_Query} are SQL Server {@code text}. They are mapped as
 * {@code LONGVARCHAR} rather than as a LOB so the driver reads and writes them as ordinary strings.
 */
@Entity
@Table(name = "TM_Call_Queue")
public class CallQueue {

    /** The type the Delphi's New Queue wrote into every new row. */
    public static final String DEFAULT_TYPE = "Sales";

    /** {@code CAL_Name char(10)} — over-long names were silently truncated (A32). */
    public static final int NAME_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CAL_ID", nullable = false)
    private Integer id;

    @Column(name = "CAL_Name", length = NAME_LENGTH, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "CAL_Desc")
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "CAL_Query")
    private String query;

    @Column(name = "CAL_Type", length = 10)
    private String type;

    public CallQueue() {
        // for JPA
    }

    /**
     * US-5.5.3 — a new queue: named, with an empty description and query, typed {@code Sales}.
     *
     * <p>The Delphi wrote empty strings for description and query and the literal {@code Sales} for
     * the type. Both are kept: the type is the only value any row was ever created with, and the
     * screen has no control for it.
     */
    public static CallQueue create(String name) {
        CallQueue q = new CallQueue();
        q.name = name;
        q.description = "";
        q.query = "";
        q.type = DEFAULT_TYPE;
        return q;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name == null ? null : name.trim();
    }

    public String getDescription() {
        return description;
    }

    public String getQuery() {
        return query;
    }

    /**
     * The real stored type, blank where there is none. The Delphi's list rendered the literal
     * {@code Sales} for every row regardless (A31); 67 live rows hold {@code NULL} and one is blank.
     */
    public String getType() {
        return type == null ? "" : type.trim();
    }

    /** US-5.4.2 — Save Queue writes exactly these two columns and nothing else. */
    public void updateDefinition(String description, String query) {
        this.description = description;
        this.query = query;
    }
}
