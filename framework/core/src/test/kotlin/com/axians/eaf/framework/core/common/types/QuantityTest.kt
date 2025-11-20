package com.axians.eaf.framework.core.common.types

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Unit tests for Quantity value object - unit-aware measurements.
 *
 * Validates the Quantity value object used for measurements throughout EAF, ensuring unit
 * safety, precision handling, and proper value object semantics.
 *
 * **Test Coverage:**
 * - Creation with BigDecimal value and unit string
 * - Structural equality (value + unit must both match)
 * - Immutability (data class semantics)
 * - BigDecimal precision preservation
 * - Various unit types (kg, meters, liters, etc.)
 * - Zero quantities
 * - Unit mismatch detection (5 kg ≠ 5 liters)
 *
 * **DDD Patterns:**
 * - Value Object (no identity, compared by value)
 * - Unit safety (prevents kg + liters errors)
 * - Domain-specific measurements
 *
 * @see Quantity Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class QuantityTest {

    @Test
    fun `should create Quantity with value and unit`() {
        val quantity = Quantity(value = BigDecimal("10.5"), unit = "kg")

        assertThat(quantity.value).isEqualTo(BigDecimal("10.5"))
        assertThat(quantity.unit).isEqualTo("kg")
    }

    @Test
    fun `should be equal when value and unit equal`() {
        val quantity1 = Quantity(value = BigDecimal("100"), unit = "items")
        val quantity2 = Quantity(value = BigDecimal("100"), unit = "items")

        assertThat(quantity1).isEqualTo(quantity2)
    }

    @Test
    fun `should not be equal when value differs`() {
        val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
        val quantity2 = Quantity(value = BigDecimal("20"), unit = "kg")

        assertThat(quantity1).isNotEqualTo(quantity2)
    }

    @Test
    fun `should not be equal when unit differs`() {
        val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
        val quantity2 = Quantity(value = BigDecimal("10"), unit = "liters")

        assertThat(quantity1).isNotEqualTo(quantity2)
    }

    @Test
    fun `should preserve BigDecimal precision`() {
        val preciseValue = BigDecimal("123.456789")
        val quantity = Quantity(value = preciseValue, unit = "meters")

        assertThat(quantity.value).isEqualTo(preciseValue)
        assertThat(quantity.value.scale()).isEqualTo(6)
    }

    @Test
    fun `should handle various unit types`() {
        val quantities =
            listOf(
                Quantity(value = BigDecimal("10"), unit = "kg"),
                Quantity(value = BigDecimal("5"), unit = "liters"),
                Quantity(value = BigDecimal("100"), unit = "items"),
                Quantity(value = BigDecimal("25.5"), unit = "meters"),
                Quantity(value = BigDecimal("3"), unit = "boxes"),
            )

        assertThat(quantities[0].unit).isEqualTo("kg")
        assertThat(quantities[1].unit).isEqualTo("liters")
        assertThat(quantities[2].unit).isEqualTo("items")
        assertThat(quantities[3].unit).isEqualTo("meters")
        assertThat(quantities[4].unit).isEqualTo("boxes")
    }

    @Test
    fun `should handle zero quantities`() {
        val quantity = Quantity(value = BigDecimal.ZERO, unit = "items")

        assertThat(quantity.value).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should have consistent hashCode for same values`() {
        val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
        val quantity2 = Quantity(value = BigDecimal("10"), unit = "kg")

        assertThat(quantity1.hashCode()).isEqualTo(quantity2.hashCode())
    }

    @Test
    fun `should work as Map keys`() {
        val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
        val quantity2 = Quantity(value = BigDecimal("10"), unit = "kg")

        val map = mutableMapOf<Quantity, String>()
        map[quantity1] = "first"
        map[quantity2] = "second"

        assertThat(map).hasSize(1)
        assertThat(map[quantity1]).isEqualTo("second")
    }

    @Test
    fun `should support copy() with modifications`() {
        val original = Quantity(value = BigDecimal("10"), unit = "kg")
        val modified = original.copy(value = BigDecimal("20"))

        assertThat(original.value).isEqualTo(BigDecimal("10"))
        assertThat(modified.value).isEqualTo(BigDecimal("20"))
        assertThat(modified.unit).isEqualTo("kg")
    }

    @Test
    fun `should handle fractional quantities`() {
        val quantity = Quantity(value = BigDecimal("0.5"), unit = "kg")

        assertThat(quantity.value).isEqualTo(BigDecimal("0.5"))
    }

    @Test
    fun `should support negative quantities (e.g., stock adjustments)`() {
        val quantity = Quantity(value = BigDecimal("-10"), unit = "items")

        assertThat(quantity.value).isEqualTo(BigDecimal("-10"))
    }
}
