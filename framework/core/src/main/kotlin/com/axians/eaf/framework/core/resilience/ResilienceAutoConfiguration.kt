package com.axians.eaf.framework.core.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.BaseUnits
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

/**
 * Auto-configuration for Resilience4j patterns.
 *
 * Provides:
 * - Circuit Breaker metrics integration
 * - Retry metrics integration
 * - Bulkhead metrics integration
 * - Event listeners for state changes
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(CircuitBreaker::class)
class ResilienceAutoConfiguration {

    private val logger = LoggerFactory.getLogger(ResilienceAutoConfiguration::class.java)

    /**
     * Circuit Breaker metrics integration.
     *
     * Registers metrics for:
     * - Circuit breaker state (CLOSED=0, OPEN=1, HALF_OPEN=2)
     * - Success/failure call counts
     * - State transition events
     */
    @Configuration
    @ConditionalOnBean(MeterRegistry::class)
    class CircuitBreakerMetricsConfiguration(
        private val meterRegistry: MeterRegistry,
        private val circuitBreakerRegistry: CircuitBreakerRegistry
    ) {
        @PostConstruct
        fun registerCircuitBreakerMetrics() {
            circuitBreakerRegistry.allCircuitBreakers.forEach { circuitBreaker ->
                val tags = Tags.of("name", circuitBreaker.name)

                // State gauge (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
                meterRegistry.gauge(
                    "resilience4j.circuitbreaker.state",
                    tags,
                    circuitBreaker
                ) { it.state.order.toDouble() }

                // Failure rate gauge
                meterRegistry.gauge(
                    "resilience4j.circuitbreaker.failure_rate",
                    tags.and("unit", BaseUnits.PERCENT),
                    circuitBreaker
                ) { it.metrics.failureRate.toDouble() }

                // Event listeners
                circuitBreaker.eventPublisher
                    .onSuccess { event ->
                        meterRegistry.counter(
                            "resilience4j.circuitbreaker.calls",
                            tags.and("result", "success")
                        ).increment()
                    }
                    .onError { event ->
                        meterRegistry.counter(
                            "resilience4j.circuitbreaker.calls",
                            tags.and("result", "error")
                                .and("exception", event.throwable.javaClass.simpleName)
                        ).increment()
                    }
                    .onCallNotPermitted { event ->
                        meterRegistry.counter(
                            "resilience4j.circuitbreaker.calls",
                            tags.and("result", "rejected")
                        ).increment()
                    }
                    .onStateTransition { event ->
                        logger.warn(
                            "Circuit breaker state transition: {} -> {} (name: {})",
                            event.stateTransition.fromState,
                            event.stateTransition.toState,
                            circuitBreaker.name
                        )
                        meterRegistry.counter(
                            "resilience4j.circuitbreaker.transitions",
                            tags.and("from", event.stateTransition.fromState.name)
                                .and("to", event.stateTransition.toState.name)
                        ).increment()
                    }

                logger.info(
                    "Registered circuit breaker metrics: name={}, config={}",
                    circuitBreaker.name,
                    circuitBreaker.circuitBreakerConfig
                )
            }
        }
    }

    /**
     * Retry metrics integration.
     *
     * Registers metrics for:
     * - Retry attempt counts
     * - Success after retry counts
     * - Exhausted retry counts
     */
    @Configuration
    @ConditionalOnBean(MeterRegistry::class)
    class RetryMetricsConfiguration(
        private val meterRegistry: MeterRegistry,
        private val retryRegistry: RetryRegistry
    ) {
        @PostConstruct
        fun registerRetryMetrics() {
            retryRegistry.allRetries.forEach { retry ->
                val tags = Tags.of("name", retry.name)

                retry.eventPublisher
                    .onRetry { event ->
                        meterRegistry.counter(
                            "resilience4j.retry.calls",
                            tags.and("attempt", event.numberOfRetryAttempts.toString())
                                .and("result", "retry")
                        ).increment()
                    }
                    .onSuccess { event ->
                        meterRegistry.counter(
                            "resilience4j.retry.calls",
                            tags.and("result", "success")
                                .and("retries", event.numberOfRetryAttempts.toString())
                        ).increment()
                    }
                    .onError { event ->
                        meterRegistry.counter(
                            "resilience4j.retry.calls",
                            tags.and("result", "exhausted")
                                .and("exception", event.lastThrowable.javaClass.simpleName)
                        ).increment()

                        logger.warn(
                            "Retry exhausted: name={}, attempts={}, exception={}",
                            retry.name,
                            event.numberOfRetryAttempts,
                            event.lastThrowable.message
                        )
                    }

                logger.info(
                    "Registered retry metrics: name={}, config={}",
                    retry.name,
                    retry.retryConfig
                )
            }
        }
    }

    /**
     * Bulkhead metrics integration.
     *
     * Registers metrics for:
     * - Available concurrent calls
     * - Queue depth
     * - Rejected calls
     */
    @Configuration
    @ConditionalOnBean(MeterRegistry::class)
    class BulkheadMetricsConfiguration(
        private val meterRegistry: MeterRegistry,
        private val bulkheadRegistry: BulkheadRegistry
    ) {
        @PostConstruct
        fun registerBulkheadMetrics() {
            bulkheadRegistry.allBulkheads.forEach { bulkhead ->
                val tags = Tags.of("name", bulkhead.name)

                // Available calls gauge
                meterRegistry.gauge(
                    "resilience4j.bulkhead.available_concurrent_calls",
                    tags,
                    bulkhead
                ) { it.metrics.availableConcurrentCalls.toDouble() }

                // Event listeners
                bulkhead.eventPublisher
                    .onCallPermitted { event ->
                        meterRegistry.counter(
                            "resilience4j.bulkhead.calls",
                            tags.and("result", "permitted")
                        ).increment()
                    }
                    .onCallRejected { event ->
                        meterRegistry.counter(
                            "resilience4j.bulkhead.calls",
                            tags.and("result", "rejected")
                        ).increment()

                        logger.warn(
                            "Bulkhead rejected call: name={}, available={}",
                            bulkhead.name,
                            bulkhead.metrics.availableConcurrentCalls
                        )
                    }
                    .onCallFinished { event ->
                        meterRegistry.counter(
                            "resilience4j.bulkhead.calls",
                            tags.and("result", "finished")
                        ).increment()
                    }

                logger.info(
                    "Registered bulkhead metrics: name={}, config={}",
                    bulkhead.name,
                    bulkhead.bulkheadConfig
                )
            }
        }
    }

    @Bean
    fun resilientOperationExecutor(
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry,
        bulkheadRegistry: BulkheadRegistry
    ): ResilientOperationExecutor {
        return ResilientOperationExecutor(
            circuitBreakerRegistry,
            retryRegistry,
            bulkheadRegistry
        )
    }
}
