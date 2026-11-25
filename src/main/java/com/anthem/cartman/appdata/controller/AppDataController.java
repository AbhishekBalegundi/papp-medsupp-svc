package com.anthem.cartman.appdata.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationEdit;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationSummary;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationUpdate;
import com.anthem.cartman.appdata.dto.AppDataViews.DeletionResult;
import com.anthem.cartman.appdata.dto.AppDataViews.Pickers;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionRow;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionUpdate;
import com.anthem.cartman.appdata.service.AppDataService;

/**
 * Module 7 — Application Data Manager.
 *
 * <p>An application is addressed by its id, which is an {@code int} in the database. A key value
 * that is not a number never reaches here: Spring rejects it before the method runs, and the screen
 * refuses it before that.
 */
@RestController
@RequestMapping("/api/app-data")
public class AppDataController {

    private final AppDataService service;

    public AppDataController(AppDataService service) {
        this.service = service;
    }

    /** US-7.9 — the status codes and the four login lists, read when the screen opens. */
    @GetMapping("/pickers")
    public Pickers pickers() {
        return service.pickers();
    }

    /** US-7.2.3 — find one application. */
    @GetMapping("/applications/{applicationId}")
    public ApplicationSummary application(@PathVariable int applicationId) {
        return service.findApplication(applicationId);
    }

    /** US-7.6 — the same application with everything the editing tab needs. */
    @GetMapping("/applications/{applicationId}/edit")
    public ApplicationEdit editView(@PathVariable int applicationId) {
        return service.editView(applicationId);
    }

    /** US-7.2.5 — the underwriting actions for one application. */
    @GetMapping("/applications/{applicationId}/uw-actions")
    public List<UwActionRow> uwActions(@PathVariable int applicationId) {
        return service.uwActions(applicationId);
    }

    /** US-7.3 — delete the application and its dependent records, reporting what went. */
    @DeleteMapping("/applications/{applicationId}")
    public DeletionResult deleteApplication(@PathVariable int applicationId) {
        return service.deleteApplication(applicationId);
    }

    /** US-7.8.1 — save the eight dates and five ownership fields. */
    @PutMapping("/applications/{applicationId}")
    public ApplicationEdit saveApplication(@PathVariable int applicationId,
                                           @RequestBody ApplicationUpdate update) {
        return service.saveApplication(applicationId, update);
    }

    /** US-7.4 — delete one underwriting action, by its own key. */
    @DeleteMapping("/uw-actions/{uwId}")
    public ResponseEntity<Void> deleteUwAction(@PathVariable int uwId) {
        service.deleteUwAction(uwId);
        return ResponseEntity.noContent().build();
    }

    /** US-7.8.3 — save one underwriting action's three dates. */
    @PutMapping("/uw-actions/{uwId}")
    public UwActionRow saveUwAction(@PathVariable int uwId, @RequestBody UwActionUpdate update) {
        return service.saveUwAction(uwId, update);
    }
}
