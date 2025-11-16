package com.axians.eaf.framework.security.jwt

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Jazzer fuzz test for Bearer token extraction from Authorization headers.
 *
 * Tests Spring Security's Bearer token resolver with random Authorization header
 * formats to ensure it handles malformed inputs gracefully without crashes, infinite
 * loops, or DoS conditions.
 *
 * Covers various Bearer token extraction scenarios:
 * - Case sensitivity ("Bearer", "bearer", "BEARER")
 * - Whitespace handling (spaces, tabs, newlines)
 * - Missing/malformed schemes
 * - Multiple Authorization headers
 * - Unicode and injection attempts
 *
 * Story 3.12: Security Fuzz Testing with Jazzer
 * AC #2: TokenExtractorFuzzer.kt (fuzzes Bearer token extraction)
 * AC #3: Each fuzz test runs 5 minutes
 */
class TokenExtractorFuzzer {
    private val tokenResolver: BearerTokenResolver = DefaultBearerTokenResolver()

    @FuzzTest(maxDuration = "5m")
    fun fuzzTokenExtraction(data: FuzzedDataProvider) {
        val request = MockHttpServletRequest()

        // Fuzz Authorization header format
        val authHeader = generateFuzzedAuthorizationHeader(data)
        request.addHeader("Authorization", authHeader)

        // Optional: Add additional headers to test multi-header scenarios
        if (data.consumeBoolean()) {
            repeat(data.consumeInt(1, 5)) {
                request.addHeader("Authorization", data.consumeString(100))
            }
        }

        // The resolver should handle all malformed headers gracefully
        // It should either extract a token or return null without crashing
        assertDoesNotThrow {
            try {
                tokenResolver.resolve(request)
            } catch (ex: IllegalArgumentException) {
                // Expected for invalid formats
                require(ex.message != null) { "Exception should have a message" }
            } catch (ex: NullPointerException) {
                // Should not happen - indicates a bug in token extraction
                throw AssertionError("Token resolver should not throw NPE", ex)
            }
        }
    }

    private fun generateFuzzedAuthorizationHeader(data: FuzzedDataProvider): String {
        val strategy = data.consumeInt(0, 9)

        return when (strategy) {
            0 -> fuzzStandardFormat(data)       // "Bearer <token>"
            1 -> fuzzCaseSensitivity(data)      // "bearer", "BEARER", "BeArEr"
            2 -> fuzzWhitespace(data)           // Extra spaces, tabs, newlines
            3 -> fuzzMissingScheme(data)        // Just token, no "Bearer"
            4 -> fuzzWrongScheme(data)          // "Basic", "Digest", etc.
            5 -> fuzzEmptyToken(data)           // "Bearer " (no token)
            6 -> fuzzMultipleTokens(data)       // "Bearer token1 token2"
            7 -> fuzzUnicodeAttacks(data)       // Unicode, homoglyphs
            8 -> fuzzInjectionPatterns(data)    // SQL, XSS, CRLF injection
            else -> fuzzEdgeCases(data)         // Very long, null bytes, etc.
        }
    }

    private fun fuzzStandardFormat(data: FuzzedDataProvider): String {
        val token = data.consumeString(data.consumeInt(10, 500))
        return "Bearer $token"
    }

    private fun fuzzCaseSensitivity(data: FuzzedDataProvider): String {
        val schemes = listOf("Bearer", "bearer", "BEARER", "BeArEr", "bEaReR", "BeaRER")
        val scheme = data.pickValue(schemes)
        val token = data.consumeString(data.consumeInt(10, 200))
        return "$scheme $token"
    }

    private fun fuzzWhitespace(data: FuzzedDataProvider): String {
        val whitespaces = listOf(" ", "  ", "\t", "\n", "\r\n", "   ", " \t ")
        val ws1 = data.pickValue(whitespaces)
        val ws2 = data.pickValue(whitespaces)
        val token = data.consumeString(data.consumeInt(10, 200))
        return "Bearer$ws1$token$ws2"
    }

    private fun fuzzMissingScheme(data: FuzzedDataProvider): String {
        return data.consumeString(data.consumeInt(10, 200))
    }

    private fun fuzzWrongScheme(data: FuzzedDataProvider): String {
        val schemes = listOf("Basic", "Digest", "OAuth", "Token", "JWT", "API-Key", "CustomScheme")
        val scheme = data.pickValue(schemes)
        val token = data.consumeString(data.consumeInt(10, 200))
        return "$scheme $token"
    }

    private fun fuzzEmptyToken(data: FuzzedDataProvider): String {
        val schemes = listOf("Bearer", "Bearer ", "Bearer  ", "Bearer\t", "Bearer\n")
        return data.pickValue(schemes)
    }

    private fun fuzzMultipleTokens(data: FuzzedDataProvider): String {
        val tokenCount = data.consumeInt(2, 5)
        val tokens = List(tokenCount) { data.consumeString(50) }
        return "Bearer ${tokens.joinToString(" ")}"
    }

    private fun fuzzUnicodeAttacks(data: FuzzedDataProvider): String {
        // Homoglyph attacks, zero-width characters, right-to-left override
        val unicodePayloads = listOf(
            "Bearer \u200B", // Zero-width space
            "Bearer \u202E", // Right-to-left override
            "Вearer", // Cyrillic 'В' instead of 'B'
            "Bearer \uFEFF", // Zero-width no-break space
        )
        val base = data.pickValue(unicodePayloads)
        return base + data.consumeString(50)
    }

    private fun fuzzInjectionPatterns(data: FuzzedDataProvider): String {
        val injectionPayloads = listOf(
            "Bearer ' OR '1'='1",
            "Bearer <script>alert('xss')</script>",
            "Bearer \r\nX-Injected: true",
            "Bearer ${'\u0000'}admin",
            "Bearer ../../../etc/passwd",
        )
        return data.pickValue(injectionPayloads)
    }

    private fun fuzzEdgeCases(data: FuzzedDataProvider): String {
        val edgeCases = listOf(
            "", // Empty header
            " ", // Space only
            "Bearer", // Scheme only
            "Bearer\u0000", // Null byte
            "Bearer " + "a".repeat(10000), // Very long token
            "Bearer " + "\n".repeat(100), // Many newlines
        )
        return data.pickValue(edgeCases)
    }
}
