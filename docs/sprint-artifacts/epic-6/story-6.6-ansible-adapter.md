# Story 6.6: Ansible Adapter for Legacy Migration

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007

---

## ⚠️ CRITICAL SECURITY REQUIREMENT (2025-11-16)

**MANDATORY:** This story MUST integrate **CommandInjectionProtection** implemented in framework/workflow as part of OWASP Top 10:2025 compliance (A03:2025 - Injection).

**Command Injection Protection Available:**
- `framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/security/CommandInjectionProtection.kt`
- Validates ALL Ansible command parameters for shell injection attacks
- Blocks: Command chaining (`;`, `&&`, `||`), Redirection (`>`, `<`, `|`), Command substitution (`` ` ``, `$()`), Variable expansion (`$`)
- Comprehensive test suite with 22+ tests including fuzz testing

**Why This Is Critical:**
- Ansible playbook execution involves shell commands
- BPMN process variables are user-controlled input
- WITHOUT validation: **CRITICAL RCE vulnerability (Remote Code Execution)**
- WITH validation: OWASP A03:2025 compliance

**Implementation Approach:**
Wrap ALL Ansible command construction with `CommandInjectionProtection.validateCommand()` and `validateParameter()`. See implementation guide in `docs/owasp-top-10-2025-story-mapping.md`.

**⛔ DO NOT PROCEED without this security integration - acceptance criteria blocked until implemented.**

---

## User Story

As a framework developer,
I want an Ansible adapter allowing BPMN service tasks to execute Ansible playbooks,
So that legacy Dockets automation can be migrated to Flowable.

---

## Acceptance Criteria

1. ✅ AnsibleAdapter.kt implements JavaDelegate
2. ✅ JSch 0.2.18 dependency for SSH execution
3. ✅ Adapter executes Ansible playbooks from BPMN with process variable parameters (MUST validate with CommandInjectionProtection)
4. ✅ Playbook execution results captured and stored in process variables
5. ✅ Error handling: playbook failures trigger BPMN error events
6. ✅ Integration test executes sample Ansible playbook from BPMN
7. ✅ Security: SSH keys and credentials managed securely (not in process variables)
8. ✅ **CRITICAL:** ALL command parameters validated with CommandInjectionProtection before execution
9. ✅ **CRITICAL:** Security test validates injection attempts are blocked (SQL injection, command chaining, path traversal)
10. ✅ Ansible adapter usage documented with security best practices

---

## Prerequisites

**Story 6.3** - Axon Command Gateway Delegate

---

## Technical Notes

### Secure Ansible Adapter Implementation

**framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/ansible/AnsibleAdapter.kt:**
```kotlin
@Component
class AnsibleAdapter(
    private val commandInjectionProtection: CommandInjectionProtection,
    private val ansibleExecutor: AnsibleExecutor
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val playbookPath = execution.getVariable("playbookPath") as? String
            ?: throw BpmnError("ANSIBLE_ERROR", "Missing playbookPath variable")

        val inventory = execution.getVariable("inventory") as? String
            ?: throw BpmnError("ANSIBLE_ERROR", "Missing inventory variable")

        val extraVars = execution.getVariable("extraVars") as? Map<String, String> ?: emptyMap()

        // CRITICAL: Validate ALL parameters for injection attacks
        validateAnsibleParameters(playbookPath, inventory, extraVars)

        // Safe to execute
        val result = ansibleExecutor.executePlaybook(
            playbookPath = playbookPath,
            inventory = inventory,
            extraVars = extraVars
        )

        // Store results in process variables
        execution.setVariable("ansibleExitCode", result.exitCode)
        execution.setVariable("ansibleStdout", result.stdout)
        execution.setVariable("ansibleStderr", result.stderr)

        if (result.exitCode != 0) {
            throw BpmnError("ANSIBLE_PLAYBOOK_FAILED", "Playbook execution failed: ${result.stderr}")
        }
    }

    private fun validateAnsibleParameters(
        playbookPath: String,
        inventory: String,
        extraVars: Map<String, String>
    ) {
        // Validate playbook path (prevent path traversal)
        commandInjectionProtection.validateCommand("ansible-playbook", playbookPath)
            .onLeft { error ->
                throw BpmnError(
                    "ANSIBLE_SECURITY_VIOLATION",
                    "Invalid playbookPath: ${error.message}"
                )
            }

        // Validate inventory path
        commandInjectionProtection.validateCommand("ansible-playbook", inventory)
            .onLeft { error ->
                throw BpmnError(
                    "ANSIBLE_SECURITY_VIOLATION",
                    "Invalid inventory: ${error.message}"
                )
            }

        // Validate all extra vars (prevent variable expansion, command substitution)
        extraVars.forEach { (key, value) ->
            commandInjectionProtection.validateParameter(key, value)
                .onLeft { error ->
                    throw BpmnError(
                        "ANSIBLE_SECURITY_VIOLATION",
                        "Invalid extra var '$key': ${error.message}"
                    )
                }
        }
    }
}
```

### Secure Ansible Executor

```kotlin
@Component
class AnsibleExecutor(
    private val commandInjectionProtection: CommandInjectionProtection,
    private val credentialsProvider: AnsibleCredentialsProvider
) {

    fun executePlaybook(
        playbookPath: String,
        inventory: String,
        extraVars: Map<String, String>
    ): AnsibleResult {
        // Build command with pre-validated parameters
        val command = buildList {
            add("ansible-playbook")
            add(playbookPath)
            add("-i")
            add(inventory)

            // Add extra vars (already validated)
            extraVars.forEach { (key, value) ->
                add("--extra-vars")
                add("$key=$value") // Safe: validated by CommandInjectionProtection
            }

            // Add SSH key (managed securely)
            val sshKey = credentialsProvider.getPrivateKeyPath()
            add("--private-key")
            add(sshKey)
        }

        // Execute with ProcessBuilder (safer than Runtime.exec for arrays)
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return AnsibleResult(
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr
        )
    }
}
```

### Security Test (MANDATORY)

**framework/workflow/src/test/kotlin/com/axians/eaf/framework/workflow/ansible/AnsibleAdapterSecurityTest.kt:**
```kotlin
class AnsibleAdapterSecurityTest : FunSpec({
    test("should block command injection in playbookPath") {
        val execution = MockDelegateExecution()
        execution.setVariable("playbookPath", "playbook.yml; rm -rf /") // Injection attempt
        execution.setVariable("inventory", "inventory.ini")

        val exception = shouldThrow<BpmnError> {
            ansibleAdapter.execute(execution)
        }
        exception.errorCode shouldBe "ANSIBLE_SECURITY_VIOLATION"
    }

    test("should block command substitution in extraVars") {
        val execution = MockDelegateExecution()
        execution.setVariable("playbookPath", "playbook.yml")
        execution.setVariable("inventory", "inventory.ini")
        execution.setVariable("extraVars", mapOf(
            "user" to "\$(whoami)" // Command substitution attempt
        ))

        val exception = shouldThrow<BpmnError> {
            ansibleAdapter.execute(execution)
        }
        exception.errorCode shouldBe "ANSIBLE_SECURITY_VIOLATION"
    }

    test("should allow valid Ansible parameters") {
        val execution = MockDelegateExecution()
        execution.setVariable("playbookPath", "/opt/ansible/playbooks/deploy.yml")
        execution.setVariable("inventory", "/opt/ansible/inventory/production.ini")
        execution.setVariable("extraVars", mapOf(
            "version" to "1.2.3",
            "environment" to "production"
        ))

        shouldNotThrow<BpmnError> {
            ansibleAdapter.execute(execution)
        }
    }
})
```

---

## References

- PRD: FR007 (Ansible adapter for Dockets migration)
- Tech Spec: Section 3 (FR007)
- **Command Injection Protection:** `docs/owasp-top-10-2025-story-mapping.md`
- **OWASP Compliance:** A03:2025 - Injection
- **Security Best Practices:** OWASP Command Injection Prevention Cheat Sheet
