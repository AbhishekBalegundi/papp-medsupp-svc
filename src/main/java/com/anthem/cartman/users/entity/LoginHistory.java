package com.anthem.cartman.users.entity;

import java.time.LocalDateTime;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_Login_History} — one row per sign-in attempt.
 *
 * <p>The table has <strong>no primary key</strong> in the schema, so JPA needs a composite
 * identity to read it. Name plus timestamp plus status is used; it is a read-only view for
 * CARTMAN (US-2.12, US-2.2), nothing here writes to the table, so the identity only has to be
 * good enough to distinguish rows in the persistence context.
 */
@Entity
@Table(name = "TM_Login_History")
@IdClass(LoginHistory.Key.class)
public class LoginHistory {

    /** Delphi stored {@code 'S'} for success; anything else counts as a failure. */
    public static final String SUCCESS = "S";

    @Id
    @Column(name = "Logh_Nm", length = 10, nullable = false)
    private String loginName;

    @Id
    @Column(name = "Logh_Date")
    private LocalDateTime attemptedAt;

    /** Part of the identity, so it cannot carry a converter; trimmed by {@link #getStatus()}. */
    @Id
    @Column(name = "Logh_Status", length = 1, nullable = false)
    private String status;

    @Column(name = "Logh_System", length = 16)
    @Convert(converter = TrimmedStringConverter.class)
    private String system;

    public LoginHistory() {
        // for JPA
    }

    public String getLoginName() {
        return loginName == null ? null : loginName.trim();
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public String getStatus() {
        return status == null ? null : status.trim();
    }

    public String getSystem() {
        return system;
    }

    /** US-2.12.2 — {@code S} in any case is Successful; everything else is Failed. */
    public boolean successful() {
        return SUCCESS.equalsIgnoreCase(getStatus());
    }

    /** Composite identity; see the class note on why the table has none of its own. */
    public record Key(String loginName, LocalDateTime attemptedAt, String status) {
        public Key() {
            this(null, null, null);
        }
    }
}
