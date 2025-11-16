# Story 5.6: Observability Performance Limits and Backpressure

**Epic:** Epic 5 - Observability & Monitoring
**Status:** TODO
**Related Requirements:** FR005, FR011, FR018 (Resilience)

---

## 🔄 IMPLEMENTATION NOTE (2025-11-16)

**CRITICAL:** This story should leverage the **ResilientOperationExecutor** implemented in framework/core as part of OWASP Top 10:2025 compliance (A10:2025 - Exception Handling).

**Resilience4j Infrastructure Available:**
- `framework/core/src/main/kotlin/com/axians/eaf/framework/core/resilience/ResilientOperationExecutor.kt`
- `framework/core/src/main/kotlin/com/axians/eaf/framework/core/resilience/ResilienceAutoConfiguration.kt`
- Circuit Breaker, Retry, Bulkhead, Rate Limiter patterns with Micrometer metrics
- Comprehensive test suite with 20+ tests

**Implementation Approach:**
Use `ResilientOperationExecutor` for telemetry exports with fail-open circuit breakers to ensure observability failures never block application code. See implementation guide in `docs/owasp-top-10-2025-story-mapping.md`.

**Benefits:**
- ✅ Reuse tested resilience infrastructure (112+ tests)
- ✅ OWASP A10:2025 compliance
- ✅ Built-in metrics for circuit breaker states
- ✅ Fail-open design pattern (telemetry failures don't block app)
- ✅ Consistent resilience patterns across framework

---

## User Story

As a framework developer,
I want enforced performance limits on observability components,
So that logging/metrics/tracing never impact application performance >1%.

---

## Acceptance Criteria

1. ✅ Log rotation configured: daily rotation, 7-day retention, max 1GB per day
2. ✅ Intelligent trace sampling: 100% errors, 10% success (configurable)
3. ✅ Metric collection performance validated: <1% CPU overhead
4. ✅ Backpressure handling: drop telemetry data when systems unavailable (don't block application)
5. ✅ Performance test validates: observability overhead <1% under load
6. ✅ Circuit breaker for telemetry exports using ResilientOperationExecutor (fail-open on errors)
7. ✅ Performance limits documented in architecture.md

---

## Prerequisites

**Story 5.5** - OpenTelemetry Distributed Tracing

---

## Technical Notes

### Circuit Breaker for Telemetry Exports

**framework/observability/src/main/kotlin/com/axians/eaf/framework/observability/export/ResilientTelemetryExporter.kt:**
```kotlin
@Component
class ResilientTelemetryExporter(
    private val resilientExecutor: ResilientOperationExecutor,
    private val metricsBackend: MetricsBackend,
    private val tracingBackend: TracingBackend,
    private val loggingBackend: LoggingBackend
) {

    fun exportMetrics(metrics: List<Metric>) {
        resilientExecutor.execute(
            operation = "telemetry-metrics-export",
            block = { metricsBackend.send(metrics) },
            circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f) // Open circuit if 50% failures
                .slowCallRateThreshold(50f) // Consider slow calls as failures
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Recovery time
                .permittedNumberOfCallsInHalfOpenState(5)
                .slidingWindowSize(20)
                .build(),
            retryConfig = RetryConfig.custom()
                .maxAttempts(2) // Minimal retries for telemetry (fail-fast)
                .waitDuration(Duration.ofMillis(100))
                .build()
        ).fold(
            ifLeft = { error ->
                // CRITICAL: Telemetry failures NEVER block application
                // Log locally and increment failure counter
                logger.warn("Metrics export failed (circuit breaker): ${error.message}")
                localMetrics.increment("telemetry.export.failures", "type", "metrics")
            },
            ifRight = {
                localMetrics.increment("telemetry.export.success", "type", "metrics")
            }
        )
    }

    fun exportTraces(spans: List<Span>) {
        resilientExecutor.execute(
            operation = "telemetry-traces-export",
            block = { tracingBackend.send(spans) },
            circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(60)) // Longer recovery for traces
                .build(),
            retryConfig = RetryConfig.custom()
                .maxAttempts(1) // No retries for traces (time-sensitive data)
                .build()
        ).fold(
            ifLeft = { error ->
                logger.debug("Trace export failed (circuit breaker): ${error.message}")
                localMetrics.increment("telemetry.export.failures", "type", "traces")
            },
            ifRight = {
                localMetrics.increment("telemetry.export.success", "type", "traces")
            }
        )
    }
}
```

### Backpressure Configuration

**application.yml:**
```yaml
eaf:
  observability:
    # Performance limits (AC1-3)
    logging:
      rotation: daily
      retention-days: 7
      max-size-per-day: 1GB

    tracing:
      sampling:
        error-rate: 1.0    # 100% error traces
        success-rate: 0.1  # 10% success traces

    metrics:
      max-cpu-overhead: 1.0  # <1% CPU overhead target

    # Backpressure and resilience (AC4, AC6)
    resilience:
      circuit-breaker:
        failure-rate-threshold: 50
        slow-call-threshold: 2s
        wait-in-open-state: 30s
        fail-open: true  # CRITICAL: Never block application

      retry:
        max-attempts: 2
        wait-duration: 100ms

      bulkhead:
        max-concurrent-calls: 10
        max-wait-duration: 0ms  # Don't block, fail immediately
```

### Performance Monitoring

```kotlin
@Component
class ObservabilityPerformanceMonitor(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val meterRegistry: MeterRegistry
) {

    @PostConstruct
    fun registerMetrics() {
        // Track circuit breaker states for all telemetry exporters
        circuitBreakerRegistry.allCircuitBreakers.forEach { cb ->
            cb.eventPublisher.onStateTransition { event ->
                logger.warn(
                    "Telemetry circuit breaker state changed: {} -> {}",
                    event.stateTransition.fromState,
                    event.stateTransition.toState
                )
                meterRegistry.counter(
                    "telemetry.circuit_breaker.transitions",
                    "operation", event.circuitBreakerName,
                    "from", event.stateTransition.fromState.name,
                    "to", event.stateTransition.toState.name
                ).increment()
            }
        }
    }

    @Scheduled(fixedRate = 60000) // Every minute
    fun validatePerformanceTargets() {
        val cpuUsage = getCpuUsageByObservability()
        if (cpuUsage > 1.0) {
            logger.error("Observability CPU overhead exceeds 1% target: {}%", cpuUsage)
            // Alert or throttle telemetry
        }
    }
}
```

---

## References

- PRD: FR005 (<1% overhead), FR011, FR018
- Architecture: Section 17 (Performance Limits)
- Tech Spec: Section 3 (FR005, FR018), Section 8.2 (Optimization Strategies)
- **Resilience Infrastructure:** `docs/owasp-top-10-2025-story-mapping.md`
- **OWASP Compliance:** A10:2025 - Exception Handling
