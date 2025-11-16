package com.axians.eaf.framework.security.jwt

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.util.Base64

/**
 * Jazzer fuzz test for JWT format parsing.
 *
 * Tests the JWT decoder with random token formats to ensure it handles
 * malformed inputs gracefully without crashes, infinite loops, or DoS conditions.
 * Covers 3-part JWT structure parsing (header.payload.signature).
 *
 * Story 3.12: Security Fuzz Testing with Jazzer
 * AC #2: JwtFormatFuzzer.kt (fuzzes token format parsing)
 * AC #3: Each fuzz test runs 5 minutes
 */
class JwtFormatFuzzer {
    @FuzzTest(maxDuration = "5m")
    fun fuzzJwtFormat(data: FuzzedDataProvider) {
        val jwtToken = generateFuzzedJwtToken(data)

        // Create a mock JWKS provider for testing (won't validate signatures, just format)
        val decoder = NimbusJwtDecoder.withJwkSetUri("https://mock.keycloak/jwks").build()

        // The decoder should handle all malformed tokens gracefully
        // It should either decode successfully or throw a controlled JwtException
        assertDoesNotThrow {
            try {
                decoder.decode(jwtToken)
            } catch (ex: JwtException) {
                // Expected for malformed tokens - verify exception is well-formed
                require(ex.message != null) { "JWT exception should have a message" }
            } catch (ex: BadJwtException) {
                // Expected for invalid JWT format
                require(ex.message != null) { "BadJWT exception should have a message" }
            } catch (ex: IllegalArgumentException) {
                // Expected for invalid Base64 or structure
                require(ex.message != null) { "IllegalArgumentException should have a message" }
            }
        }
    }

    private fun generateFuzzedJwtToken(data: FuzzedDataProvider): String {
        val strategy = data.consumeInt(0, 5)

        return when (strategy) {
            0 -> fuzzValidStructure(data)      // Valid 3-part structure with random content
            1 -> fuzzPartCount(data)           // Wrong number of parts (0-10 parts)
            2 -> fuzzBase64Encoding(data)      // Invalid Base64 in parts
            3 -> fuzzDelimiters(data)          // Wrong delimiters (not dots)
            4 -> fuzzEmptyParts(data)          // Empty parts
            else -> fuzzEdgeCases(data)        // Edge cases (nulls, unicode, very long)
        }
    }

    private fun fuzzValidStructure(data: FuzzedDataProvider): String {
        val header = generateRandomBase64(data, data.consumeInt(10, 200))
        val payload = generateRandomBase64(data, data.consumeInt(50, 1000))
        val signature = generateRandomBase64(data, data.consumeInt(20, 500))

        return "$header.$payload.$signature"
    }

    private fun fuzzPartCount(data: FuzzedDataProvider): String {
        val partCount = data.consumeInt(0, 10)
        val parts = mutableListOf<String>()

        repeat(partCount) {
            parts.add(generateRandomBase64(data, data.consumeInt(5, 100)))
        }

        return parts.joinToString(".")
    }

    private fun fuzzBase64Encoding(data: FuzzedDataProvider): String {
        val header = data.consumeString(data.consumeInt(10, 100))
        val payload = data.consumeString(data.consumeInt(50, 500))
        val signature = data.consumeString(data.consumeInt(20, 200))

        return "$header.$payload.$signature"
    }

    private fun fuzzDelimiters(data: FuzzedDataProvider): String {
        val delimiter = data.pickValue(listOf(".", ",", ":", ";", " ", "", "|", "_"))
        val parts = List(3) { generateRandomBase64(data, data.consumeInt(10, 100)) }

        return parts.joinToString(delimiter)
    }

    private fun fuzzEmptyParts(data: FuzzedDataProvider): String {
        val parts = List(3) { index ->
            if (data.consumeBoolean()) {
                generateRandomBase64(data, data.consumeInt(10, 100))
            } else {
                ""
            }
        }

        return parts.joinToString(".")
    }

    private fun fuzzEdgeCases(data: FuzzedDataProvider): String {
        val edgeCases = listOf(
            "", // Empty string
            ".", // Only delimiter
            "..", // Two delimiters
            "...", // Three delimiters
            data.consumeString(10000), // Very long string
            "\u0000.\u0001.\u0002", // Null bytes
            "header." + "a".repeat(100000) + ".signature", // Extremely long payload
        )

        return data.pickValue(edgeCases)
    }

    private fun generateRandomBase64(data: FuzzedDataProvider, maxLength: Int): String {
        val length = data.consumeInt(1, maxLength)
        val randomBytes = ByteArray(length) { data.consumeByte() }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }
}
