package com.axians.eaf.framework.security

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow

/**
 * Jazzer fuzz test for injection detection (Layer 10).
 *
 * Tests the InjectionDetector with random input data to ensure it handles
 * all possible inputs gracefully without crashes, infinite loops, or
 * unexpected exceptions. Covers SQL injection, XSS, JNDI, Expression
 * Injection, and Path Traversal pattern detection.
 *
 * Story 3.8: User Validation and Injection Detection (Layers 9-10)
 */
class InjectionDetectionFuzzer {
    @FuzzTest(maxDuration = "5m")
    fun fuzzInjectionPatterns(data: FuzzedDataProvider) {
        // Generate random claim data
        val claimValue = data.consumeRemainingAsString()

        // Create a mock claims map with the fuzzed data
        val claims = mapOf("test_claim" to claimValue)

        val detector = InjectionDetector()

        // The detector should never crash or throw unexpected exceptions
        // It should either succeed (no injection detected) or throw InjectionDetectedException
        assertDoesNotThrow {
            try {
                detector.scan(claims)
            } catch (ex: InjectionDetectedException) {
                // Expected when malicious patterns are detected
                // Verify the exception contains useful information
                require(ex.claim.isNotBlank()) { "Exception should identify the problematic claim" }
                require(ex.detectedPattern.isNotBlank()) { "Exception should identify the matched pattern" }
                require(ex.value == claimValue) { "Exception should contain the original value" }
            }
        }
    }

    @FuzzTest(maxDuration = "2m")
    fun fuzzMultipleClaims(data: FuzzedDataProvider) {
        // Generate multiple random claims
        val numClaims = data.consumeInt(1, 10) // 1-10 claims
        val claims = mutableMapOf<String, Any>()

        repeat(numClaims) { index ->
            val claimName = "claim_$index"
            val claimValue = data.consumeString(100) // Up to 100 chars per claim
            claims[claimName] = claimValue
        }

        val detector = InjectionDetector()

        // Should handle multiple claims without issues
        assertDoesNotThrow {
            try {
                detector.scan(claims)
            } catch (ex: InjectionDetectedException) {
                // Expected - verify exception details
                require(claims.containsKey(ex.claim)) { "Exception should reference an existing claim" }
            }
        }
    }

    @FuzzTest(maxDuration = "2m")
    fun fuzzEdgeCases(data: FuzzedDataProvider) {
        // Test various edge cases
        val testCases =
            listOf(
                "", // Empty string
                "   ", // Whitespace only
                "\u0000\u0001\u0002", // Null bytes
                "normal text", // Safe text
                data.consumeString(1000), // Long string
                "text with ${data.consumeString(50)} interpolation", // Template-like strings
            )

        val detector = InjectionDetector()

        testCases.forEach { testValue ->
            val claims = mapOf("test" to testValue)

            assertDoesNotThrow {
                try {
                    detector.scan(claims)
                } catch (ex: InjectionDetectedException) {
                    // Verify exception is well-formed
                    require(ex.claim == "test")
                    require(ex.value == testValue)
                }
            }
        }
    }
}
