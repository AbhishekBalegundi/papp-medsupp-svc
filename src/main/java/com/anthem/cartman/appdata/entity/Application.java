package com.anthem.cartman.appdata.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code TM_Application} — one application, as the Application Data Manager reads and writes it.
 *
 * <p>Only the columns that screen touches are mapped: the eleven identifying fields it shows, the
 * eight workflow dates and the five ownership fields it edits. The table has 262 columns; mapping
 * the rest would invite writes nobody asked for.
 *
 * <p>{@code @DynamicUpdate} so a save writes only the columns that actually changed (US-7.8, and the
 * correction to the Delphi's habit of writing all five ownership fields whether or not they were
 * touched).
 *
 * <p>{@code App_ID} is an {@code int}. The Delphi quotes it as a string in every statement and lets
 * SQL Server coerce; here it is a number, so a key value that is not one is refused before any query
 * runs.
 */
@Entity
@Table(name = "TM_Application")
@DynamicUpdate
public class Application {

    @Id
    @Column(name = "App_ID", nullable = false)
    private Integer id;

    // ── the eleven identifying fields, read-only on both tabs ────────
    @Column(name = "App_Lnm", length = 16)
    private String lastName;

    @Column(name = "App_Fnm", length = 16)
    private String firstName;

    @Column(name = "App_Typ", length = 3)
    private String type;

    @Column(name = "App_Addr1", length = 50)
    private String address1;

    @Column(name = "App_City", length = 32)
    private String city;

    @Column(name = "App_State", length = 2)
    private String state;

    @Column(name = "App_Zip", length = 10)
    private String zip;

    @Column(name = "App_Phone1", length = 12)
    private String phone1;

    @Column(name = "App_DOB")
    private LocalDateTime dateOfBirth;

    @Column(name = "App_HCID", length = 20)
    private String hcid;

    @Column(name = "App_DCN", length = 16)
    private String dcn;

    // ── the eight workflow dates (US-7.6.2) ──────────────────────────
    @Column(name = "App_Enroll_Complete_Dt")
    private LocalDate enrollCompleteDate;

    @Column(name = "App_Enroll_Dt")
    private LocalDate enrollDate;

    @Column(name = "App_EnrollSnt_Dt")
    private LocalDate enrollSentDate;

    @Column(name = "App_Keying_Dt")
    private LocalDate keyingDate;

    @Column(name = "App_KeyingSnt_Dt")
    private LocalDate keyingSentDate;

    @Column(name = "App_UWSnt_Dt")
    private LocalDate uwSentDate;

    @Column(name = "App_UWStatus_Dt")
    private LocalDate uwStatusDate;

    @Column(name = "App_UW_Dt")
    private LocalDate uwDate;

    // ── the five ownership fields (US-7.6.3) ─────────────────────────
    @Column(name = "App_UW_Status", length = 48)
    private String uwStatus;

    @Column(name = "App_Underwriter", length = 10)
    private String underwriter;

    @Column(name = "App_Keyer", length = 10)
    private String keyer;

    @Column(name = "App_Auditor", length = 10)
    private String auditor;

    @Column(name = "App_Coordinator", length = 10)
    private String coordinator;

    public Integer getId() {
        return id;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getType() {
        return type;
    }

    public String getAddress1() {
        return address1;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getZip() {
        return zip;
    }

    public String getPhone1() {
        return phone1;
    }

    public LocalDateTime getDateOfBirth() {
        return dateOfBirth;
    }

    public String getHcid() {
        return hcid;
    }

    public String getDcn() {
        return dcn;
    }

    public LocalDate getEnrollCompleteDate() {
        return enrollCompleteDate;
    }

    public LocalDate getEnrollDate() {
        return enrollDate;
    }

    public LocalDate getEnrollSentDate() {
        return enrollSentDate;
    }

    public LocalDate getKeyingDate() {
        return keyingDate;
    }

    public LocalDate getKeyingSentDate() {
        return keyingSentDate;
    }

    public LocalDate getUwSentDate() {
        return uwSentDate;
    }

    public LocalDate getUwStatusDate() {
        return uwStatusDate;
    }

    public LocalDate getUwDate() {
        return uwDate;
    }

    public String getUwStatus() {
        return uwStatus;
    }

    public String getUnderwriter() {
        return underwriter;
    }

    public String getKeyer() {
        return keyer;
    }

    public String getAuditor() {
        return auditor;
    }

    public String getCoordinator() {
        return coordinator;
    }

    public void setEnrollCompleteDate(LocalDate value) {
        this.enrollCompleteDate = value;
    }

    public void setEnrollDate(LocalDate value) {
        this.enrollDate = value;
    }

    public void setEnrollSentDate(LocalDate value) {
        this.enrollSentDate = value;
    }

    public void setKeyingDate(LocalDate value) {
        this.keyingDate = value;
    }

    public void setKeyingSentDate(LocalDate value) {
        this.keyingSentDate = value;
    }

    public void setUwSentDate(LocalDate value) {
        this.uwSentDate = value;
    }

    public void setUwStatusDate(LocalDate value) {
        this.uwStatusDate = value;
    }

    public void setUwDate(LocalDate value) {
        this.uwDate = value;
    }

    public void setUwStatus(String value) {
        this.uwStatus = value;
    }

    public void setUnderwriter(String value) {
        this.underwriter = value;
    }

    public void setKeyer(String value) {
        this.keyer = value;
    }

    public void setAuditor(String value) {
        this.auditor = value;
    }

    public void setCoordinator(String value) {
        this.coordinator = value;
    }
}
