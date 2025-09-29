package com.axians.eaf.framework.cqrs.interceptors

import com.axians.eaf.framework.observability.metrics.CustomMetrics
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Message handler interceptor that records Micrometer metrics for command processing.
 */
@Component
class CommandMetricsInterceptor(
    private val customMetrics: CustomMetrics,
) : MessageHandlerInterceptor<CommandMessage<*>> {
    companion object {
        private val logger = LoggerFactory.getLogger(CommandMetricsInterceptor::class.java)
    }

    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any {
        val command = unitOfWork.message
        val start = Instant.now()

        return try {
            val result = interceptorChain.proceed()
            record(command, start, success = true)
            result
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            // Legitimate use of generic exception in infrastructure interceptor:
            // We record metrics for ANY exception type then re-throw immediately
            record(command, start, success = false)
            throw ex
        }
    }

    private fun record(
        command: CommandMessage<*>,
        start: Instant,
        success: Boolean,
    ) {
        val duration = Duration.between(start, Instant.now())
        val commandType = command.commandName
        logger.trace("Recording metrics for command {} (success={})", commandType, success)
        customMetrics.recordCommand(commandType, duration, success)
    }
}
