package com.axians.eaf.framework.core.common.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Unit tests for Identifier interface implementations.
 *
 * Validates:
 * - Value-based equality
 * - Type-safety across different identifier types
 * - Collections behavior
 */
class IdentifierTest :
    FunSpec({

        // Test identifier implementations
        data class OrderId(
            override val value: String,
        ) : Identifier

        data class WidgetId(
            override val value: String,
        ) : Identifier

        test("should create identifier with string value") {
            val id = OrderId("order-123")
            id.value shouldBe "order-123"
        }

        test("should be equal when values equal") {
            val id1 = OrderId("order-123")
            val id2 = OrderId("order-123")
            id1 shouldBe id2
        }

        test("should not be equal when values differ") {
            val id1 = OrderId("order-123")
            val id2 = OrderId("order-456")
            id1 shouldNotBe id2
        }

        test("should work with different identifier types") {
            val orderId = OrderId("123")
            val widgetId = WidgetId("456")

            orderId.value shouldBe "123"
            widgetId.value shouldBe "456"
        }

        test("should work correctly in Set collections") {
            val id1 = OrderId("order-123")
            val id2 = OrderId("order-123")
            val id3 = OrderId("order-456")

            val set = setOf(id1, id2, id3)
            set.size shouldBe 2 // id1 and id2 are duplicates
        }

        test("should work correctly as Map keys") {
            val id1 = OrderId("order-123")
            val id2 = OrderId("order-123")

            val map = mutableMapOf<OrderId, String>()
            map[id1] = "first"
            map[id2] = "second"

            map.size shouldBe 1 // Same key
            map[id1] shouldBe "second" // Overwritten value
        }
    })
