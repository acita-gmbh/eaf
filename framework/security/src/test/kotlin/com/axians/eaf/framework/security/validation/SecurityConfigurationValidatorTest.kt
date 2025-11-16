package com.axians.eaf.framework.security.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

/**
 * Unit tests for SecurityConfigurationValidator.
 *
 * Tests:
 * - Profile validation (production vs dev/test)
 * - SSL/TLS configuration validation
 * - JWT configuration validation
 * - Database configuration validation
 * - Validation result handling (success/warning/error)
 *
 * Uses Spring's StandardEnvironment (real test double, not mocks)
 * per EAF "No Mocks" policy.
 *
 * OWASP A02:2025 - Security Misconfiguration
 *
 * @since 1.0.0
 */
class SecurityConfigurationValidatorTest :
    FunSpec({

        fun createEnvironment(
            profiles: Array<String> = emptyArray(),
            properties: Map<String, Any> = emptyMap(),
        ): StandardEnvironment {
            val env = StandardEnvironment()
            env.setActiveProfiles(*profiles)
            env.propertySources.addFirst(MapPropertySource("test", properties))
            return env
        }

        context("Profile Validation") {
            test("should accept production profile without dev/test profiles") {
                // Given: Production profile only
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://keycloak.production.com/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require",
                                "spring.datasource.username" to "eaf_user",
                                "spring.datasource.password" to "strong-password-123",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception
                validator.run(args)
            }

            test("should reject production profile with dev profile") {
                // Given: Production profile with dev profile
                val env = createEnvironment(profiles = arrayOf("prod", "dev"))

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                val exception =
                    shouldThrow<SecurityConfigurationException> {
                        validator.run(args)
                    }
                exception.message shouldContain "Fix the errors above"
            }

            test("should reject production profile with test profile") {
                // Given: Production profile with test profile
                val env = createEnvironment(profiles = arrayOf("prod", "test"))

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should accept dev profile") {
                // Given: Dev profile
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://localhost:8080/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/eaf",
                                "spring.datasource.username" to "postgres",
                                "spring.datasource.password" to "postgres",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception (warnings are ok)
                validator.run(args)
            }
        }

        context("SSL/TLS Validation") {
            test("should reject disabled SSL in production") {
                // Given: Production without SSL
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties = mapOf("server.ssl.enabled" to false),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject SSL without keystore in production") {
                // Given: SSL enabled but no keystore
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                // No keystore property
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject SSL without keystore password in production") {
                // Given: SSL enabled with keystore but no password
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                // No password property
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should accept disabled SSL in dev") {
                // Given: Dev profile without SSL
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://localhost:8080/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/eaf",
                                "spring.datasource.username" to "postgres",
                                "spring.datasource.password" to "postgres",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception (only warning)
                validator.run(args)
            }
        }

        context("Security Headers Validation") {
            test("should reject disabled security headers in production") {
                // Given: Production with disabled headers
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to false,
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should accept enabled security headers") {
                // Given: Headers enabled
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://localhost:8080/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/eaf",
                                "spring.datasource.username" to "postgres",
                                "spring.datasource.password" to "postgres",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception
                validator.run(args)
            }
        }

        context("JWT Configuration Validation") {
            test("should reject missing JWT issuer URI") {
                // Given: Missing issuer URI
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                // No JWT issuer URI
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject HTTP issuer URI in production") {
                // Given: HTTP issuer in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://keycloak.com/realms/eaf",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject localhost issuer URI in production") {
                // Given: Localhost issuer in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://localhost:8443/realms/eaf",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should accept HTTPS issuer URI in production") {
                // Given: Valid HTTPS issuer in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://keycloak.production.com/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require",
                                "spring.datasource.username" to "eaf_user",
                                "spring.datasource.password" to "strong-password-123",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception
                validator.run(args)
            }
        }

        context("Database Configuration Validation") {
            test("should reject missing database URL") {
                // Given: Missing database URL
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://localhost:8080/realms/eaf",
                                // No datasource URL
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject default username in production") {
                // Given: Default username (postgres) in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://keycloak.production.com/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require",
                                "spring.datasource.username" to "postgres",
                                "spring.datasource.password" to "strong-password-123",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should reject default password in production") {
                // Given: Default password in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://keycloak.production.com/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require",
                                "spring.datasource.username" to "eaf_user",
                                "spring.datasource.password" to "password",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When/Then: Should throw exception
                shouldThrow<SecurityConfigurationException> {
                    validator.run(args)
                }
            }

            test("should accept default credentials in dev") {
                // Given: Default credentials in dev
                val env =
                    createEnvironment(
                        profiles = arrayOf("dev"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to false,
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "http://localhost:8080/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://localhost:5432/eaf",
                                "spring.datasource.username" to "postgres",
                                "spring.datasource.password" to "postgres",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception (only warnings)
                validator.run(args)
            }

            test("should accept strong credentials and SSL in production") {
                // Given: Strong credentials and SSL in production
                val env =
                    createEnvironment(
                        profiles = arrayOf("prod"),
                        properties =
                            mapOf(
                                "server.ssl.enabled" to true,
                                "server.ssl.key-store" to "/path/to/keystore.p12",
                                "server.ssl.key-store-password" to "secure-password",
                                "eaf.security.headers.enabled" to true,
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri" to
                                    "https://keycloak.production.com/realms/eaf",
                                "spring.datasource.url" to "jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require",
                                "spring.datasource.username" to "eaf_user",
                                "spring.datasource.password" to "Str0ng-P@ssw0rd-2025!",
                            ),
                    )

                val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(env)
                val args = DefaultApplicationArguments(*emptyArray<String>())

                // When: Validation runs
                // Then: Should not throw exception
                validator.run(args)
            }
        }

        context("ValidationResult") {
            test("should create success result") {
                // Given/When: Create success result
                val result = ValidationResult.success("Test passed")

                // Then: Should have SUCCESS level
                result.level shouldBe ValidationLevel.SUCCESS
                result.message shouldBe "Test passed"
            }

            test("should create warning result") {
                // Given/When: Create warning result
                val result = ValidationResult.warning("Test warning")

                // Then: Should have WARNING level
                result.level shouldBe ValidationLevel.WARNING
                result.message shouldBe "Test warning"
            }

            test("should create error result") {
                // Given/When: Create error result
                val result = ValidationResult.error("Test failed")

                // Then: Should have ERROR level
                result.level shouldBe ValidationLevel.ERROR
                result.message shouldBe "Test failed"
            }
        }
    })
