package com.anthem.cartman.appdata.entity;

import java.time.LocalDate;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code TM_UW_Actions} — one underwriting action against an application.
 *
 * <p>Addressed by {@code UW_ID}, the table's real primary key. The Delphi addresses a row by
 * {@code UW_App_ID} + {@code UW_Action_ID}, which is not a unique pair: nothing in the database
 * enforces it, so an update written that way can reach more than one row. The pair happens to be
 * unique across all 388 rows today, which is exactly the kind of thing that stops being true later.
 */
@Entity
@Table(name = "TM_UW_Actions")
@DynamicUpdate
public class UwAction {

    @Id
    @Column(name = "UW_ID", nullable = false)
    private Integer id;

    @Column(name = "UW_App_ID")
    private Integer applicationId;

    @Column(name = "UW_Action_ID", nullable = false)
    private Integer actionId;

    @Column(name = "UW_Typ", length = 3)
    private String type;

    @Column(name = "UW_Action", length = 60)
    private String action;

    @Column(name = "UW_Start")
    private LocalDate start;

    @Column(name = "UW_Close")
    private LocalDate close;

    @Column(name = "UW_App_Dt")
    private LocalDate applicationDate;

    public Integer getId() {
        return id;
    }

    public Integer getApplicationId() {
        return applicationId;
    }

    public Integer getActionId() {
        return actionId;
    }

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getClose() {
        return close;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public void setStart(LocalDate value) {
        this.start = value;
    }

    public void setClose(LocalDate value) {
        this.close = value;
    }

    public void setApplicationDate(LocalDate value) {
        this.applicationDate = value;
    }
}
