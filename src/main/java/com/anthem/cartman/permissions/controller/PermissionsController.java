package com.anthem.cartman.permissions.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.common.dto.PageView;
import com.anthem.cartman.permissions.service.PermissionsService.GrantChange;
import com.anthem.cartman.permissions.dto.PermissionViews.CopyResult;
import com.anthem.cartman.permissions.dto.PermissionViews.Grantee;
import com.anthem.cartman.permissions.dto.PermissionViews.ObjectGrantRow;
import com.anthem.cartman.permissions.dto.PermissionViews.UserGrantRow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.anthem.cartman.permissions.service.PermissionsService;

/** Module 3 — Object Permissions. */
@RestController
@RequestMapping("/api/permissions")
public class PermissionsController {

    private final PermissionsService service;

    public PermissionsController(PermissionsService service) {
        this.service = service;
    }

    /** US-3.1 — users and groups for the pickers. */
    @GetMapping("/grantees")
    public List<Grantee> grantees() {
        return service.grantees();
    }

    /**
     * US-3.1.3 — objects for the picker, one page at a time. `TM_Reports` holds 5,187 rows, so
     * the picker never asks for them all; `search` narrows by what has been typed.
     */
    @GetMapping("/objects")
    public PageView<String> objects(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageView.DEFAULT_SIZE) int size) {
        return service.objectNames(search, page, size);
    }

    /** US-3.3 — the By Object grid, paged. */
    @GetMapping("/by-object")
    public PageView<ObjectGrantRow> byObject(
            @RequestParam String objectName,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean onlyPermitted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageView.DEFAULT_SIZE) int size) {
        return service.byObject(objectName, search, onlyPermitted, page, size);
    }

    /** US-3.4 — the By User grid, paged: one grantee against 5,187 objects. */
    @GetMapping("/by-user")
    public PageView<UserGrantRow> byUser(
            @RequestParam String granteeName,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean onlyPermitted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageView.DEFAULT_SIZE) int size) {
        return service.byUser(granteeName, search, onlyPermitted, page, size);
    }

    /** US-3.5 — grant or revoke across a selection. */
    @PostMapping("/apply")
    public ApplyResult apply(@Valid @RequestBody ApplyRequest request) {
        return new ApplyResult(service.apply(request.changes(), request.level()));
    }

    /** US-3.7 — what a copy would do. */
    /** US-3.7 — perform the copy. */
    @PostMapping("/copy")
    public CopyResult copy(@Valid @RequestBody CopyRequest request) {
        return service.copy(request.from(), request.to(), request.replace());
    }

    /** @param level 0 revoke, 1 execute only, 2 edit */
    public record ApplyRequest(List<GrantChange> changes, int level) {
    }

    public record ApplyResult(int changed) {
    }

    public record CopyRequest(@NotBlank String from, @NotBlank String to, boolean replace) {
    }
}
