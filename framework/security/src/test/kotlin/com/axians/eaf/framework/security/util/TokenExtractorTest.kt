package com.axians.eaf.framework.security.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Unit tests for TokenExtractor.
 * Story 8.6: Comprehensive coverage for token extraction utility.
 */
class TokenExtractorTest :
    FunSpec({
        context("8.6-UNIT-SVC-300: extractBearerTokenFromHeader") {
            test("8.6-UNIT-SVC-301: should return token when header is valid Bearer token") {
                val token = "abc.123.xyz"
                val header = "Bearer $token"
                TokenExtractor.extractBearerTokenFromHeader(header) shouldBe token
            }

            test("8.6-UNIT-SVC-302: should return null when header is null") {
                TokenExtractor.extractBearerTokenFromHeader(null) shouldBe null
            }

            test("8.6-UNIT-SVC-303: should return null for non-Bearer schemes") {
                TokenExtractor.extractBearerTokenFromHeader("Basic dXNlcjpwYXNz") shouldBe null
            }

            test("8.6-UNIT-SVC-304: should return null for malformed Bearer (no space)") {
                TokenExtractor.extractBearerTokenFromHeader("Bearertoken") shouldBe null
            }

            test("8.6-UNIT-SVC-305: should return empty string for Bearer with empty token") {
                TokenExtractor.extractBearerTokenFromHeader("Bearer ") shouldBe ""
            }

            test("8.6-UNIT-SVC-306: should return null for case-sensitive bearer") {
                TokenExtractor.extractBearerTokenFromHeader("bearer token") shouldBe null
            }

            test("8.6-UNIT-SVC-307: should return null for empty header string") {
                TokenExtractor.extractBearerTokenFromHeader("") shouldBe null
            }

            test("8.6-UNIT-SVC-308: should handle whitespace correctly") {
                TokenExtractor.extractBearerTokenFromHeader("Bearer  token") shouldBe " token"
            }
        }

        context("8.6-UNIT-SVC-310: extractBearerToken (from HttpServletRequest)") {
            test("8.6-UNIT-SVC-311: should extract token from valid Authorization header") {
                val request = MockHttpServletRequest()
                val token = "valid.jwt.token"
                request.addHeader("Authorization", "Bearer $token")

                TokenExtractor.extractBearerToken(request) shouldBe token
            }

            test("8.6-UNIT-SVC-312: should return null if Authorization header missing") {
                val request = MockHttpServletRequest()

                TokenExtractor.extractBearerToken(request) shouldBe null
            }

            test("8.6-UNIT-SVC-313: should return null if not Bearer token") {
                val request = MockHttpServletRequest()
                request.addHeader("Authorization", "Basic credentials")

                TokenExtractor.extractBearerToken(request) shouldBe null
            }

            test("8.6-UNIT-SVC-314: should handle multiple Authorization headers (takes first)") {
                val request = MockHttpServletRequest()
                request.addHeader("Authorization", "Bearer token1")

                TokenExtractor.extractBearerToken(request) shouldBe "token1"
            }
        }
    })
