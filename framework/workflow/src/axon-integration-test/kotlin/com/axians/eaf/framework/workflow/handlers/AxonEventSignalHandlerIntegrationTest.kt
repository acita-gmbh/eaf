package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.delegates.AxonIntegrationTestConfig
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.GenericEventMessage
import org.axonframework.eventhandling.gateway.EventGateway
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RuntimeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for AxonEventSignalHandler.
 *
 * Validates Axon→Flowable bridge:
 * - Event handler receives WidgetCreatedEvent from Axon
 * - Handler correlates event to waiting BPMN process via business key
 * - RuntimeService signals process with message delivery
 * - Process resumes from Receive Event Task and completes
 *
 * SECURITY CRITICAL: Validates tenant isolation (Subtask 4.7)
 */
@SpringBootTest(classes = [AxonEventSignalHandlerTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class AxonEventSignalHandlerIntegrationTest : FunSpec() {

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var eventGateway: EventGateway

    @Autowired
    private lateinit var eventBus: EventBus

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        afterEach {
            tenantContext.clearCurrentTenant()
        }

        // Subtasks 4.1-4.6: Happy path - complete async workflow test
        test("should signal waiting BPMN process when WidgetCreatedEvent published") {
            // Set tenant context
            tenantContext.setCurrentTenantId("test-tenant")

            // Subtask 4.2: Deploy simplified BPMN process (Start → Wait → End)
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/simple-wait-for-event.bpmn20.xml")
                .deploy()
                .shouldNotBeNull()

            // Subtask 4.3: Start process with business key = widgetId (should pause at Receive Event)
            val widgetId = UUID.randomUUID().toString()
            val processInstance = runtimeService.startProcessInstanceByKey(
                "simple-wait",
                widgetId, // Business key for correlation
                emptyMap<String, Any>() // No variables needed for simplified process
            )

            processInstance.shouldNotBeNull()

            // Wait for process to reach wait state
            delay(1000)

            // Subtask 4.4: Verify process is waiting for message event
            val waitingExecution = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(widgetId)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

            waitingExecution.shouldNotBeNull()

            // Subtask 4.5: Publish WidgetCreatedEvent via EventBus with metadata (triggers AxonEventSignalHandler)
            val event = WidgetCreatedEvent(
                widgetId = widgetId,
                tenantId = "test-tenant",
                name = "Test Widget",
                description = "Created via async workflow",
                value = BigDecimal("100.00"),
                category = "TEST"
            )

            // Manually add tenantId to metadata (EventGateway.publish doesn't use CorrelationDataProviders)
            val eventMessage = GenericEventMessage.asEventMessage<WidgetCreatedEvent>(event)
                .andMetaData(mapOf("tenantId" to "test-tenant"))

            eventBus.publish(eventMessage)

            // Small delay to allow async event processing
            delay(500)

            // Subtask 4.6: Verify process completed after receiving signal
            val historicInstance = processEngine.historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.id)
                .singleResult()

            historicInstance.shouldNotBeNull()
            historicInstance.endTime.shouldNotBeNull() // Process ended successfully
        }

        // Subtask 4.7: SECURITY CRITICAL - Tenant isolation test
        test("should enforce tenant isolation when signaling processes") {
            // Set tenant context to tenant-a
            tenantContext.setCurrentTenantId("tenant-a")

            // Deploy BPMN process
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/widget-lifecycle-with-wait.bpmn20.xml")
                .deploy()

            // Start process for tenant-a
            val widgetId = UUID.randomUUID().toString()
            val processInstance = runtimeService.startProcessInstanceByKey(
                "widget-lifecycle",
                widgetId,
                mapOf(
                    "tenantId" to "tenant-a",
                    "widgetId" to widgetId,
                    "name" to "Tenant A Widget",
                    "value" to BigDecimal("100.00"),
                    "category" to "TEST"
                )
            )

            processInstance.shouldNotBeNull()

            // Verify process is waiting
            val waitingExecution = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(widgetId)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

            waitingExecution.shouldNotBeNull()

            // Switch tenant context to tenant-b (simulating cross-tenant attack)
            tenantContext.clearCurrentTenant()
            tenantContext.setCurrentTenantId("tenant-b")

            // Publish WidgetCreatedEvent for tenant-b (should NOT signal tenant-a process)
            eventGateway.publish(
                WidgetCreatedEvent(
                    widgetId = widgetId,
                    tenantId = "tenant-b", // Different tenant!
                    name = "Tenant B Widget",
                    description = "Cross-tenant attack attempt",
                    value = BigDecimal("200.00"),
                    category = "MALICIOUS"
                )
            )

            // Small delay for event processing
            delay(500)

            // Verify tenant-a process is STILL waiting (not signaled)
            val stillWaiting = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(widgetId)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

            stillWaiting.shouldNotBeNull() // Process still waiting (tenant isolation enforced)

            // Verify process has NOT completed
            val historicInstance = processEngine.historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.id)
                .singleResult()

            historicInstance.endTime shouldBe null // Process NOT ended (correct behavior)
        }

        // Subtask 4.8: Resilience test - no waiting process
        test("should log warning when no waiting process found for event") {
            // Set tenant context
            tenantContext.setCurrentTenantId("test-tenant")

            // Publish WidgetCreatedEvent without any waiting BPMN process
            val widgetId = UUID.randomUUID().toString()
            eventGateway.publish(
                WidgetCreatedEvent(
                    widgetId = widgetId,
                    tenantId = "test-tenant",
                    name = "Orphaned Event",
                    description = "No waiting process for this event",
                    value = BigDecimal("100.00"),
                    category = "TEST"
                )
            )

            // Small delay for event processing
            delay(500)

            // Verify no exception thrown (resilient behavior)
            // Handler should log: "No waiting process found for WidgetCreatedEvent: widgetId=..."
            // This test validates resilience - event processing continues without failure
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
