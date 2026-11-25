package com.anthem.cartman.zipcodes.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.anthem.cartman.common.exception.ValidationException;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.NewZip;
import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;
import com.anthem.cartman.zipcodes.service.ZipCodeService;

/** Module 14 — the endpoints. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZipCodeControllerTest {

    @Mock
    private ZipCodeService service;

    @InjectMocks
    private ZipCodeController controller;

    private static final ZipRow ROW = new ZipRow(9001, "KY", "Magoffin", "Cyrus", null);

    @Test
    @DisplayName("US-14.4 — the zip reaches the service as it was typed")
    void find() {
        when(service.find("41465")).thenReturn(List.of(ROW));

        assertEquals(List.of(ROW), controller.find("41465"));
        verify(service).find("41465");
    }

    /** US-14.3.2 — a short zip is the service's decision to read nothing, not a bad request. */
    @Test
    @DisplayName("a missing zip is passed through, not rejected here")
    void findWithoutZip() {
        when(service.find(null)).thenReturn(List.of());

        assertEquals(List.of(), controller.find(null));
    }

    @Test
    @DisplayName("US-14.9 — an insert answers the row it stored")
    void insert() {
        NewZip request = new NewZip("40391", "Winchester", "KY", "Clark", true);
        when(service.insert(request)).thenReturn(ROW);

        assertEquals(ROW, controller.insert(request));
        verify(service).insert(request);
    }

    @Test
    @DisplayName("and the service's refusal is not swallowed")
    void insertRefused() {
        NewZip request = new NewZip("404", "", "", "", false);
        when(service.insert(request)).thenThrow(new ValidationException("Zip Code Length Should Be 5 Digits"));

        assertThrows(ValidationException.class, () -> controller.insert(request));
    }

    @Test
    @DisplayName("US-14.6 — Delete takes the row id")
    void delete() {
        controller.delete(9001);

        verify(service).delete(9001);
    }

    /* US-14.7 — the two Choice buttons are one endpoint and differ only in the value they ask for. */
    @Test
    @DisplayName("US-14.7 — Choice in and Choice out reach the service as true and false")
    void choice() {
        controller.setChoice(9001, true);
        controller.setChoice(9001, false);

        verify(service).setChoice(9001, true);
        verify(service).setChoice(9001, false);
    }
}
