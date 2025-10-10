package com.axians.eaf.framework.security.jwt

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.test.JwtTestTokens
import com.axians.eaf.framework.security.test.NullableJwtDecoder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

/**
 * Unit tests for JwtFormatValidator (Layers 1-3).
 * Story 8.6-UNIT-JFV: JWT format, signature, and algorithm validation
 */
class JwtFormatValidatorTest :
    FunSpec({
        lateinit var meterRegistry: SimpleMeterRegistry
        lateinit var nullableJwtDecoder: NullableJwtDecoder
        lateinit var validator: JwtFormatValidator

        beforeTest {
            meterRegistry = SimpleMeterRegistry()
            nullableJwtDecoder = NullableJwtDecoder.createNull()
            validator = JwtFormatValidator(nullableJwtDecoder, meterRegistry)
        }

        context("8.6-UNIT-JFV-001: validateBasicFormat (Layer 1)") {
            test("should pass for well-formed JWT token") {
                val validToken = JwtTestTokens.validRs256
                validator.validateBasicFormat(validToken).shouldBeRight()
            }

            test("should fail for empty token") {
                validator.validateBasicFormat("").shouldBeLeft(SecurityError.EmptyToken)
            }

            test("should fail for blank token") {
                validator.validateBasicFormat("   ").shouldBeLeft(SecurityError.EmptyToken)
            }

            test("should fail for token with invalid characters") {
                validator.validateBasicFormat("a.b!@#.c").shouldBeLeft(SecurityError.InvalidTokenFormat)
            }

            test("should fail for token with spaces") {
                validator.validateBasicFormat("a b.c.d").shouldBeLeft(SecurityError.InvalidTokenFormat)
            }

            test("should fail for token with more than 3 parts") {
                val result = validator.validateBasicFormat("a.b.c.d")
                result.shouldBeLeft() // InvalidTokenFormat or InvalidJwtStructure both acceptable
            }

            test("should fail for token with less than 3 parts") {
                val result = validator.validateBasicFormat("a.b")
                result.shouldBeLeft() // InvalidTokenFormat or InvalidJwtStructure both acceptable
            }

            test("should fail for token exceeding max size (8192)") {
                val oversizedToken = "a.b." + "c".repeat(9000)
                validator.validateBasicFormat(oversizedToken).shouldBeLeft(SecurityError.TokenTooLarge)
            }

            test("should pass for token at exact max size boundary") {
                val maxSizeToken = "a.b." + "c".repeat(8188) // Total = 8192
                maxSizeToken.length shouldBe 8192
                validator.validateBasicFormat(maxSizeToken).shouldBeRight()
            }

            test("should fail for single part token") {
                validator.validateBasicFormat("onlyonepart").shouldBeLeft(SecurityError.InvalidTokenFormat)
            }
        }

        context("8.6-UNIT-JFV-002: verifySignature (Layer 2)") {
            test("should return JWT on successful signature verification") {
                val token = JwtTestTokens.validRs256
                val result = validator.verifySignature(token)
                result.shouldBeRight()
            }

            test("should fail for token with invalid signature") {
                val token = JwtTestTokens.invalidSignature
                val result = validator.verifySignature(token)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.InvalidSignature>()
                meterRegistry.counter("jwt.validation.signature_failure").count() shouldBe 1.0
            }

            test("should fail for malformed token during signature verification") {
                val token = "malformed"
                val result = validator.verifySignature(token)
                result.shouldBeLeft()
                meterRegistry.counter("jwt.validation.signature_failure").count() shouldBe 1.0
            }
        }

        context("8.6-UNIT-JFV-003: ensureRS256Algorithm (Layer 3)") {
            test("should pass for RS256 algorithm") {
                val token = JwtTestTokens.validRs256
                val jwt = nullableJwtDecoder.decode(token)
                validator.ensureRS256Algorithm(jwt).shouldBeRight()
            }

            test("should fail for HS256 algorithm") {
                val token = JwtTestTokens.hs256
                val jwt = nullableJwtDecoder.decode(token)
                val result = validator.ensureRS256Algorithm(jwt)
                result.shouldBeLeft()
                result.leftOrNull().shouldBeInstanceOf<SecurityError.UnsupportedAlgorithm>()
                (result.leftOrNull() as SecurityError.UnsupportedAlgorithm).algorithm shouldBe "HS256"
                meterRegistry.counter("jwt.validation.algorithm_mismatch", "algorithm", "HS256").count() shouldBe 1.0
            }

            test("should fail for 'none' algorithm") {
                val token = JwtTestTokens.noneAlgorithm
                val jwt = nullableJwtDecoder.decode(token)
                val result = validator.ensureRS256Algorithm(jwt)
                result.shouldBeLeft()
                (result.leftOrNull() as SecurityError.UnsupportedAlgorithm).algorithm shouldBe "none"
                meterRegistry.counter("jwt.validation.algorithm_mismatch", "algorithm", "none").count() shouldBe 1.0
            }

            test("should fail if algorithm header is missing") {
                val token = JwtTestTokens.missingAlgorithm
                val jwt = nullableJwtDecoder.decode(token)
                val result = validator.ensureRS256Algorithm(jwt)
                result.shouldBeLeft()
                (result.leftOrNull() as SecurityError.UnsupportedAlgorithm).algorithm shouldBe null
                meterRegistry.counter("jwt.validation.algorithm_mismatch", "algorithm", "null").count() shouldBe 1.0
            }

            test("should fail for ES256 algorithm") {
                val token = JwtTestTokens.es256
                val jwt = nullableJwtDecoder.decode(token)
                val result = validator.ensureRS256Algorithm(jwt)
                result.shouldBeLeft()
                (result.leftOrNull() as SecurityError.UnsupportedAlgorithm).algorithm shouldBe "ES256"
            }
        }
    })
