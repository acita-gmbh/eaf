package com.axians.eaf.framework.security.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for JWT role normalization logic.
 * Story 8.6-UNIT-001 through 8.6-UNIT-030: Comprehensive role normalization testing
 * Targets RemoveConditionalMutator and boundary condition mutations.
 */
class RoleNormalizationTest :
    FunSpec({

        context("8.6-UNIT-001: Basic role normalization") {
            test("should add ROLE_ prefix to role without prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should handle role with single ROLE_ prefix (idempotent)") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should verify idempotence (double normalization)") {
                val once = JwtValidationFilter.normalizeRoleAuthority("admin")
                val twice = JwtValidationFilter.normalizeRoleAuthority(once.authority)
                once.authority shouldBe twice.authority
            }
        }

        context("8.6-UNIT-002: Multiple ROLE_ prefix handling") {
            test("should strip double ROLE_ prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should strip triple ROLE_ prefix") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_ROLE_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should handle case-insensitive prefixes") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("role_admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should handle mixed-case prefixes") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("RoLe_AdMiN")
                authority.authority shouldBe "ROLE_AdMiN"
            }
        }

        context("8.6-UNIT-003: Permission-style authority handling") {
            test("should handle permission with single colon") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("widget:create")
                authority.authority shouldBe "widget:create" // No ROLE_ prefix
            }

            test("should handle permission with multiple colons") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("resource:action:scope")
                authority.authority shouldBe "resource:action:scope"
            }

            test("should validate all permission segments") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("api:v1:widgets:read")
                authority.authority shouldBe "api:v1:widgets:read"
            }
        }

        context("8.6-UNIT-004: Null and empty validation (fail-closed)") {
            test("should reject null role name") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority(null)
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "must not be null"
            }

            test("should reject empty string") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "non-whitespace"
            }

            test("should reject whitespace-only string") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("   ")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "non-whitespace"
            }

            test("should reject tabs and newlines") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("\t\n")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        context("8.6-UNIT-005: Prefix-only edge cases") {
            test("should reject single ROLE_ prefix alone") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("ROLE_")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "only prefixes"
            }

            test("should reject double ROLE_ prefix alone") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("ROLE_ROLE_")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "only prefixes"
            }

            test("should reject ROLE_ with whitespace") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("ROLE_   ")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        context("8.6-UNIT-006: Injection attack prevention") {
            test("should reject SQL injection (single quote)") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("admin' OR '1'='1")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "prohibited characters"
            }

            test("should reject command injection (semicolon)") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("admin;drop table")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "prohibited characters"
            }

            test("should reject LDAP injection (parentheses)") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("admin)(uid=*")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "prohibited characters"
            }

            test("should reject shell injection (backtick)") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("admin`whoami`")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "prohibited characters"
            }

            test("should reject null bytes") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("admin\u0000")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
            }
        }

        context("8.6-UNIT-007: Length boundary conditions") {
            test("should accept role at max length (256)") {
                val maxRole = "a".repeat(256)
                val authority = JwtValidationFilter.normalizeRoleAuthority(maxRole)
                authority shouldNotBe null
                // Expected length calculation: 256 (max role body) + 5 (prefix "ROLE_") = 261
                val expectedLength = 256 + "ROLE_".length
                authority.authority.length shouldBe expectedLength
            }

            test("should reject role over max length (257)") {
                val longRole = "a".repeat(257)
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority(longRole)
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "too long"
            }

            test("should reject extremely long role (DoS prevention)") {
                val dosRole = "a".repeat(10000)
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority(dosRole)
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "too long"
            }

            test("should handle single character role") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("a")
                authority.authority shouldBe "ROLE_a"
            }
        }

        context("8.6-UNIT-008: Permission-style validation") {
            test("should reject empty segment before colon") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority(":create")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "empty segment"
            }

            test("should reject empty segment after colon") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("widget:")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "empty segment"
            }

            test("should reject empty middle segment") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("widget::create")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "empty segment"
            }

            test("should reject permission segment with prohibited characters") {
                val exception =
                    runCatching {
                        JwtValidationFilter.normalizeRoleAuthority("widget:create;drop")
                    }.exceptionOrNull()

                exception.shouldBeInstanceOf<IllegalArgumentException>()
                exception?.message shouldContain "prohibited characters"
            }
        }

        context("8.6-UNIT-009: Allowed characters validation") {
            test("should accept Unicode letters") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("администратор")
                authority.authority shouldBe "ROLE_администратор"
            }

            test("should accept digits") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("admin123")
                authority.authority shouldBe "ROLE_admin123"
            }

            test("should accept underscore") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf_admin")
                authority.authority shouldBe "ROLE_eaf_admin"
            }

            test("should accept hyphen") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf-admin")
                authority.authority shouldBe "ROLE_eaf-admin"
            }

            test("should accept dot") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf.admin")
                authority.authority shouldBe "ROLE_eaf.admin"
            }

            test("should accept combination of allowed characters") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("eaf_admin-123.user")
                authority.authority shouldBe "ROLE_eaf_admin-123.user"
            }
        }

        context("8.6-UNIT-010: Whitespace handling") {
            test("should trim leading whitespace") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("  admin")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should trim trailing whitespace") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("admin  ")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should trim both sides") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("  admin  ")
                authority.authority shouldBe "ROLE_admin"
            }

            test("should trim after prefix stripping") {
                val authority = JwtValidationFilter.normalizeRoleAuthority("ROLE_  admin")
                authority.authority shouldBe "ROLE_admin"
            }
        }
    })
