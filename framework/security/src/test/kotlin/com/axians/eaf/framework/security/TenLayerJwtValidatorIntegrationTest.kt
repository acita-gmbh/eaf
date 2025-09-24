package com.axians.eaf.framework.security

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.jwt.TenLayerJwtValidator
import com.axians.eaf.testing.auth.KeycloakTestTokenProvider
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import kotlin.system.measureTimeMillis

/**
 * Comprehensive integration tests for 10-layer JWT validation system.
 * Tests all validation layers with security focus and performance requirements.
 */
@SpringBootTest
@TestPropertySource(properties = ["spring.profiles.active=test"])
class TenLayerJwtValidatorIntegrationTest(
    private val tenLayerValidator: TenLayerJwtValidator,
) : BehaviorSpec() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf"
            }
            registry.add("spring.data.redis.url") {
                "redis://localhost:${TestContainers.redis.firstMappedPort}"
            }
        }
    }

    init {
        given("10-layer JWT validation system") {

            `when`("processing valid JWT token from Keycloak") {
                then("should pass all 10 layers successfully (AC3 - valid token)") {
                    val token = KeycloakTestTokenProvider.getAdminToken()

                    val result = tenLayerValidator.validateTenLayers(token)

                    result.isRight() shouldBe true
                    val validationResult = result.getOrNull()!!
                    validationResult.user.isActive shouldBe true
                    validationResult.tenantId.isNotEmpty() shouldBe true
                    validationResult.roles.isNotEmpty() shouldBe true
                }
            }

            `when`("performance testing with valid token") {
                then("should complete validation in <50ms (PERF-001)") {
                    val token = KeycloakTestTokenProvider.getAdminToken()

                    val duration =
                        measureTimeMillis {
                            tenLayerValidator.validateTenLayers(token)
                        }

                    duration shouldBe lessThan(50L)
                }
            }

            `when`("testing Layer 1 - Format validation failures") {
                then("should reject malformed tokens (AC3 - invalid format)") {
                    val malformedToken = "invalid.token"

                    val result = tenLayerValidator.validateTenLayers(malformedToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidJwtStructure>()
                }

                then("should reject oversized tokens") {
                    val oversizedToken = "a".repeat(10000) + ".payload.signature"

                    val result = tenLayerValidator.validateTenLayers(oversizedToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.TokenTooLarge>()
                }
            }

            `when`("testing Layer 2 - Signature validation failures") {
                then("should reject tokens with invalid signatures (AC3 - invalid signature)") {
                    val tokenWithInvalidSignature = createTokenWithInvalidSignature()

                    val result = tenLayerValidator.validateTenLayers(tokenWithInvalidSignature)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidSignature>()
                }
            }

            `when`("testing Layer 3 - Algorithm validation (CRITICAL SECURITY)") {
                then("should reject HS256 algorithm tokens (SEC-001 mitigation)") {
                    val hs256Token = createTokenWithHS256Algorithm()

                    val result = tenLayerValidator.validateTenLayers(hs256Token)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                }

                then("should reject 'none' algorithm tokens (SEC-001 mitigation)") {
                    val noneAlgorithmToken = createTokenWithNoneAlgorithm()

                    val result = tenLayerValidator.validateTenLayers(noneAlgorithmToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                }
            }

            `when`("testing Layer 4 - Claim schema validation") {
                then("should reject tokens missing tenant_id claim (AC3 - missing tenant)") {
                    val tokenWithoutTenantId = createTokenWithoutTenantId()

                    val result = tenLayerValidator.validateTenLayers(tokenWithoutTenantId)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.MissingClaim>()
                }
            }

            `when`("testing Layer 5 - Time-based validation") {
                then("should reject expired tokens (AC3 - expired timestamp)") {
                    val expiredToken = createExpiredToken()

                    val result = tenLayerValidator.validateTenLayers(expiredToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.TokenExpired>()
                }
            }

            `when`("testing Layer 6 - Issuer/Audience validation") {
                then("should reject tokens with wrong issuer") {
                    val wrongIssuerToken = createTokenWithWrongIssuer()

                    val result = tenLayerValidator.validateTenLayers(wrongIssuerToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidIssuer>()
                }
            }

            `when`("testing Layer 10 - Injection detection (SEC-002 mitigation)") {
                then("should detect SQL injection patterns") {
                    val sqlInjectionToken = createTokenWithSQLInjection()

                    val result = tenLayerValidator.validateTenLayers(sqlInjectionToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
                }

                then("should detect XSS patterns") {
                    val xssToken = createTokenWithXSSPayload()

                    val result = tenLayerValidator.validateTenLayers(xssToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
                }
            }
        }
    }

    // Test helper functions for creating various token types
    private fun createTokenWithInvalidSignature(): String {
        val validToken = KeycloakTestTokenProvider.getAdminToken()
        val parts = validToken.split(".")
        return "${parts[0]}.${parts[1]}.invalidSignature"
    }

    private fun createTokenWithHS256Algorithm(): String {
        // Create a token with HS256 algorithm header
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }

    private fun createTokenWithNoneAlgorithm(): String {
        val header = """{"alg":"none","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload."
    }

    private fun createTokenWithoutTenantId(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }

    private fun createExpiredToken(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 - 3600
        val iat = System.currentTimeMillis() / 1000 - 7200
        val payload =
            """{"sub":"test","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }

    private fun createTokenWithWrongIssuer(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test","tenant_id":"test-tenant",""" +
                """"iss":"http://malicious.com/realms/evil","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }

    private fun createTokenWithSQLInjection(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test'; DROP TABLE users; --","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }

    private fun createTokenWithXSSPayload(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"<script>alert('xss')</script>","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat}"""
        val encodedHeader =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(header.toByteArray())
        val encodedPayload =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.fakeSignature"
    }
}

private fun lessThan(value: Long) =
    object {
        fun test(actual: Long): Boolean = actual < value

        override fun toString() = "less than $value"
    }

private infix fun Long.shouldBe(matcher: Any) {
    when (matcher) {
        is Function1<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val result = (matcher as Function1<Long, Boolean>)(this)
            if (!result) throw AssertionError("Expected $this to match $matcher")
        }
        else -> if (this != matcher) throw AssertionError("Expected $matcher but was $this")
    }
}
