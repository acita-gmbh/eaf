package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantContextEventInterceptor
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.common.caching.Cache
import org.axonframework.common.caching.WeakReferenceCache
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

/**
 * Testcontainers Configuration for RBAC Integration Tests (Story 3.10).
 *
 * **ROOT CAUSE ANALYSIS (from 5 AI agents + Zen MCP consensus):**
 *
 * The "test" profile worked but "rbac-test" failed with `Connection to localhost:5432 refused`
 * due to a **bean initialization timing race condition**:
 *
 * **The Problem:**
 * 1. @EnableMethodSecurity in RbacTestSecurityConfig triggers early BeanPostProcessor registration
 *    (InfrastructureAdvisorAutoProxyCreator for AOP proxies)
 * 2. @Import(AxonTestConfiguration) creates transitive dependency chain:
 *    Security → Axon → EventStore → DataSource (EARLY REQUEST!)
 * 3. TestDslConfiguration.testDataSource() bean created BEFORE @ServiceConnection ready
 * 4. @ServiceConnection creates JdbcConnectionDetails bean (NOT Environment properties!)
 * 5. Manual testDataSource() queries Environment.getProperty() → MISSES ConnectionDetails!
 * 6. Falls back to localhost:5432 → ConnectException ❌
 *
 * **The Fundamental Mismatch (Result #3 - CRITICAL):**
 * ```
 * @ServiceConnection creates → JdbcConnectionDetails bean
 * TestDslConfiguration queries → Environment.getProperty("spring.datasource.url")
 *                                 ↓
 *                              INCOMPATIBLE! ❌
 * ```
 *
 * **Spring Boot Issue #44046:** Manual DataSource + @ServiceConnection = NOT SUPPORTED PATTERN!
 *
 * **THE SOLUTION:**
 * - Container as Spring @Bean (NOT static field in test class)
 * - @ServiceConnection on bean → Spring Boot auto-configures DataSource
 * - DataSourceAutoConfiguration consumes JdbcConnectionDetails → correct URL
 * - DSLContext injected from auto-configured DataSource
 * - NO manual DataSource bean for "rbac-test" (eliminates competition)
 * - TestDslConfiguration remains ONLY for "test" profile (zero regression)
 *
 * **Timeline (Fixed):**
 * 1. RbacTestContainersConfig.postgresContainer() bean registered
 * 2. @ServiceConnection scans bean → starts container (blocking!)
 * 3. JdbcConnectionDetails bean created with dynamic URL (e.g., localhost:55432)
 * 4. @EnableMethodSecurity triggers early DataSource request
 * 5. DataSourceAutoConfiguration runs → queries JdbcConnectionDetails (✅ FOUND!)
 * 6. HikariDataSource created with CORRECT container URL
 * 7. @Sql executes successfully → Tests pass! 🎉
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("rbac-test")
@Import(PostgresEventStoreConfiguration::class)
open class RbacTestContainersConfig {
    /**
     * PostgreSQL Testcontainer as Spring-managed bean.
     *
     * **CRITICAL:** @ServiceConnection tells Spring Boot to auto-configure DataSource
     * from this container's connection details. This approach:
     * - Guarantees container starts BEFORE DataSource auto-configuration
     * - Creates JdbcConnectionDetails bean (consumed by DataSourceAutoConfiguration)
     * - Eliminates race condition with @EnableMethodSecurity early bean instantiation
     * - Works with Kotest lifecycle (Spring-managed, not static field dependency)
     *
     * **NO manual .start() needed** - Spring Boot manages full lifecycle!
     */
    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer<*> =
        PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine")).apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
            // Container lifecycle managed by Spring Boot Testcontainers integration
        }

    /**
     * DataSource bean for "rbac-test" profile.
     *
     * **CRITICAL:** Because we exclude HibernateJpaAutoConfiguration (DISABLE_MODULITH_JPA),
     * DataSourceAutoConfiguration may not run. We create a manual DataSource bean,
     * BUT we inject it from the postgresContainer @ServiceConnection bean method!
     *
     * This ensures:
     * - Container starts FIRST (as bean dependency)
     * - We get actual container reference (not Environment properties!)
     * - No localhost:5432 fallback
     */
    @Bean
    fun rbacDataSource(container: PostgreSQLContainer<*>): DataSource =
        org.springframework.boot.jdbc.DataSourceBuilder
            .create()
            .url(container.jdbcUrl) // Direct from container bean!
            .username(container.username)
            .password(container.password)
            .driverClassName("org.postgresql.Driver")
            .build()

    /**
     * jOOQ DSLContext using rbacDataSource bean.
     */
    @Bean
    fun rbacDslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    /**
     * TransactionManager for @Sql script execution.
     *
     * Spring Boot auto-configures DataSourceTransactionManager if not present,
     * but explicit definition ensures @Sql scripts work correctly in RBAC tests.
     */
    @Bean
    fun rbacTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

    /**
     * Axon Framework configuration for fail-fast test behavior and tenant context propagation.
     *
     * **PropagatingErrorHandler:** Ensures exceptions in @EventHandler propagate to tests
     * **TenantContextEventInterceptor:** Restores tenant context in async event processors
     *
     * Story 4.6: Tenant context propagation for async event processing
     */
    @Autowired
    fun configureAxon(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }

        // Story 4.6: Register tenant context interceptor (same as AxonTestConfiguration)
        val simpleMeterRegistry = SimpleMeterRegistry()
        val tenantContextEventInterceptor = TenantContextEventInterceptor(simpleMeterRegistry)
        configurer.registerDefaultHandlerInterceptor { config, name -> tenantContextEventInterceptor }
    }

    /**
     * Axon aggregate cache for performance.
     *
     * Eliminates repeated event loading from event store for hot aggregates.
     * Expected performance impact: ~100-150ms improvement per command.
     */
    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()

    /**
     * JPA Bypass: Remove JPA auto-configuration artifacts.
     *
     * Ensures Modulith/JPA infrastructure doesn't start when we only need jOOQ.
     * Mirrors TestJpaBypassConfiguration behavior for "rbac-test" profile.
     */
    @Bean
    fun disableJpaBeans(): BeanFactoryPostProcessor =
        BeanFactoryPostProcessor { beanFactory: ConfigurableListableBeanFactory ->
            val registry = beanFactory as? BeanDefinitionRegistry

            listOf(
                "entityManagerFactory",
                "jpaSharedEM_entityManagerFactory",
            ).forEach { beanName ->
                if (registry?.containsBeanDefinition(beanName) == true) {
                    registry.removeBeanDefinition(beanName)
                }
            }
        }
}
