package com.axians.eaf.products.widgetdemo.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Widget aggregate tenant isolation with audit logging.
 *
 * These tests verify that TenantIsolationViolation error class maintains CWE-209 protection
 * and provides proper utility methods without exposing tenant IDs.
 *
 * Note: Audit logging paths in Widget.kt (lines 80-100, 153-177) are covered through
 * integration tests where the actual command handlers execute with real tenant context.
 * Unit testing the logging paths directly would require complex log capture infrastructure.
 *
 * Story 8.5: Architectural Patterns Alignment
 */
class WidgetTenantAuditLoggingTest :
    FunSpec({

        context("TenantIsolationViolation utility methods") {
            test("8.5-UNIT-AUDIT-005: data object should be singleton (referential equality)") {
                // Given: TenantIsolationViolation is a data object (singleton)
                val error1 = WidgetError.TenantIsolationViolation
                val error2 = WidgetError.TenantIsolationViolation

                // When/Then: Should be the exact same instance (singleton)
                (error1 === error2) shouldBe true
                (error1 == error2) shouldBe true
            }

            test("8.5-UNIT-AUDIT-006: equals should return false for different types") {
                // Given: TenantIsolationViolation and other error type
                val error1 = WidgetError.TenantIsolationViolation
                val error2 = WidgetError.NotFound("widget-id")

                // When/Then: Should not be equal
                (error1 == error2) shouldBe false
            }

            test("8.5-UNIT-AUDIT-007: hashCode should be consistent (singleton)") {
                // Given: TenantIsolationViolation is a data object
                val error1 = WidgetError.TenantIsolationViolation
                val error2 = WidgetError.TenantIsolationViolation

                // When/Then: HashCodes should be identical (same instance)
                error1.hashCode() shouldBe error2.hashCode()
            }

            test("8.5-UNIT-AUDIT-008: toString should never expose tenant IDs (CWE-209)") {
                // Given: TenantIsolationViolation
                val error = WidgetError.TenantIsolationViolation

                // When: Converting to string
                val message = error.toString()

                // Then: Should be completely generic
                message shouldBe "Access denied: tenant context mismatch"
                // Verify no UUID-like patterns (tenant IDs are UUIDs) - use compiled regex
                UUID_PATTERN.containsMatchIn(message) shouldBe false
            }
        }
    }) {
    companion object {
        // Compile UUID regex once for performance (Copilot suggestion)
        private val UUID_PATTERN =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
