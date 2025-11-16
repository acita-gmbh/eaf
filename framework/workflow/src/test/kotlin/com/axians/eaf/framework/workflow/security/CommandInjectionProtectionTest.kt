package com.axians.eaf.framework.workflow.security

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for Command Injection protection.
 *
 * Tests:
 * - Shell metacharacter blocking
 * - Parameter validation
 * - Command allowlist/blocklist
 * - File path sanitization
 * - Path traversal protection
 * - Environment variable validation
 *
 * OWASP A05:2025 - Security Misconfiguration
 *
 * @since 1.0.0
 */
class CommandInjectionProtectionTest : FunSpec({

    context("Parameter Validation") {
        test("should allow safe parameter values") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Safe values should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.validateParameter("name", "John Doe")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.validateParameter("email", "user@example.com")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.validateParameter("number", "12345")
            }
        }

        test("should block parameters with semicolon") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Semicolon should be blocked
            val exception = shouldThrow<CommandInjectionException> {
                protection.validateParameter("cmd", "test; rm -rf /")
            }
            exception.message shouldContain "forbidden characters"
        }

        test("should block parameters with pipe") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Pipe should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("input", "data | cat /etc/passwd")
            }
        }

        test("should block parameters with backticks") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Backticks should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("value", "test`whoami`")
            }
        }

        test("should block parameters with dollar sign") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Dollar sign should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("var", "test$(whoami)")
            }
        }

        test("should block parameters with redirects") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Redirects should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("output", "data > /tmp/output")
            }
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("input", "cat < /etc/passwd")
            }
        }

        test("should block parameters with null bytes") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Null bytes should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("value", "test\u0000malicious")
            }
        }

        test("should enforce maximum parameter length") {
            // Given: Protection with max length 100
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(maxParameterLength = 100)
            )

            // When/Then: Long parameters should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateParameter("data", "x".repeat(101))
            }
        }
    }

    context("Command Validation") {
        test("should allow all commands when no whitelist") {
            // Given: Protection without whitelist
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(
                    allowedCommands = emptySet()
                )
            )

            // When/Then: Commands should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.validateCommand("git status")
            }
        }

        test("should block commands not in whitelist") {
            // Given: Protection with whitelist
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(
                    allowedCommands = setOf("git", "docker")
                )
            )

            // When/Then: Whitelisted command should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.validateCommand("git status")
            }

            // And: Non-whitelisted command should be blocked
            val exception = shouldThrow<CommandInjectionException> {
                protection.validateCommand("kubectl get pods")
            }
            exception.message shouldContain "not in allowed commands list"
        }

        test("should block explicitly forbidden commands") {
            // Given: Protection with blocklist
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(
                    blockedCommands = setOf("rm", "dd")
                )
            )

            // When/Then: Blocked commands should be rejected
            val exception = shouldThrow<CommandInjectionException> {
                protection.validateCommand("rm -rf /")
            }
            exception.message shouldContain "explicitly blocked"
        }

        test("should block dangerous commands by default") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Dangerous commands should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateCommand("rm -rf /tmp")
            }
            shouldThrow<CommandInjectionException> {
                protection.validateCommand("shutdown now")
            }
        }
    }

    context("File Path Sanitization") {
        test("should allow safe file paths") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Safe paths should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.sanitizeFilePath("data/file.txt")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.sanitizeFilePath("/tmp/output.log")
            }
        }

        test("should block path traversal attempts") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Path traversal should be blocked
            val exception = shouldThrow<CommandInjectionException> {
                protection.sanitizeFilePath("data/../../etc/passwd")
            }
            exception.message shouldContain "path traversal"
        }

        test("should normalize paths") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When: Sanitize path with redundant separators
            val sanitized = protection.sanitizeFilePath("data//file.txt")

            // Then: Path should be normalized
            sanitized shouldBe "data/file.txt"
        }

        test("should enforce base directory constraint") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Paths escaping base directory should be blocked
            // Note: This test is system-dependent and may need adjustment
            val baseDir = "/tmp"
            shouldThrow<CommandInjectionException> {
                protection.sanitizeFilePath("/etc/passwd", baseDir)
            }
        }

        test("should block paths with null bytes") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Null bytes should be blocked
            shouldThrow<CommandInjectionException> {
                protection.sanitizeFilePath("file.txt\u0000.sh")
            }
        }
    }

    context("Environment Variable Validation") {
        test("should allow safe environment variables") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Safe env vars should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("APP_ENV", "production")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("DATABASE_URL", "postgresql://localhost:5432/db")
            }
        }

        test("should block invalid variable names") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Invalid names should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("APP-ENV", "test")
            }
            shouldThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("123VAR", "test")
            }
        }

        test("should block environment values with shell metacharacters") {
            // Given: Default protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties()
            )

            // When/Then: Shell metacharacters should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("CMD", "test; rm -rf /")
            }
        }

        test("should enforce maximum length for environment variables") {
            // Given: Protection with max length 100
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(
                    maxEnvironmentVariableLength = 100
                )
            )

            // When/Then: Long values should be blocked
            shouldThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("LONG_VAR", "x".repeat(101))
            }
        }
    }

    context("Disabled Protection") {
        test("should allow everything when disabled") {
            // Given: Disabled protection
            val protection = CommandInjectionProtection(
                CommandInjectionProtectionProperties(enabled = false)
            )

            // When/Then: All patterns should be allowed
            shouldNotThrow<CommandInjectionException> {
                protection.validateParameter("cmd", "test; rm -rf /")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.validateCommand("rm -rf /")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.sanitizeFilePath("../../etc/passwd")
            }
            shouldNotThrow<CommandInjectionException> {
                protection.validateEnvironmentVariable("VAR", "test`whoami`")
            }
        }
    }
})
