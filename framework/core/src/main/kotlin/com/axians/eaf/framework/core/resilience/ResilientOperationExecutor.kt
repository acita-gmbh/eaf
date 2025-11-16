package com.axians.eaf.framework.core.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.kotlin.bulkhead.executeSuspendFunction
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Executor for resilient operations with circuit breaker, retry, and bulkhead patterns.
 *
 * Bean lifecycle managed by [ResilienceAutoConfiguration].
 *
 * Usage:
 * ```kotlin
 * val result = resilientOperationExecutor.execute(
 *     circuitBreakerName = "externalApi",
 *     retryName = "api",
 *     bulkheadName = "externalApi"
 * ) {
 *     externalApiClient.fetchData()
 * }
 * ```
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
class ResilientOperationExecutor(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry,
    private val bulkheadRegistry: BulkheadRegistry,
) {
    private val logger = LoggerFactory.getLogger(ResilientOperationExecutor::class.java)

    /**
     * Execute operation with circuit breaker, retry, and bulkhead protection.
     *
     * @param circuitBreakerName Name of circuit breaker configuration
     * @param retryName Name of retry configuration (optional)
     * @param bulkheadName Name of bulkhead configuration (optional)
     * @param operation Operation to execute
     * @return Result of operation
     * @throws CallNotPermittedException if circuit breaker is OPEN
     * @throws BulkheadFullException if bulkhead is full
     * @throws Exception if operation fails after all retries
     */
    fun <T> execute(
        circuitBreakerName: String,
        retryName: String? = null,
        bulkheadName: String? = null,
        operation: () -> T,
    ): T {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)

        return try {
            when {
                bulkheadName != null && retryName != null -> {
                    val bulkhead = bulkheadRegistry.bulkhead(bulkheadName)
                    val retry = retryRegistry.retry(retryName)
                    executeWithAllPatterns(circuitBreaker, retry, bulkhead, operation)
                }
                retryName != null -> {
                    val retry = retryRegistry.retry(retryName)
                    executeWithRetry(circuitBreaker, retry, operation)
                }
                bulkheadName != null -> {
                    val bulkhead = bulkheadRegistry.bulkhead(bulkheadName)
                    executeWithBulkhead(circuitBreaker, bulkhead, operation)
                }
                else -> {
                    circuitBreaker.executeSupplier(operation)
                }
            }
        } catch (ex: CallNotPermittedException) {
            logger.error(
                "Circuit breaker is OPEN - operation not permitted: name={}",
                circuitBreakerName,
            )
            throw ResilientOperationException(
                "Service temporarily unavailable - circuit breaker is OPEN",
                ex,
                circuitBreakerName,
            )
        } catch (ex: BulkheadFullException) {
            logger.warn(
                "Bulkhead is full - operation rejected: name={}",
                bulkheadName,
            )
            throw ResilientOperationException(
                "Service capacity exceeded - too many concurrent requests",
                ex,
                bulkheadName ?: "unknown",
            )
        }
    }

    /**
     * Execute suspend operation with circuit breaker, retry, and bulkhead protection.
     *
     * Kotlin coroutine-friendly version of [execute].
     */
    suspend fun <T> executeSuspend(
        circuitBreakerName: String,
        retryName: String? = null,
        bulkheadName: String? = null,
        operation: suspend () -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)

            try {
                when {
                    bulkheadName != null && retryName != null -> {
                        val bulkhead = bulkheadRegistry.bulkhead(bulkheadName)
                        val retry = retryRegistry.retry(retryName)
                        executeSuspendWithAllPatterns(circuitBreaker, retry, bulkhead, operation)
                    }
                    retryName != null -> {
                        val retry = retryRegistry.retry(retryName)
                        executeSuspendWithRetry(circuitBreaker, retry, operation)
                    }
                    bulkheadName != null -> {
                        val bulkhead = bulkheadRegistry.bulkhead(bulkheadName)
                        executeSuspendWithBulkhead(circuitBreaker, bulkhead, operation)
                    }
                    else -> {
                        circuitBreaker.executeSuspendFunction(operation)
                    }
                }
            } catch (ex: CallNotPermittedException) {
                logger.error(
                    "Circuit breaker is OPEN - operation not permitted: name={}",
                    circuitBreakerName,
                )
                throw ResilientOperationException(
                    "Service temporarily unavailable - circuit breaker is OPEN",
                    ex,
                    circuitBreakerName,
                )
            } catch (ex: BulkheadFullException) {
                logger.warn(
                    "Bulkhead is full - operation rejected: name={}",
                    bulkheadName,
                )
                throw ResilientOperationException(
                    "Service capacity exceeded - too many concurrent requests",
                    ex,
                    bulkheadName ?: "unknown",
                )
            }
        }

    private fun <T> executeWithRetry(
        circuitBreaker: CircuitBreaker,
        retry: Retry,
        operation: () -> T,
    ): T =
        circuitBreaker.executeSupplier {
            retry.executeSupplier(operation)
        }

    private fun <T> executeWithBulkhead(
        circuitBreaker: CircuitBreaker,
        bulkhead: Bulkhead,
        operation: () -> T,
    ): T =
        circuitBreaker.executeSupplier {
            bulkhead.executeSupplier(operation)
        }

    private fun <T> executeWithAllPatterns(
        circuitBreaker: CircuitBreaker,
        retry: Retry,
        bulkhead: Bulkhead,
        operation: () -> T,
    ): T =
        circuitBreaker.executeSupplier {
            retry.executeSupplier {
                bulkhead.executeSupplier(operation)
            }
        }

    private suspend fun <T> executeSuspendWithRetry(
        circuitBreaker: CircuitBreaker,
        retry: Retry,
        operation: suspend () -> T,
    ): T =
        circuitBreaker.executeSuspendFunction {
            retry.executeSuspendFunction(operation)
        }

    private suspend fun <T> executeSuspendWithBulkhead(
        circuitBreaker: CircuitBreaker,
        bulkhead: Bulkhead,
        operation: suspend () -> T,
    ): T =
        circuitBreaker.executeSuspendFunction {
            bulkhead.executeSuspendFunction(operation)
        }

    private suspend fun <T> executeSuspendWithAllPatterns(
        circuitBreaker: CircuitBreaker,
        retry: Retry,
        bulkhead: Bulkhead,
        operation: suspend () -> T,
    ): T =
        circuitBreaker.executeSuspendFunction {
            retry.executeSuspendFunction {
                bulkhead.executeSuspendFunction(operation)
            }
        }
}

/**
 * Exception thrown when resilient operation fails.
 *
 * @property message User-friendly error message
 * @property cause Original exception
 * @property patternName Name of the resilience pattern that triggered the failure
 */
class ResilientOperationException(
    message: String,
    cause: Throwable,
    val patternName: String,
) : RuntimeException(message, cause)
