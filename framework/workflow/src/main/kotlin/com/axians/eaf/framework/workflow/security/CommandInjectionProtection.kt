package com.axians.eaf.framework.workflow.security

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.io.File
import java.nio.file.Paths

/**
 * Command Injection protection for workflow engine.
 *
 * Implements OWASP A05:2025 - Security Misconfiguration recommendations:
 * - Validate workflow parameters against injection attacks
 * - Block shell metacharacters in user input
 * - Sanitize file paths
 * - Validate environment variables
 * - Use allowlists for external commands
 *
 * Reference: docs/security/owasp-top-10-2025-compliance.md
 *
 * @since 1.0.0
 */
class CommandInjectionProtection(
    private val properties: CommandInjectionProtectionProperties,
) {
    private val logger = LoggerFactory.getLogger(CommandInjectionProtection::class.java)

    /**
     * Validate a workflow parameter value.
     *
     * @param parameterName The parameter name
     * @param value The parameter value
     * @throws CommandInjectionException if the value contains malicious patterns
     */
    fun validateParameter(
        parameterName: String,
        value: String,
    ) {
        if (!properties.enabled) {
            return
        }

        // Check for shell metacharacters
        if (containsShellMetacharacters(value)) {
            logger.warn(
                "Blocked command injection attempt: parameter={}, value={}",
                parameterName,
                sanitizeForLog(value),
            )
            throw CommandInjectionException(
                "Parameter '$parameterName' contains forbidden characters",
            )
        }

        // Check for null bytes
        if (value.contains('\u0000')) {
            throw CommandInjectionException(
                "Parameter '$parameterName' contains null byte",
            )
        }

        // Check maximum length
        if (value.length > properties.maxParameterLength) {
            throw CommandInjectionException(
                "Parameter '$parameterName' exceeds maximum length of ${properties.maxParameterLength}",
            )
        }
    }

    /**
     * Validate an external command before execution.
     *
     * @param command The command to validate
     * @throws CommandInjectionException if the command is not allowed
     */
    fun validateCommand(command: String) {
        if (!properties.enabled) {
            return
        }

        val commandName =
            command.split(" ").firstOrNull()
                ?: throw CommandInjectionException("Empty command")

        // Check if command is in allowlist
        if (properties.allowedCommands.isNotEmpty()) {
            if (commandName !in properties.allowedCommands) {
                logger.warn("Blocked execution of non-whitelisted command: {}", commandName)
                throw CommandInjectionException(
                    "Command '$commandName' is not in allowed commands list",
                )
            }
        }

        // Check if command is explicitly blocked
        if (commandName in properties.blockedCommands) {
            logger.warn("Blocked execution of explicitly forbidden command: {}", commandName)
            throw CommandInjectionException(
                "Command '$commandName' is explicitly blocked",
            )
        }
    }

    /**
     * Sanitize a file path to prevent path traversal attacks.
     *
     * @param path The file path to sanitize
     * @param baseDirectory The base directory (optional)
     * @return The sanitized canonical path
     * @throws CommandInjectionException if the path is invalid or attempts traversal
     */
    fun sanitizeFilePath(
        path: String,
        baseDirectory: String? = null,
    ): String {
        if (!properties.enabled) {
            return path
        }

        try {
            // Normalize the path
            val normalizedPath = Paths.get(path).normalize().toString()

            // Check for path traversal attempts
            if (normalizedPath.contains("..")) {
                throw CommandInjectionException(
                    "File path contains path traversal: $path",
                )
            }

            // Check for absolute paths when base directory is specified
            if (baseDirectory != null) {
                val canonicalPath = File(normalizedPath).canonicalPath
                val canonicalBase = File(baseDirectory).canonicalPath

                if (!canonicalPath.startsWith(canonicalBase)) {
                    throw CommandInjectionException(
                        "File path escapes base directory: $path",
                    )
                }
            }

            // Check for null bytes
            if (normalizedPath.contains('\u0000')) {
                throw CommandInjectionException(
                    "File path contains null byte",
                )
            }

            return normalizedPath
        } catch (ex: CommandInjectionException) {
            logger.warn("Blocked path traversal attempt: path={}", path)
            throw ex
        } catch (ex: Exception) {
            logger.error("Error sanitizing file path: path={}", path, ex)
            throw CommandInjectionException("Invalid file path: ${ex.message}", ex)
        }
    }

    /**
     * Validate an environment variable value.
     *
     * @param name The environment variable name
     * @param value The environment variable value
     * @throws CommandInjectionException if the value is invalid
     */
    fun validateEnvironmentVariable(
        name: String,
        value: String,
    ) {
        if (!properties.enabled) {
            return
        }

        // Check variable name
        if (!isValidEnvironmentVariableName(name)) {
            throw CommandInjectionException(
                "Invalid environment variable name: $name",
            )
        }

        // Check for shell metacharacters in value
        if (containsShellMetacharacters(value)) {
            logger.warn(
                "Blocked environment variable with shell metacharacters: name={}, value={}",
                name,
                sanitizeForLog(value),
            )
            throw CommandInjectionException(
                "Environment variable '$name' contains forbidden characters",
            )
        }

        // Check maximum length
        if (value.length > properties.maxEnvironmentVariableLength) {
            throw CommandInjectionException(
                "Environment variable '$name' exceeds maximum length",
            )
        }
    }

    private fun containsShellMetacharacters(value: String): Boolean = SHELL_METACHARACTERS.any { value.contains(it) }

    private fun isValidEnvironmentVariableName(name: String): Boolean {
        // Environment variable names should contain only alphanumeric and underscore
        return name.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*$"))
    }

    private fun sanitizeForLog(value: String): String {
        // Truncate and mask sensitive parts for logging
        return if (value.length > 50) {
            "${value.take(50)}... [truncated]"
        } else {
            value
        }
    }

    companion object {
        /**
         * Shell metacharacters that could enable command injection.
         */
        private val SHELL_METACHARACTERS =
            listOf(
                ";", // Command separator
                "&", // Background execution
                "|", // Pipe
                "`", // Command substitution
                "$", // Variable expansion
                "(", // Subshell
                ")", // Subshell
                "<", // Input redirection
                ">", // Output redirection
                "\n", // Newline
                "\r", // Carriage return
                "\\", // Escape character
                "!", // History expansion (bash)
            )
    }
}

/**
 * Configuration properties for command injection protection.
 */
@ConfigurationProperties(prefix = "eaf.security.command-injection")
data class CommandInjectionProtectionProperties(
    /**
     * Enable/disable command injection protection.
     * Default: true
     */
    val enabled: Boolean = true,
    /**
     * Maximum length for workflow parameters.
     * Default: 1000 characters
     */
    val maxParameterLength: Int = 1000,
    /**
     * Maximum length for environment variables.
     * Default: 1000 characters
     */
    val maxEnvironmentVariableLength: Int = 1000,
    /**
     * Allowed external commands (whitelist).
     * If empty, all commands except blocked ones are allowed.
     * Example: ["git", "docker", "kubectl"]
     */
    val allowedCommands: Set<String> = emptySet(),
    /**
     * Explicitly blocked commands.
     * Example: ["rm", "dd", "mkfs", "format"]
     */
    val blockedCommands: Set<String> =
        setOf(
            "rm", // Remove files
            "dd", // Disk operations
            "mkfs", // Format filesystem
            "format", // Format filesystem
            "fdisk", // Partition disk
            "mkswap", // Create swap
            "shutdown", // System shutdown
            "reboot", // System reboot
            "halt", // System halt
            "poweroff", // System poweroff
        ),
)

/**
 * Exception thrown when command injection is detected.
 */
class CommandInjectionException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)
