package com.anthem.cartman.datadictionary.entity;

import com.anthem.cartman.common.util.TrimmedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Maps {@code TM_Data_Dictionary} — the business description of one database column.
 *
 * <p>Key is ({@code DD_Field_Name}, {@code DD_Field_Table}), in that order, as
 * {@code PK_TM_Data_Dictionary} declares it.
 *
 * <p>{@code DD_Screen_Name} and {@code DD_Record_Name} exist on the table but the Delphi screen
 * neither showed nor wrote them (US-6.3.3), so they are not mapped — mapping them would invite a
 * later reader to assume this screen owns them.
 */
@Entity
@Table(name = "TM_Data_Dictionary")
@IdClass(DataDictionaryEntry.Key.class)
public class DataDictionaryEntry {

    /** Column widths the Delphi never enforced — see US-6.4.4 and anomaly A24. */
    public static final int PRETTY_NAME_MAX = 32;
    public static final int DESCRIPTION_MAX = 64;
    public static final int FIELD_LENGTH_MAX = 5;
    public static final int FIELD_TYPE_MAX = 10;

    @Id
    @Column(name = "DD_Field_Name", length = 32, nullable = false)
    private String fieldName;

    @Id
    @Column(name = "DD_Field_Table", length = 32, nullable = false)
    private String tableName;

    @Column(name = "DD_Pretty_Name", length = PRETTY_NAME_MAX)
    @Convert(converter = TrimmedStringConverter.class)
    private String prettyName;

    @Column(name = "DD_Description", length = DESCRIPTION_MAX)
    @Convert(converter = TrimmedStringConverter.class)
    private String description;

    @Column(name = "DD_Field_Length", length = FIELD_LENGTH_MAX)
    @Convert(converter = TrimmedStringConverter.class)
    private String fieldLength;

    @Column(name = "DD_Field_Type", length = FIELD_TYPE_MAX)
    @Convert(converter = TrimmedStringConverter.class)
    private String fieldType;

    public DataDictionaryEntry() {
        // for JPA
    }

    public static DataDictionaryEntry of(String tableName, String fieldName) {
        DataDictionaryEntry e = new DataDictionaryEntry();
        e.tableName = tableName;
        e.fieldName = fieldName;
        return e;
    }

    public String getFieldName() {
        return fieldName == null ? null : fieldName.trim();
    }

    public String getTableName() {
        return tableName == null ? null : tableName.trim();
    }

    public String getPrettyName() {
        return prettyName;
    }

    public String getDescription() {
        return description;
    }

    public String getFieldLength() {
        return fieldLength;
    }

    public String getFieldType() {
        return fieldType;
    }

    /** US-6.4.1 — Save writes exactly these four values. */
    public void describe(String prettyName, String fieldType, String fieldLength, String description) {
        this.prettyName = prettyName;
        this.fieldType = fieldType;
        this.fieldLength = fieldLength;
        this.description = description;
    }

    public record Key(String fieldName, String tableName) {
        public Key() {
            this(null, null);
        }
    }
}
