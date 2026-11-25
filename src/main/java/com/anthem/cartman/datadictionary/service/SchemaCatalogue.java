package com.anthem.cartman.datadictionary.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

/**
 * The database's own table and column lists — US-6.1 and US-6.2.
 *
 * <p>The Delphi called {@code TM.GetTableNames} and {@code TM.GetFieldNames}, which are ADO
 * connection metadata calls, not queries. The JDBC equivalent is {@link DatabaseMetaData}, so
 * this reads the catalogue the same way rather than hardcoding a table list — a table added to the
 * database appears without a code change, exactly as the Delphi behaved.
 *
 * <p>Read-only by construction: {@code DatabaseMetaData} cannot modify anything.
 */
@Component
public class SchemaCatalogue {

    /** {@code GetTableNames(…, False)} suppressed system tables; these are the JDBC equivalent. */
    private static final String[] USER_TABLE_TYPES = {"TABLE"};

    private final DataSource dataSource;

    public SchemaCatalogue(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** US-6.1 — every non-system table, ordered by name. */
    public List<String> tableNames() {
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            try (ResultSet r = meta.getTables(c.getCatalog(), null, "%", USER_TABLE_TYPES)) {
                while (r.next()) {
                    String schema = r.getString("TABLE_SCHEM");
                    // H2 keeps its own catalogue in INFORMATION_SCHEMA; SQL Server uses sys/INFORMATION_SCHEMA.
                    if (schema != null && isSystemSchema(schema)) {
                        continue;
                    }
                    names.add(r.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new CatalogueUnavailableException("Could not read the database's table list.", e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * US-6.2 — the column names of one table, in the order the database reports them.
     *
     * <p>{@code tableName} must be the catalogue's own spelling — see {@link #resolveTableName}.
     * JDBC metadata patterns are case-sensitive, and databases fold unquoted identifiers
     * differently (H2 upper-cases them, SQL Server preserves them), so passing the name a user
     * typed finds nothing.
     */
    public List<String> columnNames(String tableName) {
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            try (ResultSet r = c.getMetaData().getColumns(c.getCatalog(), null, tableName, "%")) {
                while (r.next()) {
                    names.add(r.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new CatalogueUnavailableException(
                    "Could not read the columns of " + tableName + ".", e);
        }
        return names;
    }

    /**
     * Maps a table name as typed onto the spelling the catalogue uses, or empty when there is no
     * such table. Every other call takes the resolved name.
     */
    public Optional<String> resolveTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }
        String wanted = tableName.trim();
        return tableNames().stream().filter(t -> t.equalsIgnoreCase(wanted)).findFirst();
    }

    private static boolean isSystemSchema(String schema) {
        String s = schema.toUpperCase();
        return s.equals("INFORMATION_SCHEMA") || s.startsWith("SYS") || s.equals("DB_ACCESSADMIN")
                || s.equals("DB_OWNER") || s.equals("DB_SECURITYADMIN");
    }

    public static class CatalogueUnavailableException extends RuntimeException {
        public CatalogueUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
