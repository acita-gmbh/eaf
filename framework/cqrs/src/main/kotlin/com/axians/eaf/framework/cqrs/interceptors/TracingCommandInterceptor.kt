package com.axians.eaf.framework.cqrs.interceptors

import io.opentelemetry.api.trace.Span
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.messaging.MessageDispatchInterceptor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.function.BiFunction

/**
 * Injects OpenTelemetry trace context into Axon command metadata before dispatch.
 *
 * Enables distributed tracing across command boundaries by propagating trace_id,
 * span_id, and trace_flags through Axon's metadata mechanism.
 *
 * Story 5.3: Implement OpenTelemetry (Tracing) Configuration
 * Risk Mitigation: TECH-001 (trace context loss prevention)
 */
@Component
@ConditionalOnProperty(
    prefix = "management.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TracingCommandInterceptor : MessageDispatchInterceptor<CommandMessage<*>> {
    override fun handle(messages: List<CommandMessage<*>>): BiFunction<Int, CommandMessage<*>, CommandMessage<*>> =
        BiFunction { _, message ->
            val span = Span.current()
            if (span.isRecording) {
                val traceContext =
                    mapOf(
                        "trace_id" to span.spanContext.traceId,
                        "span_id" to span.spanContext.spanId,
                        "trace_flags" to span.spanContext.traceFlags.asHex(),
                    )
                message.andMetaData(traceContext)
            } else {
                message
            }
        }
}
