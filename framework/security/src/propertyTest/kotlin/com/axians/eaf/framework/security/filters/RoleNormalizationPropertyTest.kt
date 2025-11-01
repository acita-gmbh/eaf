package com.axians.eaf.framework.security.filters

import com.axians.eaf.testing.tags.PbtTag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
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
 * Property-based tests for JWT role normalization.
 * Story 8.6: Multi-stage testing pipeline with optimized constructive generators.
 *
 * EXECUTION: Runs in nightly/post-merge pipeline only (not on PRs).
 * EXCLUDED FROM: Mutation testing (pitest) due to exponential time complexity.
 *
 * Uses constructive generation (NOT .filter()) for performance:
 * - Builds strings from constrained character sets
 * - Never discards generated values
 * - Orders of magnitude faster than regex filtering
 *
 * Industry standard: Property tests for deep validation, mutation tests for fast feedback.
 */
class RoleNormalizationPropertyTest : FunSpec() {
    override fun tags() = setOf(PbtTag)

    init {
        context("8.6-PBT-001: Idempotence property") {
            test("normalizing twice should equal normalizing once") {
                checkAll(1000, validRoleArb()) { validRole ->
                    val once = JwtValidationFilter.normalizeRoleAuthority(validRole)
                    val twice = JwtValidationFilter.normalizeRoleAuthority(once.authority)
                    once.authority shouldBe twice.authority
                }
            }
        }

        context("8.6-PBT-002: Prefix consistency") {
            test("all non-permission roles should start with ROLE_ prefix") {
                forAll(500, validTraditionalRoleArb()) { role ->
                    val authority = JwtValidationFilter.normalizeRoleAuthority(role)
                    authority.authority.startsWith("ROLE_")
                }
            }

            test("ROLE_ prefix should appear exactly once") {
                checkAll(500, validTraditionalRoleArb()) { role ->
                    val authority = JwtValidationFilter.normalizeRoleAuthority(role)
                    val prefixCount = authority.authority.windowed(5).count { it == "ROLE_" }
                    prefixCount shouldBe 1
                }
            }
        }

        context("8.6-PBT-003: Permission-style roles have no ROLE_ prefix") {
            test("permission-style roles should NOT have ROLE_ prefix") {
                forAll(500, validPermissionArb()) { permission ->
                    val authority = JwtValidationFilter.normalizeRoleAuthority(permission)
                    !authority.authority.startsWith("ROLE_")
                }
            }

            test("permission-style roles preserve colon separator") {
                checkAll(500, validPermissionArb()) { permission ->
                    val authority = JwtValidationFilter.normalizeRoleAuthority(permission)
                    authority.authority shouldMatch Regex(".*:.*")
                }
            }
        }

        context("8.6-PBT-004: Security invariants") {
            test("normalized roles never contain injection characters") {
                checkAll(1000, validRoleArb()) { role ->
                    val authority = JwtValidationFilter.normalizeRoleAuthority(role)
                    authority.authority shouldNotContain "'"
                    authority.authority shouldNotContain ";"
                    authority.authority shouldNotContain "("
                    authority.authority shouldNotContain ")"
                    authority.authority shouldNotContain "`"
                    authority.authority shouldNotContain "\u0000"
                }
            }
        }

        context("8.6-PBT-005: Fail-closed for invalid inputs") {
            test("should reject roles with prohibited characters") {
                checkAll(500, invalidRoleWithBadCharsArb()) { invalidRole ->
                    val result =
                        runCatching {
                            JwtValidationFilter.normalizeRoleAuthority(invalidRole)
                        }
                    result.isFailure shouldBe true
                    result.exceptionOrNull() shouldNotBe null
                }
            }

            test("should reject empty segments in permissions") {
                checkAll(200, invalidPermissionArb()) { invalidPermission ->
                    val result =
                        runCatching {
                            JwtValidationFilter.normalizeRoleAuthority(invalidPermission)
                        }
                    result.isFailure shouldBe true
                }
            }
        }
    }
}

// ============================================================================
// Optimized Arb Generators using Constructive Generation
// ============================================================================

/**
 * Generates valid role characters using CONSTRUCTIVE approach (NOT filter).
 * Builds from explicit character set - never discards values.
 */
private fun validCharArb(): Arb<Char> {
    val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-', '.')
    return Arb.of(validChars)
}

/**
 * Generates valid role body (1-256 chars, constructive).
 * PERFORMANCE: 100x faster than Arb.string().filter { regex }
 */
private fun validRoleBodyArb(): Arb<String> = Arb.list(validCharArb(), 1..256).map { it.joinToString("") }

/**
 * Generates whitespace strings constructively.
 */
private fun whitespaceArb(): Arb<String> = Arb.list(Arb.of(' ', '\t'), 0..5).map { it.joinToString("") }

/**
 * Generates valid traditional roles (no colons, with optional ROLE_ prefixes).
 */
private fun validTraditionalRoleArb(): Arb<String> =
    Arb.bind(
        Arb.int(0..3), // Random number of ROLE_ prefixes
        validRoleBodyArb(),
        whitespaceArb(), // Leading whitespace
        whitespaceArb(), // Trailing whitespace
    ) { prefixCount, body, leadingWs, trailingWs ->
        val bodyWithoutColons = body.replace(":", "") // Ensure no colons
        val prefixes = "ROLE_".repeat(prefixCount)
        "$leadingWs$prefixes$bodyWithoutColons$trailingWs"
    }

/**
 * Generates valid permission-style roles (2-4 segments with colons).
 * Constructive: builds segments, joins with colons.
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
 * Generates any valid role (50/50 traditional vs permission).
 */
private fun validRoleArb(): Arb<String> = Arb.of(validTraditionalRoleArb(), validPermissionArb()).flatMap { it }

/**
 * Generates roles with prohibited characters using CONSTRUCTIVE approach.
 * Builds: validPrefix + invalidChar + validSuffix (no filtering).
 */
private fun invalidRoleWithBadCharsArb(): Arb<String> {
    val validPartArb = Arb.list(validCharArb(), 1..20).map { it.joinToString("") }
    val badCharArb = Arb.of('\'', ';', '(', ')', '`', '\u0000', '<', '>', '&', '|', '$')
    return Arb.bind(validPartArb, badCharArb, validPartArb) { prefix, bad, suffix ->
        "$prefix$bad$suffix"
    }
}

/**
 * Generates invalid permission-style roles (empty segments).
 */
private fun invalidPermissionArb(): Arb<String> =
    Arb.of(
        ":create", // Empty first segment
        "widget:", // Empty last segment
        "widget::create", // Empty middle segment
        ":::", // All empty
    )
