package com.anthem.cartman.workqueues.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.workqueues.dto.WorkQueueViews.CopyResult;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.QueueRow;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserBoard;
import com.anthem.cartman.workqueues.dto.WorkQueueViews.UserRow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.anthem.cartman.workqueues.service.WorkQueueService;

/**
 * Module 5 — Work Queue Manager.
 *
 * <p>Queues are addressed by {@code Cal_ID}, which is the table's actual primary key; the name is
 * not unique (anomaly A32). Every write returns the whole board, so the client redraws from server
 * state rather than patching its own lists — the Delphi patched, and got it wrong (A29).
 *
 * <p>There is no delete endpoint. The Delphi's Delete Queue button has no handler and no delete
 * procedure exists in the unit (A33), so there is no behaviour to reproduce.
 */
@RestController
@RequestMapping("/api/work-queues")
public class WorkQueueController {

    private final WorkQueueService service;

    public WorkQueueController(WorkQueueService service) {
        this.service = service;
    }

    /** US-5.1.1 — the queue list. */
    @GetMapping
    public List<QueueRow> queues() {
        return service.allQueues();
    }

    /** US-5.1.4 — every login, for both user lists. */
    @GetMapping("/users")
    public List<UserRow> users() {
        return service.allUsers();
    }

    /** US-5.2 / US-5.3.1 — a queue's definition and its two user lists. */
    @GetMapping("/{queueId}")
    public QueueBoard board(@PathVariable int queueId) {
        return service.board(queueId);
    }

    /** US-5.5 — create a queue. */
    @PostMapping
    public QueueBoard create(@Valid @RequestBody CreateQueueRequest request) {
        return service.createQueue(request.name());
    }

    /** US-5.4.2 — Save Queue. */
    @PutMapping("/{queueId}")
    public QueueBoard saveDefinition(@PathVariable int queueId,
                                     @Valid @RequestBody DefinitionRequest request) {
        return service.saveDefinition(queueId, request.description(), request.sql());
    }

    /** US-5.3.3 — permit users on this queue. */
    @PostMapping("/{queueId}/permissions/add")
    public QueueBoard grant(@PathVariable int queueId, @RequestBody UserNamesRequest request) {
        return service.grant(queueId, request.users());
    }

    /** US-5.3.5 — revoke users from this queue. */
    @PostMapping("/{queueId}/permissions/remove")
    public QueueBoard revoke(@PathVariable int queueId, @RequestBody UserNamesRequest request) {
        return service.revoke(queueId, request.users());
    }

    /** US-5.7.1 — the By User tab. */
    @GetMapping("/by-user/{loginName}")
    public UserBoard userBoard(@PathVariable String loginName) {
        return service.userBoard(loginName);
    }

    /** US-5.7.2 — assign queues to a user. */
    @PostMapping("/by-user/{loginName}/add")
    public UserBoard assign(@PathVariable String loginName, @RequestBody QueueIdsRequest request) {
        return service.assignQueues(loginName, request.queueIds());
    }

    /** US-5.7.3 — unassign queues from a user, by name so unknown queues can be cleared. */
    @PostMapping("/by-user/{loginName}/remove")
    public UserBoard unassign(@PathVariable String loginName,
                              @RequestBody QueueNamesRequest request) {
        return service.unassignQueues(loginName, request.queues());
    }

    /** US-5.8 — what a copy would do. */
    /** US-5.8 — perform the copy. */
    @PostMapping("/copy")
    public CopyResult copy(@Valid @RequestBody CopyRequest request) {
        return service.copy(request.from(), request.to(), request.replace());
    }

    public record CreateQueueRequest(@NotBlank String name) {
    }

    public record DefinitionRequest(String description, String sql) {
    }

    public record UserNamesRequest(List<String> users) {
    }

    public record QueueIdsRequest(List<Integer> queueIds) {
    }

    public record QueueNamesRequest(List<String> queues) {
    }

    public record CopyRequest(@NotBlank String from, @NotBlank String to, boolean replace) {
    }
}
