package com.axians.eaf.framework.security.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.ApplicationArguments
import org.springframework.core.env.Environment

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
 * OWASP A02:2025 - Security Misconfiguration
 *
 * @since 1.0.0
 */
class SecurityConfigurationValidatorTest : FunSpec({

    context("Profile Validation") {
        test("should accept production profile without dev/test profiles") {
            // Given: Production profile only
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://keycloak.production.com/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("eaf_user")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("strong-password-123")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception
            validator.run(args)
        }

        test("should reject production profile with dev profile") {
            // Given: Production profile with dev profile
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod", "dev"))

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            val exception = shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
            exception.message shouldContain "Fix the errors above"
        }

        test("should reject production profile with test profile") {
            // Given: Production profile with test profile
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod", "test"))

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should accept dev profile") {
            // Given: Dev profile
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://localhost:8080/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://localhost:5432/eaf")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("postgres")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("postgres")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception (warnings are ok)
            validator.run(args)
        }
    }

    context("SSL/TLS Validation") {
        test("should reject disabled SSL in production") {
            // Given: Production without SSL
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject SSL without keystore in production") {
            // Given: SSL enabled but no keystore
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn(null)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject SSL without keystore password in production") {
            // Given: SSL enabled with keystore but no password
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn(null)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should accept disabled SSL in dev") {
            // Given: Dev profile without SSL
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://localhost:8080/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://localhost:5432/eaf")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("postgres")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("postgres")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception (only warning)
            validator.run(args)
        }
    }

    context("Security Headers Validation") {
        test("should reject disabled security headers in production") {
            // Given: Production with disabled headers
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(false)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should accept enabled security headers") {
            // Given: Headers enabled
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://localhost:8080/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://localhost:5432/eaf")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("postgres")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("postgres")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception
            validator.run(args)
        }
    }

    context("JWT Configuration Validation") {
        test("should reject missing JWT issuer URI") {
            // Given: Missing issuer URI
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")).thenReturn(null)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject HTTP issuer URI in production") {
            // Given: HTTP issuer in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://keycloak.com/realms/eaf")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject localhost issuer URI in production") {
            // Given: Localhost issuer in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://localhost:8443/realms/eaf")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should accept HTTPS issuer URI in production") {
            // Given: Valid HTTPS issuer in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://keycloak.production.com/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("eaf_user")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("strong-password-123")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception
            validator.run(args)
        }
    }

    context("Database Configuration Validation") {
        test("should reject missing database URL") {
            // Given: Missing database URL
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://localhost:8080/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url")).thenReturn(null)

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject default username in production") {
            // Given: Default username (postgres) in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://keycloak.production.com/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("postgres")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("strong-password-123")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should reject default password in production") {
            // Given: Default password in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://keycloak.production.com/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("eaf_user")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("password")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When/Then: Should throw exception
            shouldThrow<SecurityConfigurationException> {
                validator.run(args)
            }
        }

        test("should accept default credentials in dev") {
            // Given: Default credentials in dev
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("dev"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(false)
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("http://localhost:8080/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://localhost:5432/eaf")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("postgres")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("postgres")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

            // When: Validation runs
            // Then: Should not throw exception (only warnings)
            validator.run(args)
        }

        test("should accept strong credentials and SSL in production") {
            // Given: Strong credentials and SSL in production
            val environment = mock<Environment>()
            whenever(environment.activeProfiles).thenReturn(arrayOf("prod"))
            whenever(environment.getProperty("server.ssl.enabled", Boolean::class.java, false)).thenReturn(true)
            whenever(environment.getProperty("server.ssl.key-store")).thenReturn("/path/to/keystore.p12")
            whenever(environment.getProperty("server.ssl.key-store-password")).thenReturn("secure-password")
            whenever(environment.getProperty("eaf.security.headers.enabled", Boolean::class.java, true)).thenReturn(true)
            whenever(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .thenReturn("https://keycloak.production.com/realms/eaf")
            whenever(environment.getProperty("spring.datasource.url"))
                .thenReturn("jdbc:postgresql://db.prod.com:5432/eaf?sslmode=require")
            whenever(environment.getProperty("spring.datasource.username")).thenReturn("eaf_user")
            whenever(environment.getProperty("spring.datasource.password")).thenReturn("Str0ng-P@ssw0rd-2025!")

            val validator = SecurityConfigurationValidatorAutoConfiguration.SecurityConfigurationValidator(environment)
            val args = mock<ApplicationArguments>()

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
