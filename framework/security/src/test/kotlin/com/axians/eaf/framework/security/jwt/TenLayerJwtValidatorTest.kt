package com.axians.eaf.framework.security.jwt

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.test.JwtTestTokens
import com.axians.eaf.framework.security.test.NullableJwtDecoder
import com.axians.eaf.framework.security.test.NullableRedisTemplate
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

/**
 * Unit tests for 10-layer JWT validation system.
 * Tests each validation layer individually using robust nullable dependencies.
 */
class TenLayerJwtValidatorTest : BehaviorSpec() {
    private val meterRegistry = SimpleMeterRegistry()
    private val nullableJwtDecoder = NullableJwtDecoder.createNull()
    private val nullableRedisTemplate = NullableRedisTemplate.createNull()

    private val validator =
        TenLayerJwtValidator(
            formatValidator = JwtFormatValidator(nullableJwtDecoder, meterRegistry),
            claimsValidator = JwtClaimsValidator(meterRegistry),
            securityValidator = JwtSecurityValidator(nullableRedisTemplate, meterRegistry),
            meterRegistry = meterRegistry,
        )

    init {
        beforeTest {
            nullableRedisTemplate.clear()
        }

        given("TenLayerJwtValidator") {

            `when`("validating token format (Layer 1)") {
                then("should reject malformed tokens") {
                    val result = validator.validateTenLayers("invalid.token.format.with.too.many.parts")
                    result.isLeft() shouldBe true
                    result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidTokenFormat>()
                }

                then("should reject oversized tokens") {
                    val oversizedToken = "a.b." + "c".repeat(9000)
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
                then("should reject HS256 algorithm") {
                    val result = validator.validateTenLayers(JwtTestTokens.hs256)
                    result.isLeft() shouldBe true
                    val error = result.leftOrNull()
                    error.shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                    error.algorithm shouldBe "HS256"
                }

                then("should reject 'none' algorithm") {
                    val result = validator.validateTenLayers(JwtTestTokens.noneAlgorithm)
                    result.isLeft() shouldBe true
                    val error = result.leftOrNull()
                    error.shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                    error.algorithm shouldBe "none"
                }
            }

            `when`("validating token revocation (Layer 7)") {
                then("should reject a token with a JTI present in the revocation list") {
                    val tokenKey = JwtTestTokens.validRs256
                    val jwt = nullableJwtDecoder.decode(tokenKey)
                    val jti = jwt.getClaim<String>("jti")

                    nullableRedisTemplate.addKey("revoked:$jti")

                    val result = validator.validateTenLayers(tokenKey)

                    result.isLeft() shouldBe true
                    val error = result.leftOrNull()
                    error.shouldBeInstanceOf<SecurityError.TokenRevoked>()
                    error.jti shouldBe jti
                }

                then("should accept a token with a JTI that is not revoked") {
                    val result = validator.validateTenLayers(JwtTestTokens.validRs256)
                    result.isRight() shouldBe true
                }
            }

            `when`("validating claim schema (Layer 4)") {
                then("should reject a token without a JTI claim") {
                    val result = validator.validateTenLayers(JwtTestTokens.missingJti)
                    result.isLeft() shouldBe true
                    val error = result.leftOrNull()
                    error.shouldBeInstanceOf<SecurityError.MissingClaim>()
                    error.claimName shouldBe "jti"
                }
            }
        }
    }
}
