@file:Suppress("TooGenericExceptionCaught", "SwallowedException") // Infrastructure interceptor - observability pattern

package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.core.resilience.dlq.DeadLetterQueueService
import com.axians.eaf.framework.core.resilience.dlq.OperationType
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.queryhandling.QueryMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

/**
 * Axon interceptor that captures failed commands/events/queries and stores them in the DLQ.
 *
 * This interceptor is the last line of defense - it catches exceptions that have already
 * passed through retry and circuit breaker patterns.
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * Reference: docs/security/exception-handling-improvements.md
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnBean(DeadLetterQueueService::class)
class DeadLetterQueueInterceptor(
    private val dlqService: DeadLetterQueueService,
    // Note: ObservationRegistry will be added in future story for tracing
) : MessageHandlerInterceptor<CommandMessage<*>> {
    private val logger = LoggerFactory.getLogger(DeadLetterQueueInterceptor::class.java)

    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any? =
        try {
            interceptorChain.proceed()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception, // LEGITIMATE: DLQ captures all failures after retries exhausted
        ) {
            // Store in DLQ
            captureFailure<Any>(unitOfWork.message, ex, OperationType.COMMAND)

            // Re-throw to maintain error propagation
            throw ex
        }

    /**
     * Capture event processing failures.
     */
    fun <T> handleEvent(
        unitOfWork: UnitOfWork<out EventMessage<T>>,
        interceptorChain: InterceptorChain,
    ): Any? =
        try {
            interceptorChain.proceed()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception, // LEGITIMATE: DLQ captures all failures after retries exhausted
        ) {
            // Store in DLQ
            captureFailure<Any>(unitOfWork.message, ex, OperationType.EVENT)

            // Re-throw to maintain error propagation
            throw ex
        }

    /**
     * Capture query processing failures.
     */
    fun <T, R> handleQuery(
        unitOfWork: UnitOfWork<out QueryMessage<T, R>>,
        interceptorChain: InterceptorChain,
    ): Any? =
        try {
            interceptorChain.proceed()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception, // LEGITIMATE: DLQ captures all failures after retries exhausted
        ) {
            // Store in DLQ
            captureFailure<Any>(unitOfWork.message, ex, OperationType.QUERY)

            // Re-throw to maintain error propagation
            throw ex
        }

    private fun <T> captureFailure(
        message: org.axonframework.messaging.Message<*>,
        exception: Exception,
        operationType: OperationType,
    ) {
        try {
            // Extract context
            // TenantContext will be added in Story 4.1
            val tenantId: String? = null

            // Trace ID will be added when ObservationRegistry is integrated
            val traceId: String? = null

            // Extract retry count from metadata if available
            val retryCount = message.metaData["retryCount"] as? Int ?: 0

            // Store in DLQ
            dlqService.storeFailed(
                operationType = operationType,
                payload = message.payload,
                exception = exception,
                tenantId = tenantId,
                traceId = traceId,
                retryCount = retryCount,
                metadata =
                    mapOf(
                        "messageId" to message.identifier,
                        "messageType" to message.payloadType.simpleName,
                    ),
            )

            logger.error(
                "Captured failed operation in DLQ: type={}, payloadType={}, exception={}",
                operationType,
                message.payloadType.simpleName,
                exception::class.simpleName,
            )
        } catch (dlqEx: Exception) {
            // Never let DLQ capture fail the original operation
            logger.error(
                "Failed to store operation in DLQ: type={}, payloadType={}",
                operationType,
                message.payloadType.simpleName,
                dlqEx,
            )
        }
    }
}
