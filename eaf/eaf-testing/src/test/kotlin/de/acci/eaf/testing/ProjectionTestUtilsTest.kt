package de.acci.eaf.testing

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProjectionTestUtilsTest {

    @Test
    fun `awaitProjection returns immediately when projection exists`() = runTest {
        // Given
        val expected = TestProjection("test-value")

        // When
        val result = awaitProjection(
            repository = { expected },
            timeout = 1.seconds
        )

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `awaitProjection waits and returns when projection becomes available`() = runTest {
        // Given
        val expected = TestProjection("test-value")
        val callCount = AtomicInteger(0)

        // When
        val result = awaitProjection(
            repository = {
                if (callCount.incrementAndGet() >= 3) expected else null
            },
            timeout = 1.seconds,
            pollInterval = 10.milliseconds
        )

        // Then
        assertEquals(expected, result)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `awaitProjection throws TimeoutCancellationException when projection never appears`() {
        // Given / When / Then
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                awaitProjection<TestProjection>(
                    repository = { null },
                    timeout = 100.milliseconds,
                    pollInterval = 10.milliseconds
                )
            }
        }
    }

    @Test
    fun `awaitProjection with aggregateId returns immediately when projection exists`() = runTest {
        // Given
        val aggregateId = UUID.randomUUID()
        val expected = TestProjection("test-value")

        // When
        val result = awaitProjection(
            aggregateId = aggregateId,
            repository = { expected },
            timeout = 1.seconds
        )

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `awaitProjectionCondition returns when condition is satisfied`() = runTest {
        // Given
        val callCount = AtomicInteger(0)
        val expected = TestProjection("final-state")

        // When
        val result = ProjectionTestUtils.awaitProjectionCondition(
            repository = {
                val count = callCount.incrementAndGet()
                when {
                    count < 3 -> TestProjection("initial-state")
                    else -> expected
                }
            },
            condition = { it?.value == "final-state" },
            timeout = 1.seconds,
            pollInterval = 10.milliseconds
        )

        // Then
        assertEquals(expected, result)
        assertEquals(3, callCount.get())
    }

    @Test
    fun `awaitProjectionCondition throws timeout when condition never satisfied`() {
        // Given / When / Then
        assertThrows<TimeoutCancellationException> {
            runBlocking {
                ProjectionTestUtils.awaitProjectionCondition<TestProjection>(
                    repository = { TestProjection("wrong-state") },
                    condition = { proj -> proj?.value == "expected-state" },
                    timeout = 100.milliseconds,
                    pollInterval = 10.milliseconds
                )
            }
        }
    }

    @Test
    fun `awaitProjectionCondition handles null until entity appears with correct state`() = runTest {
        // Given
        val callCount = AtomicInteger(0)
        val expected = TestProjection("correct-state")

        // When
        val result = ProjectionTestUtils.awaitProjectionCondition(
            repository = {
                val count = callCount.incrementAndGet()
                when {
                    count < 2 -> null
                    count < 4 -> TestProjection("wrong-state")
                    else -> expected
                }
            },
            condition = { it?.value == "correct-state" },
            timeout = 1.seconds,
            pollInterval = 10.milliseconds
        )

        // Then
        assertEquals(expected, result)
    }

    /**
     * Simple test projection class.
     */
    private data class TestProjection(val value: String)
}
