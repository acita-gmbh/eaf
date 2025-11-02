package com.axians.eaf.framework.core.common.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal

/**
 * Unit tests for Quantity value object.
 *
 * Validates:
 * - Creation with value and unit
 * - Structural equality (value + unit)
 * - Immutability (data class)
 * - BigDecimal precision
 * - Various unit types
 * - Zero quantities
 */
class QuantityTest :
    FunSpec({

        test("should create Quantity with value and unit") {
            val quantity = Quantity(value = BigDecimal("10.5"), unit = "kg")

            quantity.value shouldBe BigDecimal("10.5")
            quantity.unit shouldBe "kg"
        }

        test("should be equal when value and unit equal") {
            val quantity1 = Quantity(value = BigDecimal("100"), unit = "items")
            val quantity2 = Quantity(value = BigDecimal("100"), unit = "items")

            quantity1 shouldBe quantity2
        }

        test("should not be equal when value differs") {
            val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
            val quantity2 = Quantity(value = BigDecimal("20"), unit = "kg")

            quantity1 shouldNotBe quantity2
        }

        test("should not be equal when unit differs") {
            val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
            val quantity2 = Quantity(value = BigDecimal("10"), unit = "liters")

            quantity1 shouldNotBe quantity2
        }

        test("should preserve BigDecimal precision") {
            val preciseValue = BigDecimal("123.456789")
            val quantity = Quantity(value = preciseValue, unit = "meters")

            quantity.value shouldBe preciseValue
            quantity.value.scale() shouldBe 6
        }

        test("should handle various unit types") {
            val quantities =
                listOf(
                    Quantity(value = BigDecimal("10"), unit = "kg"),
                    Quantity(value = BigDecimal("5"), unit = "liters"),
                    Quantity(value = BigDecimal("100"), unit = "items"),
                    Quantity(value = BigDecimal("25.5"), unit = "meters"),
                    Quantity(value = BigDecimal("3"), unit = "boxes"),
                )

            quantities[0].unit shouldBe "kg"
            quantities[1].unit shouldBe "liters"
            quantities[2].unit shouldBe "items"
            quantities[3].unit shouldBe "meters"
            quantities[4].unit shouldBe "boxes"
        }

        test("should handle zero quantities") {
            val quantity = Quantity(value = BigDecimal.ZERO, unit = "items")

            quantity.value shouldBe BigDecimal.ZERO
        }

        test("should have consistent hashCode for same values") {
            val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
            val quantity2 = Quantity(value = BigDecimal("10"), unit = "kg")

            quantity1.hashCode() shouldBe quantity2.hashCode()
        }

        test("should work as Map keys") {
            val quantity1 = Quantity(value = BigDecimal("10"), unit = "kg")
            val quantity2 = Quantity(value = BigDecimal("10"), unit = "kg")

            val map = mutableMapOf<Quantity, String>()
            map[quantity1] = "first"
            map[quantity2] = "second"

            map.size shouldBe 1
            map[quantity1] shouldBe "second"
        }

        test("should support copy() with modifications") {
            val original = Quantity(value = BigDecimal("10"), unit = "kg")
            val modified = original.copy(value = BigDecimal("20"))

            original.value shouldBe BigDecimal("10")
            modified.value shouldBe BigDecimal("20")
            modified.unit shouldBe "kg"
        }

        test("should handle fractional quantities") {
            val quantity = Quantity(value = BigDecimal("0.5"), unit = "kg")

            quantity.value shouldBe BigDecimal("0.5")
        }

        test("should support negative quantities (e.g., stock adjustments)") {
            val quantity = Quantity(value = BigDecimal("-10"), unit = "items")

            quantity.value shouldBe BigDecimal("-10")
        }
    })
