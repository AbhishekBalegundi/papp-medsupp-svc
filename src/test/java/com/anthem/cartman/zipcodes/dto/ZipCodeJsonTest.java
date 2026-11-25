package com.anthem.cartman.zipcodes.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.anthem.cartman.zipcodes.dto.ZipCodeViews.ZipRow;

/**
 * Module 14 — the shape of the JSON the screen actually receives.
 *
 * <p>Reported on 26 August: the Choice buttons wrote the column correctly and the list still drew
 * *No* on every row. The cause was here rather than in either end's logic — a record serialises its
 * components plus whatever Jackson recognises as a getter, and {@code inChoiceNetwork()} was named in
 * a way that is neither. The field was absent from every response, the screen read {@code undefined},
 * and {@code undefined} is falsy.
 *
 * <p>Asserting objects, as the controller test does, could never have caught it. This asserts the
 * serialised form, which is the thing the browser sees.
 */
@SpringBootTest
class ZipCodeJsonTest {

    @Autowired
    private ObjectMapper mapper;

    private JsonNode json(Short choice) {
        return mapper.readTree(mapper.writeValueAsString(
                new ZipRow(9001, "KY", "Magoffin", "Cyrus", choice)));
    }

    @Test
    @DisplayName("every field the screen reads is in the JSON")
    void fields() {
        JsonNode node = json((short) 1);

        for (String field : new String[] {"id", "state", "county", "city", "choice", "inChoiceNetwork"}) {
            assertTrue(node.has(field), "the response is missing " + field);
        }
        assertEquals(6, node.size(), "and carries nothing else");
    }

    /* A87 — Yes for a stored 1, No for a stored 0, No for a null. All three, over the wire. */
    @Test
    @DisplayName("A87 — inChoiceNetwork is true only for a stored 1")
    void choiceOverTheWire() {
        assertTrue(json((short) 1).get("inChoiceNetwork").asBoolean());
        assertFalse(json((short) 0).get("inChoiceNetwork").asBoolean());
        assertFalse(json(null).get("inChoiceNetwork").asBoolean());
        assertTrue(json(null).get("choice").isNull(), "and the raw value stays distinguishable");
    }
}
