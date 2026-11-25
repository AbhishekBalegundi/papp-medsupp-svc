package com.anthem.cartman.datadictionary.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.EntryEdit;
import com.anthem.cartman.datadictionary.dto.DataDictionaryViews.FieldRow;
import com.anthem.cartman.datadictionary.service.DataDictionaryService;

/** Module 6 — the endpoint: the three things the screen asks for, and what it sends back. */
@ExtendWith(MockitoExtension.class)
class DataDictionaryControllerTest {

    @Mock
    private DataDictionaryService service;

    @InjectMocks
    private DataDictionaryController controller;

    @Test
    @DisplayName("the table list comes straight from the service")
    void tables() {
        when(service.tables()).thenReturn(List.of("TM_Application", "TM_Login"));

        assertEquals(List.of("TM_Application", "TM_Login"), controller.tables());
    }

    @Test
    @DisplayName("the fields of one table are asked for by name")
    void fields() {
        FieldRow row = new FieldRow("App_ID", true, "Application", "int", "4", "the key");
        when(service.fields("TM_Application")).thenReturn(List.of(row));

        assertEquals(List.of(row), controller.fields("TM_Application"));
    }

    @Test
    @DisplayName("and a save carries the table, the field and the four values")
    void save() {
        EntryEdit edit = new EntryEdit("Application", "int", "4", "the key");
        FieldRow saved = new FieldRow("App_ID", true, "Application", "int", "4", "the key");
        when(service.saveEntry("TM_Application", "App_ID", edit)).thenReturn(saved);

        assertEquals(saved, controller.save("TM_Application", "App_ID", edit));
        verify(service).saveEntry("TM_Application", "App_ID", edit);
    }
}
