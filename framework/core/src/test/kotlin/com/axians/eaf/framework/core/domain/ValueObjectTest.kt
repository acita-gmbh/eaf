package com.axians.eaf.framework.core.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for ValueObject base class.
 *
 * Validates:
 * - Structural equality (all properties)
 * - Immutability (data class pattern)
 * - HashCode consistency
 * - Usage as Map keys
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 */
class ValueObjectTest {

    // Test value object implementations
    data class Address(
        val street: String,
        val city: String,
        val zipCode: String,
    ) : ValueObject()

    data class Point(
        val x: Int,
        val y: Int,
    ) : ValueObject()

    @Test
    fun `should be equal when all properties equal (structural equality)`() {
        val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

        assertThat(address1).isEqualTo(address2)
    }

    @Test
    fun `should not be equal when any property differs`() {
        val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val address2 = Address(street = "Oak St", city = "Springfield", zipCode = "12345")

        assertThat(address1).isNotEqualTo(address2)
    }

    @Test
    fun `should have consistent hashCode for same values`() {
        val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

        assertThat(address1.hashCode()).isEqualTo(address2.hashCode())
    }

    @Test
    fun `should support copy() with property changes (data class)`() {
        val original = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val modified = original.copy(street = "Oak St")

        assertThat(original.street).isEqualTo("Main St")
        assertThat(modified.street).isEqualTo("Oak St")
        assertThat(modified.city).isEqualTo("Springfield")
        assertThat(modified.zipCode).isEqualTo("12345")
    }

    @Test
    fun `should be usable as Map keys`() {
        val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

        val map = mutableMapOf<Address, String>()
        map[address1] = "first"
        map[address2] = "second"

        assertThat(map).hasSize(1) // Same key
        assertThat(map[address1]).isEqualTo("second") // Overwritten value
    }

    @Test
    fun `should work correctly in Set collections`() {
        val point1 = Point(x = 10, y = 20)
        val point2 = Point(x = 10, y = 20)
        val point3 = Point(x = 30, y = 40)

        val set = setOf(point1, point2, point3)
        assertThat(set).hasSize(2) // point1 and point2 are duplicates
    }

    @Test
    fun `should support nested value objects`() {
        data class Person(
            val name: String,
            val address: Address,
        ) : ValueObject()

        val address = Address(street = "Main St", city = "Springfield", zipCode = "12345")
        val person1 = Person(name = "Alice", address = address)
        val person2 = Person(name = "Alice", address = address)

        assertThat(person1).isEqualTo(person2)
    }
}
