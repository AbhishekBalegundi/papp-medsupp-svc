package com.anthem.cartman.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_User_Alias}. Primary key is ({@code UA_Log_Id}, {@code UA_Alias}).
 *
 * <p>{@code UA_Log_Id} is the owning login and {@code UA_Alias} is the name they may act as.
 * The two are easy to confuse — the Delphi read a user's aliases by {@code UA_Log_Id}
 * ({@code frmUserManager.pas:835}) but deleted by {@code UA_Alias} ({@code :227}). Accessors
 * here are named for the roles, not the columns, so a caller cannot pick the wrong one silently.
 */
@Entity
@Table(name = "TM_User_Alias")
@IdClass(UserAlias.Key.class)
public class UserAlias {

    @Id
    @Column(name = "UA_Log_Id", length = 10, nullable = false)
    private String ownerLoginName;

    @Id
    @Column(name = "UA_Alias", length = 10, nullable = false)
    private String alias;

    @Column(name = "UA_Default", nullable = false)
    private short defaultAlias;

    public UserAlias() {
        // for JPA
    }

    /**
     * A granted alias, never the default — {@code frmAliasManager1.pas:181} inserts
     * {@code UA_Default = 0} and leaves the existing default alone (US-4.3.4).
     */
    public static UserAlias granted(String ownerLoginName, String alias) {
        UserAlias a = new UserAlias();
        a.ownerLoginName = ownerLoginName;
        a.alias = alias;
        a.defaultAlias = 0;
        return a;
    }

    /** The self-alias every new user gets — {@code frmUserManager.pas:359}. */
    public static UserAlias self(String loginName) {
        UserAlias a = new UserAlias();
        a.ownerLoginName = loginName;
        a.alias = loginName;
        a.defaultAlias = 1;
        return a;
    }

    public String getOwnerLoginName() {
        return ownerLoginName == null ? null : ownerLoginName.trim();
    }

    public String getAlias() {
        return alias == null ? null : alias.trim();
    }

    public boolean isDefault() {
        return defaultAlias != 0;
    }

    public record Key(String ownerLoginName, String alias) {
        public Key() {
            this(null, null);
        }
    }
}
