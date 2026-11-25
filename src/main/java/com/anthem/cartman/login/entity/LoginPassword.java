package com.anthem.cartman.login.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import com.anthem.cartman.login.service.AuthService;

/**
 * Maps {@code TM_LoginPW} — the table that actually holds passwords.
 *
 * <p>Not {@code TM_Login.Log_Pw}: that column exists but is empty in all 320 live rows.
 * The Delphi read a field <em>named</em> {@code Log_Pw} ({@code LoginForm.pas:343}), but it
 * came from the {@code spLogin2} stored procedure, which is not in the source tree — the name
 * was the procedure's alias, not the column it read. Verified directly: 314 of these rows hold
 * a password, every one printable ASCII, so the comparison is against clear text.
 *
 * <p>All of this disappears with Okta. Nothing outside {@link AuthService} may read
 * {@link #getPassword()}.
 */
@Entity
@Table(name = "TM_LoginPW")
public class LoginPassword {

    /** {@code char(10)}, space padded, and the primary key ({@code PK_TM_LoginPW}). */
    @Id
    @Column(name = "Lpw_Nm", length = 10, nullable = false)
    private String loginName;

    @Column(name = "Lpw_Pw", length = 32)
    private String password;

    /**
     * When the password expires. Every live row is already in the past, so the interim
     * sign-in does not enforce it — doing so would lock out all 314 users at once. Okta
     * takes over expiry policy.
     */
    @Column(name = "Lpw_ChngDt", nullable = false)
    private LocalDateTime changeDue;

    /** The change interval the Delphi wrote for a new user — {@code frmUserManager.pas:356}. */
    public static final int DEFAULT_CHANGE_DAYS = 45;

    @Column(name = "Lpw_ChngDays", nullable = false)
    private int changeDays;

    @Column(name = "Lpw_LastPW", length = 32)
    private String lastPassword;

    @Column(name = "Lpw_Last_Changed")
    private LocalDateTime lastChangedAt;

    public LoginPassword() {
        // for JPA
    }

    /**
     * Builds the password row for a new user (US-2.6.4): the initial password, a 45-day
     * change interval, today as {@code Lpw_ChngDt}, and an empty previous password.
     */
    public static LoginPassword initial(String loginName, String password, LocalDateTime now) {
        LoginPassword entry = new LoginPassword();
        entry.loginName = loginName;
        entry.password = password;
        entry.changeDays = DEFAULT_CHANGE_DAYS;
        entry.changeDue = now;
        entry.lastPassword = "";
        return entry;
    }

    public int getChangeDays() {
        return changeDays;
    }

    /** US-2.2.2 — "PW Last Changed" on the properties panel. */
    public LocalDateTime getLastChangedAt() {
        return lastChangedAt;
    }

    public String getLoginName() {
        return loginName == null ? null : loginName.trim();
    }

    /** Package-private: only {@link AuthService} may see it. */
    public String getPassword() {
        return password == null ? null : password.trim();
    }

    public LocalDateTime getChangeDue() {
        return changeDue;
    }
}
