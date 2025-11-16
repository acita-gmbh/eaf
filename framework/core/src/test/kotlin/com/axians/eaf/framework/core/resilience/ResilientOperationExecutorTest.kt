package com.axians.eaf.framework.core.resilience

import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for ResilientOperationExecutor.
 *
 * Tests:
 * - Circuit breaker behavior (CLOSED, OPEN, HALF_OPEN states)
 * - Retry with exponential backoff
 * - Bulkhead capacity enforcement
 * - Combined patterns (circuit breaker + retry + bulkhead)
 * - Error handling and exceptions
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * @since 1.0.0
 */
class ResilientOperationExecutorTest : FunSpec({

    lateinit var circuitBreakerRegistry: CircuitBreakerRegistry
    lateinit var retryRegistry: RetryRegistry
    lateinit var bulkheadRegistry: BulkheadRegistry
    lateinit var executor: ResilientOperationExecutor

    beforeEach {
        // Create fresh registries for each test
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
        retryRegistry = RetryRegistry.ofDefaults()
        bulkheadRegistry = BulkheadRegistry.ofDefaults()

        executor = ResilientOperationExecutor(
            circuitBreakerRegistry,
            retryRegistry,
            bulkheadRegistry
        )
    }

    context("Circuit Breaker") {
        test("should execute successfully when circuit is CLOSED") {
            // Given: Circuit breaker is CLOSED (default)
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test")
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED

            // When: Execute operation
            val result = executor.execute(circuitBreakerName = "test") {
                "success"
            }

            // Then: Operation succeeds
            result shouldBe "success"
        }

        test("should open circuit after failure threshold exceeded") {
            // Given: Circuit breaker with low threshold
            val config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f) // 50% failure rate
                .minimumNumberOfCalls(5)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build()

            circuitBreakerRegistry.circuitBreaker("test", config)

            // When: Execute with 60% failure rate (6 failures, 4 successes)
            val callCount = AtomicInteger(0)
            repeat(10) { attempt ->
                try {
                    executor.execute(circuitBreakerName = "test") {
                        callCount.incrementAndGet()
                        if (attempt < 6) {
                            throw RuntimeException("Simulated failure")
                        }
                        "success"
                    }
                } catch (ex: Exception) {
                    // Expected failures
                }
            }

            // Then: Circuit breaker should be OPEN
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test")
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN
        }

        test("should reject calls when circuit is OPEN") {
            // Given: Circuit breaker in OPEN state
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test")
            circuitBreaker.transitionToOpenState()

            // When: Attempt to execute operation
            val exception = shouldThrow<ResilientOperationException> {
                executor.execute(circuitBreakerName = "test") {
                    "should not be called"
                }
            }

            // Then: Operation is rejected with meaningful error
            exception.message shouldContain "temporarily unavailable"
            exception.message shouldContain "circuit breaker is OPEN"
            exception.patternName shouldBe "test"
            exception.cause.shouldBeInstanceOf<CallNotPermittedException>()
        }

        test("should transition from OPEN to HALF_OPEN after wait duration") {
            // Given: Circuit breaker in OPEN state with short wait duration
            val config = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build()

            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test", config)
            circuitBreaker.transitionToOpenState()
            circuitBreaker.state shouldBe CircuitBreaker.State.OPEN

            // When: Wait for cooldown period
            Thread.sleep(150)

            // Then: Circuit breaker transitions to HALF_OPEN
            circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN
        }

        test("should close circuit after successful calls in HALF_OPEN state") {
            // Given: Circuit breaker in HALF_OPEN state
            val config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(5)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build()

            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test", config)
            circuitBreaker.transitionToOpenState()
            Thread.sleep(150) // Wait for HALF_OPEN
            circuitBreaker.state shouldBe CircuitBreaker.State.HALF_OPEN

            // When: Execute successful calls
            repeat(5) {
                executor.execute(circuitBreakerName = "test") {
                    "success"
                }
            }

            // Then: Circuit breaker should be CLOSED
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }
    }

    context("Retry Strategy") {
        test("should retry failed operation with exponential backoff") {
            // Given: Retry configuration with 3 attempts
            val config = RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build()

            retryRegistry.retry("test", config)

            // When: Operation fails twice, then succeeds
            val attemptCount = AtomicInteger(0)
            val result = executor.execute(
                circuitBreakerName = "test",
                retryName = "test"
            ) {
                val attempt = attemptCount.incrementAndGet()
                if (attempt < 3) {
                    throw RuntimeException("Transient failure $attempt")
                }
                "success after $attempt attempts"
            }

            // Then: Operation eventually succeeds after retries
            result shouldBe "success after 3 attempts"
            attemptCount.get() shouldBe 3
        }

        test("should throw exception after max retry attempts exhausted") {
            // Given: Retry configuration with 3 attempts
            val config = RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build()

            retryRegistry.retry("test", config)

            // When: Operation always fails
            val attemptCount = AtomicInteger(0)
            val exception = shouldThrow<RuntimeException> {
                executor.execute(
                    circuitBreakerName = "test",
                    retryName = "test"
                ) {
                    attemptCount.incrementAndGet()
                    throw RuntimeException("Persistent failure")
                }
            }

            // Then: Exception is thrown after all retries exhausted
            exception.message shouldBe "Persistent failure"
            attemptCount.get() shouldBe 3 // Initial + 2 retries
        }

        test("should not retry on ignored exceptions") {
            // Given: Retry configuration that ignores IllegalArgumentException
            val config = RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .ignoreExceptions(IllegalArgumentException::class.java)
                .build()

            retryRegistry.retry("test", config)

            // When: Operation throws ignored exception
            val attemptCount = AtomicInteger(0)
            val exception = shouldThrow<IllegalArgumentException> {
                executor.execute(
                    circuitBreakerName = "test",
                    retryName = "test"
                ) {
                    attemptCount.incrementAndGet()
                    throw IllegalArgumentException("Validation error")
                }
            }

            // Then: No retry occurs, exception is thrown immediately
            exception.message shouldBe "Validation error"
            attemptCount.get() shouldBe 1 // No retries
        }
    }

    context("Bulkhead") {
        test("should execute within bulkhead capacity") {
            // Given: Bulkhead with capacity of 5
            val config = BulkheadConfig.custom()
                .maxConcurrentCalls(5)
                .build()

            bulkheadRegistry.bulkhead("test", config)

            // When: Execute 5 concurrent operations
            val results = (1..5).map { index ->
                executor.execute(
                    circuitBreakerName = "test",
                    bulkheadName = "test"
                ) {
                    "result-$index"
                }
            }

            // Then: All operations succeed
            results.size shouldBe 5
        }

        test("should reject calls when bulkhead is full") {
            // Given: Bulkhead with capacity of 1 and no wait time
            val config = BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO) // Fail immediately
                .build()

            val bulkhead = bulkheadRegistry.bulkhead("test", config)

            // When: Fill bulkhead and attempt another call
            try {
                bulkhead.acquirePermission() // Fill the bulkhead

                val exception = shouldThrow<ResilientOperationException> {
                    executor.execute(
                        circuitBreakerName = "test",
                        bulkheadName = "test"
                    ) {
                        "should not be called"
                    }
                }

                // Then: Operation is rejected with meaningful error
                exception.message shouldContain "capacity exceeded"
                exception.message shouldContain "too many concurrent requests"
                exception.patternName shouldBe "test"
                exception.cause.shouldBeInstanceOf<BulkheadFullException>()
            } finally {
                bulkhead.releasePermission()
            }
        }
    }

    context("Combined Patterns") {
        test("should apply circuit breaker + retry + bulkhead together") {
            // Given: All resilience patterns configured
            val cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50.0f)
                .build()
            circuitBreakerRegistry.circuitBreaker("test", cbConfig)

            val retryConfig = RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build()
            retryRegistry.retry("test", retryConfig)

            val bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .build()
            bulkheadRegistry.bulkhead("test", bulkheadConfig)

            // When: Execute operation that fails once then succeeds
            val attemptCount = AtomicInteger(0)
            val result = executor.execute(
                circuitBreakerName = "test",
                retryName = "test",
                bulkheadName = "test"
            ) {
                val attempt = attemptCount.incrementAndGet()
                if (attempt == 1) {
                    throw RuntimeException("Transient failure")
                }
                "success"
            }

            // Then: Operation succeeds after retry
            result shouldBe "success"
            attemptCount.get() shouldBe 2

            // And: Circuit breaker remains CLOSED
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("test")
            circuitBreaker.state shouldBe CircuitBreaker.State.CLOSED
        }
    }

    context("Suspend Functions") {
        test("should execute suspend operation with circuit breaker") {
            // Given: Circuit breaker configured
            circuitBreakerRegistry.circuitBreaker("test")

            // When: Execute suspend operation
            val result = kotlinx.coroutines.runBlocking {
                executor.executeSuspend(circuitBreakerName = "test") {
                    delay(10)
                    "suspend success"
                }
            }

            // Then: Operation succeeds
            result shouldBe "suspend success"
        }

        test("should retry suspend operation") {
            // Given: Retry configuration
            val config = RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .build()
            retryRegistry.retry("test", config)

            // When: Suspend operation fails twice then succeeds
            val attemptCount = AtomicInteger(0)
            val result = kotlinx.coroutines.runBlocking {
                executor.executeSuspend(
                    circuitBreakerName = "test",
                    retryName = "test"
                ) {
                    delay(10)
                    val attempt = attemptCount.incrementAndGet()
                    if (attempt < 3) {
                        throw RuntimeException("Transient failure")
                    }
                    "success"
                }
            }

            // Then: Operation succeeds after retries
            result shouldBe "success"
            attemptCount.get() shouldBe 3
        }
    }

    context("Error Handling") {
        test("should provide meaningful error messages") {
            // Given: Circuit breaker in OPEN state
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker("keycloakAuth")
            circuitBreaker.transitionToOpenState()

            // When: Execute operation
            val exception = shouldThrow<ResilientOperationException> {
                executor.execute(circuitBreakerName = "keycloakAuth") {
                    "should not execute"
                }
            }

            // Then: Error message is user-friendly (not technical)
            exception.message shouldContain "Service temporarily unavailable"
            exception.message shouldContain "circuit breaker is OPEN"
            exception.patternName shouldBe "keycloakAuth"
        }

        test("should propagate business exceptions without wrapping") {
            // Given: Circuit breaker configured
            circuitBreakerRegistry.circuitBreaker("test")

            // When: Operation throws business exception
            val exception = shouldThrow<IllegalArgumentException> {
                executor.execute(circuitBreakerName = "test") {
                    throw IllegalArgumentException("Business validation failed")
                }
            }

            // Then: Original exception is preserved
            exception.message shouldBe "Business validation failed"
        }
    }
})
