package com.axians.eaf.licensing

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

data class SeatTotals(
    val committed: Int,
    val bonus: Int,
) {
    val total: Int = committed + bonus
}

data class LicenseCalculationError(
    val message: String,
)

object LicenseCalculator {
    fun calculate(
        committed: Int,
        bonus: Int,
    ): Either<LicenseCalculationError, SeatTotals> =
        either {
            ensure(committed >= 0) {
                LicenseCalculationError("committed seats cannot be negative")
            }
            ensure(bonus >= 0) {
                LicenseCalculationError("bonus seats cannot be negative")
            }
            SeatTotals(committed, bonus)
        }
}
