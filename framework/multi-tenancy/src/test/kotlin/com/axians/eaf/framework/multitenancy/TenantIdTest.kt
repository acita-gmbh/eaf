package com.axians.eaf.framework.multitenancy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for TenantId value object.
 *
 * Epic 4, Story 4.1: AC2 - TenantId.kt value object with validation
 * Tests validate:
 * - Valid tenant IDs (lowercase alphanumeric and hyphens)
 * - Blank ID rejection
 * - Invalid character rejection (uppercase, underscores, special chars)
 * - Length constraints (1-64 characters)
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 *
 * @since 1.0.0
 */
class TenantIdTest {

    // TenantId validation

    @Test
    fun `should create TenantId with valid alphanumeric value`() {
        // Given
        val value = "tenant-123"

        // When
        val tenantId = TenantId(value)

        // Then
        assertThat(tenantId.value).isEqualTo(value)
    }

    @Test
    fun `should create TenantId with hyphens`() {
        // Given
        val value = "tenant-abc-123"

        // When
        val tenantId = TenantId(value)

        // Then
        assertThat(tenantId.value).isEqualTo(value)
    }

    @Test
    fun `should reject blank tenant ID`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("")
        }
        assertThat(exception.message).contains("Tenant ID cannot be blank")
    }

    @Test
    fun `should reject tenant ID with only whitespace`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("   ")
        }
        assertThat(exception.message).contains("Tenant ID cannot be blank")
    }

    @Test
    fun `should reject tenant ID with invalid characters`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("tenant@123")
        }
        assertThat(exception.message).contains("lowercase alphanumeric")
    }

    @Test
    fun `should reject tenant ID with uppercase letters`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("Tenant-123")
        }
        assertThat(exception.message).contains("lowercase alphanumeric")
    }

    @Test
    fun `should reject tenant ID with underscores`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("tenant_123")
        }
        assertThat(exception.message).contains("lowercase alphanumeric")
    }

    @Test
    fun `should reject tenant ID with special characters`() {
        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId("tenant!#$%")
        }
        assertThat(exception.message).contains("lowercase alphanumeric")
    }

    @Test
    fun `should reject tenant ID exceeding maximum length`() {
        // Given
        val tooLong = "a".repeat(65)

        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            TenantId(tooLong)
        }
        assertThat(exception.message).contains("1-64 characters")
    }

    @Test
    fun `should accept tenant ID at maximum length boundary`() {
        // Given
        val maxLength = "a".repeat(64)

        // When
        val tenantId = TenantId(maxLength)

        // Then
        assertThat(tenantId.value).isEqualTo(maxLength)
    }

    @Test
    fun `should accept single character tenant ID`() {
        // Given
        val value = "a"

        // When
        val tenantId = TenantId(value)

        // Then
        assertThat(tenantId.value).isEqualTo(value)
    }

    // TenantId equality

    @Test
    fun `should be equal when values are the same`() {
        // Given
        val tenantId1 = TenantId("tenant-123")
        val tenantId2 = TenantId("tenant-123")

        // When/Then
        assertThat(tenantId1).isEqualTo(tenantId2)
    }

    @Test
    fun `should not be equal when values differ`() {
        // Given
        val tenantId1 = TenantId("tenant-123")
        val tenantId2 = TenantId("tenant-456")

        // When/Then
        assertThat(tenantId1 == tenantId2).isFalse()
    }
}
