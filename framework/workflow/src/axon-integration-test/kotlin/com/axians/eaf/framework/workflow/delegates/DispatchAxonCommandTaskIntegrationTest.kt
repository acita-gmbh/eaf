package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.test.TestEntityProjectionHandler
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
 * Integration test for DispatchAxonCommandTask JavaDelegate.
 *
 * Uses framework-local test types (TestEntity) to validate generic infrastructure
 * without depending on products module.
 *
 * ## Architecture: Framework Test Independence
 *
 * Framework tests MUST NOT depend on products. This test uses:
 * - TestEntityAggregate (framework test aggregate)
 * - CreateTestEntityCommand (framework test command)
 * - TestEntityCreatedEvent (framework test event)
 * - TestEntityProjectionHandler (framework test handler)
 *
 * This validates the generic infrastructure works without coupling to Widget domain.
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
    private lateinit var testEntityProjectionHandler: TestEntityProjectionHandler

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        afterEach {
            tenantContext.clearCurrentTenant()
        }

        test("6.2-INT-001: should dispatch CreateTestEntityCommand from BPMN process") {
            // Set tenant context (required for delegate validation)
            tenantContext.setCurrentTenantId("test-tenant")
            // Deploy BPMN process
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/example-test-entity-creation.bpmn20.xml")
                    .deploy()

            deployment.shouldNotBeNull()

            // Start process with variables (ARCH-001 Remediation: Pure reflection pattern)
            val entityId = UUID.randomUUID().toString()
            val processVariables =
                mapOf(
                    // Generic delegate uses pure reflection (framework-agnostic)
                    "commandClassName" to "com.axians.eaf.framework.workflow.test.CreateTestEntityCommand",
                    "constructorParameters" to
                        listOf(
                            "entityId",
                            "tenantId",
                            "name",
                            "description",
                            "value",
                            "category",
                            "metadata",
                        ),
                    // Command constructor parameters
                    "entityId" to entityId,
                    "tenantId" to "test-tenant",
                    "name" to "Test Entity",
                    "description" to "Created via BPMN process",
                    "value" to BigDecimal("100.00"),
                    "category" to "TEST",
                    "metadata" to emptyMap<String, Any>(),
                )

            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "example-test-entity-creation",
                    processVariables,
                )

            processInstance.shouldNotBeNull()

            // Verify TestEntity was created via Axon aggregate (with async projection polling)
            eventually(duration = 5.seconds) {
                val entity = testEntityProjectionHandler.findById(entityId)
                entity.shouldNotBeNull()
                entity.name shouldBe "Test Entity"
                entity.tenantId shouldBe "test-tenant"
            }
        }

        test("6.2-INT-002: should handle missing required variables with BPMN error") {
            tenantContext.setCurrentTenantId("test-tenant")

            // Deploy BPMN process
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/example-test-entity-creation.bpmn20.xml")
                .deploy()

            // Start process WITHOUT required constructor parameters (ARCH-001 pattern)
            val processVariables =
                mapOf(
                    "commandClassName" to "com.axians.eaf.framework.workflow.test.CreateTestEntityCommand",
                    "constructorParameters" to
                        listOf(
                            "entityId",
                            "tenantId",
                            "name",
                            "description",
                            "value",
                            "category",
                            "metadata",
                        ),
                    "entityId" to UUID.randomUUID().toString(),
                    "tenantId" to "test-tenant",
                    // Missing: name, value, category (required fields) - should trigger MISSING_VARIABLE error
                )

            // Process starts but error boundary event should be triggered
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "example-test-entity-creation",
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

        test("6.2-INT-003: should enforce tenant context validation") {
            // SECURITY CRITICAL: Validate tenant isolation
            tenantContext.setCurrentTenantId("tenant-a")

            // Deploy BPMN process
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/example-test-entity-creation.bpmn20.xml")
                .deploy()

            // Attempt to create entity for DIFFERENT tenant (tenant-b) - ARCH-001 pattern
            val processVariables =
                mapOf(
                    "commandClassName" to "com.axians.eaf.framework.workflow.test.CreateTestEntityCommand",
                    "constructorParameters" to
                        listOf(
                            "entityId",
                            "tenantId",
                            "name",
                            "description",
                            "value",
                            "category",
                            "metadata",
                        ),
                    "entityId" to UUID.randomUUID().toString(),
                    "tenantId" to "tenant-b", // Mismatched tenant!
                    "name" to "Malicious Entity",
                    "description" to "Cross-tenant attack attempt",
                    "value" to BigDecimal("99.99"),
                    "category" to "ATTACK",
                    "metadata" to emptyMap<String, Any>(),
                )

            // BpmnError triggers error boundary event (does not throw to caller)
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "example-test-entity-creation",
                    processVariables,
                )

            processInstance.shouldNotBeNull()

            // Verify process ended at error end event (tenant isolation enforced)
            val historicProcessInstance =
                processEngine.historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstance.id)
                    .singleResult()

            historicProcessInstance.shouldNotBeNull()
            historicProcessInstance.endActivityId shouldBe "errorEndEvent"
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
