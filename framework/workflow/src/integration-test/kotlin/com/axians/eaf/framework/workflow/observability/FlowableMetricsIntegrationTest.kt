package com.axians.eaf.framework.workflow.observability

import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.TimeUnit

/**
 * Integration tests for FlowableMetrics component.
 *
 * Tests Prometheus metrics recording using real Spring Boot context and MeterRegistry.
 * **Constitutional TDD**: Integration-first approach with real infrastructure.
 *
 * Story 6.4 (Task 1) - Flowable Observability
 */
@SpringBootTest
@ActiveProfiles("test")
class FlowableMetricsIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var flowableMetrics: FlowableMetrics

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    init {
        extension(SpringExtension())

        test("recordProcessDuration should record timer metric with process_key and status tags") {
            // Given
            val processInstanceId = "proc-123"
            val durationMs = 1500L
            val processKey = "hello-world-ansible"

            // When
            flowableMetrics.recordProcessDuration(processInstanceId, durationMs, processKey)

            // Then
            val timer =
                meterRegistry
                    .find("flowable.process.duration")
                    .tag("process_key", processKey)
                    .tag("status", "completed")
                    .timer()

            timer.shouldNotBeNull()
            timer.count() shouldBe 1
            timer.totalTime(TimeUnit.MILLISECONDS) shouldBeGreaterThan 0.0
        }

        test("recordBpmnError should increment counter with error_code and process_key tags") {
            // Given
            val errorCode = "ANSIBLE_FAILED"
            val processKey = "ansible-deployment"

            // When
            flowableMetrics.recordBpmnError(errorCode, processKey)
            flowableMetrics.recordBpmnError(errorCode, processKey)

            // Then
            val counter =
                meterRegistry
                    .find("flowable.process.errors")
                    .tag("error_code", errorCode)
                    .tag("process_key", processKey)
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeGreaterThan 0.0 // At least 2
        }

        test("recordSignalDelivery should increment counter with signal_name and success status") {
            // Given
            val signalName = "OrderCreated"

            // When
            flowableMetrics.recordSignalDelivery(signalName, delivered = true)

            // Then
            val counter =
                meterRegistry
                    .find("flowable.signal.deliveries")
                    .tag("signal_name", signalName)
                    .tag("status", "success")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeGreaterThan 0.0
        }

        test("recordSignalDelivery should increment counter with signal_name and failed status") {
            // Given
            val signalName = "PaymentProcessed"

            // When
            flowableMetrics.recordSignalDelivery(signalName, delivered = false)

            // Then
            val counter =
                meterRegistry
                    .find("flowable.signal.deliveries")
                    .tag("signal_name", signalName)
                    .tag("status", "failed")
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeGreaterThan 0.0
        }

        test("recordBpmnError should handle tenant isolation violation error code") {
            // Given - This is the critical security metric for rollback triggers
            val errorCode = "TENANT_ISOLATION_VIOLATION"
            val processKey = "axon-event-signal"

            // When
            flowableMetrics.recordBpmnError(errorCode, processKey)

            // Then
            val counter =
                meterRegistry
                    .find("flowable.process.errors")
                    .tag("error_code", errorCode)
                    .tag("process_key", processKey)
                    .counter()

            counter.shouldNotBeNull()
            counter.count() shouldBeGreaterThan 0.0
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
        }
    }
}
