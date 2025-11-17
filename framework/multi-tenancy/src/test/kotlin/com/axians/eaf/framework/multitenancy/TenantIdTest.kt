package com.axians.eaf.framework.multitenancy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for TenantId value object.
 *
 * Epic 4, Story 4.1: AC2 - TenantId.kt value object with validation
 * Tests validate:
 * - Valid tenant IDs (alphanumeric, hyphens, underscores)
 * - Blank ID rejection
 * - Invalid character rejection
 * - Length constraints (1-255 characters)
 *
 * @since 1.0.0
 */
class TenantIdTest :
    FunSpec({

        context("TenantId validation") {

            test("should create TenantId with valid alphanumeric value") {
                // Given
                val value = "tenant-123"

                // When
                val tenantId = TenantId(value)

                // Then
                tenantId.value shouldBe value
            }

            test("should create TenantId with hyphens") {
                // Given
                val value = "tenant-abc-123"

                // When
                val tenantId = TenantId(value)

                // Then
                tenantId.value shouldBe value
            }

            test("should reject blank tenant ID") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("")
                    }
                exception.message shouldContain "Tenant ID cannot be blank"
            }

            test("should reject tenant ID with only whitespace") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("   ")
                    }
                exception.message shouldContain "Tenant ID cannot be blank"
            }

            test("should reject tenant ID with invalid characters") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("tenant@123")
                    }
                exception.message shouldContain "lowercase alphanumeric"
            }

            test("should reject tenant ID with uppercase letters") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("Tenant-123")
                    }
                exception.message shouldContain "lowercase alphanumeric"
            }

            test("should reject tenant ID with underscores") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("tenant_123")
                    }
                exception.message shouldContain "lowercase alphanumeric"
            }

            test("should reject tenant ID with special characters") {
                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId("tenant!#$%")
                    }
                exception.message shouldContain "lowercase alphanumeric"
            }

            test("should reject tenant ID exceeding maximum length") {
                // Given
                val tooLong = "a".repeat(65)

                // When/Then
                val exception =
                    shouldThrow<IllegalArgumentException> {
                        TenantId(tooLong)
                    }
                exception.message shouldContain "1-64 characters"
            }

            test("should accept tenant ID at maximum length boundary") {
                // Given
                val maxLength = "a".repeat(64)

                // When
                val tenantId = TenantId(maxLength)

                // Then
                tenantId.value shouldBe maxLength
            }

            test("should accept single character tenant ID") {
                // Given
                val value = "a"

                // When
                val tenantId = TenantId(value)

                // Then
                tenantId.value shouldBe value
            }
        }

        context("TenantId equality") {

            test("should be equal when values are the same") {
                // Given
                val tenantId1 = TenantId("tenant-123")
                val tenantId2 = TenantId("tenant-123")

                // When/Then
                tenantId1 shouldBe tenantId2
            }

            test("should not be equal when values differ") {
                // Given
                val tenantId1 = TenantId("tenant-123")
                val tenantId2 = TenantId("tenant-456")

                // When/Then
                (tenantId1 == tenantId2) shouldBe false
            }
        }
    })
