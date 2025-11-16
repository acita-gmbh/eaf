package com.axians.eaf.framework.security.validation

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Validates security configuration at application startup.
 *
 * Implements OWASP A02:2025 - Security Misconfiguration:
 * - Fails fast on insecure production configuration
 * - Warns on development-only settings in production
 * - Validates SSL/TLS enablement
 * - Checks for default/weak credentials
 *
 * Reference: docs/security/owasp-top-10-2025-compliance.md
 *
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "eaf.security.validation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class SecurityConfigurationValidatorAutoConfiguration {

    @Component
    class SecurityConfigurationValidator(
        private val environment: Environment
    ) : ApplicationRunner {

        private val logger = LoggerFactory.getLogger(SecurityConfigurationValidator::class.java)

        override fun run(args: ApplicationArguments) {
            logger.info("Running security configuration validation...")

            val validationResults = mutableListOf<ValidationResult>()

            // Validate profile-specific security requirements
            validationResults.add(validateProfiles())

            // Validate SSL/TLS configuration
            validationResults.add(validateSslConfiguration())

            // Validate security headers
            validationResults.add(validateSecurityHeaders())

            // Validate JWT configuration
            validationResults.add(validateJwtConfiguration())

            // Validate database configuration
            validationResults.add(validateDatabaseConfiguration())

            // Report results
            val failures = validationResults.filter { it.level == ValidationLevel.ERROR }
            val warnings = validationResults.filter { it.level == ValidationLevel.WARNING }

            if (failures.isNotEmpty()) {
                logger.error("Security configuration validation FAILED with ${failures.size} error(s):")
                failures.forEach { logger.error("  ❌ ${it.message}") }
                throw SecurityConfigurationException(
                    "Security configuration validation failed. " +
                        "Fix the errors above before starting the application."
                )
            }

            if (warnings.isNotEmpty()) {
                logger.warn("Security configuration validation completed with ${warnings.size} warning(s):")
                warnings.forEach { logger.warn("  ⚠️ ${it.message}") }
            }

            if (failures.isEmpty() && warnings.isEmpty()) {
                logger.info("✅ Security configuration validation passed")
            }
        }

        private fun validateProfiles(): ValidationResult {
            val activeProfiles = environment.activeProfiles

            return when {
                "prod" in activeProfiles -> {
                    // Production profile must not include dev/test profiles
                    val invalidProfiles = activeProfiles.filter { it in listOf("dev", "test", "local") }
                    if (invalidProfiles.isNotEmpty()) {
                        ValidationResult.error(
                            "Production profile includes development profiles: $invalidProfiles. " +
                                "Remove development profiles from production deployment."
                        )
                    } else {
                        ValidationResult.success("Production profile configuration is valid")
                    }
                }
                activeProfiles.isEmpty() -> {
                    ValidationResult.warning(
                        "No active Spring profiles detected. Using default configuration. " +
                            "Consider using explicit profiles (dev, test, prod)."
                    )
                }
                else -> {
                    ValidationResult.success("Profile configuration: ${activeProfiles.joinToString()}")
                }
            }
        }

        private fun validateSslConfiguration(): ValidationResult {
            val activeProfiles = environment.activeProfiles
            val sslEnabled = environment.getProperty("server.ssl.enabled", Boolean::class.java, false)

            return when {
                "prod" in activeProfiles && !sslEnabled -> {
                    ValidationResult.error(
                        "SSL/TLS is DISABLED in production profile. " +
                            "Enable SSL with server.ssl.enabled=true and configure certificates."
                    )
                }
                "prod" in activeProfiles && sslEnabled -> {
                    // Validate certificate configuration
                    val keyStore = environment.getProperty("server.ssl.key-store")
                    val keyStorePassword = environment.getProperty("server.ssl.key-store-password")

                    when {
                        keyStore.isNullOrBlank() -> {
                            ValidationResult.error(
                                "SSL enabled but server.ssl.key-store is not configured"
                            )
                        }
                        keyStorePassword.isNullOrBlank() -> {
                            ValidationResult.error(
                                "SSL enabled but server.ssl.key-store-password is not configured"
                            )
                        }
                        else -> {
                            ValidationResult.success("SSL/TLS configuration is valid")
                        }
                    }
                }
                "dev" in activeProfiles || "test" in activeProfiles -> {
                    if (sslEnabled) {
                        ValidationResult.success("SSL/TLS enabled in development/test environment")
                    } else {
                        ValidationResult.warning(
                            "SSL/TLS is disabled in development/test environment. " +
                                "Consider enabling for production-like testing."
                        )
                    }
                }
                else -> {
                    ValidationResult.success("SSL configuration valid for current profile")
                }
            }
        }

        private fun validateSecurityHeaders(): ValidationResult {
            val headersEnabled = environment.getProperty(
                "eaf.security.headers.enabled",
                Boolean::class.java,
                true
            )

            return when {
                !headersEnabled && "prod" in environment.activeProfiles -> {
                    ValidationResult.error(
                        "Security headers are DISABLED in production. " +
                            "Enable with eaf.security.headers.enabled=true"
                    )
                }
                !headersEnabled -> {
                    ValidationResult.warning(
                        "Security headers are disabled. " +
                            "Enable for better security posture."
                    )
                }
                else -> {
                    ValidationResult.success("Security headers are enabled")
                }
            }
        }

        private fun validateJwtConfiguration(): ValidationResult {
            val issuerUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")

            return when {
                issuerUri.isNullOrBlank() -> {
                    ValidationResult.error(
                        "JWT issuer URI is not configured. " +
                            "Set spring.security.oauth2.resourceserver.jwt.issuer-uri"
                    )
                }
                issuerUri.startsWith("http://") && "prod" in environment.activeProfiles -> {
                    ValidationResult.error(
                        "JWT issuer URI uses HTTP in production: $issuerUri. " +
                            "Use HTTPS for production Keycloak."
                    )
                }
                issuerUri.contains("localhost") && "prod" in environment.activeProfiles -> {
                    ValidationResult.error(
                        "JWT issuer URI points to localhost in production: $issuerUri. " +
                            "Configure production Keycloak URL."
                    )
                }
                else -> {
                    ValidationResult.success("JWT configuration is valid")
                }
            }
        }

        private fun validateDatabaseConfiguration(): ValidationResult {
            val activeProfiles = environment.activeProfiles
            val datasourceUrl = environment.getProperty("spring.datasource.url")
            val datasourceUsername = environment.getProperty("spring.datasource.username")
            val datasourcePassword = environment.getProperty("spring.datasource.password")

            // Check for default credentials
            val defaultUsernames = listOf("postgres", "sa", "admin", "root")
            val defaultPasswords = listOf("postgres", "password", "admin", "root", "")

            return when {
                datasourceUrl.isNullOrBlank() -> {
                    ValidationResult.error("Database URL is not configured")
                }
                datasourceUsername in defaultUsernames && "prod" in activeProfiles -> {
                    ValidationResult.error(
                        "Database uses default username '$datasourceUsername' in production. " +
                            "Use a custom username for production databases."
                    )
                }
                datasourcePassword in defaultPasswords && "prod" in activeProfiles -> {
                    ValidationResult.error(
                        "Database uses weak/default password in production. " +
                            "Configure a strong password."
                    )
                }
                !datasourceUrl.contains("ssl=true") && !datasourceUrl.contains("sslmode=require")
                    && "prod" in activeProfiles -> {
                    ValidationResult.warning(
                        "Database connection does not enforce SSL in production: $datasourceUrl. " +
                            "Add sslmode=require for encrypted connections."
                    )
                }
                datasourceUsername in defaultUsernames -> {
                    ValidationResult.warning(
                        "Database uses default username '$datasourceUsername' in development. " +
                            "This is acceptable for dev/test but not for production."
                    )
                }
                else -> {
                    ValidationResult.success("Database configuration is valid")
                }
            }
        }
    }
}

/**
 * Result of a security configuration validation check.
 */
data class ValidationResult(
    val level: ValidationLevel,
    val message: String
) {
    companion object {
        fun success(message: String) = ValidationResult(ValidationLevel.SUCCESS, message)
        fun warning(message: String) = ValidationResult(ValidationLevel.WARNING, message)
        fun error(message: String) = ValidationResult(ValidationLevel.ERROR, message)
    }
}

enum class ValidationLevel {
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Exception thrown when security configuration validation fails.
 */
class SecurityConfigurationException(message: String) : RuntimeException(message)
