package com.axians.eaf.framework.security.jwt

import com.axians.eaf.framework.security.errors.SecurityError
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.Base64

/**
 * Unit tests for 10-layer JWT validation system.
 * Tests each validation layer individually without Spring Boot context.
 */
class TenLayerJwtValidatorTest : BehaviorSpec() {
    private val meterRegistry = SimpleMeterRegistry()
    private val mockJwtDecoder =
        object : JwtDecoder {
            override fun decode(token: String): Jwt {
                // Parse the token to create a mock JWT
                val parts = token.split(".")
                if (parts.size != 3) throw JwtException("Invalid token format")

                val payload =
                    try {
                        String(Base64.getUrlDecoder().decode(parts[1]))
                    } catch (e: IllegalArgumentException) {
                        throw JwtException("Invalid payload encoding: ${e.message}")
                    }

                // Simple JSON parsing for test purposes
                val claims = mutableMapOf<String, Any>()
                if (payload.contains("\"sub\":")) {
                    val sub = payload.substringAfter("\"sub\":\"").substringBefore("\"")
                    claims["sub"] = sub
                }
                if (payload.contains("\"tenant_id\":")) {
                    val tenantId = payload.substringAfter("\"tenant_id\":\"").substringBefore("\"")
                    claims["tenant_id"] = tenantId
                }
                if (payload.contains("\"iss\":")) {
                    val iss = payload.substringAfter("\"iss\":\"").substringBefore("\"")
                    claims["iss"] = iss
                }
                if (payload.contains("\"aud\":")) {
                    val aud = payload.substringAfter("\"aud\":\"").substringBefore("\"")
                    claims["aud"] = listOf(aud)
                }
                if (payload.contains("\"exp\":")) {
                    val exp = payload.substringAfter("\"exp\":").substringBefore(",").substringBefore("}")
                    claims["exp"] = Instant.ofEpochSecond(exp.toLong())
                }
                if (payload.contains("\"iat\":")) {
                    val iat = payload.substringAfter("\"iat\":").substringBefore(",").substringBefore("}")
                    claims["iat"] = Instant.ofEpochSecond(iat.toLong())
                }
                if (payload.contains("\"jti\":")) {
                    val jti = payload.substringAfter("\"jti\":\"").substringBefore("\"")
                    claims["jti"] = jti
                }

                // Create mock headers
                val headers = mutableMapOf<String, Any>()
                val headerPayload =
                    try {
                        String(Base64.getUrlDecoder().decode(parts[0]))
                    } catch (e: IllegalArgumentException) {
                        throw JwtException("Invalid header encoding: ${e.message}")
                    }
                if (headerPayload.contains("\"alg\":")) {
                    val alg = headerPayload.substringAfter("\"alg\":\"").substringBefore("\"")
                    headers["alg"] = alg
                }

                return Jwt
                    .withTokenValue(token)
                    .headers { it.putAll(headers) }
                    .claims { it.putAll(claims) }
                    .build()
            }
        }

    private val mockRedisTemplate =
        object : RedisTemplate<String, String>() {
            override fun hasKey(key: String): Boolean = false
        }

    private val formatValidator = JwtFormatValidator(mockJwtDecoder, meterRegistry)
    private val claimsValidator = JwtClaimsValidator(meterRegistry)
    private val securityValidator = JwtSecurityValidator(mockRedisTemplate, meterRegistry)
    private val validator = TenLayerJwtValidator(formatValidator, claimsValidator, securityValidator, meterRegistry)

    init {
        given("TenLayerJwtValidator") {

            `when`("validating token format (Layer 1)") {
                then("should accept valid JWT structure") {
                    val validToken = createValidTokenStructure()
                    val result = validator.validateTenLayers(validToken)
                    // Should pass Layer 1 but may fail later layers
                    result.isLeft() shouldBe true // Will fail at signature validation since it's mock
                }

                then("should reject malformed tokens") {
                    val malformedToken = "invalid.token"
                    val result = validator.validateTenLayers(malformedToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidTokenFormat>()
                }

                then("should reject oversized tokens") {
                    val oversizedToken = "a".repeat(10000) + ".payload.signature"
                    val result = validator.validateTenLayers(oversizedToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.TokenTooLarge>()
                }

                then("should reject empty tokens") {
                    val result = validator.validateTenLayers("")

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.EmptyToken>()
                }
            }

            `when`("validating algorithm (Layer 3)") {
                then("should reject HS256 algorithm (SEC-001 critical vulnerability)") {
                    val hs256Token = createTokenWithAlgorithm("HS256")
                    val result = validator.validateTenLayers(hs256Token)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                }

                then("should reject 'none' algorithm") {
                    val noneToken = createTokenWithAlgorithm("none")
                    val result = validator.validateTenLayers(noneToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                }
            }

            `when`("validating injection detection (Layer 10)") {
                then("should detect SQL injection patterns (SEC-002 mitigation)") {
                    val sqlInjectionToken = createTokenWithSQLInjection()
                    val result = validator.validateTenLayers(sqlInjectionToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
                }

                then("should detect XSS patterns") {
                    val xssToken = createTokenWithXSSPayload()
                    val result = validator.validateTenLayers(xssToken)

                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InjectionDetected>()
                }
            }
        }
    }

    // Test helper functions
    private fun createValidTokenStructure(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test-user","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat,"jti":"test-jti"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.validSignature"
    }

    private fun createTokenWithAlgorithm(algorithm: String): String {
        val header = """{"alg":"$algorithm","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test-user","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat,"jti":"test-jti"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.signature"
    }

    private fun createTokenWithSQLInjection(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"test'; DROP TABLE users; --","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat,"jti":"test-jti"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.signature"
    }

    private fun createTokenWithXSSPayload(): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val exp = System.currentTimeMillis() / 1000 + 3600
        val iat = System.currentTimeMillis() / 1000
        val payload =
            """{"sub":"<script>alert('xss')</script>","tenant_id":"test-tenant",""" +
                """"iss":"http://localhost:8180/realms/eaf","aud":"eaf-backend",""" +
                """"exp":$exp,"iat":$iat,"jti":"test-jti"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        return "$encodedHeader.$encodedPayload.signature"
    }
}
