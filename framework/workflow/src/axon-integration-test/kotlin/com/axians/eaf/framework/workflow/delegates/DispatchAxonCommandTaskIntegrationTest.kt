package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RuntimeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.util.UUID

/**
 * Integration test for DispatchAxonCommandTask JavaDelegate.
 *
 * ## Test-First Spike Strategy (Quinn's Recommendation)
 *
 * This test serves as a "test-first spike" to validate technical unknowns before implementing
 * production code. It validates:
 * 1. Spring can inject Axon CommandGateway into Flowable JavaDelegate
 * 2. TenantContext propagates correctly from BPMN execution thread to Axon command bus
 * 3. CommandExecutionException maps correctly to BpmnError for error boundary events
 * 4. Process variables extract and map type-safely to Command fields
 *
 * If this test passes, the Flowable-Axon integration is validated and implementation can proceed
 * with high confidence. If it fails, integration issues are discovered early (1-2 hours) and the
 * test becomes a debugging harness.
 *
 * Story 6.2: Create Flowable-to-Axon Bridge (Command Dispatch)
 */
@SpringBootTest(classes = [DispatchAxonCommandTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class DispatchAxonCommandTaskIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var processEngine: ProcessEngine

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var widgetProjectionRepository: WidgetProjectionRepository

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        afterEach {
            tenantContext.clearCurrentTenant()
        }

        test("should dispatch CreateWidgetCommand from BPMN process") {
            // Set tenant context (required for delegate validation)
            tenantContext.setCurrentTenantId("test-tenant")
            // Deploy BPMN process
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/example-widget-creation.bpmn20.xml")
                    .deploy()

            deployment.shouldNotBeNull()

            // Start process with variables
            val widgetId = UUID.randomUUID().toString()
            val processVariables =
                mapOf(
                    "widgetId" to widgetId,
                    "tenantId" to "test-tenant",
                    "name" to "Test Widget",
                    "description" to "Created via BPMN process",
                    "value" to BigDecimal("100.00"),
                    "category" to "TEST",
                    // metadata omitted - optional field, avoids JSONB type configuration complexity
                )

            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "example-widget-creation",
                    processVariables,
                )

            processInstance.shouldNotBeNull()

            // Verify Widget was created via Axon aggregate
            // Wait for async projection (event processing)
            Thread.sleep(2000)

            val widget = widgetProjectionRepository.findByWidgetIdAndTenantId(widgetId, "test-tenant")
            widget.shouldNotBeNull()
            widget.name shouldBe "Test Widget"
            widget.getTenantId() shouldBe "test-tenant"
        }

        test("should handle missing required variables with BPMN error") {
            tenantContext.setCurrentTenantId("test-tenant")

            // Deploy BPMN process
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/example-widget-creation.bpmn20.xml")
                .deploy()

            // Start process WITHOUT required variables
            val processVariables =
                mapOf(
                    "widgetId" to UUID.randomUUID().toString(),
                    "tenantId" to "test-tenant",
                    // Missing: name, value, category (required fields)
                )

            // Process starts but error boundary event should be triggered
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "example-widget-creation",
                    processVariables,
                )

            processInstance.shouldNotBeNull()

            // Verify process ended at error end event (not success end event)
            val historicProcessInstance =
                processEngine.historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstance.id)
                    .singleResult()

            historicProcessInstance.shouldNotBeNull()
            historicProcessInstance.endActivityId shouldBe "errorEndEvent"
        }

        test("should enforce tenant context validation") {
            // SECURITY CRITICAL: Validate tenant isolation
            tenantContext.setCurrentTenantId("tenant-a")

            // Deploy BPMN process
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/example-widget-creation.bpmn20.xml")
                .deploy()

            // Attempt to create widget for DIFFERENT tenant (tenant-b)
            val processVariables =
                mapOf(
                    "widgetId" to UUID.randomUUID().toString(),
                    "tenantId" to "tenant-b", // Mismatched tenant!
                    "name" to "Malicious Widget",
                    "description" to "Cross-tenant attack attempt",
                    "value" to BigDecimal("99.99"),
                    "category" to "ATTACK",
                )

            // Should throw BpmnError due to tenant isolation violation
            val exception =
                runCatching {
                    runtimeService.startProcessInstanceByKey(
                        "example-widget-creation",
                        processVariables,
                    )
                }.exceptionOrNull()

            exception.shouldNotBeNull()
            exception.message.shouldContain("TENANT_ISOLATION_VIOLATION")
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

            // Flowable configuration
            registry.add("spring.flowable.database-schema-update") { "true" }
            registry.add("spring.flowable.database-schema") { "flowable" }
            registry.add("spring.flowable.async-executor-activate") { "true" }
            registry.add("spring.flowable.check-process-definitions") { "true" }

            // JPA configuration (Story 6.2: enable DDL for Widget projection)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.jpa.show-sql") { "false" }
        }
    }
}
