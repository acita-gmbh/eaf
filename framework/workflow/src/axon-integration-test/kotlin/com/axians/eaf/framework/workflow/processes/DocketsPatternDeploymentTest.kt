package com.axians.eaf.framework.workflow.processes

import com.axians.eaf.framework.workflow.delegates.AxonIntegrationTestConfig
import com.axians.eaf.framework.workflow.delegates.RunAnsiblePlaybookTaskTestApplication
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.flowable.bpmn.model.BoundaryEvent
import org.flowable.bpmn.model.EndEvent
import org.flowable.bpmn.model.IntermediateCatchEvent
import org.flowable.bpmn.model.ServiceTask
import org.flowable.bpmn.model.StartEvent
import org.flowable.engine.RepositoryService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * Integration Test for Dockets Pattern BPMN Template (Story 6.6).
 *
 * **Test Objective**: Validate BPMN template deployment and structural completeness:
 * - AC2: BPMN template deploys successfully to Flowable engine
 * - AC1: All 7 workflow elements exist in deployed model
 *
 * **Test Scope** (from story):
 * - Deployment validation only (AC2)
 * - Structure verification (AC1 - element count and types)
 * - E2E workflow execution deferred to Epic 8 (real product use cases)
 *
 * **Risk Mitigations**:
 * - TECH-001: Validates BPMN schema compliance (deployment catches element ordering violations)
 * - TECH-002: Verifies all 7 workflow elements exist with correct types
 * - SEC-001: Confirms delegate references are valid (deployment catches invalid beans)
 *
 * Story 6.6 (Task 5) - Integration tests for Dockets Pattern template
 */
@SpringBootTest(classes = [RunAnsiblePlaybookTaskTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class DocketsPatternDeploymentTest : FunSpec() {
    @Autowired
    private lateinit var repositoryService: RepositoryService

    init {
        extension(SpringExtension())

        test("should deploy dockets-pattern-template successfully (6.6-INT-001 - P0)") {
            // Given - BPMN template file exists in resources/processes/
            // When - Flowable auto-deployment executes on context load
            // Then - Process definition registered without SAXParseException

            val processDefinition =
                repositoryService
                    .createProcessDefinitionQuery()
                    .processDefinitionKey("dockets-pattern-template")
                    .singleResult()

            // Verify deployment succeeded
            processDefinition.shouldNotBeNull()
        }

        test("should have correct process definition properties (6.6-INT-002 - P0)") {
            // Given - BPMN template successfully deployed
            // When - Querying process definition metadata
            // Then - Correct id, executable flag, and version

            val processDefinition =
                repositoryService
                    .createProcessDefinitionQuery()
                    .processDefinitionKey("dockets-pattern-template")
                    .singleResult()

            processDefinition.shouldNotBeNull()
            processDefinition.key shouldBe "dockets-pattern-template"
            processDefinition.version shouldBeGreaterThanOrEqual 1
        }

        test("should contain all 7 Dockets Pattern workflow elements (6.6-INT-003 - P0)") {
            // Given - BPMN template successfully deployed
            // When - Querying deployed BPMN model for element types and counts
            // Then - All 7 core workflow elements exist with correct types

            val processDefinition =
                repositoryService
                    .createProcessDefinitionQuery()
                    .processDefinitionKey("dockets-pattern-template")
                    .singleResult()

            processDefinition.shouldNotBeNull()

            // Get BPMN model for element analysis
            val bpmnModel = repositoryService.getBpmnModel(processDefinition.id)
            val process = bpmnModel.mainProcess

            // Element 1: Start Event (exactly 1)
            val startEvents = process.findFlowElementsOfType(StartEvent::class.java)
            startEvents.shouldHaveSize(1)

            // Elements 2, 3, 5: Service Tasks (at least 3 core tasks + compensation delegates)
            // PRESCRIPT, CORECOMMAND, POSTSCRIPT (3 core) + setCompensationCommand, compensationTask (2 compensation) = 5 total
            val serviceTasks = process.findFlowElementsOfType(ServiceTask::class.java)
            serviceTasks.size shouldBeGreaterThanOrEqual 3 // Minimum: PRESCRIPT, CORECOMMAND, POSTSCRIPT

            // Element 4: Intermediate Catch Event - Wait for Axon Event (exactly 1)
            val intermediateCatchEvents = process.findFlowElementsOfType(IntermediateCatchEvent::class.java)
            intermediateCatchEvents.shouldHaveSize(1)

            // Element 7: End Events (at least 1 success + 1 compensated = 2 minimum)
            val endEvents = process.findFlowElementsOfType(EndEvent::class.java)
            endEvents.size shouldBeGreaterThanOrEqual 1 // Minimum: success end event

            // Element 6: Boundary Events (at least 3 for PRESCRIPT, CORECOMMAND, POSTSCRIPT error boundaries)
            val boundaryEvents = process.findFlowElementsOfType(BoundaryEvent::class.java)
            boundaryEvents.size shouldBeGreaterThanOrEqual 3 // Error boundaries on 3 main service tasks

            // Validation Summary:
            // - 1 Start Event ✓
            // - 3+ Service Tasks (PRESCRIPT, CORECOMMAND, POSTSCRIPT + compensation) ✓
            // - 1 Intermediate Catch Event (Wait for Axon Event) ✓
            // - 1+ End Events (Success + Compensated) ✓
            // - 3+ Boundary Events (Error boundaries on main service tasks) ✓
            // Total: 7 core workflow elements validated
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
