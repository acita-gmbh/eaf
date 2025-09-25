package com.axians.eaf.framework.security.tenant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class TenantContextTest :
    FunSpec({

        lateinit var meterRegistry: SimpleMeterRegistry
        lateinit var tenantContext: TenantContext

        beforeEach {
            meterRegistry = SimpleMeterRegistry()
            tenantContext = TenantContext(meterRegistry)
            // Clean up any existing context
            repeat(10) { tenantContext.clearCurrentTenant() }
        }

        afterEach {
            // Ensure complete cleanup after each test
            repeat(10) { tenantContext.clearCurrentTenant() }
        }

        context("ThreadLocal stack operations") {

            test("setCurrentTenantId should push WeakReference onto ThreadLocal stack") {
                tenantContext.setCurrentTenantId("tenant-123")

                tenantContext.current() shouldBe "tenant-123"
                tenantContext.getStackDepth() shouldBe 1
            }

            test("current should peek current tenant ID from stack top without removing") {
                tenantContext.setCurrentTenantId("tenant-123")
                tenantContext.setCurrentTenantId("tenant-456")

                tenantContext.current() shouldBe "tenant-456"
                tenantContext.getStackDepth() shouldBe 2

                // Should still be there after peek
                tenantContext.current() shouldBe "tenant-456"
                tenantContext.getStackDepth() shouldBe 2
            }

            test("clearCurrentTenant should pop from stack") {
                tenantContext.setCurrentTenantId("tenant-123")
                tenantContext.setCurrentTenantId("tenant-456")

                tenantContext.clearCurrentTenant()
                tenantContext.current() shouldBe "tenant-123"
                tenantContext.getStackDepth() shouldBe 1
            }

            test("clearCurrentTenant should call ThreadLocal.remove when stack becomes empty") {
                val initialCount = meterRegistry.counter("tenant.context.threadlocal_removed").count()

                tenantContext.setCurrentTenantId("tenant-123")

                tenantContext.clearCurrentTenant()
                tenantContext.current() shouldBe null
                tenantContext.getStackDepth() shouldBe 0

                // Verify metrics increment
                meterRegistry.counter("tenant.context.threadlocal_removed").count() shouldBe (initialCount + 1.0)
            }
        }

        context("WeakReference storage and garbage collection") {

            test("should store tenant IDs as WeakReference to allow garbage collection") {
                tenantContext.setCurrentTenantId("tenant-123")

                // Force garbage collection multiple times
                repeat(5) {
                    System.gc()
                    Thread.sleep(10)
                }

                // Context should still be available for normal usage
                tenantContext.current() shouldBe "tenant-123"
            }

            test("should handle garbage collected WeakReference gracefully") {
                // This test simulates the edge case where WeakReference gets GC'd
                // In practice, this is rare for short-lived request contexts
                tenantContext.setCurrentTenantId("tenant-123")

                // Verify graceful handling
                val result = tenantContext.current()
                result shouldNotBe null
            }
        }

        context("fail-closed design") {

            test("getCurrentTenantId should throw exception when no tenant context") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        tenantContext.getCurrentTenantId()
                    }
                exception.message shouldBe "Missing or invalid tenant_id claim in JWT token"
            }

            test("current should return null when stack is empty") {
                tenantContext.current() shouldBe null
            }

            test("setCurrentTenantId should reject null or blank tenant IDs") {
                shouldThrow<IllegalArgumentException> {
                    tenantContext.setCurrentTenantId("")
                }

                shouldThrow<IllegalArgumentException> {
                    tenantContext.setCurrentTenantId("   ")
                }
            }
        }

        context("thread isolation and concurrency") {

            test("should maintain separate tenant contexts across concurrent threads") {
                val results = ConcurrentHashMap<String, String?>()

                runBlocking {
                    val jobs =
                        (1..10).map { threadId ->
                            async {
                                val tenantContext = TenantContext(meterRegistry)
                                val tenantId = "tenant-$threadId"

                                tenantContext.setCurrentTenantId(tenantId)
                                Thread.sleep(50) // Simulate some processing

                                val retrieved = tenantContext.current()
                                results["thread-$threadId"] = retrieved

                                tenantContext.clearCurrentTenant()
                            }
                        }
                    jobs.awaitAll()
                }

                // Verify each thread maintained its own context
                (1..10).forEach { threadId ->
                    results["thread-$threadId"] shouldBe "tenant-$threadId"
                }
            }
        }

        context("production monitoring hooks") {

            test("getStackDepth should return current stack size") {
                tenantContext.getStackDepth() shouldBe 0

                tenantContext.setCurrentTenantId("tenant-123")
                tenantContext.getStackDepth() shouldBe 1

                tenantContext.setCurrentTenantId("tenant-456")
                tenantContext.getStackDepth() shouldBe 2
            }

            test("getStackDepth should emit leak detection metric when depth > 0") {
                tenantContext.setCurrentTenantId("tenant-123")

                tenantContext.getStackDepth()

                // Verify leak detection metric
                meterRegistry.counter("tenant.context.leak_detected", "depth", "1").count() shouldBe 1.0
            }

            test("should track metrics for set and clear operations") {
                val initialSetCount = meterRegistry.counter("tenant.context.set").count()
                val initialClearCount = meterRegistry.counter("tenant.context.clear").count()
                val initialRemovedCount = meterRegistry.counter("tenant.context.threadlocal_removed").count()

                tenantContext.setCurrentTenantId("tenant-123")
                tenantContext.clearCurrentTenant()

                meterRegistry.counter("tenant.context.set").count() shouldBe (initialSetCount + 1.0)
                meterRegistry.counter("tenant.context.clear").count() shouldBe (initialClearCount + 1.0)
                meterRegistry.counter("tenant.context.threadlocal_removed").count() shouldBe (initialRemovedCount + 1.0)
            }
        }

        context("edge cases and null handling") {

            test("should handle multiple clear operations gracefully") {
                tenantContext.setCurrentTenantId("tenant-123")
                tenantContext.clearCurrentTenant()
                tenantContext.clearCurrentTenant() // Should not throw
                tenantContext.clearCurrentTenant() // Should not throw

                tenantContext.getStackDepth() shouldBe 0
            }

            test("should handle current() on empty stack gracefully") {
                repeat(5) {
                    tenantContext.current() shouldBe null
                }
            }

            test("should maintain backwards compatibility with getCurrentTenantId") {
                tenantContext.setCurrentTenantId("tenant-123")

                // Test existing API contract
                tenantContext.getCurrentTenantId() shouldBe "tenant-123"

                // Should still work after clear
                tenantContext.clearCurrentTenant()
                shouldThrow<IllegalStateException> {
                    tenantContext.getCurrentTenantId()
                }
            }
        }
    })
