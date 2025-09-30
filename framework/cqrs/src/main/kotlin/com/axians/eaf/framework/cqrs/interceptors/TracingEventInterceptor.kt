package com.axians.eaf.framework.cqrs.interceptors

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Propagates OpenTelemetry trace context through Axon event metadata and restores
 * context in async event handlers.
 *
 * Ensures parent-child span relationships are maintained across async event processing
 * boundaries, enabling end-to-end distributed tracing.
 *
 * Story 5.3: Implement OpenTelemetry (Tracing) Configuration
 * Risk Mitigation: TECH-001 (async trace context propagation - HIGHEST PRIORITY)
 */
@Component
@ConditionalOnProperty(
    prefix = "management.tracing",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class TracingEventInterceptor(
    private val tracer: Tracer,
) : MessageHandlerInterceptor<EventMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain,
    ): Any {
        val message = unitOfWork.message
        val traceId = message.metaData["trace_id"] as? String
        val spanId = message.metaData["span_id"] as? String

        // Restore trace context for async event handlers
        return if (traceId != null && spanId != null) {
            val spanContext =
                SpanContext.create(
                    traceId,
                    spanId,
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                )
            val remoteContext = Context.current().with(Span.wrap(spanContext))
            val span =
                tracer
                    .spanBuilder("event-handler")
                    .setParent(remoteContext)
                    .setAttribute("event.type", message.payloadType.simpleName)
                    .startSpan()

            try {
                span.makeCurrent().use {
                    interceptorChain.proceed()
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                ex: Exception,
            ) {
                // Infrastructure interceptor pattern: record span error then re-throw
                span.recordException(ex)
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
                throw ex
            } finally {
                span.end()
            }
        } else {
            // No trace context available - proceed without tracing
            interceptorChain.proceed()
        }
    }
}
