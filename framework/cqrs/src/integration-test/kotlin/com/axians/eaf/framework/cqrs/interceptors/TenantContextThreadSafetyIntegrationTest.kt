package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration tests for TenantEventMessageInterceptor validating thread safety
 * and tenant isolation with real Redis Testcontainer.
 *
 * **CRITICAL SECURITY VALIDATION (SEC-001 - Risk Score 9)**:
 * These tests validate that ThreadLocal cleanup prevents cross-tenant data leakage
 * in pooled worker threads.
 *
 * Test Scenarios:
 * - 4.4-INT-004: Sequential tenant processing with cleanup validation
 * - 4.4-INT-008: Multiple tenants on same thread without leakage
 */
class TenantContextThreadSafetyIntegrationTest :
    BehaviorSpec({
        lateinit var redis: GenericContainer<*>
        lateinit var tenantContext: TenantContext
        lateinit var redisTemplate: RedisTemplate<String, String>
        lateinit var interceptor: TenantEventMessageInterceptor

        beforeSpec {
            redis =
                GenericContainer(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
            redis.start()

            val redisConfig = RedisStandaloneConfiguration(redis.host, redis.getMappedPort(6379))
            val connectionFactory = LettuceConnectionFactory(redisConfig)
            connectionFactory.afterPropertiesSet()

            redisTemplate = RedisTemplate<String, String>()
            redisTemplate.connectionFactory = connectionFactory
            redisTemplate.keySerializer = StringRedisSerializer()
            redisTemplate.valueSerializer = StringRedisSerializer()
            redisTemplate.afterPropertiesSet()

            tenantContext = TenantContext(meterRegistry = null)
            interceptor = TenantEventMessageInterceptor(tenantContext, redisTemplate, null)
        }

        afterSpec {
            redis.stop()
        }

        Given("Interceptor processing events on same worker thread") {
            val successChain = InterceptorChain { "success" }

            When("processing event from tenant-a followed by tenant-b") {
                val eventA = createEventWithMetadata(mapOf("tenantId" to "tenant-a"))
                val eventB = createEventWithMetadata(mapOf("tenantId" to "tenant-b"))

                val unitOfWorkA = DefaultUnitOfWork.startAndGet(eventA)
                val unitOfWorkB = DefaultUnitOfWork.startAndGet(eventB)

                interceptor.handle(unitOfWorkA, successChain)
                val contextAfterA = tenantContext.current()

                interceptor.handle(unitOfWorkB, successChain)
                val contextAfterB = tenantContext.current()

                Then("4.4-INT-004: TenantContext should be cleaned up after each event") {
                    contextAfterA shouldBe null
                    contextAfterB shouldBe null
                }
            }

            When("processing multiple events from different tenants sequentially") {
                val tenants = listOf("tenant-alpha", "tenant-beta", "tenant-gamma")
                val results = mutableListOf<String?>()

                tenants.forEach { tenantId ->
                    val event = createEventWithMetadata(mapOf("tenantId" to tenantId))
                    val unitOfWork = DefaultUnitOfWork.startAndGet(event)

                    interceptor.handle(unitOfWork, successChain)
                    results.add(tenantContext.current())
                }

                Then("4.4-INT-008: No cross-tenant leakage on same thread") {
                    results.all { it == null } shouldBe true
                }
            }

            When("event handler throws exception during processing") {
                val event = createEventWithMetadata(mapOf("tenantId" to "tenant-exception"))
                val unitOfWork = DefaultUnitOfWork.startAndGet(event)
                val failingChain = InterceptorChain { throw RuntimeException("Handler failed") }

                try {
                    interceptor.handle(unitOfWork, failingChain)
                } catch (e: RuntimeException) {
                    // Expected
                }

                Then("TenantContext cleaned up even on exception (finally block)") {
                    tenantContext.current() shouldBe null
                }
            }
        }
    })

private fun createEventWithMetadata(metadata: Map<String, Any?>): EventMessage<TestEvent> =
    GenericEventMessage
        .asEventMessage<TestEvent>(TestEvent("test-payload"))
        .andMetaData(metadata)

private data class TestEvent(
    val data: String,
)
