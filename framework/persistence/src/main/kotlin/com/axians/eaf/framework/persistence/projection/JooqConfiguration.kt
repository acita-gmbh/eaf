package com.axians.eaf.framework.persistence.projection

import org.jooq.DSLContext
import org.jooq.ExecuteListener
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * jOOQ Configuration for type-safe SQL queries on projection tables.
 *
 * Configures a DSLContext bean for executing type-safe SQL queries against
 * PostgreSQL projection tables (e.g., widget_projection).
 *
 * **Story 4.4 Enhancement:**
 * - Registers TenantContextExecuteListener for RLS session variable propagation
 * - Sets `app.tenant_id` PostgreSQL session variable before each query
 * - Enables Layer 3 tenant isolation via Row-Level Security policies
 *
 * Architecture Decision:
 * - Uses PostgreSQL dialect for optimal query generation
 * - Shares DataSource with JPA/Axon Event Store
 * - Generated classes provide compile-time safety for read models
 * - ExecuteListeners enable cross-cutting concerns (tenant context, metrics)
 *
 * @see org.jooq.DSLContext
 * @see org.jooq.codegen.KotlinGenerator
 * @see com.axians.eaf.framework.multitenancy.TenantContextExecuteListener
 */
@Configuration
class JooqConfiguration {
    /**
     * Creates a jOOQ DSLContext bean with tenant context propagation.
     *
     * The DSLContext provides a fluent API for building and executing SQL queries
     * with compile-time safety through generated table classes.
     *
     * **Story 4.4:** Registers TenantContextExecuteListener to set PostgreSQL
     * session variable `app.tenant_id` before each query, enabling RLS enforcement.
     *
     * @param dataSource the shared DataSource for PostgreSQL connection
     * @param executeListeners list of ExecuteListeners (autowired from Spring context)
     * @return configured DSLContext with PostgreSQL dialect and execute listeners
     */
    @Bean
    @Suppress("SpreadOperator") // Acceptable: Bean initialization only, 1-2 listeners
    fun dslContext(
        dataSource: DataSource,
        executeListeners: List<ExecuteListener>,
    ): DSLContext {
        val configuration =
            DefaultConfiguration()
                .set(dataSource)
                .set(SQLDialect.POSTGRES)
                .set(*executeListeners.toTypedArray()) // Spread operator for vararg

        return DSL.using(configuration)
    }
}
