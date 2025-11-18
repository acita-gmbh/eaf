package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantAwareCommand
import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
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
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

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
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class, PostgresEventStoreConfiguration::class)
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
     * Test interceptor that sets tenant context from command payload for tests.
     *
     * **Story 4.6:** Multi-tenancy tests need tenant context propagation to command handlers.
     * Since commands execute in Axon threads (not HTTP request threads), we extract
     * the tenant ID from the command payload and set it in TenantContext before
     * the command handler executes.
     *
     * This replaces the production TenantValidationInterceptor which expects the
     * tenant to already be set in TenantContext (from HTTP filter).
     *
     * @return Test-specific command interceptor
     */
    @Bean
    fun testTenantContextInterceptor(): MessageHandlerInterceptor<CommandMessage<*>> =
        MessageHandlerInterceptor { unitOfWork, chain ->
            val command = unitOfWork.message.payload

            // Extract tenant from command and set in TenantContext for handler execution
            if (command is TenantAwareCommand) {
                TenantContext.setCurrentTenantId(command.tenantId)
                try {
                    chain.proceed()
                } finally {
                    TenantContext.clearCurrentTenant()
                }
            } else {
                chain.proceed()
            }
        }
}
