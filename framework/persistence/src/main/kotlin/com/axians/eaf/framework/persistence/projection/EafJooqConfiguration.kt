package com.axians.eaf.framework.persistence.projection

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * EAF jOOQ Configuration for type-safe SQL queries on projection tables.
 *
 * Configures a DSLContext bean for executing type-safe SQL queries against
 * PostgreSQL projection tables (e.g., widget_view).
 *
 * Architecture Decision:
 * - Uses PostgreSQL dialect for optimal query generation
 * - Shares DataSource with JPA/Axon Event Store
 * - Generated classes provide compile-time safety for read models
 *
 * **Class Naming:**
 * Named `EafJooqConfiguration` (not `JooqConfiguration`) to avoid bean name conflict
 * with Spring Boot's auto-configured `JooqAutoConfiguration`.
 *
 * @see org.jooq.DSLContext
 * @see org.jooq.codegen.KotlinGenerator
 */
@Configuration
open class EafJooqConfiguration {
    /**
     * Creates a jOOQ DSLContext bean for type-safe SQL query execution.
     *
     * The DSLContext provides a fluent API for building and executing SQL queries
     * with compile-time safety through generated table classes.
     *
     * @param dataSource the shared DataSource for PostgreSQL connection
     * @return configured DSLContext using PostgreSQL dialect
     */
    @Bean
    open fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)
}
