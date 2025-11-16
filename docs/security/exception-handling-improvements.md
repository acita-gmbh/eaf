# Exception Handling & Resilience Improvements

**Enterprise Application Framework (EAF) v1.0**
**OWASP Category:** A10:2025 - Mishandling of Exceptional Conditions ⭐ **NEW**
**Priority:** CRITICAL
**Estimated Effort:** 2-3 weeks (Epic 1.5 scope)
**Date:** 2025-11-16

---

## Executive Summary

The OWASP Top 10:2025 introduces **A10: Mishandling of Exceptional Conditions** as a new category focusing on organizational resilience and error handling. This category addresses three critical failings:
1. **Prevention:** Applications don't prevent unusual situations
2. **Detection:** Applications fail to identify exceptional conditions when occurring
3. **Response:** Applications respond inadequately afterward

**Current Compliance Score:** 60/100
**Target Compliance Score:** 95/100
**Risk Level:** HIGH - Cascading failures, data loss, security bypass

### Quick Summary

| Component | Current Status | Target | Priority |
|-----------|---------------|--------|----------|
| **Domain Error Handling** | ✅ Excellent (Arrow Either) | ✅ Maintain | LOW |
| **Circuit Breakers** | ❌ None | ✅ Implemented | CRITICAL |
| **Retry Strategies** | ❌ None | ✅ Implemented | CRITICAL |
| **Bulkheads** | ❌ None | ✅ Implemented | HIGH |
| **Event Processor Resilience** | ⚠️ Partial | ✅ Enhanced | HIGH |
| **Chaos Engineering** | ❌ None | ✅ Implemented | MEDIUM |

---

## Background: OWASP A10:2025

### What is A10: Mishandling of Exceptional Conditions?

**OWASP Definition:**
> "Catching and handling exceptional conditions ensures that the underlying infrastructure of our programs is not left to deal with unpredictable situations."

**Three Failure Modes:**

1. **Prevention Failure:** Not preventing unusual situations
   - Example: No rate limiting → resource exhaustion
   - Example: No input validation → invalid state

2. **Detection Failure:** Not identifying exceptional conditions
   - Example: Silent failures in async processing
   - Example: No monitoring of event processor lag

3. **Response Failure:** Inadequate response to exceptions
   - Example: Retry without backoff → thundering herd
   - Example: No circuit breaker → cascading failures

### Real-World Impact

**Case Studies:**

**1. AWS S3 Outage (2017)**
- **Cause:** Cascading failure during debugging (removed too many servers)
- **Impact:** 4-hour outage, 150,000+ websites offline
- **Root Cause:** No circuit breakers between service dependencies

**2. GitHub Outage (2018)**
- **Cause:** Database replication lag → stale data
- **Impact:** 24-hour degradation
- **Root Cause:** Inadequate response to exceptional MySQL replication state

**3. Cloudflare Outage (2020)**
- **Cause:** Router config change → BGP route leak
- **Impact:** 27-minute global outage
- **Root Cause:** No automated rollback on detection of exceptional routing state

**Cost of Poor Exception Handling:**
- Availability loss: 99.9% → 99.0% = +8.76 hours downtime/year
- Cascading failures: 10x impact vs. isolated failures
- Data corruption: Potentially irreversible
- Security bypass: Exception paths often lack authorization checks

---

## Current State Analysis

### Existing Controls (✅)

**1. Domain Error Handling (Arrow Either)**
- ✅ Explicit error types (DomainError hierarchy)
- ✅ No exceptions in domain logic (functional purity)
- ✅ Type-safe error propagation

```kotlin
sealed class DomainError {
    data class ValidationError(val field: String, val constraint: String) : DomainError()
    data class BusinessRuleViolation(val rule: String, val reason: String) : DomainError()
    data class TenantIsolationViolation(val requestedTenant: String) : DomainError()
}

fun createWidget(command: CreateWidgetCommand): Either<DomainError, Widget> = either {
    ensure(command.name.isNotBlank()) {
        DomainError.ValidationError("name", "required")
    }
    Widget.create(command).bind()
}
```

**2. Global Exception Handling**
- ✅ @ControllerAdvice with RFC 7807 ProblemDetail
- ✅ Context enrichment (traceId, tenantId)
- ✅ Generic error messages (CWE-209 protection)

**3. Fail-Closed Design**
- ✅ TenantContext fails on missing tenant (no fallback)
- ✅ JWT validation rejects invalid tokens (no bypass)

### Identified Gaps (❌⚠️)

**1. No Circuit Breakers** ❌ **CRITICAL**
- External service failures propagate to caller
- No protection against slow/unavailable dependencies
- Cascading failure risk (e.g., Keycloak down → all requests fail)

**2. No Retry Strategies** ❌ **CRITICAL**
- Transient failures not retried
- Event processor failures require manual intervention
- No exponential backoff → thundering herd risk

**3. No Bulkheads** ❌ **HIGH**
- Single thread pool for all operations
- CPU-heavy operations can starve I/O operations
- No resource isolation between tenants

**4. Limited Event Processor Error Handling** ⚠️ **HIGH**
- Basic dead-letter queue (Axon default)
- No automated retry with backoff
- No poison message detection
- No circuit breaker for failing event handlers

**5. No Chaos Engineering** ❌ **MEDIUM**
- No systematic resilience testing
- Unknown failure modes
- Cannot validate exception handling under stress

---

## Recommended Improvements

### Priority 1: Resilience4j Circuit Breakers (CRITICAL)

**Effort:** 3-4 days
**Risk Mitigation:** Prevents cascading failures, isolates faults

#### Why Resilience4j?

- ✅ Kotlin-friendly (functional API)
- ✅ Spring Boot integration
- ✅ Lightweight (no external dependencies)
- ✅ Comprehensive: Circuit Breaker, Retry, RateLimiter, Bulkhead, TimeLimiter
- ✅ Metrics integration (Micrometer)

#### Implementation

**Step 1: Add Dependencies**

**gradle/libs.versions.toml:**
```toml
[versions]
resilience4j = "2.2.0"  # Latest stable

[libraries]
resilience4j-spring-boot = { module = "io.github.resilience4j:resilience4j-spring-boot3", version.ref = "resilience4j" }
resilience4j-kotlin = { module = "io.github.resilience4j:resilience4j-kotlin", version.ref = "resilience4j" }
resilience4j-micrometer = { module = "io.github.resilience4j:resilience4j-micrometer", version.ref = "resilience4j" }

[bundles]
resilience4j = ["resilience4j-spring-boot", "resilience4j-kotlin", "resilience4j-micrometer"]
```

**framework/core/build.gradle.kts:**
```kotlin
dependencies {
    api(libs.bundles.resilience4j)
}
```

**Step 2: Configuration**

**application.yml:**
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100
        permittedNumberOfCallsInHalfOpenState: 10
        waitDurationInOpenState: 60s
        failureRateThreshold: 50
        slowCallRateThreshold: 100
        slowCallDurationThreshold: 5s
        recordExceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
        ignoreExceptions:
          - com.axians.eaf.framework.core.exceptions.ValidationException
          - com.axians.eaf.framework.core.exceptions.BusinessRuleViolation

    instances:
      keycloakAuth:
        baseConfig: default
        waitDurationInOpenState: 30s  # Faster recovery for auth
        failureRateThreshold: 30      # More sensitive

      externalApi:
        baseConfig: default
        slidingWindowSize: 50
        failureRateThreshold: 60

      database:
        baseConfig: default
        waitDurationInOpenState: 120s  # Longer recovery for DB
        slowCallDurationThreshold: 2s

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.dao.TransientDataAccessException
        ignoreExceptions:
          - com.axians.eaf.framework.core.exceptions.ValidationException

    instances:
      eventProcessor:
        maxAttempts: 5
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        exponentialMaxWaitDuration: 60s

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 25
        maxWaitDuration: 0  # Fail immediately if full

    instances:
      externalApi:
        maxConcurrentCalls: 10  # Limit external API calls
        maxWaitDuration: 100ms

      cpuIntensive:
        maxConcurrentCalls: 4  # Match CPU cores
        maxWaitDuration: 0

  timelimiter:
    configs:
      default:
        timeoutDuration: 5s
        cancelRunningFuture: true

    instances:
      externalApi:
        timeoutDuration: 10s
```

**Step 3: Circuit Breaker for Keycloak**

**framework/security/src/main/kotlin/.../KeycloakCircuitBreakerConfiguration.kt:**
```kotlin
package com.axians.eaf.framework.security.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Instant

@Configuration
class KeycloakCircuitBreakerConfiguration(
    private val circuitBreakerRegistry: CircuitBreakerRegistry
) {
    @Bean
    fun keycloakCircuitBreaker(): CircuitBreaker {
        return circuitBreakerRegistry.circuitBreaker("keycloakAuth")
    }

    @Bean
    fun resilientJwtDecoder(
        jwtDecoder: JwtDecoder,
        keycloakCircuitBreaker: CircuitBreaker
    ): JwtDecoder {
        return ResilientJwtDecoder(jwtDecoder, keycloakCircuitBreaker)
    }
}

class ResilientJwtDecoder(
    private val delegate: JwtDecoder,
    private val circuitBreaker: CircuitBreaker
) : JwtDecoder {
    override fun decode(token: String): Jwt {
        return try {
            circuitBreaker.executeSupplier {
                delegate.decode(token)
            }
        } catch (ex: CallNotPermittedException) {
            // Circuit is open - use cached JWKS or fail gracefully
            logger.error("Keycloak circuit breaker is OPEN - authentication unavailable")
            throw JwtValidationException("Authentication service temporarily unavailable")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientJwtDecoder::class.java)
    }
}
```

**Step 4: Circuit Breaker for External APIs**

**framework/web/src/main/kotlin/.../ResilientRestTemplate.kt:**
```kotlin
package com.axians.eaf.framework.web.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.circuitbreaker.executeSupplier
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@Component
class ResilientRestTemplate(
    private val restTemplate: RestTemplate,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val timeLimiterRegistry: TimeLimiterRegistry
) {
    private val executor = Executors.newCachedThreadPool()

    fun <T> executeWithResilience(
        circuitBreakerName: String = "externalApi",
        retryName: String = "default",
        timeLimiterName: String = "externalApi",
        operation: () -> T
    ): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)
        val retry = retryRegistry.retry(retryName)
        val timeLimiter = timeLimiterRegistry.timeLimiter(timeLimiterName)

        // Compose resilience patterns
        val decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker) {
            Retry.decorateSupplier(retry) {
                TimeLimiter.decorateFutureSupplier(timeLimiter) {
                    CompletableFuture.supplyAsync(operation, executor)
                }.get()
            }.get()
        }

        return try {
            decoratedSupplier.get()
        } catch (ex: Exception) {
            logger.error("Resilient operation failed", ex, kv("circuit", circuitBreakerName))
            throw ex
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientRestTemplate::class.java)
    }
}
```

**Usage Example:**
```kotlin
@Service
class ExternalApiClient(
    private val resilientRestTemplate: ResilientRestTemplate
) {
    fun fetchData(id: String): ExternalData {
        return resilientRestTemplate.executeWithResilience(
            circuitBreakerName = "externalApi",
            retryName = "default"
        ) {
            restTemplate.getForObject(
                "https://api.example.com/data/$id",
                ExternalData::class.java
            ) ?: throw DataNotFoundException()
        }
    }
}
```

**Step 5: Metrics Integration**

**framework/observability/src/main/kotlin/.../ResilienceMetricsConfiguration.kt:**
```kotlin
@Configuration
class ResilienceMetricsConfiguration(
    private val meterRegistry: MeterRegistry,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val bulkheadRegistry: BulkheadRegistry
) {
    @PostConstruct
    fun registerMetrics() {
        // Circuit Breaker metrics
        circuitBreakerRegistry.allCircuitBreakers.forEach { circuitBreaker ->
            Tags.of("name", circuitBreaker.name)
                .let { tags ->
                    Gauge.builder("resilience4j.circuitbreaker.state") { circuitBreaker.state.order }
                        .tags(tags)
                        .description("Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                        .register(meterRegistry)

                    circuitBreaker.eventPublisher
                        .onSuccess { event ->
                            meterRegistry.counter("resilience4j.circuitbreaker.calls", tags.and("result", "success")).increment()
                        }
                        .onError { event ->
                            meterRegistry.counter("resilience4j.circuitbreaker.calls", tags.and("result", "error")).increment()
                        }
                        .onStateTransition { event ->
                            logger.warn(
                                "Circuit breaker state transition",
                                kv("name", circuitBreaker.name),
                                kv("from", event.stateTransition.fromState),
                                kv("to", event.stateTransition.toState)
                            )
                        }
                }
        }

        // Retry metrics
        retryRegistry.allRetries.forEach { retry ->
            Tags.of("name", retry.name)
                .let { tags ->
                    retry.eventPublisher
                        .onRetry { event ->
                            meterRegistry.counter("resilience4j.retry.calls", tags.and("attempt", event.numberOfRetryAttempts.toString())).increment()
                        }
                }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilienceMetricsConfiguration::class.java)
    }
}
```

#### Benefits
- ✅ Prevents cascading failures
- ✅ Fast failure detection (<1s)
- ✅ Automatic recovery (half-open state)
- ✅ Metrics for monitoring
- ✅ Graceful degradation

#### Testing

**Circuit Breaker Tests:**
```kotlin
// framework/core/src/test/kotlin/.../CircuitBreakerTest.kt
class CircuitBreakerTest : FunSpec({
    lateinit var circuitBreaker: CircuitBreaker
    lateinit var failingService: MockFailingService

    beforeEach {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .build()

        circuitBreaker = CircuitBreaker.of("test", config)
        failingService = MockFailingService()
    }

    test("should open circuit after failure threshold") {
        // Given: Service fails 6 out of 10 calls (60% > 50% threshold)
        repeat(10) { attempt ->
            try {
                circuitBreaker.executeSupplier {
                    failingService.call(shouldFail = attempt < 6)
                }
            } catch (ex: Exception) {
                // Expected failures
            }
        }

        // Then: Circuit should be OPEN
        circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

        // And: Next call should fail fast
        shouldThrow<CallNotPermittedException> {
            circuitBreaker.executeSupplier { failingService.call() }
        }
    }

    test("should transition to half-open and recover") {
        // Given: Circuit is OPEN
        circuitBreaker.transitionToOpenState()

        // When: Wait for cooldown period
        delay(1100)  // waitDurationInOpenState + margin

        // Then: Circuit should be HALF_OPEN
        circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN

        // When: Successful calls in half-open state
        failingService.recover()
        repeat(10) {
            circuitBreaker.executeSupplier { failingService.call() }
        }

        // Then: Circuit should be CLOSED
        circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
    }
})
```

---

### Priority 2: Event Processor Retry Strategies (CRITICAL)

**Effort:** 2-3 days
**Risk Mitigation:** Automatic recovery from transient failures

#### Problem Statement

**Current Behavior:**
1. Event handler throws exception
2. Axon retries immediately (default: infinite retries)
3. Poison message blocks processor
4. Manual intervention required

**Required Behavior:**
1. Event handler throws exception
2. Retry with exponential backoff (1s, 2s, 4s, 8s, 16s)
3. After max retries → Dead Letter Queue
4. Monitor DLQ for manual triage

#### Implementation

**Step 1: Custom Error Handler**

**framework/cqrs/src/main/kotlin/.../ResilientEventProcessor.kt:**
```kotlin
package com.axians.eaf.framework.cqrs.resilience

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.kotlin.retry.executeFunction
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.ListenerInvocationErrorHandler
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class ResilientEventProcessorConfiguration(
    private val retryRegistry: RetryRegistry
) {
    @Bean
    fun eventProcessorErrorHandler(): ListenerInvocationErrorHandler {
        return ResilientEventErrorHandler(retryRegistry.retry("eventProcessor"))
    }

    @Autowired
    fun configureEventProcessing(eventProcessingConfigurer: EventProcessingConfigurer) {
        eventProcessingConfigurer.registerListenerInvocationErrorHandler(
            "widget-processor"
        ) { ResilientEventErrorHandler(retryRegistry.retry("eventProcessor")) }
    }
}

class ResilientEventErrorHandler(
    private val retry: Retry
) : ListenerInvocationErrorHandler {
    override fun onError(exception: Exception, event: EventMessage<*>, eventHandler: EventMessageHandler) {
        try {
            // Retry with exponential backoff
            retry.executeFunction {
                eventHandler.handle(event)
            }
        } catch (ex: Exception) {
            // After all retries exhausted → log and move to DLQ
            logger.error(
                "Event processing failed after retries - moving to DLQ",
                ex,
                kv("event_type", event.payloadType.simpleName),
                kv("event_id", event.identifier),
                kv("aggregate_id", event.aggregateIdentifier),
                kv("retry_attempts", retry.metrics.numberOfSuccessfulCallsWithRetryAttempt)
            )

            // Record metrics
            meterRegistry.counter(
                "eaf.events.failed",
                "event_type", event.payloadType.simpleName,
                "reason", "retry_exhausted"
            ).increment()

            // Dead Letter Queue is handled by Axon's default behavior
            throw ex
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResilientEventErrorHandler::class.java)
    }
}
```

**Step 2: Dead Letter Queue Monitoring**

**framework/cqrs/src/main/kotlin/.../DeadLetterQueueMonitor.kt:**
```kotlin
@Component
class DeadLetterQueueMonitor(
    private val eventProcessingConfiguration: EventProcessingConfiguration,
    private val meterRegistry: MeterRegistry,
    private val alertService: AlertService
) {
    @Scheduled(fixedDelay = 60000)  // Every 60 seconds
    fun monitorDeadLetterQueues() {
        eventProcessingConfiguration.eventProcessors().forEach { (name, eventProcessor) ->
            if (eventProcessor is TrackingEventProcessor) {
                val deadLetterSize = eventProcessor.deadLetterSize()

                // Record metric
                meterRegistry.gauge(
                    "eaf.events.dead_letter_queue.size",
                    Tags.of("processor", name),
                    deadLetterSize.toDouble()
                )

                // Alert on non-empty DLQ
                if (deadLetterSize > 0) {
                    logger.warn(
                        "Dead letter queue is non-empty",
                        kv("processor", name),
                        kv("dead_letter_count", deadLetterSize)
                    )

                    alertService.sendAlert(
                        severity = Severity.WARNING,
                        title = "Event Processor DLQ Alert",
                        message = "Processor '$name' has $deadLetterSize messages in DLQ"
                    )
                }
            }
        }
    }
}
```

**Step 3: DLQ Management API**

**framework/cqrs/src/main/kotlin/.../DeadLetterQueueController.kt:**
```kotlin
@RestController
@RequestMapping("/api/internal/dlq")
class DeadLetterQueueController(
    private val eventProcessingConfiguration: EventProcessingConfiguration
) {
    @GetMapping
    fun listDeadLetterQueues(): List<DeadLetterQueueStatus> {
        return eventProcessingConfiguration.eventProcessors()
            .mapNotNull { (name, eventProcessor) ->
                if (eventProcessor is TrackingEventProcessor) {
                    DeadLetterQueueStatus(
                        processorName = name,
                        deadLetterCount = eventProcessor.deadLetterSize(),
                        messages = eventProcessor.deadLetterMessages().take(10)
                    )
                } else null
            }
    }

    @PostMapping("/{processorName}/retry/{sequenceId}")
    fun retryDeadLetter(
        @PathVariable processorName: String,
        @PathVariable sequenceId: Long
    ) {
        val processor = eventProcessingConfiguration.eventProcessor(processorName)
            .orElseThrow { IllegalArgumentException("Processor not found: $processorName") }

        if (processor is TrackingEventProcessor) {
            processor.retryDeadLetter(sequenceId)
            logger.info("Retrying dead letter", kv("processor", processorName), kv("sequence", sequenceId))
        } else {
            throw IllegalArgumentException("Processor is not a TrackingEventProcessor")
        }
    }

    @DeleteMapping("/{processorName}/delete/{sequenceId}")
    fun deleteDeadLetter(
        @PathVariable processorName: String,
        @PathVariable sequenceId: Long
    ) {
        val processor = eventProcessingConfiguration.eventProcessor(processorName)
            .orElseThrow { IllegalArgumentException("Processor not found: $processorName") }

        if (processor is TrackingEventProcessor) {
            processor.deleteDeadLetter(sequenceId)
            logger.warn("Deleted dead letter", kv("processor", processorName), kv("sequence", sequenceId))
        } else {
            throw IllegalArgumentException("Processor is not a TrackingEventProcessor")
        }
    }
}

data class DeadLetterQueueStatus(
    val processorName: String,
    val deadLetterCount: Long,
    val messages: List<DeadLetterMessage>
)
```

#### Benefits
- ✅ Automatic retry with backoff
- ✅ DLQ for manual triage
- ✅ Metrics and monitoring
- ✅ Management API for operations

---

### Priority 3: Bulkheads for Resource Isolation (HIGH)

**Effort:** 1-2 days
**Risk Mitigation:** Prevents resource exhaustion, tenant isolation

#### Implementation

**Step 1: Bulkhead Configuration (See Priority 1 - application.yml)**

**Step 2: CPU-Intensive Operations Bulkhead**

```kotlin
@Service
class ReportGenerationService(
    private val bulkheadRegistry: BulkheadRegistry
) {
    private val cpuBulkhead = bulkheadRegistry.bulkhead("cpuIntensive")

    fun generateReport(reportId: String): Report {
        return try {
            cpuBulkhead.executeSupplier {
                // CPU-intensive operation (PDF generation, data aggregation)
                performHeavyComputation(reportId)
            }
        } catch (ex: BulkheadFullException) {
            logger.warn("CPU bulkhead full - rejecting report generation", kv("report_id", reportId))
            throw ServiceUnavailableException("Report generation capacity exceeded - please retry later")
        }
    }
}
```

**Step 3: Tenant-Specific Bulkheads (Advanced)**

```kotlin
@Component
class TenantBulkheadRegistry {
    private val bulkheads = ConcurrentHashMap<String, Bulkhead>()

    fun getBulkhead(tenantId: String): Bulkhead {
        return bulkheads.computeIfAbsent(tenantId) {
            Bulkhead.of(
                "tenant-$tenantId",
                BulkheadConfig.custom()
                    .maxConcurrentCalls(10)  // 10 concurrent operations per tenant
                    .build()
            )
        }
    }
}

@Service
class TenantAwareService(
    private val tenantBulkheadRegistry: TenantBulkheadRegistry
) {
    fun processRequest(tenantId: String, request: Request): Response {
        val bulkhead = tenantBulkheadRegistry.getBulkhead(tenantId)

        return try {
            bulkhead.executeSupplier {
                performOperation(request)
            }
        } catch (ex: BulkheadFullException) {
            throw TooManyRequestsException("Tenant $tenantId has exceeded concurrent request limit")
        }
    }
}
```

#### Benefits
- ✅ Prevents thread pool starvation
- ✅ Isolates tenant resource usage
- ✅ Protects critical operations

---

### Priority 4: Chaos Engineering Tests (MEDIUM)

**Effort:** 3-5 days
**Risk Mitigation:** Validates resilience under stress

#### Implementation

**Step 1: Add Chaos Toolkit**

**gradle/libs.versions.toml:**
```toml
[versions]
toxiproxy = "2.1.7"  # Network failure simulation

[libraries]
toxiproxy-java = { module = "eu.rekawek.toxiproxy:toxiproxy-java", version.ref = "toxiproxy" }
```

**Step 2: Chaos Tests**

**framework/core/src/chaosTest/kotlin/.../NetworkPartitionChaosTest.kt:**
```kotlin
class NetworkPartitionChaosTest : FunSpec({
    lateinit var toxiproxy: ToxiproxyClient
    lateinit var postgresProxy: Proxy

    beforeSpec {
        toxiproxy = ToxiproxyClient("localhost", 8474)
        postgresProxy = toxiproxy.getProxy("postgres")
    }

    test("should handle PostgreSQL network partition gracefully") {
        // Given: Application is running normally
        val service = WidgetService(...)

        // When: Simulate network partition (500ms latency + 50% packet loss)
        postgresProxy.toxics()
            .latency("network-delay", ToxicDirection.DOWNSTREAM, 500)
            .setJitter(200)

        postgresProxy.toxics()
            .limitData("packet-loss", ToxicDirection.DOWNSTREAM, 0)
            .setBytes(1024)  // Drop after 1KB

        // Then: Circuit breaker should open
        eventually(Duration.ofSeconds(30)) {
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("database")
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }

        // And: Application should return 503 Service Unavailable
        shouldThrow<ServiceUnavailableException> {
            service.createWidget(CreateWidgetCommand(...))
        }

        // When: Network recovers
        postgresProxy.toxics().getAll().forEach { it.remove() }

        // Then: Circuit breaker should transition to HALF_OPEN and eventually CLOSED
        eventually(Duration.ofSeconds(120)) {
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("database")
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }

        // And: Application should recover
        val result = service.createWidget(CreateWidgetCommand(...))
        result.shouldBeRight()
    }

    test("should handle Keycloak outage gracefully") {
        // Similar test for authentication service
    }
})
```

**Step 3: Resource Exhaustion Tests**

```kotlin
class ResourceExhaustionChaosTest : FunSpec({
    test("should handle thread pool exhaustion") {
        // When: Saturate thread pool with slow operations
        val futures = (1..100).map {
            async {
                restTemplate.getForObject("http://slow-service/data", String::class.java)
            }
        }

        // Then: Bulkhead should reject excess requests
        val results = futures.awaitAll()
        val rejections = results.count { it is BulkheadFullException }

        rejections shouldBeGreaterThan 0
    }

    test("should handle memory pressure") {
        // Simulate memory exhaustion
        // Validate graceful degradation
    }
})
```

#### Benefits
- ✅ Validates resilience patterns
- ✅ Identifies unknown failure modes
- ✅ Builds confidence in production resilience

---

## Implementation Roadmap

### Week 1: Circuit Breakers & Retry
- **Day 1-2:** Add Resilience4j dependencies, configuration
- **Day 3:** Implement Keycloak circuit breaker
- **Day 4:** Implement external API circuit breaker
- **Day 5:** Metrics integration, testing

### Week 2: Event Processor Resilience
- **Day 1-2:** Event processor retry strategies
- **Day 3:** Dead Letter Queue monitoring
- **Day 4:** DLQ management API
- **Day 5:** Testing

### Week 3: Bulkheads & Chaos Engineering
- **Day 1-2:** Bulkhead implementation (CPU, tenant)
- **Day 3-5:** Chaos engineering tests

---

## Acceptance Criteria

- [ ] Circuit breakers configured for all external dependencies
- [ ] Retry strategies with exponential backoff
- [ ] Dead Letter Queue monitoring and alerting
- [ ] Bulkheads for resource isolation
- [ ] Resilience metrics integrated with Prometheus
- [ ] Chaos engineering tests passing
- [ ] Documentation updated

---

## References

- [OWASP Top 10:2025 - A10](https://owasp.org/Top10/2025/A10_2025-Mishandling_of_Exceptional_Conditions/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Chaos Engineering Principles](https://principlesofchaos.org/)

---

**Next Steps:**
1. Review and approve implementation plan
2. Create GitHub issues for each priority
3. Assign Epic 1.5 scope
4. Begin implementation (Week 1)

**Estimated Total Effort:** 2-3 weeks
**Risk Mitigation:** Addresses new OWASP 2025 category (A10) with highest resilience impact
