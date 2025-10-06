package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.test.TestEntityCancelledEvent
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.eventsourcing.eventstore.EventStore
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RuntimeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.MountableFile
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * E2E Integration Test for Compensation Workflow (Story 6.5, Test 6.5-E2E-001).
 *
 * **Framework-Pure Testing**: Uses TestEntity aggregate (framework test infrastructure)
 * instead of Widget (product code) to maintain architectural purity. Framework tests
 * must NOT depend on products module.
 *
 * **Test Objective**: Validate end-to-end compensation flow:
 * 1. Create TestEntity via DispatchAxonCommandTask
 * 2. Ansible task fails (invalid playbook path)
 * 3. Boundary error event triggers compensation
 * 4. Compensation dispatches CancelTestEntityCommand
 * 5. Process completes via compensation path
 * 6. Verify TestEntityCancelledEvent emitted via Axon event store
 *
 * **Risk Mitigations**:
 * - TECH-001: Validates compensation branch wiring (variables propagated correctly)
 * - OPS-001: Verifies compensation observability (metrics in separate test)
 *
 * Story 6.5 (Task 4.1) - CRITICAL integration test for compensating action pattern
 */
@SpringBootTest(classes = [RunAnsiblePlaybookTaskTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class CompensationWorkflowIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var processEngine: ProcessEngine

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        test("6.5-E2E-001: should execute compensation flow when Ansible fails (CRITICAL)") {
            // Given - Deploy compensation test process
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/test-entity-compensation.bpmn20.xml")
                    .deploy()

            deployment.shouldNotBeNull()

            tenantContext.setCurrentTenantId("test-tenant")

            val entityId = UUID.randomUUID().toString()

            // When - Start process: create entity → Ansible fails → compensation (ARCH-001 pattern)
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "test-entity-compensation",
                    mapOf(
                        // Step 1: Create TestEntity (pure reflection - framework-agnostic)
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
                        "entityId" to entityId,
                        "tenantId" to "test-tenant",
                        "name" to "Test Entity for Compensation",
                        "description" to "Will be cancelled after Ansible failure",
                        "value" to BigDecimal("100.00"),
                        "category" to "COMPENSATION_TEST",
                        "metadata" to emptyMap<String, Any>(),
                        // Step 2: Ansible (invalid path forces failure)
                        "playbookPath" to "/playbooks/nonexistent-force-compensation.yml",
                        // Step 3: Compensation variables (SetCommandTypeDelegate will set these)
                        "cancellationReason" to "Ansible playbook execution failed",
                        "operator" to "SYSTEM",
                    ),
                )

            processInstance.shouldNotBeNull()

            // Then - Process completes via compensation path
            eventually(30.seconds) {
                val historicInstance =
                    processEngine.historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .singleResult()

                historicInstance.endTime.shouldNotBeNull()
            }

            // Verify compensation task executed (TECH-001 validation)
            val compensationTaskHistory =
                processEngine.historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstance.id)
                    .activityId("compensationTask")
                    .singleResult()

            compensationTaskHistory.shouldNotBeNull()
            compensationTaskHistory.endTime.shouldNotBeNull()

            // Verify commandResult variable set by DispatchAxonCommandTask
            val commandResultVar =
                processEngine.historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.id)
                    .variableName("commandResult")
                    .singleResult()

            commandResultVar.shouldNotBeNull()
            commandResultVar.value shouldBe "SUCCESS"

            // Verify CancelTestEntityCommand dispatched via Axon event store
            val domainEvents =
                eventStore
                    .readEvents(entityId)
                    .asStream()
                    .map { it.payload }
                    .toList()

            domainEvents.shouldHaveAtLeastSize(2) // At minimum: TestEntityCreatedEvent + TestEntityCancelledEvent

            // Find the cancellation event
            val cancelledEvent = domainEvents.find { it is TestEntityCancelledEvent }
            cancelledEvent.shouldNotBeNull()
            cancelledEvent.shouldBeInstanceOf<TestEntityCancelledEvent>()

            val typedEvent = cancelledEvent as TestEntityCancelledEvent
            typedEvent.entityId shouldBe entityId
            typedEvent.tenantId shouldBe "test-tenant"
            typedEvent.cancellationReason shouldBe "Ansible playbook execution failed"
            typedEvent.operator shouldBe "SYSTEM"

            // Cleanup
            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }
    }

    companion object {
        /**
         * Story 6.4 Remediation: Randomized test credentials per test run.
         * Replaced hardcoded literals with UUID-based randomization.
         */
        @JvmStatic
        private val SSH_PASSWORD = "test-${java.util.UUID.randomUUID().toString().take(12)}"

        @JvmStatic
        private val SSH_USER = "ansible" // Non-privileged user

        /**
         * SSH Testcontainer with Ansible EE (reused from RunAnsiblePlaybookTaskIntegrationTest).
         * Required for testing compensation flow with real Ansible failures.
         */
        @JvmStatic
        val sshContainer: GenericContainer<*> =
            GenericContainer(
                ImageFromDockerfile()
                    .withDockerfileFromBuilder { builder ->
                        builder
                            .from("quay.io/ansible/creator-ee:latest")
                            .run("microdnf install -y openssh-server passwd sudo && microdnf clean all")
                            .run("ssh-keygen -A")
                            .run("mkdir -p /run/sshd")
                            .run("useradd -m -s /bin/bash $SSH_USER")
                            .run("mkdir -p /playbooks && chown -R $SSH_USER:$SSH_USER /playbooks")
                            .run("sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config")
                            .expose(22)
                            .cmd("/usr/sbin/sshd", "-D", "-e")
                            .build()
                    },
            ).withExposedPorts(22)
                .withEnv("SSH_PASSWORD", SSH_PASSWORD)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("playbooks/hello-world.yml"),
                    "/playbooks/hello-world.yml",
                ).withCommand(
                    "/bin/sh",
                    "-c",
                    "echo '$SSH_USER:$SSH_PASSWORD' | chpasswd && /usr/sbin/sshd -D -e",
                ).apply {
                    start()
                }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }

            // SSH container configuration
            registry.add("eaf.ansible.ssh.host") { sshContainer.host }
            registry.add("eaf.ansible.ssh.port") { sshContainer.getMappedPort(22) }
            registry.add("eaf.ansible.ssh.username") { SSH_USER }
            registry.add("eaf.ansible.ssh.password") { SSH_PASSWORD }
            registry.add("eaf.ansible.ssh.timeout-seconds") { "60" }

            // Axon in-memory mode
            registry.add("axon.axonserver.enabled") { "false" }
        }
    }
}
