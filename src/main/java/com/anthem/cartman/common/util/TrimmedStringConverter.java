package com.anthem.cartman.common.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Trims fixed-width character columns on read.
 *
 * <p>Verified against the live schema: {@code TM_Login.Log_Nm} is {@code char(10)} and
 * {@code Log_Typ} is {@code char(5)}, so SQL Server pads every value with trailing spaces —
 * a login reads back as {@code "CosmoAdm  "}. Without this the padding leaks into JSON,
 * comparisons and audit records.
 *
 * <p>Writes pass through untouched: SQL Server re-pads {@code char} columns itself, and its
 * {@code =} comparison ignores trailing spaces, so lookups still match.
 */
@Converter
public class TrimmedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : dbData.trim();
    }
}
