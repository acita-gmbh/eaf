package de.acci.eaf.core.result

import de.acci.eaf.core.error.DomainError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `map transforms success and leaves failure`() {
        val success = 2.success()
        val failure: Result<Int, DomainError> = DomainError.ValidationFailed("f", "m").failure()

        val mappedSuccess = success.map { it * 2 }
        val mappedFailure = failure.map { it * 2 }

        assertEquals(4, (mappedSuccess as Result.Success).value)
        assertTrue(mappedFailure is Result.Failure)
    }

    @Test
    fun `flatMap chains success and short-circuits failure`() {
        val success = 3.success()
        val failure: Result<Int, DomainError> = DomainError.InvalidStateTransition("s", "a").failure()

        val chained = success.flatMap { (it + 1).success() }
        val shortCircuited = failure.flatMap { (it + 1).success() }

        assertEquals(4, (chained as Result.Success).value)
        assertTrue(shortCircuited is Result.Failure)
    }

    @Test
    fun `fold returns appropriate branch`() {
        val success = "ok".success()
        val failure: Result<String, DomainError> = DomainError.ResourceNotFound("X", "1").failure()

        val s = success.fold(onSuccess = { it.uppercase() }, onFailure = { "fail" })
        val f = failure.fold(onSuccess = { it.uppercase() }, onFailure = { "fail" })

        assertEquals("OK", s)
        assertEquals("fail", f)
    }

    @Test
    fun `getOrElse yields fallback on failure`() {
        val success = 10.success()
        val failure: Result<Int, DomainError> = DomainError.InfrastructureError("io").failure()

        assertEquals(10, success.getOrElse { -1 })
        assertEquals(-1, failure.getOrElse { -1 })
    }

    @Test
    fun `onSuccess and onFailure trigger only relevant side`() {
        val sideEffects = mutableListOf<String>()

        "hi".success()
            .onSuccess { sideEffects.add("success:$it") }
            .onFailure { sideEffects.add("failure:$it") }

        val err: Result<String, DomainError> = DomainError.ValidationFailed("f", "bad").failure()
        err.onSuccess { sideEffects.add("success-err") }
            .onFailure { sideEffects.add("failure:err") }

        assertEquals(listOf("success:hi", "failure:err"), sideEffects)
    }

    @Test
    fun `failure exposes original error`() {
        val error = DomainError.ResourceNotFound(type = "Entity", id = "42")
        val failure: Result<String, DomainError> = Result.Failure(error)

        assertEquals(error, (failure as Result.Failure).error)
    }

    @Test
    fun `map and flatMap exercised on both branches for mutation coverage`() {
        val success = 5.success()
        val failure: Result<Int, DomainError> = DomainError.InfrastructureError("err").failure()

        // success path
        val mapped = success.map { it + 1 }.flatMap { (it * 2).success() }
        assertEquals(12, (mapped as Result.Success).value)

        // failure path
        val mappedFail = failure.map { it + 1 }.flatMap { (it * 2).success() }
        assertTrue(mappedFail is Result.Failure)
    }

    @Test
    fun `fold and getOrElse cover both branches`() {
        val s = "ok".success()
        val f: Result<String, DomainError> = DomainError.ValidationFailed("f", "m").failure()

        assertEquals("OK", s.fold({ it.uppercase() }, { "fail" }))
        assertEquals("fallback", f.fold({ it }, { "fallback" }))

        assertEquals("ok", s.getOrElse { "fallback" })
        assertEquals("fallback", f.getOrElse { "fallback" })
    }

    @Test
    fun `onSuccess and onFailure no-ops on opposite branch`() {
        val success = 1.success()
        val failure: Result<Int, DomainError> = DomainError.ValidationFailed("f", "m").failure()
        val sideEffects = mutableListOf<String>()

        success.onFailure { sideEffects.add("fail") }
        failure.onSuccess { sideEffects.add("success") }

        assertTrue(sideEffects.isEmpty())
    }
}
