package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantContextEventInterceptor
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.common.caching.Cache
import org.axonframework.common.caching.WeakReferenceCache
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
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
 * - Active ONLY for "test" profile (rbac-test uses RbacTestContainersConfig)
 * - Prevents @ServiceConnection timing race condition
 */
@TestConfiguration
@Profile("test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class, PostgresEventStoreConfiguration::class)
class AxonTestConfiguration {
    /**
     * Registers PropagatingErrorHandler and TenantContextEventInterceptor for tests.
     *
     * **PropagatingErrorHandler:** Fails fast on event handler exceptions (test correctness)
     * **TenantContextEventInterceptor:** Restores tenant context from event metadata (Story 4.5)
     */
    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }

        // Story 4.6: Register tenant context interceptor globally for all event processors
        // Create both MeterRegistry and Interceptor inline to avoid circular dependencies
        val simpleMeterRegistry = SimpleMeterRegistry()
        val tenantContextEventInterceptor = TenantContextEventInterceptor(simpleMeterRegistry)
        configurer.registerDefaultHandlerInterceptor { _, _ -> tenantContextEventInterceptor }
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
}
