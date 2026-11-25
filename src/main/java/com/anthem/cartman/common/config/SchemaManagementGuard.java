package com.anthem.cartman.common.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Refuses to start unless Hibernate schema management is switched off.
 *
 * <p>CARTMAN runs against the live Delphi schema. It reads and writes rows; it must never
 * create, alter or drop an object. Configuration alone is not enough to guarantee that:
 * profiles compose, and a {@code create-drop} left in a base layer stays in effect when
 * another profile swaps the datasource underneath it. That is not hypothetical — a
 * {@code create-drop} in the test configuration reached the shared SQL Server through
 * {@code @ActiveProfiles("dev")} and dropped {@code TM_Login}.
 *
 * <p>So the rule is enforced in code, at the earliest point Spring offers, before any
 * datasource is built:
 *
 * <ul>
 *   <li>{@code spring.jpa.hibernate.ddl-auto} must be present and equal to {@code none}</li>
 *   <li>{@code spring.jpa.properties.hibernate.hbm2ddl.auto}, the alternate spelling that
 *       silently outranks it, must be absent or {@code none}</li>
 * </ul>
 *
 * <p>Absence is rejected rather than tolerated: with an embedded datasource on the
 * classpath Spring Boot defaults {@code ddl-auto} to {@code create-drop}, so "unset" is
 * the dangerous state, not the safe one. Every profile states {@code none} explicitly.
 */
public class SchemaManagementGuard implements EnvironmentPostProcessor {

    static final String DDL_AUTO = "spring.jpa.hibernate.ddl-auto";
    static final String HBM2DDL = "spring.jpa.properties.hibernate.hbm2ddl.auto";
    static final String REQUIRED = "none";

    /** Values that would let Hibernate issue DDL. Listed for the error message. */
    private static final List<String> DESTRUCTIVE = List.of("create", "create-drop", "update", "create-only", "drop");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String ddlAuto = normalise(environment.getProperty(DDL_AUTO));
        if (ddlAuto == null) {
            throw new IllegalStateException(message(DDL_AUTO, "not set",
                    "Spring Boot defaults it to create-drop when an embedded database is on the "
                    + "classpath, so leaving it unset is not safe. Set it explicitly."));
        }
        if (!REQUIRED.equals(ddlAuto)) {
            throw new IllegalStateException(message(DDL_AUTO, ddlAuto,
                    DESTRUCTIVE.contains(ddlAuto)
                        ? "That setting lets Hibernate drop or alter the live Delphi tables."
                        : "Only 'none' is permitted."));
        }

        String hbm2ddl = normalise(environment.getProperty(HBM2DDL));
        if (hbm2ddl != null && !REQUIRED.equals(hbm2ddl)) {
            throw new IllegalStateException(message(HBM2DDL, hbm2ddl,
                    "This native property overrides " + DDL_AUTO + ". Remove it or set it to none."));
        }
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String message(String key, String actual, String why) {
        return "CARTMAN refuses to start: " + key + " is " + actual + ". " + why
                + " CARTMAN owns no schema — the Delphi database is the source of truth and "
                + "every profile must set " + DDL_AUTO + "=none.";
    }
}
