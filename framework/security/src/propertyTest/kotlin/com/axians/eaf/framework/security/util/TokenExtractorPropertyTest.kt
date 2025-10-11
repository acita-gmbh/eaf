package com.axians.eaf.framework.security.util

import com.axians.eaf.testing.tags.PbtTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.kotest.property.forAll

/**
 * Property-based tests for TokenExtractor.
 * Story 8.6: Multi-stage testing with optimized constructive generators.
 *
 * EXECUTION: Nightly/post-merge pipeline only.
 * EXCLUDED FROM: Mutation testing (exponential time complexity).
 *
 * Performance optimization: Uses constructive generation, not filtering.
 * Validates token parsing properties across thousands of random inputs.
 */
class TokenExtractorPropertyTest : FunSpec() {
    override fun tags() = setOf(PbtTag)

    init {
        context("8.6-PBT-TOKEN-001: Valid Bearer token extraction") {
            test("extracted token should match original") {
                checkAll(1000, validTokenArb()) { token ->
                    val header = "Bearer $token"
                    val extracted = TokenExtractor.extractBearerTokenFromHeader(header)
                    extracted shouldBe token
                }
            }

            test("extraction is idempotent on valid headers") {
                forAll(500, validBearerHeaderArb()) { header ->
                    val expectedToken = header.removePrefix("Bearer ")
                    val actualToken = TokenExtractor.extractBearerTokenFromHeader(header)
                    actualToken == expectedToken
                }
            }
        }

        context("8.6-PBT-TOKEN-002: Null safety") {
            test("null header always returns null") {
                checkAll(100, Arb.int()) { _ ->
                    TokenExtractor.extractBearerTokenFromHeader(null) shouldBe null
                }
            }
        }

        context("8.6-PBT-TOKEN-003: Non-Bearer schemes rejected") {
            test("non-Bearer schemes return null") {
                forAll(500, nonBearerSchemeArb()) { header ->
                    TokenExtractor.extractBearerTokenFromHeader(header) == null
                }
            }

            test("case sensitivity: bearer (lowercase) returns null") {
                checkAll(500, validTokenArb()) { token ->
                    val lowercaseHeader = "bearer $token"
                    TokenExtractor.extractBearerTokenFromHeader(lowercaseHeader) shouldBe null
                }
            }
        }

        context("8.6-PBT-TOKEN-004: Malformed headers") {
            test("malformed Bearer headers (no space) return null") {
                forAll(500, validTokenArb()) { token ->
                    val malformed = "Bearer$token" // No space
                    TokenExtractor.extractBearerTokenFromHeader(malformed) == null
                }
            }

            test("whitespace-only headers return null") {
                forAll(100, whitespaceOnlyArb()) { whitespace ->
                    TokenExtractor.extractBearerTokenFromHeader(whitespace) == null
                }
            }
        }

        context("8.6-PBT-TOKEN-005: Token format preservation") {
            test("preserves special JWT characters") {
                forAll(500, jwtLikeTokenArb()) { jwt ->
                    val header = "Bearer $jwt"
                    val extracted = TokenExtractor.extractBearerTokenFromHeader(header)
                    extracted == jwt
                }
            }
        }
    }
}

// ============================================================================
// Optimized Arb Generators using Constructive Generation
// ============================================================================

/**
 * Generates valid JWT-like tokens using CONSTRUCTIVE approach.
 * Builds from base64url character set (no filtering).
 */
private fun jwtLikeTokenArb(): Arb<String> {
    val base64UrlChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_')
    val base64CharArb = Arb.of(base64UrlChars)
    val partArb = Arb.list(base64CharArb, 10..100).map { it.joinToString("") }

    return Arb.bind(partArb, partArb, partArb) { header, payload, signature ->
        "$header.$payload.$signature"
    }
}

/**
 * Generates valid tokens constructively (alphanumeric + JWT chars).
 */
private fun validTokenArb(): Arb<String> {
    val tokenChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', '.', '=')
    return Arb.list(Arb.of(tokenChars), 1..500).map { it.joinToString("") }
}

/**
 * Generates valid Bearer headers.
 */
private fun validBearerHeaderArb(): Arb<String> = validTokenArb().map { token -> "Bearer $token" }

/**
 * Generates non-Bearer authentication schemes constructively.
 */
private fun nonBearerSchemeArb(): Arb<String> {
    val schemes = Arb.of("Basic", "Digest", "OAuth", "HMAC", "AWS4-HMAC-SHA256")
    val credChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('=', '+')
    val credentialsArb = Arb.list(Arb.of(credChars), 10..100).map { it.joinToString("") }

    return Arb.bind(schemes, credentialsArb) { scheme, creds ->
        "$scheme $creds"
    }
}

/**
 * Generates whitespace-only strings constructively.
 */
private fun whitespaceOnlyArb(): Arb<String> {
    val wsChars = listOf(' ', '\t', '\n', '\r')
    return Arb.list(Arb.of(wsChars), 1..20).map { it.joinToString("") }
}

/**
 * Generates valid role characters constructively.
 */
private fun validCharArb(): Arb<Char> {
    val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-', '.')
    return Arb.of(validChars)
}

/**
 * Generates valid role body constructively (1-256 chars).
 */
private fun validRoleBodyArb(): Arb<String> = Arb.list(validCharArb(), 1..256).map { it.joinToString("") }

/**
 * Generates whitespace constructively.
 */
private fun whitespaceArb(): Arb<String> = Arb.list(Arb.of(' ', '\t'), 0..5).map { it.joinToString("") }

/**
 * Generates valid traditional roles constructively.
 */
private fun validTraditionalRoleArb(): Arb<String> =
    Arb.bind(
        Arb.int(0..3),
        validRoleBodyArb(),
        whitespaceArb(),
        whitespaceArb(),
    ) { prefixCount, body, leadingWs, trailingWs ->
        val bodyWithoutColons = body.replace(":", "")
        val prefixes = "ROLE_".repeat(prefixCount)
        "$leadingWs$prefixes$bodyWithoutColons$trailingWs"
    }

/**
 * Generates valid permission-style roles constructively.
 */
private fun validPermissionArb(): Arb<String> {
    val segmentArb = Arb.list(validCharArb(), 1..50).map { it.joinToString("") }
    return Arb.int(2..4).flatMap { segmentCount ->
        when (segmentCount) {
            2 -> Arb.bind(segmentArb, segmentArb) { s1, s2 -> "$s1:$s2" }
            3 -> Arb.bind(segmentArb, segmentArb, segmentArb) { s1, s2, s3 -> "$s1:$s2:$s3" }
            else ->
                Arb.bind(segmentArb, segmentArb, segmentArb, segmentArb) { s1, s2, s3, s4 ->
                    "$s1:$s2:$s3:$s4"
                }
        }
    }
}

/**
 * Generates any valid role constructively.
 */
private fun validRoleArb(): Arb<String> = Arb.of(validTraditionalRoleArb(), validPermissionArb()).flatMap { it }

/**
 * Generates roles with prohibited characters constructively (no filtering!).
 */
private fun invalidRoleWithBadCharsArb(): Arb<String> {
    val validPartArb = Arb.list(validCharArb(), 1..20).map { it.joinToString("") }
    val badCharArb = Arb.of('\'', ';', '(', ')', '`', '\u0000', '<', '>', '&', '|', '$')
    return Arb.bind(validPartArb, badCharArb, validPartArb) { prefix, bad, suffix ->
        "$prefix$bad$suffix"
    }
}

/**
 * Generates invalid permissions (empty segments).
 */
private fun invalidPermissionArb(): Arb<String> =
    Arb.of(
        ":create",
        "widget:",
        "widget::create",
        ":::",
    )
