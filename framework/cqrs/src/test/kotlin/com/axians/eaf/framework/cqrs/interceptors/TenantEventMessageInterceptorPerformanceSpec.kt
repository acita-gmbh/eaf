package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.security.tenant.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork
import kotlin.system.measureNanoTime

/**
 * Performance benchmark test for TenantEventMessageInterceptor.
 *
 * **Test Scenario 4.4-UNIT-005**: Validates interceptor overhead <5ms p95 per event.
 *
 * **Performance Target** (from Story 4.4):
 * - p95 latency: <5ms per event
 * - Total overhead: ~2-3ms (ThreadLocal set + cleanup)
 * - Redis rate limit: ~2ms network overhead (skipped in this test)
 *
 * **Methodology**:
 * - Process 1000 mock EventMessages sequentially
 * - Measure time per event with nanosecond precision
 * - Calculate p95 percentile
 * - Assert <5ms (5,000,000 nanoseconds)
 */
class TenantEventMessageInterceptorPerformanceSpec :
    FunSpec({
        test("4.4-UNIT-005: interceptor overhead should be <5ms p95 per event") {
            val tenantContext = TenantContext(SimpleMeterRegistry())
            val interceptor =
                TenantEventMessageInterceptor(
                    tenantContext = tenantContext,
                    redisTemplate = null, // Skip Redis for pure interceptor overhead measurement
                    meterRegistry = SimpleMeterRegistry(),
                )

            val successChain = InterceptorChain { "success" }
            val eventCount = 1000
            val latencies = mutableListOf<Long>()

            // Warm-up: 100 iterations
            repeat(100) {
                val event = createTestEvent("tenant-warmup")
                val unitOfWork = DefaultUnitOfWork.startAndGet(event)
                interceptor.handle(unitOfWork, successChain)
            }

            // Benchmark: 1000 iterations
            repeat(eventCount) { i ->
                val event = createTestEvent("tenant-${i % 10}") // 10 different tenants
                val unitOfWork = DefaultUnitOfWork.startAndGet(event)

                val nanos =
                    measureNanoTime {
                        interceptor.handle(unitOfWork, successChain)
                    }

                latencies.add(nanos)
            }

            // Calculate p95 (95th percentile)
            val sorted = latencies.sorted()
            val p95Index = (eventCount * 0.95).toInt()
            val p95Nanos = sorted[p95Index]
            val p95Millis = p95Nanos / 1_000_000.0

            println("Performance Benchmark Results:")
            println("  Events processed: $eventCount")
            println("  p50 latency: ${sorted[eventCount / 2] / 1_000_000.0} ms")
            println("  p95 latency: $p95Millis ms")
            println("  p99 latency: ${sorted[(eventCount * 0.99).toInt()] / 1_000_000.0} ms")
            println("  max latency: ${sorted.last() / 1_000_000.0} ms")

            // Assert: p95 < threshold (default 5,000,000 nanoseconds = 5ms)
            // Threshold can be overridden via system property
            val thresholdNanos =
                System.getProperty("tenant.interceptor.p95.threshold.nanos")?.toLongOrNull()
                    ?: 5_000_000L
            p95Nanos shouldBeLessThan thresholdNanos
        }
    })

private fun createTestEvent(tenantId: String) =
    GenericEventMessage
        .asEventMessage<PerfTestEvent>(PerfTestEvent("payload"))
        .andMetaData(mapOf("tenantId" to tenantId))

internal data class PerfTestEvent(
    val data: String,
)
