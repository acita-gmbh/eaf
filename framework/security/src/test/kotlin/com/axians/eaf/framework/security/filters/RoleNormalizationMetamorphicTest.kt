package com.axians.eaf.framework.security.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll

/**
 * Metamorphic tests for JWT role normalization.
 * Story 8.6: Advanced testing - Metamorphic Testing strategy.
 *
 * Metamorphic testing validates RELATIONSHIPS between inputs/outputs without needing
 * to know the exact output. Tests transformation invariants and properties.
 *
 * EXECUTION: Runs in fast-feedback loop (every PR) with pitest.
 * PERFORMANCE: Extremely fast (milliseconds), excellent for killing mutations.
 *
 * Industry standard: Test what should be TRUE about transformations, not specific values.
 */
class RoleNormalizationMetamorphicTest :
    FunSpec({

        context("8.6-META-001: Idempotence property") {
            test("normalizing twice should equal normalizing once") {
                checkAll(1000, validRoleBodyArb()) { role ->
                    val once = JwtValidationFilter.normalizeRoleAuthority(role)
                    val twice = JwtValidationFilter.normalizeRoleAuthority(once.authority)

                    // METAMORPHIC RELATION: normalize(normalize(x)) == normalize(x)
                    once.authority shouldBe twice.authority
                }
            }
        }

        context("8.6-META-002: Prefix invariance") {
            test("adding ROLE_ prefix should not change normalized output") {
                checkAll(500, validRoleBodyArb()) { role ->
                    val withoutPrefix = JwtValidationFilter.normalizeRoleAuthority(role)
                    val withPrefix = JwtValidationFilter.normalizeRoleAuthority("ROLE_$role")

                    // METAMORPHIC RELATION: normalize("ROLE_" + x) == normalize(x)
                    withPrefix.authority shouldBe withoutPrefix.authority
                }
            }

            test("multiple ROLE_ prefixes should normalize to single prefix") {
                checkAll(500, validRoleBodyArb(), Arb.int(1..5)) { role, prefixCount ->
                    val prefixes = "ROLE_".repeat(prefixCount)
                    val withMultiplePrefixes = JwtValidationFilter.normalizeRoleAuthority("$prefixes$role")
                    val withSinglePrefix = JwtValidationFilter.normalizeRoleAuthority("ROLE_$role")

                    // METAMORPHIC RELATION: normalize("ROLE_"*n + x) == normalize("ROLE_" + x)
                    withMultiplePrefixes.authority shouldBe withSinglePrefix.authority
                }
            }
        }

        context("8.6-META-003: Whitespace invariance") {
            test("leading/trailing whitespace should not affect normalized output") {
                checkAll(500, validRoleBodyArb(), whitespaceArb()) { role, ws ->
                    val normal = JwtValidationFilter.normalizeRoleAuthority(role)
                    val withWhitespace = JwtValidationFilter.normalizeRoleAuthority("$ws$role$ws")

                    // METAMORPHIC RELATION: normalize(ws + x + ws) == normalize(x)
                    withWhitespace.authority shouldBe normal.authority
                }
            }

            test("whitespace around ROLE_ prefix should not affect output") {
                checkAll(500, validRoleBodyArb()) { role ->
                    val normal = JwtValidationFilter.normalizeRoleAuthority("ROLE_$role")
                    val withSpaces = JwtValidationFilter.normalizeRoleAuthority("ROLE_  $role")

                    // METAMORPHIC RELATION: normalize("ROLE_  x") == normalize("ROLE_x")
                    withSpaces.authority shouldBe normal.authority
                }
            }
        }

        context("8.6-META-004: Length preservation (modulo prefix)") {
            test("normalized body length equals input body length (for non-permission roles)") {
                checkAll(500, validRoleBodyArb()) { role ->
                    val bodyWithoutColons = role.replace(":", "")
                    val normalized = JwtValidationFilter.normalizeRoleAuthority(bodyWithoutColons)
                    val normalizedBody = normalized.authority.removePrefix("ROLE_")

                    // METAMORPHIC RELATION: |normalize(x).body| == |x| for traditional roles
                    normalizedBody.length shouldBe bodyWithoutColons.length
                }
            }
        }

        context("8.6-META-005: Character preservation") {
            test("normalized output should preserve all valid input characters") {
                checkAll(500, validRoleBodyArb()) { role ->
                    val normalized = JwtValidationFilter.normalizeRoleAuthority(role)
                    val normalizedBody = normalized.authority.removePrefix("ROLE_")

                    // METAMORPHIC RELATION: All chars in input appear in output (modulo prefix/whitespace)
                    val inputCharsSet = role.trim().replace(":", "").toSet()
                    val outputCharsSet = normalizedBody.toSet()

                    // All non-colon input chars should be in output
                    inputCharsSet.all { it in outputCharsSet } shouldBe true
                }
            }
        }

        context("8.6-META-006: Mixed-case prefix normalization") {
            test("case variations of ROLE_ prefix should normalize identically") {
                checkAll(500, validRoleBodyArb(), mixedCasePrefixArb()) { role, prefix ->
                    val withMixedCase = JwtValidationFilter.normalizeRoleAuthority("$prefix$role")
                    val withUpperCase = JwtValidationFilter.normalizeRoleAuthority("ROLE_$role")

                    // METAMORPHIC RELATION: normalize(any_case("ROLE_") + x) == normalize("ROLE_" + x)
                    withMixedCase.authority shouldBe withUpperCase.authority
                }
            }
        }

        context("8.6-META-007: Commutativity of whitespace trimming and prefix stripping") {
            test("order of trim and prefix removal should not matter") {
                checkAll(500, validRoleBodyArb()) { role ->
                    val padded = "  ROLE_$role  "
                    val result1 = JwtValidationFilter.normalizeRoleAuthority(padded)

                    val trimmedFirst = padded.trim()
                    val result2 = JwtValidationFilter.normalizeRoleAuthority(trimmedFirst)

                    // METAMORPHIC RELATION: normalize(trim(x)) == normalize(x)
                    result1.authority shouldBe result2.authority
                }
            }
        }
    })

// ============================================================================
// Helper Generators (constructive, reused from property tests)
// ============================================================================

private fun validCharArb(): Arb<Char> {
    val validChars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('_', '-', '.')
    return Arb.of(validChars)
}

private fun validRoleBodyArb(): Arb<String> = Arb.list(validCharArb(), 1..100).map { it.joinToString("") }

private fun whitespaceArb(): Arb<String> = Arb.list(Arb.of(' ', '\t'), 0..3).map { it.joinToString("") }

private fun mixedCasePrefixArb(): Arb<String> =
    Arb.of(
        "role_",
        "Role_",
        "rOle_",
        "roLe_",
        "rolE_",
        "ROle_",
        "RoLe_",
        "RolE_",
        "rOLE_",
        "RoLE_",
        "ROLe_",
        "ROLE_",
    )
