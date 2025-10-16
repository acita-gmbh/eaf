package com.axians.eaf.framework.security.jwt

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.test.JwtTestTokens
import com.axians.eaf.framework.security.test.NullableJwtDecoder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for JwtClaimsValidator (Layers 4-6).
 * Story 8.6-UNIT-JCV: Claim schema, expiration, and issuer/audience validation
 */
class JwtClaimsValidatorTest :
    FunSpec({
        lateinit var meterRegistry: SimpleMeterRegistry
        lateinit var validator: JwtClaimsValidator
        lateinit var nullableJwtDecoder: NullableJwtDecoder

        beforeTest {
            meterRegistry = SimpleMeterRegistry()
            validator = JwtClaimsValidator(meterRegistry)
            nullableJwtDecoder = NullableJwtDecoder.createNull()
        }

        context("8.6-UNIT-JCV-001: validateClaimSchema (Layer 4)") {
            test("should pass with valid JWT and extract all claims") {
                val token = JwtTestTokens.validRs256
                val jwt = nullableJwtDecoder.decode(token)
                val result = validator.validateClaimSchema(jwt)

                result.shouldBeRight()
                result.isRight() shouldBe true
                // Claims are validated, structure is correct
            }

            test("should fail if 'jti' claim is missing") {
                val token = JwtTestTokens.missingJti
                val jwt = nullableJwtDecoder.decode(token)
                validator.validateClaimSchema(jwt).shouldBeLeft(SecurityError.MissingClaim("jti"))
            }
        }

        context("8.6-UNIT-JCV-002: ensureNotExpired (Layer 5)") {
            val clockSkew = 60L
            val now = Instant.now()

            test("should pass for valid non-expired token") {
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = now.plusSeconds(300).epochSecond,
                        iat = now.minusSeconds(100).epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                validator.ensureNotExpired(claims).shouldBeRight()
            }

            test("should fail for expired token (beyond clock skew)") {
                val expiryTime = now.minusSeconds(clockSkew + 10)
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = expiryTime.epochSecond,
                        iat = now.minusSeconds(500).epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                val result = validator.ensureNotExpired(claims)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.TokenExpired>()
                meterRegistry.counter("jwt.validation.expired").count() shouldBe 1.0
            }

            test("should pass for token expired within clock skew tolerance") {
                val expiryTime = now.minusSeconds(clockSkew - 10)
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = expiryTime.epochSecond,
                        iat = now.minusSeconds(500).epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                validator.ensureNotExpired(claims).shouldBeRight()
            }

            test("should fail for future token (issued beyond clock skew)") {
                val issueTime = now.plusSeconds(clockSkew + 10)
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = issueTime.plusSeconds(300).epochSecond,
                        iat = issueTime.epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                val result = validator.ensureNotExpired(claims)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.FutureToken>()
                meterRegistry.counter("jwt.validation.future_token").count() shouldBe 1.0
            }

            test("should pass for future token within clock skew tolerance") {
                val issueTime = now.plusSeconds(clockSkew - 10)
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = issueTime.plusSeconds(300).epochSecond,
                        iat = issueTime.epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                validator.ensureNotExpired(claims).shouldBeRight()
            }

            test("should fail for token older than 24 hours") {
                val issueTime = now.minusSeconds(25 * 3600) // 25 hours in seconds
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = now.plusSeconds(300).epochSecond,
                        iat = issueTime.epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                val result = validator.ensureNotExpired(claims)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.TokenTooOld>()
                meterRegistry.counter("jwt.validation.token_too_old").count() shouldBe 1.0
            }

            test("should pass for token at exactly 24 hours old") {
                val issueTime = now.minusSeconds(24 * 3600) // Exactly 24 hours
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = "http://localhost:8180/realms/eaf-test",
                        aud = "account",
                        exp = now.plusSeconds(300).epochSecond,
                        iat = issueTime.epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                validator.ensureNotExpired(claims).shouldBeRight()
            }
        }

        context("8.6-UNIT-JCV-003: validateIssuerAudience (Layer 6)") {
            val expectedIssuer = "http://localhost:8180/realms/eaf-test" // Story 9.1: Match deployed realm
            val expectedAudience = "account" // Story 9.1: Keycloak default audience

            test("should pass for correct issuer and audience") {
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = expectedIssuer,
                        aud = expectedAudience,
                        exp = Instant.now().plusSeconds(300).epochSecond,
                        iat = Instant.now().epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                validator.validateIssuerAudience(claims).shouldBeRight()
            }

            test("should fail for incorrect issuer") {
                val wrongIssuer = "http://evil.com/realms/fake"
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = wrongIssuer,
                        aud = expectedAudience,
                        exp = Instant.now().plusSeconds(300).epochSecond,
                        iat = Instant.now().epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                val result = validator.validateIssuerAudience(claims)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidIssuer>()
                meterRegistry.counter("jwt.validation.invalid_issuer", "issuer", wrongIssuer).count() shouldBe 1.0
            }

            test("should fail for incorrect audience") {
                val wrongAudience = "evil-app"
                val claims =
                    JwtClaims(
                        sub = UUID.randomUUID().toString(),
                        iss = expectedIssuer,
                        aud = wrongAudience,
                        exp = Instant.now().plusSeconds(300).epochSecond,
                        iat = Instant.now().epochSecond,
                        jti = UUID.randomUUID().toString(),
                        tenantId = UUID.randomUUID().toString(),
                        roles = emptyList(),
                        sessionId = null,
                    )
                val result = validator.validateIssuerAudience(claims)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidAudience>()
                meterRegistry.counter("jwt.validation.invalid_audience", "audience", wrongAudience).count() shouldBe 1.0
            }
        }
    })
