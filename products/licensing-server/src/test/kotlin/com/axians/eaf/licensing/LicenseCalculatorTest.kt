package com.axians.eaf.licensing

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LicenseCalculatorTest :
    FunSpec({
        test("returns totals when inputs valid") {
            val result = LicenseCalculator.calculate(committed = 10, bonus = 2)
            result.fold(
                { fail("Expected successful calculation but got $it") },
                {
                    it shouldBe SeatTotals(committed = 10, bonus = 2)
                    it.total shouldBe 12
                },
            )
        }

        test("rejects negative committed seats") {
            val result = LicenseCalculator.calculate(committed = -1, bonus = 0)
            result.fold(
                { error -> error.message shouldBe "committed seats cannot be negative" },
                { fail("Expected validation error but got $it") },
            )
        }
    })
