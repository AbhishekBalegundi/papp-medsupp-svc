package com.anthem.cartman.users.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.users.service.UserManagerService.DeletionReport;
import com.anthem.cartman.users.service.UserManagerService.NewUser;
import com.anthem.cartman.users.dto.PreferencesViews.MailResult;
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesUpdate;
import com.anthem.cartman.users.dto.PreferencesViews.PreferencesView;
import com.anthem.cartman.users.dto.UserViews.HistoryPage;
import com.anthem.cartman.users.dto.UserViews.LoginAttempt;
import com.anthem.cartman.users.dto.UserViews.UserDetail;
import com.anthem.cartman.users.dto.UserViews.UserListItem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.anthem.cartman.users.service.AccountRefreshMailService;
import com.anthem.cartman.users.service.UserManagerService;
import com.anthem.cartman.users.service.UserPreferencesService;

/** Module 2 — User Manager. */
@RestController
@RequestMapping("/api/users")
public class UserManagerController {

    private final UserManagerService service;
    private final UserPreferencesService preferences;
    private final AccountRefreshMailService mail;

    public UserManagerController(UserManagerService service, UserPreferencesService preferences,
                                 AccountRefreshMailService mail) {
        this.service = service;
        this.preferences = preferences;
        this.mail = mail;
    }

    /** US-2.15 — the Options dialog's contents. */
    @GetMapping("/{loginName}/preferences")
    public PreferencesView getPreferences(@PathVariable String loginName) {
        return preferences.get(loginName);
    }

    /** US-2.15 — OK writes everything the dialog holds, in one transaction. */
    @PutMapping("/{loginName}/preferences")
    public PreferencesView savePreferences(@PathVariable String loginName,
                                           @RequestBody PreferencesUpdate update) {
        return preferences.save(loginName, update);
    }

    /** US-2.14 — the account-refresh e-mail from the History tab. */
    @PostMapping("/{loginName}/account-refresh-email")
    public MailResult sendAccountRefreshEmail(@PathVariable String loginName,
                                              @Valid @RequestBody MailRequest request) {
        return mail.send(loginName, request.toAddress());
    }

    public record MailRequest(@NotBlank String toAddress) {
    }

    /**
     * US-2.1 — the user list, one page at a time. `search` matches the login name or the real
     * name; omit `page`/`size` and the first page comes back at the default size.
     */
    @GetMapping
    public PageView<UserListItem> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageView.DEFAULT_SIZE) int size) {
        return service.listUsers(search, page, size);
    }

    /** US-2.2 — the properties panel. */
    @GetMapping("/{loginName}")
    public UserDetail get(@PathVariable String loginName) {
        return service.getUser(loginName);
    }

    /**
     * US-2.6 — create a user.
     *
     * @param confirmDuplicateLanId set once the administrator has answered the
     *                              "This LAN ID already exists" prompt
     */
    @PostMapping
    public UserDetail create(@Valid @RequestBody CreateUserRequest request,
                             @RequestParam(defaultValue = "false") boolean confirmDuplicateLanId) {
        return service.createUser(new NewUser(request.loginName(), request.password(),
                request.firstName(), request.lastName(), request.role(), request.department(),
                request.manager(), request.phone(), request.extension(), request.email(),
                request.lanId()), confirmDuplicateLanId);
    }

    /** US-2.6.2 — lets the dialog ask before submitting, as the Delphi did. */
    /** US-2.9 — delete a user and everything keyed to them. */
    @DeleteMapping("/{loginName}")
    public DeletionReport delete(@PathVariable String loginName) {
        return service.deleteUser(loginName);
    }

    /** US-2.12 — one user's full sign-in history. */
    @GetMapping("/{loginName}/history")
    public List<LoginAttempt> history(@PathVariable String loginName) {
        return service.loginHistory(loginName);
    }

    /**
     * US-2.11 — a page of names that appear in the sign-in history.
     *
     * @param startAt  first name to include; the A–Z strip passes a single letter
     * @param before   read the page immediately before this name instead ("Prev")
     * @param pageSize how many rows the client's grid can show
     */
    @GetMapping("/history/page")
    public HistoryPage historyPage(@RequestParam(required = false) String startAt,
                                   @RequestParam(required = false) String after,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        // `after` continues a list being scrolled; `startAt` begins one at a letter.
        return after != null && !after.isBlank()
                ? service.historyPageAfter(after, pageSize)
                : service.historyPage(startAt, pageSize);
    }

    public record CreateUserRequest(@NotBlank String loginName,
                                    @NotBlank String password,
                                    @NotBlank String firstName,
                                    String lastName,
                                    String role,
                                    String department,
                                    String manager,
                                    String phone,
                                    String extension,
                                    String email,
                                    String lanId) {
    }

    }
