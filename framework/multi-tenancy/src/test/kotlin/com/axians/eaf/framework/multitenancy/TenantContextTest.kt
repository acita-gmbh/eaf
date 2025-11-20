package com.axians.eaf.framework.multitenancy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * Unit tests for TenantContext ThreadLocal management.
 *
 * Epic 4, Story 4.1: AC3, AC6, AC7, AC8
 * Tests validate:
 * - AC3: ThreadLocal storage with stack-based context
 * - AC6: set context → retrieve → clear cycle
 * - AC7: Thread isolation (contexts don't leak between threads)
 * - AC8: Context cleanup after request completion
 *
 * Migrated from Kotest to JUnit 6 on 2025-11-20
 *
 * @since 1.0.0
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
        val exception = assertThrows<IllegalStateException> {
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
        val latch = CountDownLatch(3)
        val results = mutableMapOf<String, String?>()

        // When - set different contexts in different threads
        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-1")
                Thread.sleep(50) // Allow other threads to potentially interfere
                results["thread-1"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-2")
                Thread.sleep(50)
                results["thread-2"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        executor.execute {
            try {
                TenantContext.setCurrentTenantId("tenant-thread-3")
                Thread.sleep(50)
                results["thread-3"] = TenantContext.current()
            } finally {
                TenantContext.clearCurrentTenant()
                latch.countDown()
            }
        }

        latch.await()
        executor.shutdown()

        // Then - each thread sees its own context
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
