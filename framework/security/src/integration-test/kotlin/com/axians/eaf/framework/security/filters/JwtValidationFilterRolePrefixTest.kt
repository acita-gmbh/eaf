package com.axians.eaf.framework.security.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Unit test validating JwtValidationFilter role name normalization prevents ROLE_ prefix duplication.
 *
 * Critical security fix (Story 5.2 Security Review):
 * Ensures roles work correctly regardless of whether they're stored in Keycloak
 * with or without ROLE_ prefix, preventing authorization bypass or admin lockout.
 *
 * Tests call the actual production normalization function to catch regressions.
 */
class JwtValidationFilterRolePrefixTest :
    FunSpec({

        context("role prefix normalization via production function") {

            test("should normalize roles WITHOUT ROLE_ prefix correctly") {
                // Simulates Keycloak role: "eaf-admin" (no prefix)
                val roleNameFromJwt = "eaf-admin"

                // Call actual production normalization function
                val authority = JwtValidationFilter.normalizeRoleAuthority(roleNameFromJwt)

                // Verify result
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should normalize roles WITH ROLE_ prefix correctly") {
                // Simulates Keycloak role: "ROLE_eaf-admin" (with prefix)
                val roleNameFromJwt = "ROLE_eaf-admin"

                // Call actual production normalization function
                val authority = JwtValidationFilter.normalizeRoleAuthority(roleNameFromJwt)

                // Verify result - prefix removed, then re-added (only once)
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should produce consistent authorities regardless of input format") {
                // Both input formats should produce identical authority
                val roleFormats = listOf("eaf-admin", "ROLE_eaf-admin")

                val authorities =
                    roleFormats.map { roleName ->
                        JwtValidationFilter.normalizeRoleAuthority(roleName)
                    }

                // Both should produce ROLE_eaf-admin
                authorities.forEach { authority ->
                    authority.authority shouldBe "ROLE_eaf-admin"
                }

                // Verify list contains only one unique authority
                authorities.map { it.authority }.distinct().size shouldBe 1
            }

            test("should handle multiple roles with mixed prefixes") {
                val mixedRoleNames =
                    listOf(
                        "eaf-admin",
                        "ROLE_user",
                        "operator",
                        "ROLE_viewer",
                    )

                val authorities =
                    mixedRoleNames.map { roleName ->
                        JwtValidationFilter.normalizeRoleAuthority(roleName)
                    }

                val authorityStrings = authorities.map { it.authority }

                // Verify all have ROLE_ prefix, but only once
                authorityStrings shouldContain "ROLE_eaf-admin"
                authorityStrings shouldContain "ROLE_user"
                authorityStrings shouldContain "ROLE_operator"
                authorityStrings shouldContain "ROLE_viewer"

                // Verify no double-prefixing occurred
                authorityStrings.none { it.contains("ROLE_ROLE_") } shouldBe true
            }

            test("should not affect roles that don't start with ROLE_") {
                val roleNamesWithoutPrefix = listOf("admin", "user", "viewer", "eaf-operator")

                val authorities =
                    roleNamesWithoutPrefix.map { roleName ->
                        JwtValidationFilter.normalizeRoleAuthority(roleName)
                    }

                // All should have ROLE_ prefix added
                authorities.map { it.authority } shouldBe
                    listOf("ROLE_admin", "ROLE_user", "ROLE_viewer", "ROLE_eaf-operator")
            }
        }
    })
