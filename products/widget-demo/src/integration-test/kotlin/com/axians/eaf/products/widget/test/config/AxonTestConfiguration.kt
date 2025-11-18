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
    TenantContextTestInterceptor::class, // Explicit import for component scanning
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
}

/**
 * Component that registers tenant context interceptor EARLY in the interceptor chain.
 *
 * **Story 4.6 - Test Infrastructure:**
 * This interceptor MUST run BEFORE TenantValidationInterceptor to set TenantContext
 * from command payload. Using @PostConstruct + CommandBus.registerHandlerInterceptor()
 * ensures early registration (before component scanning finds TenantValidationInterceptor).
 *
 * **Smart Behavior:**
 * - If TenantContext already set (manual test setup) → Don't override, use existing
 * - If TenantContext NOT set → Extract from command.tenantId (auto-propagation)
 *
 * **This allows two test patterns:**
 * 1. Auto-propagation: Performance/REST tests (CommandGateway dispatches to Axon thread pool → ThreadLocal doesn't propagate → interceptor sets from command)
 * 2. Manual control: TenantValidationInterceptor tests (explicit beforeTest setup validates strict fail-closed)
 *
 * **CRITICAL:** Must be @Component (not @Bean) to use @PostConstruct for early registration.
 */
@Component
@Profile("test | rbac-test")
class TenantContextTestInterceptor(
    private val commandBus: CommandBus,
) {
    @PostConstruct
    fun registerInterceptor() {
        // Register interceptor programmatically to guarantee execution BEFORE other interceptors
        commandBus.registerHandlerInterceptor(
            MessageHandlerInterceptor { unitOfWork, chain ->
                val command = unitOfWork.message.payload

                if (command is TenantAwareCommand) {
                    // Only set context if NOT already set (allows manual override in tests)
                    val existingTenant = TenantContext.current()
                    if (existingTenant == null) {
                        // Auto-propagation: Set context from command
                        TenantContext.setCurrentTenantId(command.tenantId)
                        try {
                            chain.proceed()
                        } finally {
                            TenantContext.clearCurrentTenant()
                        }
                    } else {
                        // Manual control: Context already set, don't override
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            },
        )
    }
}
