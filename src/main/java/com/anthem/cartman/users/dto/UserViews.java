package com.anthem.cartman.users.dto;

import java.time.LocalDateTime;
import java.util.List;

/** The shapes the User Manager screen reads. Grouped: they are one screen's vocabulary. */
public final class UserViews {

    private UserViews() {
    }

    /**
     * A row in the user list (US-2.1.6). The Delphi's list defined columns for type and name
     * but only ever filled the ID; the columns are populated here because they exist.
     */
    public record UserListItem(String loginName, String loginType, String firstName, String lastName,
                               boolean enabled) {
    }

    /** One sign-in attempt, already rendered the way the screen shows it (US-2.12.2). */
    public record LoginAttempt(LocalDateTime attemptedAt, String system, String status,
                               boolean successful) {
    }

    /**
     * Everything the properties panel shows for one user (US-2.2).
     *
     * @param lastGoodLogin newest successful sign-in among {@code recentHistory}, or null
     *                      when none of those five succeeded — matching US-2.2.4, which looks
     *                      only at the five rows on screen rather than the whole table
     */
    public record UserDetail(String loginName,
                             String displayName,
                             String firstName,
                             String lastName,
                             String loginType,
                             String department,
                             String manager,
                             String phone,
                             String extension,
                             String email,
                             String lanId,
                             String lastSystem,
                             String title,
                             boolean enabled,
                             int failedCount,
                             LocalDateTime passwordExpiresOn,
                             LocalDateTime passwordLastChangedOn,
                             LocalDateTime lastGoodLogin,
                             List<LoginAttempt> recentHistory,
                             List<String> aliases) {
    }

    /**
     * One page of the History tab's user list (US-2.11).
     *
     * @param pageSize the size actually used, which the client derives from its grid height
     */
    public record HistoryPage(List<String> loginNames, int pageSize, boolean hasNext) {
    }
}
