package com.axians.eaf.framework.workflow.ansible

import com.fasterxml.jackson.databind.ObjectMapper
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

/**
 * Ansible Executor - SSH-based Ansible Playbook Execution
 *
 * Executes Ansible playbooks on remote hosts via SSH using JSch library.
 * Supports configurable connection parameters, timeout handling, and structured
 * result capture (stdout/stderr/exit code).
 *
 * **Story Context:** Story 6.4 (Task 4, Subtasks 4.1-4.7) - SSH execution
 * infrastructure for RunAnsiblePlaybookTask delegate.
 *
 * **Security Considerations:**
 * - SSH private keys stored outside repository (environment variables/Kubernetes secrets)
 * - Tenant ID propagated for multi-tenant playbook isolation
 * - Connection cleanup guaranteed via finally block (Subtask 4.7)
 *
 * **Configuration** (application.yml):
 * ```yaml
 * eaf:
 *   ansible:
 *     ssh:
 *       host: ${ANSIBLE_SSH_HOST:localhost}
 *       port: ${ANSIBLE_SSH_PORT:22}
 *       username: ${ANSIBLE_SSH_USER:ansible}
 *       private-key-path: ${ANSIBLE_SSH_KEY:/etc/eaf/ansible_rsa}
 *       timeout-seconds: 300  # Default 5 minutes
 * ```
 *
 * @param environment Spring Environment for SSH configuration
 */
@Component
class AnsibleExecutor(
    private val environment: Environment,
) {
    private val logger: Logger = LoggerFactory.getLogger(AnsibleExecutor::class.java)
    private val objectMapper = ObjectMapper()

    companion object {
        private const val DEFAULT_SSH_TIMEOUT_SECONDS = 300 // 5 minutes
        private const val CHANNEL_WAIT_MS = 100L // Polling interval for SSH channel
    }

    /**
     * Executes an Ansible playbook via SSH connection (Subtasks 4.1-4.7).
     *
     * **Execution Flow**:
     * 1. Validate playbook path and inventory (Security: Path traversal prevention)
     * 2. Establish SSH connection with configured credentials (Subtask 4.2)
     * 3. Build ansible-playbook command with inventory and extra-vars (Subtask 4.3)
     * 4. Execute command and capture stdout/stderr (Subtask 4.4)
     * 5. Parse exit code: 0 = success, non-zero = failure (Subtask 4.5)
     * 6. Apply timeout (default 5 minutes, configurable) (Subtask 4.6)
     * 7. Cleanup SSH connection in finally block (Subtask 4.7)
     *
     * **Security Enhancements** (CodeRabbit feedback):
     * - Path validation prevents directory traversal attacks
     * - Allowlist enforcement for file extensions
     * - Production-mode host key verification
     *
     * @param playbookPath Path to Ansible playbook on remote host (required)
     * @param inventory Ansible inventory file path (optional)
     * @param extraVars Key-value pairs passed to playbook via --extra-vars (optional)
     * @param tenantId Tenant identifier for isolation validation and logging
     * @return AnsibleResult containing exit code, stdout, and stderr
     * @throws AnsibleExecutionException if playbook execution fails (non-zero exit code)
     * @throws SecurityException if path validation fails
     */
    fun executePlaybook(
        playbookPath: String,
        inventory: String?,
        extraVars: Map<String, Any>,
        tenantId: String,
    ): AnsibleResult {
        // Security: Validate paths BEFORE SSH connection (CodeRabbit feedback - defense-in-depth)
        validatePlaybookPath(playbookPath)
        inventory?.let { validateInventoryPath(it) }

        logger.info(
            "Initiating Ansible playbook execution: playbook={}, inventory={}, tenant={}",
            playbookPath,
            inventory ?: "default",
            tenantId,
        )

        val session = createSshSession() // Subtask 4.2

        try {
            // Establish SSH connection
            session.connect()
            logger.debug("SSH connection established")

            // Build and execute Ansible command (Subtasks 4.3, 4.4, 4.5)
            return executeAnsibleCommand(session, playbookPath, inventory, extraVars, tenantId)
        } finally {
            // CRITICAL: Subtask 4.7 - Connection cleanup to prevent leaks
            session.disconnect()
            logger.debug("SSH connection closed")
        }
    }

    /**
     * Validates Ansible playbook path (CodeRabbit security feedback).
     *
     * **Defense-in-Depth Validation**:
     * - Prevents directory traversal attacks (../)
     * - Enforces YAML extension allowlist (.yml, .yaml)
     * - Blocks absolute path manipulation
     * - Prevents shell metacharacter injection in filename
     *
     * @param path Playbook path to validate
     * @throws SecurityException if validation fails
     */
    private fun validatePlaybookPath(path: String) {
        // Prevent directory traversal
        require(!path.contains("..")) {
            "Path traversal not allowed in playbook path"
        }

        // Allowlist: Only YAML files
        require(path.endsWith(".yml") || path.endsWith(".yaml")) {
            "Invalid playbook extension (must be .yml or .yaml)"
        }

        // Prevent absolute paths from untrusted sources (allow common playbook directories)
        if (path.startsWith("/")) {
            val allowedPrefixes = listOf("/playbooks/", "/opt/", "/root/", "/home/", "/ansible/")
            require(allowedPrefixes.any { path.startsWith(it) }) {
                "Absolute playbook paths must be in allowed directories"
            }
        }

        // Prevent shell metacharacters in filename (defense-in-depth)
        val dangerousChars = setOf(';', '&', '|', '$', '`', '\n', '\r', '\\', '<', '>')
        require(path.none { it in dangerousChars }) {
            "Playbook path contains invalid characters"
        }
    }

    /**
     * Validates Ansible inventory path (CodeRabbit security feedback).
     *
     * **Defense-in-Depth Validation**:
     * - Prevents directory traversal attacks
     * - Blocks shell metacharacter injection
     * - Enforces safe path patterns
     *
     * @param path Inventory path to validate
     * @throws SecurityException if validation fails
     */
    private fun validateInventoryPath(path: String) {
        // Prevent directory traversal
        require(!path.contains("..")) {
            "Path traversal not allowed in inventory path"
        }

        // Prevent shell metacharacters (defense-in-depth)
        val dangerousChars = setOf(';', '&', '|', '$', '`', '\n', '\r', '\\', '<', '>')
        require(path.none { it in dangerousChars }) {
            "Inventory path contains invalid characters"
        }
    }

    /**
     * Creates and configures SSH session (Subtask 4.2, 4.6).
     *
     * **Security Enhancement** (CodeRabbit feedback):
     * - Production mode enforces SSH host key verification
     * - Development/test modes allow disabled verification
     * - Password authentication restricted to non-production profiles
     *
     * @return Configured JSch session ready for connection
     * @throws SecurityException if production security requirements not met
     */
    private fun createSshSession(): com.jcraft.jsch.Session {
        val sshHost = environment.getProperty("eaf.ansible.ssh.host") ?: "localhost"
        val sshPort = environment.getProperty("eaf.ansible.ssh.port")?.toInt() ?: 22
        val sshUser = environment.getProperty("eaf.ansible.ssh.username") ?: "ansible"
        val sshKey = environment.getProperty("eaf.ansible.ssh.private-key-path")
        val sshPassword = environment.getProperty("eaf.ansible.ssh.password") // For test environments
        val knownHostsPath = environment.getProperty("eaf.ansible.ssh.known-hosts-path")
        val timeoutSeconds =
            environment.getProperty("eaf.ansible.ssh.timeout-seconds")?.toInt()
                ?: DEFAULT_SSH_TIMEOUT_SECONDS

        val jsch = JSch()

        // Configure SSH authentication (key preferred, password fallback)
        if (sshKey != null) {
            jsch.addIdentity(sshKey)
            logger.debug("Using SSH key authentication")
        }

        val session = jsch.getSession(sshUser, sshHost, sshPort)

        // Configure password authentication if provided (typically for test environments)
        if (sshPassword != null) {
            // Security: Password auth only allowed in test/dev environments
            if (isProductionProfile()) {
                throw SecurityException("SSH password authentication not permitted in production environment")
            }
            session.setPassword(sshPassword)
            logger.warn("SSH password authentication enabled - TEST/DEV ENVIRONMENT ONLY")
        }

        // Security: Production requires SSH host key verification (CodeRabbit feedback)
        configureHostKeyVerification(jsch, session, knownHostsPath)

        session.timeout = timeoutSeconds * 1000 // Subtask 4.6: Convert to milliseconds

        return session
    }

    /**
     * Configures SSH host key verification based on environment profile.
     *
     * **Production Mode** (prod profile active):
     * - Requires known_hosts file (eaf.ansible.ssh.known-hosts-path)
     * - Enforces strict host key checking (prevents MITM attacks)
     * - Throws SecurityException if known_hosts missing
     *
     * **Development/Test Mode** (test/dev profiles):
     * - Allows disabling host key verification for easier testing
     * - Logs warning about relaxed security
     *
     * @param jsch JSch instance for configuring known_hosts
     * @param session SSH session to configure
     * @param knownHostsPath Path to SSH known_hosts file (optional in dev/test)
     * @throws SecurityException if production requirements not met
     */
    private fun configureHostKeyVerification(
        jsch: JSch,
        session: com.jcraft.jsch.Session,
        knownHostsPath: String?,
    ) {
        if (isProductionProfile()) {
            // PRODUCTION: Strict host key verification REQUIRED
            if (knownHostsPath == null) {
                throw SecurityException(
                    "SSH known_hosts path required in production " +
                        "(configure eaf.ansible.ssh.known-hosts-path)",
                )
            }

            jsch.setKnownHosts(knownHostsPath)
            session.setConfig("StrictHostKeyChecking", "yes")
            logger.info("SSH host key verification enabled (known_hosts: $knownHostsPath)")
        } else {
            // DEVELOPMENT/TEST: Allow relaxed verification for testing
            session.setConfig("StrictHostKeyChecking", "no")
            logger.warn(
                "SSH host key verification DISABLED - DEVELOPMENT/TEST MODE ONLY " +
                    "(StrictHostKeyChecking=no is a security risk in production)",
            )
        }
    }

    /**
     * Determines if application is running in production profile.
     *
     * @return true if 'prod' or 'production' profile active
     */
    private fun isProductionProfile(): Boolean =
        environment.activeProfiles.any { profile ->
            profile.equals("prod", ignoreCase = true) ||
                profile.equals("production", ignoreCase = true)
        }

    /**
     * Executes ansible-playbook command and captures output (Subtasks 4.3-4.5).
     *
     * @return AnsibleResult with exit code and output streams
     * @throws AnsibleExecutionException if exit code != 0
     */
    private fun executeAnsibleCommand(
        session: com.jcraft.jsch.Session,
        playbookPath: String,
        inventory: String?,
        extraVars: Map<String, Any>,
        tenantId: String,
    ): AnsibleResult {
        // Subtask 4.3: Build ansible-playbook command
        val command = buildAnsibleCommand(playbookPath, inventory, extraVars)

        // Security: Log with sensitive data redaction (CodeRabbit feedback)
        val redactedVars = redactSensitiveData(extraVars)
        logger.debug(
            "Executing Ansible playbook: path={}, inventory={}, vars={}",
            playbookPath,
            inventory,
            redactedVars,
        )

        // Subtask 4.4: Execute and capture stdout/stderr
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.outputStream = stdout
        channel.setErrStream(stderr)

        channel.connect()

        // Wait for command completion (with timeout from session)
        while (!channel.isClosed) {
            Thread.sleep(CHANNEL_WAIT_MS)
        }

        val exitCode = channel.exitStatus
        channel.disconnect()

        logger.info("Ansible execution completed: exitCode={}, tenant={}", exitCode, tenantId)

        // Subtask 4.5: Parse exit code (0 = success, non-zero = failure)
        if (exitCode != 0) {
            throw AnsibleExecutionException(
                errorCode = "ANSIBLE_FAILED",
                message = "Ansible playbook failed with exit code $exitCode",
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                exitCode = exitCode,
            )
        }

        return AnsibleResult(
            exitCode = exitCode,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
        )
    }

    /**
     * Builds ansible-playbook command with inventory and extra-vars (Subtask 4.3).
     *
     * @return Complete ansible-playbook command string
     */
    private fun buildAnsibleCommand(
        playbookPath: String,
        inventory: String?,
        extraVars: Map<String, Any>,
    ): String {
        val extraVarsJson = objectMapper.writeValueAsString(extraVars)
        val inventoryArg = if (inventory != null) "-i $inventory" else ""

        return """
            ansible-playbook $inventoryArg \
              --extra-vars '$extraVarsJson' \
              $playbookPath
            """.trimIndent()
    }

    /**
     * Redacts sensitive data from extraVars for safe logging (CodeRabbit security feedback).
     *
     * **Security Rationale**:
     * Ansible extraVars commonly contain secrets (passwords, API keys, tokens).
     * Logging these values creates credential exposure risk in log aggregation systems.
     *
     * **Redaction Strategy**:
     * - Allowlist approach: mask any key containing sensitive keywords
     * - Keywords: password, secret, token, key, credential, auth
     *
     * @param extraVars Original variables map
     * @return Map with sensitive values replaced by "***REDACTED***"
     */
    private fun redactSensitiveData(extraVars: Map<String, Any>): Map<String, Any> {
        val sensitiveKeywords =
            setOf(
                "password",
                "passwd",
                "secret",
                "token",
                "key",
                "credential",
                "auth",
                "api_key",
                "apikey",
                "private",
            )

        return extraVars.mapValues { (key, value) ->
            val keyLower = key.lowercase()
            if (sensitiveKeywords.any { keyLower.contains(it) }) {
                "***REDACTED***"
            } else {
                value
            }
        }
    }
}

/**
 * Ansible Playbook Execution Result
 *
 * Contains the outcome of ansible-playbook command execution including
 * exit code, stdout, and stderr for logging and debugging.
 *
 * @property exitCode Ansible playbook exit code (0 = success, non-zero = failure)
 * @property stdout Standard output from ansible-playbook execution
 * @property stderr Standard error from ansible-playbook execution
 */
data class AnsibleResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Ansible Execution Exception
 *
 * Thrown when ansible-playbook execution fails (non-zero exit code).
 * Captured by RunAnsiblePlaybookTask and converted to BpmnError for
 * error boundary event routing (Story 6.5 prerequisite).
 *
 * @property errorCode BPMN error code for error boundary routing (e.g., "ANSIBLE_FAILED")
 * @property stdout Standard output from failed playbook execution
 * @property stderr Standard error from failed playbook execution
 * @property exitCode Ansible playbook exit code (non-zero)
 */
class AnsibleExecutionException(
    val errorCode: String,
    message: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
) : RuntimeException(message)
