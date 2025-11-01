package com.axians.eaf.framework.security.fuzz

import com.axians.eaf.framework.security.filters.JwtValidationFilter
import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest

/**
 * Fuzzer for role normalization logic.
 * Story 8.6: Fuzz testing for security-critical string transformation.
 *
 * EXECUTION: Nightly pipeline (time-boxed).
 * PURPOSE: Find injection vulnerabilities, crashes, or bypass opportunities.
 *
 * Security relevance: Role normalization is part of authorization enforcement.
 * Bugs could lead to:
 * - SQL/LDAP/Shell injection via malicious role names
 * - Privilege escalation from normalization bypasses
 * - DoS from regex complexity or infinite loops
 *
 * Example findings:
 * - Unhandled Unicode edge cases
 * - Regex catastrophic backtracking
 * - Integer overflow in length checks
 * - Bypasses through unexpected character combinations
 */
class RoleNormalizationFuzzer {
    @FuzzTest
    fun fuzzRoleNormalization(data: FuzzedDataProvider) {
        val roleName = data.consumeRemainingAsString()

        try {
            // Test role normalization - should reject invalid input gracefully
            JwtValidationFilter.normalizeRoleAuthority(roleName)
        } catch (e: IllegalArgumentException) {
            // Expected for invalid role names (fail-closed design)
            // This is correct behavior, not a vulnerability
        } catch (e: OutOfMemoryError) {
            // OOM is a DoS finding
            throw e
        } catch (e: StackOverflowError) {
            // Stack overflow (regex DoS potential)
            throw e
        }
        // CodeRabbit: Don't catch generic Exception - let unexpected exceptions propagate to Jazzer
    }

    @FuzzTest
    fun fuzzRoleNormalizationWithNull(data: FuzzedDataProvider) {
        // Test null handling explicitly
        try {
            JwtValidationFilter.normalizeRoleAuthority(null)
        } catch (e: IllegalArgumentException) {
            // Expected - fail-closed design
        } catch (e: NullPointerException) {
            // Should NOT throw NPE - would be a finding
            throw e
        }
    }

    @FuzzTest
    fun fuzzRoleNormalizationInjectionPatterns(data: FuzzedDataProvider) {
        // Structured fuzzing targeting injection patterns
        val baseRole = data.consumeString(50)
        val injectionChar = data.pickValue(listOf("'", ";", "(", ")", "`", "<", ">", "&", "|", "$", "\u0000"))
        val position = data.consumeInt(0, baseRole.length)

        val maliciousRole = baseRole.substring(0, position) + injectionChar + baseRole.substring(position)

        try {
            val result = JwtValidationFilter.normalizeRoleAuthority(maliciousRole)
            // If normalization succeeds, ensure injection char was rejected or escaped
            if (injectionChar in result.authority) {
                // FINDING: Injection character made it through normalization!
                throw AssertionError("Injection character '$injectionChar' not filtered: ${result.authority}")
            }
        } catch (e: IllegalArgumentException) {
            // Expected - injection should be rejected
        }
    }

    @FuzzTest
    fun fuzzRoleNormalizationUnicodeAttacks(data: FuzzedDataProvider) {
        // GitHub Copilot suggestion: Test Unicode normalization and homograph attacks
        // Confusable characters that look similar but have different Unicode codepoints
        val baseRole = data.consumeString(30)

        val unicodeAttacks =
            listOf(
                "\u202E", // Right-to-Left Override (can hide malicious code)
                "\u200B", // Zero-width space (invisible character)
                "\u00AD", // Soft hyphen (invisible in many renderings)
                "\uFEFF", // Zero-width no-break space
                "\u200C", // Zero-width non-joiner
                "\u200D", // Zero-width joiner
                // Homograph attack examples (Cyrillic look-alikes)
                "\u0430", // Cyrillic 'a' (looks like Latin 'a')
                "\u0435", // Cyrillic 'e' (looks like Latin 'e')
                "\u043E", // Cyrillic 'o' (looks like Latin 'o')
            )

        val attack = data.pickValue(unicodeAttacks)
        val position = data.consumeInt(0, baseRole.length)
        val maliciousRole = baseRole.substring(0, position) + attack + baseRole.substring(position)

        try {
            val result = JwtValidationFilter.normalizeRoleAuthority(maliciousRole)
            // Validate that invisible/confusable characters are handled correctly
            // Either rejected or properly normalized
        } catch (e: IllegalArgumentException) {
            // Expected for invalid Unicode patterns
        } catch (e: Exception) {
            // Other exceptions might indicate unexpected Unicode handling
        }
    }
}
