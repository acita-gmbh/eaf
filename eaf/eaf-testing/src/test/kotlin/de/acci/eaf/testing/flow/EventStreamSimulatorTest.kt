package de.acci.eaf.testing.flow

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Reference implementation for testing Kotlin Flows with Turbine.
 *
 * This test demonstrates the 2025 Kotlin Backend Survey recommended patterns:
 * - Use `runTest` for deterministic async testing
 * - Use Turbine for Flow testing with assertion-based validation
 * - Virtual time eliminates real delays
 *
 * ## Key Patterns
 *
 * 1. **`runTest`**: Provides virtual time control
 * 2. **`flow.test { }`**: Turbine's test extension for Flows
 * 3. **`awaitItem()`**: Assert next emission
 * 4. **`awaitComplete()`**: Assert Flow completion
 *
 * ## References
 * - <a href="https://blog.jetbrains.com/kotlin/2025/12/how-backend-development-teams-use-kotlin-in-2025/">JetBrains 2025 Kotlin Survey</a>
 * - <a href="https://github.com/cashapp/turbine">Turbine Documentation</a>
 * - docs/claude-guides/backend-patterns.md
 */
@DisplayName("EventStreamSimulator - Flow Testing Reference")
class EventStreamSimulatorTest {

    private companion object {
        const val MIN_STEPS = 1
        const val MAX_STEPS = 100
        const val MAX_PROGRESS = 100
    }

    @Test
    fun `should emit events with virtual time control`() = runTest {
        // Given: A Flow that emits 3 events with 100ms delays
        val flow = simulateEvents(count = 3, delayMs = 100)

        // When/Then: Test with Turbine - no real delays!
        flow.test {
            assertEquals("Event-1", awaitItem())
            assertEquals("Event-2", awaitItem())
            assertEquals("Event-3", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `should emit progress updates from 0 to 100`() = runTest {
        // Given: A Flow that emits progress in 4 steps
        val flow = simulateProgress(steps = 4, stepDelayMs = 50)

        // When/Then: Verify progress sequence
        flow.test {
            assertEquals(0, awaitItem())   // Initial
            assertEquals(25, awaitItem())  // Step 1
            assertEquals(50, awaitItem())  // Step 2
            assertEquals(75, awaitItem())  // Step 3
            assertEquals(100, awaitItem()) // Step 4
            awaitComplete()
        }
    }

    @Test
    fun `should handle empty flow`() = runTest {
        // Given: An empty Flow
        val flow = simulateEvents(count = 0, delayMs = 100)

        // When/Then: Complete immediately without emissions
        flow.test {
            awaitComplete()
        }
    }

    @Test
    fun `should handle single emission`() = runTest {
        // Given: A Flow that emits once
        val flow = simulateEvents(count = 1, delayMs = 100)

        // When/Then: Single emission
        flow.test {
            assertEquals("Event-1", awaitItem())
            awaitComplete()
        }
    }

    // Helper function to create a simple event stream
    private fun simulateEvents(count: Int, delayMs: Long): Flow<String> = flow {
        repeat(count) { index ->
            delay(delayMs)
            emit("Event-${index + 1}")
        }
    }

    // Helper function to create a progress stream
    private fun simulateProgress(steps: Int, stepDelayMs: Long): Flow<Int> = flow {
        require(steps in MIN_STEPS..MAX_STEPS) { "Steps must be between $MIN_STEPS and $MAX_STEPS" }
        
        emit(0) // Initial progress
        
        val increment = MAX_PROGRESS / steps
        var currentProgress = 0
        
        repeat(steps) {
            delay(stepDelayMs)
            currentProgress = minOf(currentProgress + increment, MAX_PROGRESS)
            emit(currentProgress)
        }
    }
}
