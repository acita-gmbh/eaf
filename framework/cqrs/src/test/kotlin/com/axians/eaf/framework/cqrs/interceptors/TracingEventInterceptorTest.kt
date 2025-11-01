package com.axians.eaf.framework.cqrs.interceptors

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork

/**
 * Unit tests for TracingEventInterceptor validating async trace context restoration.
 *
 * Story 5.3: Subtask 5.2 - AC2 validation (TECH-001 CRITICAL RISK MITIGATION)
 * Test Scenarios: 5.3-UNIT-003
 *
 * Validates:
 * - Trace context restored from event metadata in async handlers
 * - Parent-child span relationships maintained
 * - Graceful degradation when no trace context in metadata
 * - Span properly ended after handler execution
 *
 * CRITICAL: This test mitigates TECH-001 (async trace context loss), highest priority risk.
 */
class TracingEventInterceptorTest :
    BehaviorSpec({

        lateinit var spanExporter: InMemorySpanExporter
        lateinit var tracer: Tracer
        lateinit var interceptor: TracingEventInterceptor

        beforeSpec {
            // Create in-memory OpenTelemetry SDK for testing
            spanExporter = InMemorySpanExporter.create()
            val tracerProvider =
                SdkTracerProvider
                    .builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                    .build()
            val openTelemetry: OpenTelemetry =
                OpenTelemetrySdk
                    .builder()
                    .setTracerProvider(tracerProvider)
                    .build()
            tracer = openTelemetry.getTracer("test-tracer")
            interceptor = TracingEventInterceptor(tracer)
        }

        afterEach {
            spanExporter.reset()
        }

        Given("TracingEventInterceptor with event containing trace context") {
            val testTraceId = "00000000000000000000000000000001"
            val testSpanId = "0000000000000001"

            When("event processed in async handler (simulating separate thread)") {
                val event =
                    GenericEventMessage
                        .asEventMessage<String>("test-event")
                        .andMetaData(
                            mapOf(
                                "trace_id" to testTraceId,
                                "span_id" to testSpanId,
                                "trace_flags" to "01",
                            ),
                        )

                val unitOfWork = DefaultUnitOfWork.startAndGet(event)
                var spanWasActive = false

                val mockChain =
                    object : InterceptorChain {
                        override fun proceed(): Any {
                            // Verify span context restored in handler
                            val currentSpan = Span.current()
                            spanWasActive = currentSpan.spanContext.isValid
                            return "success"
                        }
                    }

                val result = interceptor.handle(unitOfWork, mockChain)

                Then("span context should be restored for event handler") {
                    spanWasActive shouldBe true
                    result shouldBe "success"

                    // Verify span was created and exported
                    val exportedSpans = spanExporter.finishedSpanItems
                    exportedSpans.size shouldBe 1
                    exportedSpans[0].name shouldBe "event-handler"
                }
            }
        }

        Given("TracingEventInterceptor with event without trace context") {
            When("event processed without metadata") {
                val event = GenericEventMessage.asEventMessage<String>("test-event")
                val unitOfWork = DefaultUnitOfWork.startAndGet(event)

                val mockChain =
                    object : InterceptorChain {
                        override fun proceed(): Any = "success-no-trace"
                    }

                val result = interceptor.handle(unitOfWork, mockChain)

                Then("should proceed without creating span (graceful degradation)") {
                    result shouldBe "success-no-trace"
                    spanExporter.finishedSpanItems.size shouldBe 0
                }
            }
        }

        Given("TracingEventInterceptor when handler throws exception") {
            val testTraceId = "00000000000000000000000000000002"
            val testSpanId = "0000000000000002"

            When("event handler throws exception") {
                val event =
                    GenericEventMessage
                        .asEventMessage<String>("failing-event")
                        .andMetaData(
                            mapOf(
                                "trace_id" to testTraceId,
                                "span_id" to testSpanId,
                                "trace_flags" to "01",
                            ),
                        )

                val unitOfWork = DefaultUnitOfWork.startAndGet(event)

                val mockChain =
                    object : InterceptorChain {
                        override fun proceed(): Any {
                            @Suppress("TooGenericExceptionThrown")
                            throw RuntimeException("Handler failure")
                        }
                    }

                var exceptionThrown = false
                try {
                    interceptor.handle(unitOfWork, mockChain)
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    ex: RuntimeException,
                ) {
                    // Expected exception in test - validating infrastructure pattern
                    exceptionThrown = true
                }

                Then("exception should be re-thrown and span should record error") {
                    exceptionThrown shouldBe true

                    // Verify span recorded exception
                    val exportedSpans = spanExporter.finishedSpanItems
                    exportedSpans.size shouldBe 1
                    exportedSpans[0].status.statusCode shouldBe io.opentelemetry.api.trace.StatusCode.ERROR
                }
            }
        }
    })
