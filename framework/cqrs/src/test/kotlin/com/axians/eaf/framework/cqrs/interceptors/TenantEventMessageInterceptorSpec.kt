package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.observability.metrics.CustomMetrics
import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork

/**
 * Unit tests for TenantEventMessageInterceptor focusing on metadata extraction
 * and fail-closed security logic.
 *
 * Redis rate limiting is tested separately in integration tests with Testcontainers.
 *
 * Test Scenarios:
 * - 4.4-UNIT-001: Extract tenantId from valid metadata
 * - 4.4-UNIT-002: Fail-closed on missing/null/blank tenantId
 * - 4.4-UNIT-003: ThreadLocal cleanup verification
 */
class TenantEventMessageInterceptorSpec :
    FunSpec({
        val meterRegistry = SimpleMeterRegistry()
        val tenantContext = TenantContext(meterRegistry)
        val customMetrics = CustomMetrics(meterRegistry, tenantContext)
        val interceptor =
            TenantEventMessageInterceptor(
                tenantContext = tenantContext,
                redisTemplate = null,
                meterRegistry = null,
                customMetrics = customMetrics,
            )
        val successChain = InterceptorChain { "success" }

        test("4.4-UNIT-001: should extract tenantId from valid event metadata") {
            val event = createEventWithMetadata(mapOf("tenantId" to "tenant-a"))
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)

            val result = interceptor.handle(unitOfWork, successChain)

            result shouldBe "success"
            tenantContext.current() shouldBe null
        }

        test("4.4-UNIT-002: should fail-closed when tenantId metadata is missing") {
            val event = createEventWithMetadata(emptyMap())
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)

            val exception =
                shouldThrow<SecurityException> {
                    interceptor.handle(unitOfWork, successChain)
                }

            exception.message shouldContain "Access denied: required context missing"
            tenantContext.current() shouldBe null
        }

        test("should fail-closed when tenantId value is null") {
            val event = createEventWithMetadata(mapOf("tenantId" to null))
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)

            shouldThrow<SecurityException> {
                interceptor.handle(unitOfWork, successChain)
            }
        }

        test("should fail-closed when tenantId value is blank") {
            val event = createEventWithMetadata(mapOf("tenantId" to "   "))
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)

            shouldThrow<SecurityException> {
                interceptor.handle(unitOfWork, successChain)
            }
        }

        test("should fail-closed when tenantId is non-String type") {
            val event = createEventWithMetadata(mapOf("tenantId" to 12345))
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)

            shouldThrow<SecurityException> {
                interceptor.handle(unitOfWork, successChain)
            }
        }

        test("4.4-UNIT-003: should cleanup TenantContext even when handler throws exception") {
            val event = createEventWithMetadata(mapOf("tenantId" to "tenant-e"))
            val unitOfWork = DefaultUnitOfWork.startAndGet(event)
            val failingChain = InterceptorChain { throw IllegalStateException("Handler failed") }

            shouldThrow<IllegalStateException> {
                interceptor.handle(unitOfWork, failingChain)
            }

            tenantContext.current() shouldBe null
        }
    })

private fun createEventWithMetadata(metadata: Map<String, Any?>): EventMessage<TestEventPayload> =
    GenericEventMessage
        .asEventMessage<TestEventPayload>(TestEventPayload("test-payload"))
        .andMetaData(metadata)

internal data class TestEventPayload(
    val data: String,
)
