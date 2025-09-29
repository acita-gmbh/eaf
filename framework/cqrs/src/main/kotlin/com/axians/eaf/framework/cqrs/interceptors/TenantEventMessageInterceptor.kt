package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.observability.metrics.CustomMetrics
import com.axians.eaf.framework.security.tenant.TenantContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Axon message interceptor that propagates tenant context from event metadata to ThreadLocal
 * storage before asynchronous @EventHandler execution.
 *
 * ## Architecture Integration
 *
 * This interceptor bridges three critical architectural boundaries:
 *
 * 1. **Axon Framework**: Intercepts EventMessage processing in Tracking Event Processors
 * 2. **Micrometer Context Propagation**: Coordinates with TenantContextPropagationComponent
 *    for async task scheduling and worker thread management
 * 3. **TenantContext ThreadLocal**: Populates tenant ID before @EventHandler methods execute,
 *    enabling downstream RLS (Row-Level Security) enforcement via database interceptor
 *
 * ## Security: Fail-Closed Contract
 *
 * **CRITICAL**: This interceptor implements fail-closed security design (CWE-209 compliant):
 *
 * - **Missing tenantId metadata** → Throws SecurityException with generic message
 * - **Null tenantId value** → Throws SecurityException with generic message
 * - **Blank tenantId value** → Throws SecurityException with generic message
 * - **Rate limit exceeded** → Throws SecurityException (DoS protection)
 *
 * Generic error message prevents information disclosure: "Access denied: required context missing"
 *
 * ## Event Metadata Contract
 *
 * **Required Metadata Key**: `tenantId` (String)
 *
 * - **Source**: TenantCorrelationDataProvider enriches events during publication
 * - **Format**: Non-blank tenant identifier (e.g., "tenant-abc-123")
 * - **Validation**: Explicit null/blank checks with fail-closed exceptions
 *
 * **Failure Modes**:
 * - Missing metadata key → SecurityException
 * - Null metadata value → SecurityException
 * - Blank metadata value (empty/whitespace) → SecurityException
 * - Rate limit exceeded → SecurityException
 *
 * ## Thread Safety Guarantees
 *
 * **CRITICAL SECURITY REQUIREMENT (SEC-001 mitigation - Risk Score 9)**:
 *
 * This interceptor addresses Critical Risk SEC-001: Tenant Context Leakage in Thread Pools.
 *
 * **Problem**: Axon Tracking Event Processors use pooled worker threads. Without proper cleanup,
 * ThreadLocal tenant context from Event A (tenant-a) could leak to Event B (tenant-b) processed
 * on the same worker thread, causing catastrophic cross-tenant data contamination.
 *
 * **Solution**: Mandatory `finally` block guarantees cleanup:
 *
 * ```kotlin
 * try {
 *     tenantContext.setCurrentTenantId(tenantId)
 *     // Event handler executes with correct tenant context
 * } finally {
 *     tenantContext.clearCurrentTenant() // ALWAYS executes, even on exceptions
 * }
 * ```
 *
 * **Validation**: Integration tests 4.4-INT-004 and 4.4-INT-008 verify sequential tenant
 * isolation by processing events from different tenants on the same thread and asserting
 * TenantContext reflects the current tenant (not leaked from previous event).
 *
 * ## Rate Limiting (SEC-002 Mitigation)
 *
 * **DoS Protection**: Redis-based rate limiter prevents event flooding attacks.
 *
 * - **Limit**: 100 events/second per tenant
 * - **Window**: Sliding 1-second window using Redis INCR + EXPIRE
 * - **Enforcement**: Fail-closed (throws SecurityException if exceeded)
 * - **Key Pattern**: `tenant:events:rate:{tenantId}`
 *
 * ## Performance Characteristics
 *
 * **Target**: <5ms p95 latency per event (validated by Subtask 4.4 performance test)
 *
 * - ThreadLocal set: ~50ns (negligible)
 * - Redis rate limit check: ~2ms (network + Redis INCR)
 * - ThreadLocal cleanup: ~50ns (negligible)
 * - **Total overhead**: ~2-3ms per event (acceptable for async processing)
 *
 * Micrometer Timer tracks `tenant.event.interceptor.duration` for production monitoring.
 *
 * @param tenantContext ThreadLocal-based tenant context manager from Story 4.1
 * @param redisTemplate Redis client for distributed rate limiting (DoS protection)
 * @param meterRegistry Micrometer registry for performance monitoring (optional for tests)
 *
 * @see com.axians.eaf.framework.security.tenant.TenantContext
 * @see TenantCorrelationDataProvider Event metadata enrichment
 * @see com.axians.eaf.framework.security.tenant.TenantSessionCleanupAspect Database RLS enforcement
 */
@Component
class TenantEventMessageInterceptor(
    private val tenantContext: TenantContext,
    private val redisTemplate: RedisTemplate<String, String>?,
    private val meterRegistry: MeterRegistry?,
    private val customMetrics: CustomMetrics,
) : MessageHandlerInterceptor<EventMessage<*>> {
    companion object {
        private val logger = LoggerFactory.getLogger(TenantEventMessageInterceptor::class.java)
        private const val TENANT_METADATA_KEY = "tenantId"
        private const val RATE_LIMIT_PER_SECOND = 100
        private const val GENERIC_ERROR_MESSAGE = "Access denied: required context missing"
    }

    /**
     * Intercepts event message processing to propagate tenant context before @EventHandler execution.
     *
     * **Execution Flow**:
     * 1. Extract tenantId from event metadata (fail-closed if missing/null/blank)
     * 2. Check Redis rate limit (fail-closed if exceeded)
     * 3. Set TenantContext ThreadLocal
     * 4. Proceed with event handler chain
     * 5. **ALWAYS** cleanup TenantContext in finally block (SEC-001 mitigation)
     *
     * **Thread Safety**: The finally block MUST execute regardless of success/failure to prevent
     * cross-tenant leakage in pooled worker threads (Critical Risk Score 9).
     *
     * @param unitOfWork Axon Unit of Work for transaction management
     * @param interceptorChain Handler chain to proceed with event processing
     * @return Handled event result (may be exception if validation fails)
     * @throws SecurityException if metadata validation fails (fail-closed design)
     */
    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any? {
        val event = unitOfWork.message
        val timer = meterRegistry?.let { Timer.start(it) }
        val start = Instant.now()

        return try {
            // SECURITY: Extract tenant ID with fail-closed validation
            val tenantId = extractTenantId(event)

            // SECURITY: Rate limiting (DoS protection - SEC-002 mitigation)
            checkRateLimit(tenantId)

            // Set ThreadLocal tenant context for downstream RLS enforcement
            tenantContext.setCurrentTenantId(tenantId)
            logger.debug(
                "Tenant context propagated for event: {} (tenant: {})",
                event.payloadType.simpleName,
                tenantId,
            )

            // Proceed with event handler execution
            val result = interceptorChain.proceed()
            customMetrics.recordEvent(
                event.payloadType.simpleName,
                Duration.between(start, Instant.now()),
                success = true,
            )
            result
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use of generic exception in infrastructure interceptor:
            // We record metrics for ANY exception type then re-throw immediately
            customMetrics.recordEvent(
                event.payloadType.simpleName,
                Duration.between(start, Instant.now()),
                success = false,
            )
            throw ex
        } finally {
            // CRITICAL SECURITY REQUIREMENT (SEC-001 mitigation - Risk Score 9):
            // Mandatory cleanup prevents cross-tenant leakage in pooled worker threads.
            // This finally block MUST execute regardless of success/failure.
            tenantContext.clearCurrentTenant()
            logger.trace("Tenant context cleaned up after event processing")

            // Record performance metrics
            meterRegistry?.let { registry ->
                timer?.stop(
                    registry.timer(
                        "tenant.event.interceptor.duration",
                        "event_type",
                        event.payloadType.simpleName,
                    ),
                )
            }
        }
    }

    /**
     * Extracts tenant ID from event metadata with fail-closed validation.
     *
     * **Fail-Closed Contract**:
     * - Missing metadata key → SecurityException
     * - Null metadata value → SecurityException
     * - Blank metadata value → SecurityException
     *
     * **CWE-209 Compliance**: Generic error message prevents information disclosure.
     *
     * @param event Event message containing metadata
     * @return Validated non-blank tenant ID
     * @throws SecurityException if validation fails (fail-closed design)
     */
    private fun extractTenantId(event: EventMessage<*>): String {
        val tenantIdValue =
            event.metaData[TENANT_METADATA_KEY]
                ?: throwMetadataValidationError("missing tenantId metadata", event)

        val tenantId =
            when (tenantIdValue) {
                is String -> tenantIdValue
                else ->
                    throwMetadataValidationError(
                        "invalid tenantId type: ${tenantIdValue::class.simpleName}",
                        event,
                    )
            }

        if (tenantId.isBlank()) {
            throwMetadataValidationError("blank tenantId metadata", event)
        }

        return tenantId
    }

    private fun throwMetadataValidationError(
        reason: String,
        event: EventMessage<*>,
    ): Nothing {
        logger.warn("Event processing blocked: {} (event: {})", reason, event.payloadType.simpleName)
        meterRegistry?.counter("tenant.event.interceptor.validation_failed")?.increment()
        throw SecurityException(GENERIC_ERROR_MESSAGE)
    }

    /**
     * Checks Redis-based rate limit to prevent DoS attacks via event flooding.
     *
     * **Rate Limiting Strategy**:
     * - Limit: 100 events/second per tenant
     * - Window: Sliding 1-second window using Redis INCR + EXPIRE
     * - Enforcement: Fail-closed (throws SecurityException if exceeded)
     *
     * **Redis Key Pattern**: `tenant:events:rate:{tenantId}`
     *
     * @param tenantId Tenant identifier for rate limit key
     * @throws SecurityException if rate limit exceeded (SEC-002 mitigation)
     */
    @Suppress("TooGenericExceptionCaught")
    private fun checkRateLimit(tenantId: String) {
        // Skip rate limiting if Redis not available (graceful degradation)
        if (redisTemplate == null) {
            logger.trace("Rate limiting skipped - Redis not configured")
            return
        }

        val rateKey = "tenant:events:rate:$tenantId"

        try {
            // Atomic increment with expiration
            val currentCount = redisTemplate.opsForValue().increment(rateKey) ?: 0L

            // Set 1-second expiration on first increment
            if (currentCount == 1L) {
                redisTemplate.expire(rateKey, Duration.ofSeconds(1))
            }

            // Enforce rate limit
            if (currentCount > RATE_LIMIT_PER_SECOND) {
                logger.warn(
                    "Event processing blocked: rate limit exceeded (tenant: {}, count: {})",
                    tenantId,
                    currentCount,
                )
                meterRegistry
                    ?.counter("tenant.event.interceptor.rate_limit_exceeded", "tenant", tenantId)
                    ?.increment()
                throw SecurityException(GENERIC_ERROR_MESSAGE)
            }
        } catch (e: SecurityException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Redis failure should not block event processing (graceful degradation)
            logger.error("Rate limit check failed, proceeding without limit (tenant: {})", tenantId, e)
            meterRegistry?.counter("tenant.event.interceptor.rate_limit_error")?.increment()
        }
    }
}
