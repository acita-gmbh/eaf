package com.axians.eaf.framework.multitenancy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors

/**
 * Unit tests for TenantContext ThreadLocal management.
 *
 * Validates tenant context isolation, stack-based behavior, and thread safety for multi-tenant applications.
 * The TenantContext uses ThreadLocal storage to ensure tenant data never leaks between concurrent requests.
 *
 * **Acceptance Criteria Covered:**
 * - AC3: Fail-closed behavior (getCurrentTenantId() throws when missing, current() returns null)
 * - AC6: Stack-based nested contexts (push/pop behavior)
 * - AC7: Thread isolation (ThreadLocal prevents cross-thread contamination)
 * - AC8: Context cleanup (clearCurrentTenant() removes all context)
 *
 * **Testing Strategy:**
 * - Thread isolation tests use CyclicBarrier for deterministic concurrent execution
 * - Stack tests verify last-in-first-out behavior for nested contexts
 * - Fail-closed tests verify IllegalStateException on missing context
 *
 * @see TenantContext Primary class under test
 * @since JUnit 6 Migration (2025-11-20)
 * @author EAF Testing Framework
 */
class TenantContextTest {
    @BeforeEach
    fun beforeEach() {
        // AC8: Ensure context is cleared before each test
        TenantContext.clearCurrentTenant()
    }

    @AfterEach
    fun afterEach() {
        // AC8: Cleanup after each test
        TenantContext.clearCurrentTenant()
    }

    // AC3 & AC6: ThreadLocal context management

    @Test
    fun `should set and retrieve tenant context`() {
        // When
        TenantContext.setCurrentTenantId("tenant-123")

        // Then
        assertThat(TenantContext.current()).isEqualTo("tenant-123")
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-123")
    }

    @Test
    fun `should clear tenant context`() {
        // Given
        TenantContext.setCurrentTenantId("tenant-123")

        // When
        TenantContext.clearCurrentTenant()

        // Then
        assertThat(TenantContext.current()).isNull()
    }

    @Test
    fun `should support stack-based nested contexts`() {
        // Given
        TenantContext.setCurrentTenantId("tenant-outer")

        // When - push nested context
        TenantContext.setCurrentTenantId("tenant-inner")

        // Then - inner context is current
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-inner")

        // When - pop inner context
        TenantContext.clearCurrentTenant()

        // Then - outer context restored
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("tenant-outer")

        // Cleanup
        TenantContext.clearCurrentTenant()
    }

    // AC3: Fail-closed behavior

    @Test
    fun `getCurrentTenantId() should throw when context is missing`() {
        // Given - no context set

        // When/Then
        val exception =
            assertThrows<IllegalStateException> {
                TenantContext.getCurrentTenantId()
            }
        assertThat(exception.message).contains("Tenant context not set")
    }

    @Test
    fun `current() should return null when context is missing`() {
        // Given - no context set

        // When
        val result = TenantContext.current()

        // Then
        assertThat(result).isNull()
    }

    // AC7: Thread isolation

    @Test
    fun `tenant context should not leak between threads`() {
        // Given
        val executor = Executors.newFixedThreadPool(3)
        val barrier = CyclicBarrier(3) // Synchronize all 3 threads at once
        val latch = CountDownLatch(3)
        val results = mutableMapOf<String, String?>()

        // When - set different contexts in different threads
        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-1")
                barrier.await() // All threads reach here before proceeding (deterministic)
                results["thread-1"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-2")
                barrier.await() // Synchronization point
                results["thread-2"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-3")
                barrier.await() // Synchronization point
                results["thread-3"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        // Then - each thread sees its own context (ThreadLocal isolation verified)
        assertThat(results["thread-1"]).isEqualTo("tenant-thread-1")
        assertThat(results["thread-2"]).isEqualTo("tenant-thread-2")
        assertThat(results["thread-3"]).isEqualTo("tenant-thread-3")
    }

    // AC8: Context cleanup

    @Test
    fun `should cleanup context after request simulation`() {
        // Given - simulating request lifecycle
        TenantContext.setCurrentTenantId("tenant-request")

        // When - request completes, cleanup is called
        TenantContext.clearCurrentTenant()

        // Then - context is cleared
        assertThat(TenantContext.current()).isNull()
    }

    @Test
    fun `should handle multiple cleanup calls safely`() {
        // Given
        TenantContext.setCurrentTenantId("tenant-123")
        TenantContext.clearCurrentTenant()

        // When - cleanup called multiple times
        TenantContext.clearCurrentTenant()
        TenantContext.clearCurrentTenant()

        // Then - no exception, context remains null
        assertThat(TenantContext.current()).isNull()
    }
}
