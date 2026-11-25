package com.anthem.cartman.common.config;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Persistence settings that the Delphi schema forces on us.
 *
 * <p>Spring Boot's default physical naming strategy lowercases identifiers and inserts an
 * underscore at every case boundary — and it does so even to a name given explicitly in
 * {@code @Column}. Under that strategy {@code @Column(name = "Log_CartmanTools")} is sent to
 * SQL Server as {@code log_cartman_tools}, which does not exist.
 *
 * <p>The Delphi schema is mixed case and irregular ({@code Log_Nm}, {@code Log_Real_Fnm},
 * {@code Log_CartmanTools}) and CARTMAN does not own it, so no derivation rule can be trusted
 * to reproduce it. The standard strategy passes every name through exactly as written, making
 * the {@code @Column} annotation the single statement of what the column is called.
 *
 * <p>This is a bean rather than {@code spring.jpa.hibernate.naming.physical-strategy} on
 * purpose. Only one {@code application.yaml} is loaded from the classpath root, and under test
 * that is the one in {@code src/test/resources} — the main file is shadowed entirely. A bean
 * applies to every context regardless of which configuration file wins.
 *
 * <p>Consequence for new entities: every field must carry an explicit {@code @Column(name = …)}
 * spelled exactly as the database spells it. Nothing is inferred.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    public PhysicalNamingStrategy physicalNamingStrategy() {
        return PhysicalNamingStrategyStandardImpl.INSTANCE;
    }
}
