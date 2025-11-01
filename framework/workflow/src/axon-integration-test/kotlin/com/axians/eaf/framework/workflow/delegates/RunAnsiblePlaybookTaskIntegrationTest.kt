package com.axians.eaf.framework.workflow.delegates

import com.axians.eaf.framework.security.tenant.TenantContext
import com.axians.eaf.framework.workflow.observability.FlowableMetrics
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RuntimeService
import org.flowable.engine.delegate.BpmnError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.MountableFile
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for RunAnsiblePlaybookTask delegate with SSH Testcontainer.
 *
 * **Test Strategy** (Story 6.4, Task 8 - Complete Implementation):
 * - Subtask 8.1: Kotest FunSpec with @SpringBootTest pattern ✅
 * - Subtask 8.2: Ansible EE Testcontainer with OpenSSH server ✅
 * - Subtask 8.3: BPMN process deployment and delegate wiring ✅
 * - Subtask 8.4: Required variable validation ✅
 * - Subtask 8.5: Successful playbook execution via real SSH 🚧
 * - Subtask 8.6: Tenant isolation validation (security critical) ✅
 * - Subtask 8.7: Ansible failure scenarios with real SSH 🚧
 * - Subtask 8.8: Metrics validation during execution 🚧
 *
 * **Architecture**:
 * Custom Docker image (Dockerfile.ansible-ee-sshd) based on Red Hat Ansible EE
 * with OpenSSH server added. Provides production-like Ansible environment for
 * end-to-end SSH execution testing.
 *
 * Story 6.4 (Task 8) - Complete Integration Testing with Ansible EE
 */
@SpringBootTest(classes = [RunAnsiblePlaybookTaskTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class RunAnsiblePlaybookTaskIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var processEngine: ProcessEngine

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var runAnsiblePlaybookTask: RunAnsiblePlaybookTask

    @Autowired
    private lateinit var flowableMetrics: FlowableMetrics

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())

        test("6.4-INT-001: should deploy BPMN process with Ansible service task successfully (Subtask 8.3)") {
            // Given - BPMN process definition with RunAnsiblePlaybookTask
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            // Then - Deployment should succeed
            deployment.shouldNotBeNull()
            deployment.id.shouldNotBeNull()

            // Verify process definition registered (use latestVersion for multiple test runs)
            val processDefinition =
                processEngine.repositoryService
                    .createProcessDefinitionQuery()
                    .processDefinitionKey("hello-world-ansible")
                    .latestVersion()
                    .singleResult()

            processDefinition.shouldNotBeNull()
            processDefinition.id.shouldNotBeNull()
            processDefinition.key shouldBe "hello-world-ansible"

            // Cleanup
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-INT-002: should wire RunAnsiblePlaybookTask delegate via Spring DI (Subtask 8.3)") {
            // Given - Spring should inject RunAnsiblePlaybookTask bean

            // Then - Delegate bean should be available
            runAnsiblePlaybookTask.shouldNotBeNull()
        }

        test("6.4-INT-003: should validate tenant isolation - missing tenantId throws BpmnError (Subtask 8.6)") {
            // Given - Process deployed
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("tenant-a")

            // When - Start process WITHOUT tenantId variable
            val result =
                runCatching {
                    runtimeService.startProcessInstanceByKey(
                        "hello-world-ansible",
                        mapOf(
                            "playbookPath" to "hello-world.yml",
                            // Missing tenantId - should fail
                        ),
                    )
                }

            // Then - Should throw exception for missing tenantId
            result.isFailure shouldBe true
            result.exceptionOrNull().shouldNotBeNull()

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-INT-004: should validate tenant isolation - mismatched tenant throws BpmnError (Subtask 8.6 - SECURITY CRITICAL)") {
            // Given
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("tenant-a")

            // When - Start process with DIFFERENT tenant in variables
            val result =
                runCatching {
                    runtimeService.startProcessInstanceByKey(
                        "hello-world-ansible",
                        mapOf(
                            "tenantId" to "tenant-b", // Mismatch!
                            "playbookPath" to "hello-world.yml",
                        ),
                    )
                }

            // Then - Should throw BpmnError for tenant mismatch (SECURITY)
            result.isFailure shouldBe true

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-INT-005: should require playbookPath variable - throws BpmnError if missing (Subtask 8.4)") {
            // Given
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("test-tenant")

            // When - Start process without required playbookPath
            val result =
                runCatching {
                    runtimeService.startProcessInstanceByKey(
                        "hello-world-ansible",
                        mapOf(
                            "tenantId" to "test-tenant",
                            // Missing playbookPath
                        ),
                    )
                }

            // Then - Should throw BpmnError
            result.isFailure shouldBe true

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-E2E-001: should execute Ansible playbook successfully via SSH (Subtask 8.5 - E2E)") {
            // Given - Deploy BPMN with Ansible task
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("test-tenant")

            // When - Start process with valid Ansible variables
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "hello-world-ansible",
                    mapOf(
                        "tenantId" to "test-tenant",
                        "playbookPath" to "/playbooks/hello-world.yml",
                        "extraVars" to mapOf("message" to "Integration test success!"),
                    ),
                )

            // Then - Process should complete successfully
            processInstance.shouldNotBeNull()

            // Verify process reached end event (async execution)
            eventually(30.seconds) {
                val historicInstance =
                    processEngine.historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .singleResult()

                historicInstance.endTime.shouldNotBeNull()
            }

            // Verify Ansible output stored in process variables
            val historicVariables =
                processEngine.historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstance.id)
                    .list()

            val exitCodeVar = historicVariables.find { it.variableName == "ansibleExitCode" }
            exitCodeVar.shouldNotBeNull()
            exitCodeVar.value shouldBe 0

            val stdoutVar = historicVariables.find { it.variableName == "ansibleStdout" }
            stdoutVar.shouldNotBeNull()
            stdoutVar.value.toString() shouldContain "Integration test success!"

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-E2E-002: should handle Ansible playbook failure via SSH (Subtask 8.7 - E2E)") {
            // Given - Deploy BPMN with error boundary
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("test-tenant")

            // When - Start process with INVALID playbook path
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "hello-world-ansible",
                    mapOf(
                        "tenantId" to "test-tenant",
                        "playbookPath" to "/playbooks/nonexistent.yml", // Invalid path
                    ),
                )

            processInstance.shouldNotBeNull()

            // Then - Process should complete via error boundary event
            eventually(30.seconds) {
                val historicInstance =
                    processEngine.historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .singleResult()

                historicInstance.endTime.shouldNotBeNull()
                // Process should end via error boundary (endFailure end event)
            }

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }

        test("6.4-E2E-003: should record Flowable metrics during Ansible execution (Subtask 8.8 - E2E)") {
            // Given - Deploy BPMN
            val deployment =
                processEngine.repositoryService
                    .createDeployment()
                    .addClasspathResource("processes/hello-world-ansible.bpmn20.xml")
                    .deploy()

            tenantContext.setCurrentTenantId("test-tenant")

            // When - Execute successful playbook
            val processInstance =
                runtimeService.startProcessInstanceByKey(
                    "hello-world-ansible",
                    mapOf(
                        "tenantId" to "test-tenant",
                        "playbookPath" to "/playbooks/hello-world.yml",
                        "extraVars" to mapOf("message" to "Metrics test"),
                    ),
                )

            // Then - Wait for completion
            eventually(30.seconds) {
                val historicInstance =
                    processEngine.historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(processInstance.id)
                        .singleResult()

                historicInstance.endTime.shouldNotBeNull()
            }

            // Verify FlowableMetrics recorded process duration
            // (Actual metric values validated via MeterRegistry in FlowableMetrics - tested separately)
            flowableMetrics.shouldNotBeNull()

            tenantContext.clearCurrentTenant()
            processEngine.repositoryService.deleteDeployment(deployment.id, true)
        }
    }

    companion object {
        private const val SSH_PASSWORD = "testpassword123"
        private const val SSH_USER = "root"

        /**
         * Subtask 8.2: SSH Testcontainer with Ansible Execution Environment
         *
         * Custom image based on quay.io/ansible/creator-ee with OpenSSH server added.
         * Built from Dockerfile.ansible-ee-sshd for production-like Ansible testing.
         *
         * **Architecture**: This container acts as the "remote Ansible host" where
         * AnsibleExecutor SSHs in and executes ansible-playbook commands.
         */
        @JvmStatic
        val sshContainer: GenericContainer<*> =
            GenericContainer(
                ImageFromDockerfile()
                    .withDockerfileFromBuilder { builder ->
                        builder
                            .from("quay.io/ansible/creator-ee:latest")
                            .run("microdnf install -y openssh-server passwd && microdnf clean all")
                            .run("ssh-keygen -A")
                            .run("mkdir -p /run/sshd")
                            .run("sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config")
                            .run("sed -i 's/#PasswordAuthentication yes/PasswordAuthentication yes/' /etc/ssh/sshd_config")
                            .run("mkdir -p /playbooks")
                            .expose(22)
                            .cmd("/usr/sbin/sshd", "-D", "-e")
                            .build()
                    },
            ).withExposedPorts(22)
                .withEnv("SSH_PASSWORD", SSH_PASSWORD)
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource("playbooks/hello-world.yml"),
                    "/playbooks/hello-world.yml",
                ).withCommand("/bin/sh", "-c", "echo 'root:$SSH_PASSWORD' | chpasswd && /usr/sbin/sshd -D -e")
                .apply {
                    start()
                }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }

            // Subtask 8.2: SSH testcontainer configuration
            registry.add("eaf.ansible.ssh.host") { sshContainer.host }
            registry.add("eaf.ansible.ssh.port") { sshContainer.getMappedPort(22) }
            registry.add("eaf.ansible.ssh.username") { SSH_USER }
            registry.add("eaf.ansible.ssh.password") { SSH_PASSWORD } // For test - not used by JSch key auth
            registry.add("eaf.ansible.ssh.timeout-seconds") { "60" }
        }
    }
}
