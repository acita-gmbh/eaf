package com.axians.eaf.products.widgetdemo.config

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Manual jOOQ configuration to bypass Spring Boot autoconfiguration conflict.
 *
 * **Story 9.2 Fix**: Spring Boot's JooqAutoConfiguration is disabled when JPA EntityManagerFactory
 * exists (Axon requires JPA for event store). This manual configuration creates the DSLContext bean
 * required by TenantDatabaseSessionInterceptor.
 *
 * **Technical Details**:
 * - Spring Boot JooqAutoConfiguration has @ConditionalOnMissingClass("jakarta.persistence.EntityManagerFactory")
 * - Axon's JPA event store triggers HibernateJpaAutoConfiguration
 * - EntityManagerFactory bean creation disables JooqAutoConfiguration
 * - TenantDatabaseSessionInterceptor depends on DSLContext bean via @ConditionalOnBean(DSLContext::class)
 * - This configuration manually creates DSLContext to enable tenant database isolation
 *
 * **Reference**: Story 9.2 - Fix widget-demo QueryHandler ExecutionException
 */
@Configuration
open class ManualDslContextConfiguration {
    /**
     * Creates DSLContext bean for jOOQ query execution and tenant session variable management.
     *
     * This bean is required by:
     * - TenantDatabaseSessionInterceptor (Story 9.2 - SET LOCAL app.current_tenant)
     * - Widget projection query handlers (read model queries)
     *
     * **Transaction Integration**: DSLContext participates in Spring transactions via the injected
     * DataSource, ensuring tenant session variables are set on the correct transactional connection.
     */
    @Bean
    open fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)
}
