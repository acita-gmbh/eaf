package com.axians.eaf.licensing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LicenseCalculatorTest :
    FunSpec({
        test("9.2-UNIT-001: returns totals when inputs valid") {
            val result = LicenseCalculator.calculate(committed = 10, bonus = 2)
            result.fold(
                { error -> throw AssertionError("Expected successful calculation but got $error") },
                {
                    it shouldBe SeatTotals(committed = 10, bonus = 2)
                    it.total shouldBe 12
                },
            )
        }

        test("9.2-UNIT-002: rejects negative committed seats") {
            val result = LicenseCalculator.calculate(committed = -1, bonus = 0)
            result.fold(
                { error -> error.message shouldBe "committed seats cannot be negative" },
                { success -> throw AssertionError("Expected validation error but got $success") },
            )
        }

        test("9.2-UNIT-003: rejects negative bonus seats") {
            val result = LicenseCalculator.calculate(committed = 1, bonus = -5)
            result.fold(
                { error -> error.message shouldBe "bonus seats cannot be negative" },
                { success -> throw AssertionError("Expected validation error but got $success") },
            )
        }
    })
