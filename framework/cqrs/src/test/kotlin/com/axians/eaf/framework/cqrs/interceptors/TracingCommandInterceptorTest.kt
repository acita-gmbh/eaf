package com.axians.eaf.framework.cqrs.interceptors

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.axonframework.commandhandling.GenericCommandMessage

/**
 * Unit tests for TracingCommandInterceptor validating trace context injection.
 *
 * Story 5.3: Subtask 5.1 - AC2 validation (command trace propagation)
 * Test Scenarios: 5.3-UNIT-002
 *
 * Validates:
 * - Active span context injected into command metadata
 * - trace_id, span_id, trace_flags propagated correctly
 * - No-op when no active span (graceful degradation)
 */
class TracingCommandInterceptorTest :
    BehaviorSpec({

        Given("TracingCommandInterceptor with active OpenTelemetry span") {
            val interceptor = TracingCommandInterceptor()

            When("command message processed within span context") {
                // Create real span using OpenTelemetry SDK
                val spanExporter =
                    io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
                        .create()
                val tracerProvider =
                    io.opentelemetry.sdk.trace.SdkTracerProvider
                        .builder()
                        .addSpanProcessor(
                            io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
                                .create(spanExporter),
                        ).build()
                val openTelemetry =
                    io.opentelemetry.sdk.OpenTelemetrySdk
                        .builder()
                        .setTracerProvider(tracerProvider)
                        .buildAndRegisterGlobal()

                try {
                    val tracer = openTelemetry.getTracer("test-tracer")

                    val testSpan = tracer.spanBuilder("test-parent-span").startSpan()
                    val expectedTraceId = testSpan.spanContext.traceId
                    val expectedSpanId = testSpan.spanContext.spanId

                    val command = GenericCommandMessage.asCommandMessage<String>("test-command")

                    // Execute interceptor within active span
                    val result =
                        testSpan.makeCurrent().use {
                            interceptor.handle(listOf(command)).apply(0, command)
                        }
                    testSpan.end()

                    Then("command metadata should contain trace context") {
                        result.metaData["trace_id"] shouldBe expectedTraceId
                        result.metaData["span_id"] shouldBe expectedSpanId
                        result.metaData["trace_flags"] shouldNotBe null
                    }
                } finally {
                    // Reset global OpenTelemetry to prevent cross-test contamination
                    tracerProvider.close()
                    io.opentelemetry.api.GlobalOpenTelemetry
                        .resetForTest()
                }
            }
        }

        Given("TracingCommandInterceptor with no active span") {
            val interceptor = TracingCommandInterceptor()

            When("command processed without span context") {
                val command = GenericCommandMessage.asCommandMessage<String>("test-command")
                val result = interceptor.handle(listOf(command)).apply(0, command)

                Then("command should be unchanged (graceful degradation)") {
                    result.metaData["trace_id"] shouldBe null
                    result.metaData["span_id"] shouldBe null
                }
            }
        }
    })
