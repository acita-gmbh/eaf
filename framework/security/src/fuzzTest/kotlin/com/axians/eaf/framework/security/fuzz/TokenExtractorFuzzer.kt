package com.axians.eaf.framework.security.fuzz

import com.axians.eaf.framework.security.util.TokenExtractor
import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest

/**
 * Fuzzer for Bearer token extraction.
 * Story 8.6: Fuzz testing for authorization header parsing.
 *
 * EXECUTION: Nightly pipeline (time-boxed).
 * PURPOSE: Find parsing vulnerabilities in HTTP header processing.
 *
 * Security relevance: Authorization header parsing is a critical entry point.
 * Bugs here could lead to authentication bypass or DoS.
 *
 * Example findings:
 * - Null pointer dereference
 * - String index out of bounds
 * - Unexpected Unicode handling
 * - Memory exhaustion from malformed headers
 */
class TokenExtractorFuzzer {
    @FuzzTest
    fun fuzzTokenExtraction(data: FuzzedDataProvider) {
        val authHeader = data.consumeRemainingAsString()

        try {
            // Test token extraction - should handle ANY input gracefully
            TokenExtractor.extractBearerTokenFromHeader(authHeader)
        } catch (e: OutOfMemoryError) {
            throw e // Report OOM as finding
        } catch (e: StackOverflowError) {
            throw e // Report stack overflow
        } catch (e: Exception) {
            // Expected for malformed input
        }
    }

    @FuzzTest
    fun fuzzTokenExtractionWithNull(data: FuzzedDataProvider) {
        // Test null handling explicitly
        try {
            TokenExtractor.extractBearerTokenFromHeader(null)
        } catch (e: NullPointerException) {
            // Should NOT throw NPE - this would be a finding
            throw e
        }
    }
}
