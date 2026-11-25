package com.anthem.cartman.appdata.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationDates;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationEdit;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationOwners;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationSummary;
import com.anthem.cartman.appdata.dto.AppDataViews.ApplicationUpdate;
import com.anthem.cartman.appdata.dto.AppDataViews.DeletionResult;
import com.anthem.cartman.appdata.dto.AppDataViews.Outcome;
import com.anthem.cartman.appdata.dto.AppDataViews.Pickers;
import com.anthem.cartman.appdata.dto.AppDataViews.TableOutcome;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionRow;
import com.anthem.cartman.appdata.dto.AppDataViews.UwActionUpdate;
import com.anthem.cartman.appdata.service.AppDataService;

/** Module 7 — the endpoint: eight routes, each delegating to the service. */
@ExtendWith(MockitoExtension.class)
class AppDataControllerTest {

    @Mock
    private AppDataService service;

    @InjectMocks
    private AppDataController controller;

    private static ApplicationSummary summary() {
        return new ApplicationSummary(3455, "Stante", "Dorothy", "IHP", "14 Fore Street", "Truro",
                "VA", "23219", "804-555-0100", java.time.LocalDate.of(1974, 3, 2), "HC0001", "DCN0001");
    }

    private static ApplicationEdit edit() {
        return new ApplicationEdit(summary(),
                new ApplicationDates(null, null, null, null, null, null, null, null),
                new ApplicationOwners("Pending", "SITUW", "EKeyer", "CEllison", "ENunn"));
    }

    private static UwActionRow action() {
        return new UwActionRow(9001, 3455, 1, "UW", "Requested medical records", null, null, null);
    }

    @Test
    @DisplayName("the pickers come from the service")
    void pickers() {
        Pickers pickers = new Pickers(List.of("Approved"), List.of(), List.of(), List.of(), List.of());
        when(service.pickers()).thenReturn(pickers);

        assertEquals(pickers, controller.pickers());
    }

    @Test
    @DisplayName("finding one application")
    void application() {
        when(service.findApplication(3455)).thenReturn(summary());

        assertEquals(summary(), controller.application(3455));
    }

    @Test
    @DisplayName("the editing view is a different route from the deletion view")
    void editView() {
        when(service.editView(3455)).thenReturn(edit());

        assertEquals(edit(), controller.editView(3455));
        verify(service).editView(3455);
    }

    @Test
    @DisplayName("the underwriting actions of an application")
    void uwActions() {
        when(service.uwActions(3455)).thenReturn(List.of(action()));

        assertEquals(List.of(action()), controller.uwActions(3455));
    }

    @Test
    @DisplayName("deleting an application answers with what each table did")
    void deleteApplication() {
        DeletionResult result = new DeletionResult(3455,
                List.of(new TableOutcome("TM_Application", "TM_Application record", Outcome.DELETED, 1)));
        when(service.deleteApplication(3455)).thenReturn(result);

        assertEquals(result, controller.deleteApplication(3455));
    }

    @Test
    @DisplayName("saving an application sends the dates and owners through")
    void saveApplication() {
        ApplicationUpdate update = new ApplicationUpdate(
                new ApplicationDates(null, null, null, null, null, null, null, null),
                new ApplicationOwners("Pending", "SITUW", "EKeyer", "CEllison", "ENunn"));
        when(service.saveApplication(3455, update)).thenReturn(edit());

        assertEquals(edit(), controller.saveApplication(3455, update));
    }

    @Test
    @DisplayName("deleting one action answers 204 with no body")
    void deleteUwAction() {
        ResponseEntity<Void> out = controller.deleteUwAction(9001);

        assertEquals(204, out.getStatusCode().value());
        assertNull(out.getBody());
        verify(service).deleteUwAction(9001);
    }

    @Test
    @DisplayName("saving one action is addressed by its own key")
    void saveUwAction() {
        UwActionUpdate update = new UwActionUpdate(null, null, null);
        when(service.saveUwAction(9002, update)).thenReturn(action());

        assertEquals(action(), controller.saveUwAction(9002, update));
        // A49 — the row's own UW_ID, not the (application, action id) pair the Delphi matches on.
        verify(service).saveUwAction(9002, update);
    }
}
