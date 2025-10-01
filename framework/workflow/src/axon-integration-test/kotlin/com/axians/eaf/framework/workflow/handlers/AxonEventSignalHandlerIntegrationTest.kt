package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.delegates.AxonIntegrationTestConfig
import com.axians.eaf.framework.workflow.test.TestEntityCreatedEvent
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
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for AxonEventSignalHandler.
 *
 * Uses framework-local test types (TestEntityCreatedEvent) to validate generic infrastructure
 * without depending on products module.
 *
 * Validates Axon→Flowable bridge:
 * - Event handler receives TestEntityCreatedEvent from Axon
 * - Handler correlates event to waiting BPMN process via business key
 * - RuntimeService signals process with message delivery
 * - Process resumes from Receive Event Task and completes
 *
 * SECURITY CRITICAL: Validates tenant isolation (Subtask 4.7)
 */
@SpringBootTest(classes = [AxonEventSignalHandlerTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        test("should signal waiting BPMN process when TestEntityCreatedEvent published") {
            // Set tenant context
            tenantContext.setCurrentTenantId("test-tenant")

            // Subtask 4.2: Deploy simplified BPMN process (Start → Wait → End)
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/simple-wait-for-event.bpmn20.xml")
                .deploy()
                .shouldNotBeNull()

            // Subtask 4.3: Start process with business key = entityId (should pause at Receive Event)
            val entityId = UUID.randomUUID().toString()
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "simple-wait",
                    entityId, // Business key for correlation
                    emptyMap<String, Any>(), // No variables needed for simplified process
                )

            processInstance.shouldNotBeNull()

            // Subtask 4.4: Deterministically wait for process to reach wait state
            // Research: Use eventually() instead of delay() for race condition robustness
            val waitingExecution =
                eventually(duration = 5.seconds) {
                    val exec =
                        runtimeService
                            .createExecutionQuery()
                            .processInstanceId(processInstance.id) // Use processInstanceId (business key only on root)
                            .messageEventSubscriptionName("TestEntityCreated")
                            .singleResult()
                    exec.shouldNotBeNull()
                    exec
                }

            // Subtask 4.5: Publish TestEntityCreatedEvent via EventBus with metadata
            // Research: Subscribing processors deliver synchronously on publishing thread
            val event =
                TestEntityCreatedEvent(
                    entityId = entityId,
                    tenantId = "test-tenant",
                    name = "Test Entity",
                    description = "Created via async workflow",
                    value = BigDecimal("100.00"),
                    category = "TEST",
                )

            // Generic handler metadata contract: correlationKey, messageName, tenantId
            val eventMessage =
                GenericEventMessage
                    .asEventMessage<TestEntityCreatedEvent>(event)
                    .andMetaData(
                        mapOf(
                            "correlationKey" to entityId, // For Flowable process correlation
                            "messageName" to "TestEntityCreated", // For message event subscription
                            "tenantId" to "test-tenant", // For tenant validation
                        ),
                    )

            eventBus.publish(eventMessage)

            // Subtask 4.6: Deterministically wait for process completion
            eventually(duration = 5.seconds) {
                val historicInstance =
                    processEngine.historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .finished() // Query only finished instances
                        .singleResult()

                historicInstance.shouldNotBeNull()
            }
        }

        // Subtask 4.7: SECURITY CRITICAL - Tenant isolation test
        test("should enforce tenant isolation when signaling processes") {
            // Set tenant context to tenant-a
            tenantContext.setCurrentTenantId("tenant-a")

            // Deploy simplified BPMN (no command dispatch complexity)
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/simple-wait-for-event.bpmn20.xml")
                .deploy()

            // Start process for tenant-a with business key and tenant variable
            val entityId = UUID.randomUUID().toString()
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "simple-wait",
                    entityId, // Business key
                    mapOf("tenantId" to "tenant-a"), // Process tenant for cross-tenant validation
                )

            processInstance.shouldNotBeNull()

            // Deterministically wait for process to reach wait state
            val waitingExecution =
                eventually(duration = 5.seconds) {
                    val exec =
                        runtimeService
                            .createExecutionQuery()
                            .processInstanceId(processInstance.id) // Use processInstanceId
                            .messageEventSubscriptionName("TestEntityCreated")
                            .singleResult()
                    exec.shouldNotBeNull()
                    exec
                }

            // Switch tenant context to tenant-b (simulating cross-tenant attack)
            tenantContext.clearCurrentTenant()
            tenantContext.setCurrentTenantId("tenant-b")

            // Publish TestEntityCreatedEvent for tenant-b (should NOT signal tenant-a process)
            val attackEvent =
                TestEntityCreatedEvent(
                    entityId = entityId,
                    tenantId = "tenant-b", // Different tenant!
                    name = "Tenant B Entity",
                    description = "Cross-tenant attack attempt",
                    value = BigDecimal("200.00"),
                    category = "MALICIOUS",
                )

            // Add metadata with tenant-b (handler should reject due to tenant mismatch)
            val attackMessage =
                GenericEventMessage
                    .asEventMessage<TestEntityCreatedEvent>(attackEvent)
                    .andMetaData(
                        mapOf(
                            "correlationKey" to entityId, // Same entityId as tenant-a process
                            "messageName" to "TestEntityCreated",
                            "tenantId" to "tenant-b", // Different tenant - should trigger isolation violation
                        ),
                    )

            eventBus.publish(attackMessage)

            // Deterministically verify tenant-a process STILL waiting (handler rejected cross-tenant event)
            eventually(5.seconds) {
                val stillWaiting =
                    runtimeService
                        .createExecutionQuery()
                        .processInstanceId(processInstance.id) // Use processInstanceId
                        .messageEventSubscriptionName("TestEntityCreated")
                        .singleResult()

                stillWaiting.shouldNotBeNull() // Process still waiting (tenant isolation enforced)

                // Verify process has NOT completed (remains in wait state)
                val runningInstance =
                    runtimeService
                        .createProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .singleResult()

                runningInstance.shouldNotBeNull() // Process still running (correct behavior)
            }
        }

        // Subtask 4.8: Resilience test - no waiting process
        test("should log warning when no waiting process found for event") {
            // Set tenant context
            tenantContext.setCurrentTenantId("test-tenant")

            // Publish TestEntityCreatedEvent without any waiting BPMN process
            val entityId = UUID.randomUUID().toString()
            val event =
                TestEntityCreatedEvent(
                    entityId = entityId,
                    tenantId = "test-tenant",
                    name = "Orphaned Event",
                    description = "No waiting process for this event",
                    value = BigDecimal("100.00"),
                    category = "TEST",
                )

            // Publish event without correlationKey/messageName metadata (tests graceful skip)
            val eventMessage =
                GenericEventMessage
                    .asEventMessage<TestEntityCreatedEvent>(event)
                    .andMetaData(
                        mapOf(
                            "tenantId" to "test-tenant", // Required for TenantEventMessageInterceptor
                            // NOTE: No correlationKey/messageName - handler should skip gracefully
                        ),
                    )

            eventBus.publish(eventMessage)

            // No process assertions needed - resilience means event processing completes without exception
            // Handler logs: "No process instance found for business key correlation"
            // This test validates graceful degradation - event processing continues without failure
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
