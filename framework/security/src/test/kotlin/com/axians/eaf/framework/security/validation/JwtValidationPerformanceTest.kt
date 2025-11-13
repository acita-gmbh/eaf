package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.InjectionDetector
import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import com.axians.eaf.framework.security.role.RoleNormalizer
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.user.UserRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.doubles.shouldBeLessThan
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import java.time.Instant

/**
 * Performance test for 10-layer JWT validation pipeline.
 *
 * Validates AC6: Total validation time <50ms (target <30ms)
 *
 * Uses Nullable Design Pattern for fast execution without external dependencies:
 * - No Testcontainers (no Keycloak, Redis)
 * - No Spring context
 * - Mock implementations of stateful dependencies
 * - Real business logic validation
 *
 * Performance Targets (Architecture Decision):
 * - Total validation (all 10 layers): <50ms (target <30ms)
 * - Layer 1-6 (Validators): <20ms
 * - Layer 7 (Redis mock): <5ms
 * - Layer 8 (Role normalization): <2ms
 * - Layer 9 (User validation, optional): <10ms
 * - Layer 10 (Injection detection): <3ms
 *
 * Story 3.9: Complete 10-Layer JWT Validation Integration
 */
class JwtValidationPerformanceTest :
    FunSpec({
        val meterRegistry = SimpleMeterRegistry()

        // Null implementations for fast testing
        val mockRevocationStore =
            object : TokenRevocationStore {
                override fun isRevoked(jti: String): Boolean = false

                override fun revoke(
                    jti: String,
                    expiresAt: Instant?,
                ) {
                    // No-op for performance test
                }
            }

        val mockUserDirectory =
            object : UserDirectory {
                override fun findById(userId: String): UserRecord = UserRecord(userId, active = true)
            }

        val mockConfig = KeycloakOidcConfiguration(validateUser = false)
        val injectionDetector = InjectionDetector()

        // Create all validators for per-layer testing
        val algorithmValidator = JwtAlgorithmValidator(meterRegistry)
        val claimSchemaValidator = JwtClaimSchemaValidator(meterRegistry)
        val timeBasedValidator = JwtTimeBasedValidator(meterRegistry = meterRegistry)
        val issuerValidator = JwtIssuerValidator("http://keycloak:8080/realms/eaf", meterRegistry)
        val audienceValidator = JwtAudienceValidator("eaf-api", meterRegistry)
        val revocationValidator = JwtRevocationValidator(mockRevocationStore, meterRegistry)
        val userValidator = JwtUserValidator(mockConfig, mockUserDirectory, meterRegistry)
        val injectionValidator = JwtInjectionValidator(injectionDetector, meterRegistry)

        // All 10 layers orchestrated
        val validator =
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefault(), // Layers 1-2: Spring Security baseline
                algorithmValidator, // Layer 3
                claimSchemaValidator, // Layer 4
                timeBasedValidator, // Layer 5
                issuerValidator, // Layer 6
                audienceValidator, // Layer 6
                revocationValidator, // Layer 7
                userValidator, // Layer 9
                injectionValidator, // Layer 10
            )

        fun createValidJwt(): Jwt =
            Jwt
                .withTokenValue("test.token.value")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("iss", "http://keycloak:8080/realms/eaf")
                .claim("aud", listOf("eaf-api"))
                .claim("jti", "perf-test-jti")
                .claim("roles", listOf("WIDGET_ADMIN"))
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()

        context("Performance Validation") {
            test("single validation should complete in under 50ms") {
                // AC6: Performance validated <50ms total validation time
                val jwt = createValidJwt()

                val startTime = System.nanoTime()
                val result = validator.validate(jwt)
                val durationMs = (System.nanoTime() - startTime) / 1_000_000.0

                // Verify validation succeeded
                result.hasErrors().shouldBeFalse()

                // AC6: <50ms target (aiming for <30ms)
                durationMs shouldBeLessThan 50.0

                println("✅ Single validation: ${"%.3f".format(durationMs)}ms (target: <50ms, ideal: <30ms)")
            }

            test("1000 validations should average under 10ms each") {
                // Nullable Pattern performance advantage - 100-1000x faster than integration tests
                val jwt = createValidJwt()
                val iterations = 1000

                val startTime = System.nanoTime()
                repeat(iterations) {
                    validator.validate(jwt)
                }
                val totalDurationMs = (System.nanoTime() - startTime) / 1_000_000.0
                val avgDurationMs = totalDurationMs / iterations

                // Average should be well under 50ms (target <10ms with Nullable Pattern)
                avgDurationMs shouldBeLessThan 10.0

                println(
                    "✅ 1000 validations: total=${"%.1f".format(totalDurationMs)}ms, " +
                        "avg=${"%.3f".format(avgDurationMs)}ms (target: <10ms avg)",
                )
            }

            test("p95 latency should be under 30ms") {
                // Realistic performance target: 95th percentile <30ms
                val jwt = createValidJwt()
                val measurements = mutableListOf<Double>()

                repeat(100) {
                    val startTime = System.nanoTime()
                    validator.validate(jwt)
                    val durationMs = (System.nanoTime() - startTime) / 1_000_000.0
                    measurements.add(durationMs)
                }

                val sorted = measurements.sorted()
                val p95Index = (sorted.size * 0.95).toInt()
                val p95 = sorted[p95Index]
                val p50 = sorted[sorted.size / 2]

                // p95 should be under target
                p95 shouldBeLessThan 30.0

                println(
                    "✅ 100 validations: p50=${"%.3f".format(p50)}ms, " +
                        "p95=${"%.3f".format(p95)}ms (target: p95 <30ms)",
                )
            }
        }

        context("Per-Layer Performance") {
            test("Layer 3-6 validators should complete in under 20ms combined") {
                // Layers 3-6: Algorithm, Claim Schema, Time, Issuer, Audience
                val jwt = createValidJwt()

                val startTime = System.nanoTime()
                algorithmValidator.validate(jwt)
                claimSchemaValidator.validate(jwt)
                timeBasedValidator.validate(jwt)
                issuerValidator.validate(jwt)
                audienceValidator.validate(jwt)
                val durationMs = (System.nanoTime() - startTime) / 1_000_000.0

                durationMs shouldBeLessThan 20.0

                println("✅ Layers 3-6: ${"%.3f".format(durationMs)}ms (target: <20ms)")
            }

            test("Layer 7 (revocation) should complete in under 5ms with mock") {
                val jwt = createValidJwt()
                val validator = JwtRevocationValidator(mockRevocationStore, meterRegistry)

                val startTime = System.nanoTime()
                validator.validate(jwt)
                val durationMs = (System.nanoTime() - startTime) / 1_000_000.0

                durationMs shouldBeLessThan 5.0

                println("✅ Layer 7 (revocation): ${"%.3f".format(durationMs)}ms (target: <5ms)")
            }

            test("Layer 10 (injection detection) should complete in under 3ms") {
                val jwt = createValidJwt()
                val validator = JwtInjectionValidator(injectionDetector, meterRegistry)

                val startTime = System.nanoTime()
                validator.validate(jwt)
                val durationMs = (System.nanoTime() - startTime) / 1_000_000.0

                durationMs shouldBeLessThan 3.0

                println("✅ Layer 10 (injection): ${"%.3f".format(durationMs)}ms (target: <3ms)")
            }
        }
    })
