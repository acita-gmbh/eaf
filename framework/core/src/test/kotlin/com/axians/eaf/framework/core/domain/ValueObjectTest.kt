package com.axians.eaf.framework.core.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for ValueObject base class.
 *
 * Validates:
 * - Structural equality (all properties)
 * - Immutability (data class pattern)
 * - HashCode consistency
 * - Usage as Map keys
 */
class ValueObjectTest :
    FunSpec({

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

        test("should be equal when all properties equal (structural equality)") {
            val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

            address1 shouldBe address2
        }

        test("should not be equal when any property differs") {
            val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val address2 = Address(street = "Oak St", city = "Springfield", zipCode = "12345")

            address1 shouldNotBe address2
        }

        test("should have consistent hashCode for same values") {
            val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

            address1.hashCode() shouldBe address2.hashCode()
        }

        test("should support copy() with property changes (data class)") {
            val original = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val modified = original.copy(street = "Oak St")

            original.street shouldBe "Main St"
            modified.street shouldBe "Oak St"
            modified.city shouldBe "Springfield"
            modified.zipCode shouldBe "12345"
        }

        test("should be usable as Map keys") {
            val address1 = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val address2 = Address(street = "Main St", city = "Springfield", zipCode = "12345")

            val map = mutableMapOf<Address, String>()
            map[address1] = "first"
            map[address2] = "second"

            map.size shouldBe 1 // Same key
            map[address1] shouldBe "second" // Overwritten value
        }

        test("should work correctly in Set collections") {
            val point1 = Point(x = 10, y = 20)
            val point2 = Point(x = 10, y = 20)
            val point3 = Point(x = 30, y = 40)

            val set = setOf(point1, point2, point3)
            set.size shouldBe 2 // point1 and point2 are duplicates
        }

        test("should support nested value objects") {
            data class Person(
                val name: String,
                val address: Address,
            ) : ValueObject()

            val address = Address(street = "Main St", city = "Springfield", zipCode = "12345")
            val person1 = Person(name = "Alice", address = address)
            val person2 = Person(name = "Alice", address = address)

            person1 shouldBe person2
        }
    })
