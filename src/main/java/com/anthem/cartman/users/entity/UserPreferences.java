package com.anthem.cartman.users.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.anthem.cartman.login.entity.Login;
import com.anthem.cartman.users.util.PreferenceFlags;

/**
 * The slice of {@code TM_Login} the Options dialog ({@code Options2.pas}, *User Preferences*)
 * reads and writes — a second entity over the same table as {@link com.anthem.cartman.login.entity.Login},
 * mapping only the preference columns so neither entity has to carry the other's concerns.
 *
 * <p>The Delphi saved through the stored procedure {@code spUpdLogin}; stored procedures are not
 * used here (and that procedure does not even exist in the current database), so the same columns
 * are updated directly.
 *
 * <p>Deliberately not mapped: the password the Delphi displayed on the Admin tab and passed back
 * through the procedure (anomaly A37) — this entity cannot touch it.
 */
@Entity
@Table(name = "TM_Login")
public class UserPreferences {

    @Id
    @Column(name = "Log_Nm", length = 10, nullable = false)
    private String loginName;

    // ── Admin tab: User Information ───────────────────────────────
    @Column(name = "Log_Real_Fnm", length = 15)
    private String firstName;

    @Column(name = "Log_Real_Lnm", length = 15)
    private String lastName;

    @Column(name = "Log_User_NickName", length = 24)
    private String nickName;

    @Column(name = "Log_Phone", length = 12)
    private String phone;

    @Column(name = "Log_800", length = 14)
    private String phone800;

    @Column(name = "Log_Ext", length = 6)
    private String extension;

    @Column(name = "Log_User_EMail", length = 50)
    private String email;

    /**
     * Displayed as a free field but saved as the literal {@code 0} by the Delphi regardless of
     * what was typed ({@code Options2.pas:763}, anomaly A38). Mapped read-only until someone
     * decides the field should actually work.
     */
    @Column(name = "Log_Office", insertable = false, updatable = false)
    private Integer office;

    @Column(name = "Log_Manager", length = 30)
    private String manager;

    @Column(name = "Log_Title", length = 50)
    private String title;

    @Column(name = "Log_Typ", length = 5)
    private String role;

    @Column(name = "Log_Dept", length = 1)
    private String department;

    // ── Font tab ──────────────────────────────────────────────────
    @Column(name = "Log_FontName", length = 32)
    private String fontName;

    @Column(name = "Log_FontSize")
    private Integer fontSize;

    // ── Dedicated option columns ──────────────────────────────────
    @Column(name = "Log_Zip_lookup")
    private Integer zipLookup;

    @Column(name = "Log_Navigation")
    private Integer navigationKeys;

    @Column(name = "Log_AllowJobs")
    private Integer allowMacros;

    @Column(name = "Log_AllowOwned")
    private Integer allowNonOwned;

    @Column(name = "Log_Lock_Notes")
    private Integer lockNotes;

    @Column(name = "Log_AllowEditing")
    private Integer allowLetterEditing;

    @Column(name = "Log_StrictCodes")
    private Integer strictCodes;

    @Column(name = "Log_AllowCreating")
    private Integer allowLetterCreation;

    @Column(name = "Log_CodeMan")
    private Integer codeManager;

    // ── Macro tab ─────────────────────────────────────────────────
    @Column(name = "Log_StartMacro", length = 64)
    private String startMacro;

    @Column(name = "Log_EndMacro", length = 64)
    private String endMacro;

    @Column(name = "Log_QueryMacro", length = 64)
    private String queryMacro;

    @Column(name = "Log_ClearMacro", length = 64)
    private String clearMacro;

    @Column(name = "Log_PrintMacro", length = 64)
    private String printMacro;

    // ── Everything else lives in the 128 flag positions ───────────
    @Column(name = "Log_Flags", length = 128)
    private String flags;

    // ── Read-only labels on the Admin tab ─────────────────────────
    @Column(name = "Log_Date", insertable = false, updatable = false)
    private LocalDateTime createdOn;

    @Column(name = "Log_Abbrev_ID", length = 3, insertable = false, updatable = false)
    private String abbrevId;

    protected UserPreferences() {
        // for JPA
    }

    public String getLoginName() {
        return trim(loginName);
    }

    public String getFirstName() {
        return trim(firstName);
    }

    public String getLastName() {
        return trim(lastName);
    }

    public String getNickName() {
        return trim(nickName);
    }

    public String getPhone() {
        return trim(phone);
    }

    public String getPhone800() {
        return trim(phone800);
    }

    public String getExtension() {
        return trim(extension);
    }

    public String getEmail() {
        return trim(email);
    }

    public Integer getOffice() {
        return office;
    }

    public String getManager() {
        return trim(manager);
    }

    public String getTitle() {
        return trim(title);
    }

    public String getRole() {
        return trim(role);
    }

    public String getDepartment() {
        return trim(department);
    }

    public String getFontName() {
        return trim(fontName);
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public boolean isZipLookup() {
        return isOn(zipLookup);
    }

    public boolean isNavigationKeys() {
        return isOn(navigationKeys);
    }

    public boolean isAllowMacros() {
        return isOn(allowMacros);
    }

    public boolean isAllowNonOwned() {
        return isOn(allowNonOwned);
    }

    public boolean isLockNotes() {
        return isOn(lockNotes);
    }

    public boolean isAllowLetterEditing() {
        return isOn(allowLetterEditing);
    }

    public boolean isStrictCodes() {
        return isOn(strictCodes);
    }

    public boolean isAllowLetterCreation() {
        return isOn(allowLetterCreation);
    }

    public boolean isCodeManager() {
        return isOn(codeManager);
    }

    public String getStartMacro() {
        return trim(startMacro);
    }

    public String getEndMacro() {
        return trim(endMacro);
    }

    public String getQueryMacro() {
        return trim(queryMacro);
    }

    public String getClearMacro() {
        return trim(clearMacro);
    }

    public String getPrintMacro() {
        return trim(printMacro);
    }

    public PreferenceFlags flags() {
        return PreferenceFlags.parse(flags);
    }

    public LocalDateTime getCreatedOn() {
        return createdOn;
    }

    public String getAbbrevId() {
        return trim(abbrevId);
    }

    // ── The one mutator: the dialog's OK writes everything at once ──

    public void applyUpdate(String firstName, String lastName, String nickName, String phone,
                            String phone800, String extension, String email, String manager,
                            String title, String role, String department, String fontName,
                            Integer fontSize, boolean zipLookup, boolean navigationKeys,
                            boolean allowMacros, boolean allowNonOwned, boolean lockNotes,
                            boolean allowLetterEditing, boolean strictCodes,
                            boolean allowLetterCreation, boolean codeManager, String startMacro,
                            String endMacro, String queryMacro, String clearMacro,
                            String printMacro, PreferenceFlags flags) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.nickName = nickName;
        this.phone = phone;
        this.phone800 = phone800;
        this.extension = extension;
        this.email = email;
        this.manager = manager;
        this.title = title;
        this.role = role;
        this.department = department;
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.zipLookup = zipLookup ? 1 : 0;
        this.navigationKeys = navigationKeys ? 1 : 0;
        this.allowMacros = allowMacros ? 1 : 0;
        this.allowNonOwned = allowNonOwned ? 1 : 0;
        this.lockNotes = lockNotes ? 1 : 0;
        this.allowLetterEditing = allowLetterEditing ? 1 : 0;
        this.strictCodes = strictCodes ? 1 : 0;
        this.allowLetterCreation = allowLetterCreation ? 1 : 0;
        this.codeManager = codeManager ? 1 : 0;
        this.startMacro = startMacro;
        this.endMacro = endMacro;
        this.queryMacro = queryMacro;
        this.clearMacro = clearMacro;
        this.printMacro = printMacro;
        this.flags = flags.toStoredValue();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
