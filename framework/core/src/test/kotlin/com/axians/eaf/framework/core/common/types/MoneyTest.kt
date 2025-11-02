package com.axians.eaf.framework.core.common.types

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.util.Currency

/**
 * Unit tests for Money value object.
 *
 * Validates:
 * - Creation with amount and currency
 * - Structural equality (amount + currency)
 * - Immutability (data class)
 * - BigDecimal precision
 * - Zero and negative amounts
 */
class MoneyTest :
    FunSpec({

        test("should create Money with amount and currency") {
            val money = Money(amount = BigDecimal("100.50"), currency = Currency.getInstance("USD"))

            money.amount shouldBe BigDecimal("100.50")
            money.currency shouldBe Currency.getInstance("USD")
        }

        test("should be equal when amount and currency equal") {
            val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))
            val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))

            money1 shouldBe money2
        }

        test("should not be equal when amount differs") {
            val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
            val money2 = Money(amount = BigDecimal("200.00"), currency = Currency.getInstance("USD"))

            money1 shouldNotBe money2
        }

        test("should not be equal when currency differs") {
            val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
            val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("EUR"))

            money1 shouldNotBe money2
        }

        test("should preserve BigDecimal precision") {
            val preciseAmount = BigDecimal("123.456789")
            val money = Money(amount = preciseAmount, currency = Currency.getInstance("USD"))

            money.amount shouldBe preciseAmount
            money.amount.scale() shouldBe 6
        }

        test("should handle zero amounts") {
            val money = Money(amount = BigDecimal.ZERO, currency = Currency.getInstance("USD"))

            money.amount shouldBe BigDecimal.ZERO
        }

        test("should handle negative amounts (debt representation)") {
            val money = Money(amount = BigDecimal("-50.00"), currency = Currency.getInstance("USD"))

            money.amount shouldBe BigDecimal("-50.00")
        }

        test("should have consistent hashCode for same values") {
            val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
            val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))

            money1.hashCode() shouldBe money2.hashCode()
        }

        test("should work as Map keys") {
            val money1 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
            val money2 = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))

            val map = mutableMapOf<Money, String>()
            map[money1] = "first"
            map[money2] = "second"

            map.size shouldBe 1
            map[money1] shouldBe "second"
        }

        test("should support copy() with modifications") {
            val original = Money(amount = BigDecimal("100.00"), currency = Currency.getInstance("USD"))
            val modified = original.copy(amount = BigDecimal("200.00"))

            original.amount shouldBe BigDecimal("100.00")
            modified.amount shouldBe BigDecimal("200.00")
            modified.currency shouldBe Currency.getInstance("USD")
        }
    })
