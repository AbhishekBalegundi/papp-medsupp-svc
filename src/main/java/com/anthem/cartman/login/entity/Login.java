package com.anthem.cartman.login.entity;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.anthem.cartman.toolaccess.domain.ToolAccessMask;

/**
 * Maps the existing Delphi {@code TM_Login} table. Same table, same columns, same key.
 *
 * <p>Column types verified against the live schema (SQL Server 2022, catalog {@code cosmo},
 * 130 columns, 321 rows):
 * <pre>
 *   Log_Nm            char(10)    NOT NULL   -- space padded
 *   Log_Real_Fnm      varchar(15) NOT NULL
 *   Log_Real_Lnm      varchar(15) NULL
 *   Log_Typ           char(5)     NULL       -- space padded
 *   Log_Enabled       int         NULL
 *   Log_CartmanTools  varchar(32) NULL       -- 306 of 321 rows are NULL
 * </pre>
 *
 * <p>{@code Log_Nm} stays the primary key: it is a foreign key from {@code App_Underwriter},
 * {@code App_Keyer}, {@code App_Auditor}, {@code App_Coordinator} and {@code UW_Underwriter},
 * so it cannot be replaced without breaking historical application records.
 *
 * <p>Only the columns this module needs are mapped; other modules widen it as they are built.
 */
@Entity
@Table(name = "TM_Login")
public class Login {

    /**
     * {@code char(10)}, space padded by SQL Server.
     *
     * <p>JPA forbids an {@code AttributeConverter} on an {@code @Id}, so the field keeps the
     * padded value Hibernate needs for identity and {@link #getLoginName()} trims on the way
     * out. That is the arrangement we want anyway: the database keeps its natural key, callers
     * never see the padding.
     */
    @Id
    @Column(name = "Log_Nm", length = 10, nullable = false)
    private String loginName;

    @Column(name = "Log_Real_Fnm", length = 15, nullable = false)
    private String firstName;

    @Column(name = "Log_Real_Lnm", length = 15)
    private String lastName;

    /** {@code char(5)} — Admin, Under, Keyer, Coord, Sales, Audit. */
    @Column(name = "Log_Typ", length = 5)
    @Convert(converter = TrimmedStringConverter.class)
    private String loginType;

    @Column(name = "Log_Enabled")
    private Integer enabled;

    // Log_Pw is deliberately NOT mapped. The column exists but is empty in all 320 live
    // rows; the real password store is TM_LoginPW.Lpw_Pw (see LoginPassword). Mapping it
    // here would invite the same wrong inference twice.

    /** {@code Log_Count} — consecutive failures; 5 locks the account. */
    @Column(name = "Log_Count", nullable = false)
    private int failedCount;

    /**
     * 32 characters of {@code '0'}/{@code '1'}, or {@code NULL}. Never read directly —
     * go through {@link com.anthem.cartman.toolaccess.domain.ToolAccessMask}.
     */
    @Column(name = "Log_CartmanTools", length = 32)
    private String cartmanTools;

    // ── Profile columns, shown by the User Manager properties panel (US-2.2) ──

    /** {@code char(1)} — H, O, U or N. Stored as the first character of the picker label. */
    @Column(name = "Log_Dept", length = 1)
    @Convert(converter = TrimmedStringConverter.class)
    private String department;

    @Column(name = "Log_Manager", length = 30)
    @Convert(converter = TrimmedStringConverter.class)
    private String manager;

    @Column(name = "Log_Phone", length = 12)
    @Convert(converter = TrimmedStringConverter.class)
    private String phone;

    @Column(name = "Log_Ext", length = 6)
    @Convert(converter = TrimmedStringConverter.class)
    private String extension;

    @Column(name = "Log_User_Email", length = 50)
    @Convert(converter = TrimmedStringConverter.class)
    private String email;

    @Column(name = "Log_LAN_ID", length = 12)
    @Convert(converter = TrimmedStringConverter.class)
    private String lanId;

    /** Spelled {@code LOg_System} in the schema; kept verbatim so the mapping is literal. */
    @Column(name = "LOg_System", length = 12)
    @Convert(converter = TrimmedStringConverter.class)
    private String lastSystem;

    @Column(name = "Log_Title", length = 50)
    @Convert(converter = TrimmedStringConverter.class)
    private String title;

    /** {@code NOT NULL}. The Delphi wrote the literal {@code 'xxx'} for new users. */
    @Column(name = "Log_Abbrev_ID", length = 3, nullable = false)
    @Convert(converter = TrimmedStringConverter.class)
    private String abbrevId;

    public Login() {
        // for JPA and for constructing fixtures
    }

    /**
     * Builds a new login for US-2.6. Mirrors the Delphi insert at
     * {@code frmUserManager.pas:338-353}, including {@code Log_Count = 0}.
     *
     * <p>{@code abbrevId} is a required column the Delphi filled with the literal {@code 'xxx'};
     * the caller supplies it so the placeholder is a visible decision rather than a buried one.
     */
    public static Login create(String loginName, String firstName, String lastName,
                               String loginType, String department, String manager,
                               String phone, String extension, String email, String lanId,
                               String abbrevId) {
        Login login = new Login();
        login.loginName = loginName;
        login.firstName = firstName;
        login.lastName = lastName;
        login.loginType = loginType;
        login.department = department;
        login.manager = manager;
        login.phone = phone;
        login.extension = extension;
        login.email = email;
        login.lanId = lanId;
        login.abbrevId = abbrevId;
        login.failedCount = 0;
        return login;
    }

    /** Login name with the {@code char(10)} padding stripped. */
    public String getLoginName() {
        return loginName == null ? null : loginName.trim();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getLoginType() {
        return loginType;
    }

    public String getCartmanTools() {
        return cartmanTools;
    }

    public void setCartmanTools(String cartmanTools) {
        this.cartmanTools = cartmanTools;
    }

    /** Delphi treated a null {@code Log_Enabled} as enabled. */
    public boolean isEnabled() {
        return enabled == null || enabled != 0;
    }

    public String getDepartment() {
        return department;
    }

    public String getManager() {
        return manager;
    }

    public String getPhone() {
        return phone;
    }

    public String getExtension() {
        return extension;
    }

    public String getEmail() {
        return email;
    }

    public String getLanId() {
        return lanId;
    }

    public String getLastSystem() {
        return lastSystem;
    }

    public String getTitle() {
        return title;
    }

    public String getAbbrevId() {
        return abbrevId;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    /** Display name, falling back to the login name when the real name is absent. */
    public String displayName() {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? (loginName == null ? "" : loginName.trim()) : full;
    }
}
