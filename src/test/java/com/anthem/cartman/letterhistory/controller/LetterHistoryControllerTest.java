package com.anthem.cartman.letterhistory.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.DeleteResult;
import com.anthem.cartman.letterhistory.dto.LetterHistoryViews.LetterRow;
import com.anthem.cartman.letterhistory.service.LetterHistoryService;

/** Module 13 — the endpoints. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LetterHistoryControllerTest {

    @Mock
    private LetterHistoryService service;

    @InjectMocks
    private LetterHistoryController controller;

    private static final LetterRow ROW =
            new LetterRow(LocalDateTime.of(2026, 4, 21, 5, 53), "SM Ltr Denial 2A 24", 1);

    @Test
    @DisplayName("US-13.4 — the key value reaches the service as it was typed")
    void list() {
        when(service.list("7100")).thenReturn(List.of(ROW));

        assertEquals(List.of(ROW), controller.list("7100"));
        verify(service).list("7100");
    }

    /** US-13.5.1 — an empty box is the service's decision to refuse, not a bad request. */
    @Test
    @DisplayName("a missing key value is passed through, not rejected here")
    void listWithoutKey() {
        when(service.list(null)).thenReturn(List.of());

        assertEquals(List.of(), controller.list(null));
    }

    @Test
    @DisplayName("US-13.7 — a delete answers what it removed from each table")
    void delete() {
        when(service.delete("7100", 1)).thenReturn(new DeleteResult(1, 1));

        DeleteResult result = controller.delete("7100", 1);

        assertEquals(1, result.letters());
        assertEquals(1, result.events());
        verify(service).delete("7100", 1);
    }

    @Test
    @DisplayName("and the service's refusal is not swallowed")
    void deleteRefused() {
        when(service.delete("7100", 9)).thenThrow(new ValidationException("gone"));

        assertThrows(ValidationException.class, () -> controller.delete("7100", 9));
    }
}
