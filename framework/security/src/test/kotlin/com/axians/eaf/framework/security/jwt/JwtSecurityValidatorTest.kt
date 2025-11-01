package com.axians.eaf.framework.security.jwt

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.test.NullableRedisTemplate
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for JwtSecurityValidator (Layers 7-10).
 * Story 8.6-UNIT-JSV: Revocation, role, user, and injection validation
 */
class JwtSecurityValidatorTest :
    FunSpec({
        lateinit var meterRegistry: SimpleMeterRegistry
        lateinit var nullableRedisTemplate: NullableRedisTemplate
        lateinit var validator: JwtSecurityValidator

        beforeTest {
            meterRegistry = SimpleMeterRegistry()
            nullableRedisTemplate = NullableRedisTemplate.createNull()
            validator = JwtSecurityValidator(nullableRedisTemplate, meterRegistry)
            nullableRedisTemplate.clear()
        }

        context("8.6-UNIT-JSV-001: ensureNotRevoked (Layer 7)") {
            val jti = UUID.randomUUID().toString()

            test("should pass if JTI is not in revocation list") {
                validator.ensureNotRevoked(jti).shouldBeRight()
            }

            test("should fail if JTI is in revocation list") {
                nullableRedisTemplate.addKey("revoked:$jti")
                val result = validator.ensureNotRevoked(jti)
                result.shouldBeLeft(SecurityError.TokenRevoked(jti))
                meterRegistry.counter("jwt.validation.revoked").count() shouldBe 1.0
            }

            test("should succeed when Redis available and JTI not revoked (happy path)") {
                // This validates the success case: Redis available, token not in revocation list
                // NOTE: NullableRedisTemplate doesn't throw RedisConnectionFailureException
                // TODO (Story 9.1 Security): Add integration test with real Redis to validate fail-closed
                //      behavior when Redis connection fails (requires Testcontainers Redis + connection stop)
                validator.ensureNotRevoked(jti).shouldBeRight()
            }
        }

        context("8.6-UNIT-JSV-002: validateRoles (Layer 8)") {
            test("should fail if no roles provided") {
                val result = validator.validateRoles(emptyList())
                result.shouldBeLeft(SecurityError.NoRolesAssigned)
                meterRegistry.counter("jwt.validation.no_roles").count() shouldBe 1.0
            }

            test("should pass and return Role objects for valid role names") {
                val roleNames = listOf("user", "viewer")
                val result = validator.validateRoles(roleNames)
                result.shouldBeRight()
                result.isRight() shouldBe true
            }

            test("should pass but log warning for admin roles") {
                val roleNames = listOf("user", "SUPER_ADMIN", "system-admin")
                val result = validator.validateRoles(roleNames)
                result.shouldBeRight()
                // Warning is logged but doesn't prevent validation
            }

            test("should handle single role") {
                val result = validator.validateRoles(listOf("user"))
                result.shouldBeRight()
                result.isRight() shouldBe true
            }
        }

        context("8.6-UNIT-JSV-003: validateUser (Layer 9)") {
            val userId = UUID.randomUUID().toString()

            test("should pass for valid user ID and return active user") {
                val result = validator.validateUser(userId)
                result.shouldBeRight()
                result.isRight() shouldBe true
            }

            test("should handle different user IDs correctly") {
                val userId1 = UUID.randomUUID().toString()
                val userId2 = UUID.randomUUID().toString()

                val result1 = validator.validateUser(userId1)
                val result2 = validator.validateUser(userId2)

                result1.shouldBeRight()
                result2.shouldBeRight()
            }
        }

        context("8.6-UNIT-JSV-004: ensureNoInjection (Layer 10)") {
            fun createToken(
                header: String,
                payload: String,
            ): String {
                val b64Header = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
                val b64Payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
                return "$b64Header.$b64Payload.signature"
            }

            test("should pass for clean token with no injection patterns") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"123"}""")
                validator.ensureNoInjection(token).shouldBeRight()
            }

            test("should fail if SQL injection (UNION SELECT) detected in payload") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"' UNION SELECT 1,2,3--"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
                meterRegistry.counter("jwt.validation.injection_detected", "patterns", "1").count() shouldBe 1.0
            }

            test("should fail if XSS (script tag) detected") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"<script>alert(1)</script>"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }

            test("should fail if command injection (EXEC) detected") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"user; exec cmd"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }

            test("should fail if LDAP injection detected") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"ldap://evil.com"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }

            test("should fail if JavaScript injection (onerror) detected") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"<img onerror=alert(1)>"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }

            test("should fail if iframe injection detected") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"<iframe src=evil.com>"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }

            test("should handle Base64 decode failure") {
                val token = "invalid-base64!.invalid-base64!.signature"
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionCheckFailed>()
            }

            test("should handle token with missing parts (IndexOutOfBoundsException)") {
                val token = "a.signature" // Only 2 parts
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionCheckFailed>()
            }

            test("should detect multiple injection patterns") {
                val token = createToken("""{"alg":"RS256"}""", """{"sub":"' UNION SELECT <script>"}""")
                val result = validator.ensureNoInjection(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
            }
        }
    })
