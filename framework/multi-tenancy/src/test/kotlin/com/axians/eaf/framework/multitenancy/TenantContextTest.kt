package com.axians.eaf.framework.multitenancy

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
 * @since 1.0.0
 */
class TenantContextTest :
    FunSpec({

        beforeTest {
            // AC8: Ensure context is cleared before each test
            TenantContext.clearCurrentTenant()
        }

        afterTest {
            // AC8: Cleanup after each test
            TenantContext.clearCurrentTenant()
        }

        context("AC3 & AC6: ThreadLocal context management") {

            test("should set and retrieve tenant context") {
                // When
                TenantContext.setCurrentTenantId("tenant-123")

                // Then
                TenantContext.current() shouldBe "tenant-123"
                TenantContext.getCurrentTenantId() shouldBe "tenant-123"
            }

            test("should clear tenant context") {
                // Given
                TenantContext.setCurrentTenantId("tenant-123")

                // When
                TenantContext.clearCurrentTenant()

                // Then
                TenantContext.current() shouldBe null
            }

            test("should support stack-based nested contexts") {
                // Given
                TenantContext.setCurrentTenantId("tenant-outer")

                // When - push nested context
                TenantContext.setCurrentTenantId("tenant-inner")

                // Then - inner context is current
                TenantContext.getCurrentTenantId() shouldBe "tenant-inner"

                // When - pop inner context
                TenantContext.clearCurrentTenant()

                // Then - outer context restored
                TenantContext.getCurrentTenantId() shouldBe "tenant-outer"

                // Cleanup
                TenantContext.clearCurrentTenant()
            }
        }

        context("AC3: Fail-closed behavior") {

            test("getCurrentTenantId() should throw when context is missing") {
                // Given - no context set

                // When/Then
                val exception =
                    shouldThrow<IllegalStateException> {
                        TenantContext.getCurrentTenantId()
                    }
                exception.message shouldContain "Tenant context not set"
            }

            test("current() should return null when context is missing") {
                // Given - no context set

                // When
                val result = TenantContext.current()

                // Then
                result shouldBe null
            }
        }

        context("AC7: Thread isolation") {

            test("tenant context should not leak between threads") {
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
                results["thread-1"] shouldBe "tenant-thread-1"
                results["thread-2"] shouldBe "tenant-thread-2"
                results["thread-3"] shouldBe "tenant-thread-3"
            }
        }

        context("AC8: Context cleanup") {

            test("should cleanup context after request simulation") {
                // Given - simulating request lifecycle
                TenantContext.setCurrentTenantId("tenant-request")

                // When - request completes, cleanup is called
                TenantContext.clearCurrentTenant()

                // Then - context is cleared
                TenantContext.current() shouldBe null
            }

            test("should handle multiple cleanup calls safely") {
                // Given
                TenantContext.setCurrentTenantId("tenant-123")
                TenantContext.clearCurrentTenant()

                // When - cleanup called multiple times
                TenantContext.clearCurrentTenant()
                TenantContext.clearCurrentTenant()

                // Then - no exception, context remains null
                TenantContext.current() shouldBe null
            }
        }
    })
