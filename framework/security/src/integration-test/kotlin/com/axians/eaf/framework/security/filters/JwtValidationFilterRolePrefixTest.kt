package com.axians.eaf.framework.security.filters

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Comprehensive security test for JwtValidationFilter role name normalization.
 *
 * Critical security fix (Story 5.2 Security Review + Ollama Deep Analysis):
 * Validates production-grade normalization handles ALL edge cases:
 * - Multiple ROLE_ prefixes (Vuln 1)
 * - Empty/blank role names (Vuln 2)
 * - Injection attempts
 * - Whitespace variations
 * - Case sensitivity
 * - Unicode support
 * - Length validation
 *
 * Tests call actual production function to catch regressions.
 */
class JwtValidationFilterRolePrefixTest :
    FunSpec({

        context("valid role normalization") {

            test("should normalize role WITHOUT prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should normalize role WITH single ROLE_ prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should normalize role WITH double ROLE_ prefix (Vuln 1)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should normalize role WITH triple ROLE_ prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_ROLE_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should handle case-insensitive prefix (role_admin)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("role_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should handle mixed case prefix (RoLe_admin)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("RoLe_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should trim leading and trailing whitespace") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("  ROLE_admin  ")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should handle roles with hyphens") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should handle roles with underscores") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("admin_user")
                authority.authority shouldBe "ROLE_admin_user"
            }

            test("should handle roles with dots") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("admin.user")
                authority.authority shouldBe "ROLE_admin.user"
            }

            test("should handle Unicode letters (Spanish)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("admiñ")
                authority.authority shouldBe "ROLE_admiñ"
            }

            test("should handle Unicode letters (Greek)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("αβγ")
                authority.authority shouldBe "ROLE_αβγ"
            }

            test("should handle numeric role names") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_123")
                authority.authority shouldBe "ROLE_123"
            }

            test("should be idempotent (normalize twice yields same result)") {
                val once = JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_admin")
                val twice = JwtValidationFilter.normalizeRoleAuthority(once.authority)

                once.authority shouldBe "ROLE_admin"
                twice.authority shouldBe "ROLE_admin"
            }
        }

        context("invalid role rejection (fail-closed design)") {

            test("should reject empty string (Vuln 2)") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("")
                    }
                exception.message shouldContain "non-whitespace"
            }

            test("should reject whitespace-only string") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("   ")
                    }
                exception.message shouldContain "non-whitespace"
            }

            test("should reject tab and newline") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("\t\n")
                    }
                exception.message shouldContain "non-whitespace"
            }

            test("should reject pure ROLE_ prefix") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("ROLE_")
                    }
                exception.message shouldContain "only prefixes"
            }

            test("should reject pure double ROLE_ prefix") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_")
                    }
                exception.message shouldContain "only prefixes"
            }

            test("should reject null role name") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority(null)
                    }
                exception.message shouldContain "must not be null"
            }

            test("should reject role with semicolon (injection attempt)") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("admin;")
                    }
                exception.message shouldContain "prohibited characters"
            }

            test("should reject SQL injection attempt") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("'; DROP TABLE users--")
                    }
                exception.message shouldContain "prohibited characters"
            }

            test("should reject role with internal space") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("admin user")
                    }
                exception.message shouldContain "prohibited characters"
            }

            test("should reject role with special characters") {
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority("admin@#$%")
                    }
                exception.message shouldContain "prohibited characters"
            }

            test("should reject role exceeding max length") {
                val longRole = "ROLE_" + "a".repeat(300)
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        JwtValidationFilter.normalizeRoleAuthority(longRole)
                    }
                exception.message shouldContain "too long"
            }
        }

        context("security validation") {

            test("should produce consistent authorities for all valid prefix variations") {
                val variations =
                    listOf(
                        "eaf-admin",
                        "ROLE_eaf-admin",
                        "role_eaf-admin",
                        "RoLe_eaf-admin",
                        "  ROLE_eaf-admin  ",
                    )

                val authorities = variations.map { JwtValidationFilter.normalizeRoleAuthority(it) }

                // All should normalize to same authority
                authorities.map { it.authority }.distinct() shouldBe listOf("ROLE_eaf-admin")
            }

            test("should handle multiple roles with mixed formats") {
                val mixedRoles =
                    listOf(
                        "eaf-admin",
                        "ROLE_user",
                        "ROLE_ROLE_operator",
                        "viewer",
                    )

                val authorities = mixedRoles.map { JwtValidationFilter.normalizeRoleAuthority(it) }

                authorities.map { it.authority } shouldBe
                    listOf(
                        "ROLE_eaf-admin",
                        "ROLE_user",
                        "ROLE_operator",
                        "ROLE_viewer",
                    )

                // Verify no double-prefixing
                authorities.none { it.authority.contains("ROLE_ROLE_") } shouldBe true
            }
        }
    })
