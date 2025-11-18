package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantAwareCommand
import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
import jakarta.annotation.PostConstruct
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.common.caching.Cache
import org.axonframework.common.caching.WeakReferenceCache
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Axon Framework test configuration.
 *
 * **PropagatingErrorHandler:**
 * Ensures any exception in @EventHandler methods propagates back to sendAndWait(),
 * fails the transaction, and fails the test. Without this, the default
 * LoggingErrorHandler would silently log errors, allowing tests to pass with
 * inconsistent projection state.
 *
 * **Aggregate Caching:**
 * Provides WeakReferenceCache for aggregate caching in tests.
 * Eliminates repeated event loading from event store for hot aggregates.
 * Expected performance impact: ~100-150ms improvement per command for cached aggregates.
 *
 * Story 2.13: Performance Baseline and Monitoring - Phase 2
 *
 * **Story 3.10: Profile Isolation**
 * - Active for "test" and "rbac-test" profiles
 * - Provides test interceptor for tenant context propagation
 */
@TestConfiguration
@Profile("test | rbac-test")
@Import(
    TestDslConfiguration::class,
    TestJpaBypassConfiguration::class,
    PostgresEventStoreConfiguration::class,
)
class AxonTestConfiguration {
    /**
     * Registers PropagatingErrorHandler as default for all event processors.
     *
     * This ensures test correctness by failing fast on any event handler exception,
     * rolling back the transaction and propagating the error to the test.
     */
    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }
    }

    /**
     * Provides aggregate cache for integration tests.
     *
     * Uses WeakReferenceCache to cache aggregates in memory, avoiding repeated
     * event loading from the event store. Aggregates are cached per aggregate ID
     * and automatically garbage collected when memory is low.
     *
     * **Performance Impact (Phase 2 Optimization):**
     * - Without cache: Every command loads ALL events (O(n) for n events)
     * - With cache: First command loads events, subsequent reuse cached instance (O(1))
     * - Example: 250 commands on same aggregate → saves ~125ms avg reloading overhead
     *
     * **Test Isolation:**
     * Cache persists across test methods if Spring context is reused.
     * Use unique aggregate IDs per test to avoid interference.
     *
     * @return WeakReferenceCache instance for aggregate caching
     */
    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()

    /**
     * Tenant context interceptor registrar for CI-stable test execution.
     *
     * **Story 4.6 - CI-Stable Test Infrastructure Fix:**
     *
     * **THE PROBLEM:**
     * - @Component + @Import dual registration → non-deterministic bean initialization order
     * - Works locally (warm Gradle daemon, consistent file system ordering)
     * - Fails on CI (fresh JVM, ext4 filesystem with non-deterministic directory listing)
     *
     * **THE SOLUTION:**
     * - Remove @Component annotation (no component scanning)
     * - Use explicit @Bean registration only (deterministic lifecycle)
     * - Register interceptor during bean creation (before tests start)
     *
     * **How It Works:**
     * - Spring creates this bean during context initialization
     * - @PostConstruct runs AFTER CommandBus is available but BEFORE tests execute
     * - Interceptor registered once, consistently, regardless of environment
     *
     * **Thread-Safe Context Propagation:**
     * - beforeTest/beforeEach hooks set TenantContext in test thread (fallback)
     * - Commands dispatch to Axon thread pool (ThreadLocal doesn't propagate!)
     * - This interceptor reads command.tenantId and sets ThreadLocal in handler thread
     * - Command handler executes with TenantContext available
     * - Finally block cleans up ThreadLocal
     *
     * **Why This Is CI-Stable:**
     * - Single registration path (no @Component)
     * - Deterministic @Bean lifecycle (no filesystem ordering dependency)
     * - No race condition (@PostConstruct guaranteed before tests via Spring lifecycle)
     *
     * **References:**
     * - Spring Boot Issue #30359: ext4 vs APFS file ordering differences
     * - Agent consensus: Remove @Component to eliminate dual registration
     */
    @Bean
    fun tenantContextInterceptorRegistrar(commandBus: CommandBus): TenantContextInterceptorRegistrar =
        TenantContextInterceptorRegistrar(commandBus)
}

/**
 * Registrar component that sets up tenant context propagation in Axon command handlers.
 *
 * Managed as explicit @Bean (not @Component) for deterministic lifecycle timing.
 * The @PostConstruct method registers the interceptor with CommandBus during Spring
 * context initialization, before any tests can dispatch commands.
 */
class TenantContextInterceptorRegistrar(
    private val commandBus: CommandBus,
) {
    @PostConstruct
    fun registerInterceptor() {
        // Register interceptor to propagate tenant context from commands to handler threads
        commandBus.registerHandlerInterceptor(
            MessageHandlerInterceptor { unitOfWork, chain ->
                val command = unitOfWork.message.payload

                if (command is TenantAwareCommand) {
                    // CRITICAL: Always set context authoritatively for THIS command
                    // Do NOT check if context already exists - that creates race conditions
                    // in high-throughput scenarios where threads are reused with stale context
                    TenantContext.setCurrentTenantId(command.tenantId)
                    try {
                        chain.proceed()
                    } finally {
                        // CRITICAL: Always cleanup ThreadLocal to prevent:
                        // - State leakage between commands on same thread
                        // - Stale context when thread is reused from pool
                        TenantContext.clearCurrentTenant()
                    }
                } else {
                    // Not a tenant-aware command, proceed without tenant context
                    chain.proceed()
                }
            },
        )
    }
}
