package com.axians.eaf.framework.core.resilience.dlq

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant

/**
 * Unit tests for DeadLetterQueueService.
 *
 * Tests:
 * - Storing failed operations
 * - Querying DLQ entries with filters
 * - Replaying entries
 * - Discarding entries
 * - Deleting entries
 * - Statistics
 *
 * OWASP A10:2025 - Mishandling of Exceptional Conditions
 *
 * @since 1.0.0
 */
class DeadLetterQueueServiceTest :
    FunSpec({

        lateinit var service: DeadLetterQueueService
        lateinit var meterRegistry: SimpleMeterRegistry

        beforeEach {
            meterRegistry = SimpleMeterRegistry()
            service =
                DeadLetterQueueService(
                    objectMapper = ObjectMapper(),
                    meterRegistry = meterRegistry,
                )
        }

        context("Storing Failed Operations") {
            test("should store failed command") {
                // Given: A failed command
                val command = TestCommand("test-123")
                val exception = RuntimeException("Command failed")

                // When: Store in DLQ
                val entry =
                    service.storeFailed(
                        operationType = OperationType.COMMAND,
                        payload = command,
                        exception = exception,
                        tenantId = "tenant-a",
                        traceId = "trace-123",
                        retryCount = 3,
                    )

                // Then: Entry is created correctly
                entry.operationType shouldBe OperationType.COMMAND
                entry.payloadType shouldBe "TestCommand"
                entry.payload shouldNotBe null
                entry.exceptionType shouldBe "RuntimeException"
                entry.exceptionMessage shouldBe "Command failed"
                entry.tenantId shouldBe "tenant-a"
                entry.traceId shouldBe "trace-123"
                entry.retryCount shouldBe 3
                entry.status shouldBe DLQStatus.PENDING

                // And: Metrics are recorded
                val counter =
                    meterRegistry
                        .find("eaf.dlq.entries")
                        .tag("operation_type", "COMMAND")
                        .tag("status", "created")
                        .counter()
                counter shouldNotBe null
                counter?.count() shouldBe 1.0
            }

            test("should sanitize exception messages") {
                // Given: Exception with sensitive data
                val exception = RuntimeException("Failed with password=secret123 and token=abc123")
                val command = TestCommand("test")

                // When: Store in DLQ
                val entry =
                    service.storeFailed(
                        operationType = OperationType.COMMAND,
                        payload = command,
                        exception = exception,
                    )

                // Then: Sensitive data is sanitized
                entry.exceptionMessage shouldBe "Failed with password=*** and token=***"
            }

            test("should store event failures") {
                // Given: A failed event
                val event = TestEvent("event-123")
                val exception = IllegalStateException("Event processing failed")

                // When: Store in DLQ
                val entry =
                    service.storeFailed(
                        operationType = OperationType.EVENT,
                        payload = event,
                        exception = exception,
                    )

                // Then: Entry is created for event
                entry.operationType shouldBe OperationType.EVENT
                entry.payloadType shouldBe "TestEvent"
                entry.exceptionType shouldBe "IllegalStateException"
            }
        }

        context("Querying DLQ Entries") {
            test("should retrieve all entries") {
                // Given: Multiple DLQ entries
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("1"),
                    RuntimeException("Error 1"),
                )
                service.storeFailed(
                    OperationType.EVENT,
                    TestEvent("2"),
                    RuntimeException("Error 2"),
                )

                // When: Find all
                val entries = service.findAll()

                // Then: All entries are returned
                entries shouldHaveSize 2
            }

            test("should filter by status") {
                // Given: Entries with different statuses
                val entry1 =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("1"),
                        RuntimeException("Error"),
                    )
                val entry2 =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("2"),
                        RuntimeException("Error"),
                    )
                service.markReplayed(entry1.id)

                // When: Filter by PENDING status
                val pending = service.findAll(status = DLQStatus.PENDING)

                // Then: Only pending entries are returned
                pending shouldHaveSize 1
                pending[0].id shouldBe entry2.id
            }

            test("should filter by operation type") {
                // Given: Different operation types
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("1"),
                    RuntimeException("Error"),
                )
                service.storeFailed(
                    OperationType.EVENT,
                    TestEvent("2"),
                    RuntimeException("Error"),
                )

                // When: Filter by COMMAND
                val commands = service.findAll(operationType = OperationType.COMMAND)

                // Then: Only commands are returned
                commands shouldHaveSize 1
                commands[0].operationType shouldBe OperationType.COMMAND
            }

            test("should filter by tenant ID") {
                // Given: Entries from different tenants
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("1"),
                    RuntimeException("Error"),
                    tenantId = "tenant-a",
                )
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("2"),
                    RuntimeException("Error"),
                    tenantId = "tenant-b",
                )

                // When: Filter by tenant-a
                val tenantA = service.findAll(tenantId = "tenant-a")

                // Then: Only tenant-a entries are returned
                tenantA shouldHaveSize 1
                tenantA[0].tenantId shouldBe "tenant-a"
            }

            test("should filter by timestamp") {
                // Given: Entries at different times
                val before = Instant.now()
                Thread.sleep(10)
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("1"),
                    RuntimeException("Error"),
                )

                // When: Filter by timestamp
                val recent = service.findAll(since = before)

                // Then: Only recent entries are returned
                recent shouldHaveSize 1
            }
        }

        context("Replaying Entries") {
            test("should mark entry as replayed") {
                // Given: A DLQ entry
                val entry =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("test"),
                        RuntimeException("Error"),
                    )

                // When: Mark as replayed
                val updated = service.markReplayed(entry.id)

                // Then: Status is updated
                updated shouldNotBe null
                updated!!.status shouldBe DLQStatus.REPLAYED
                updated.replayCount shouldBe 1
                updated.lastAttempt shouldNotBe null

                // And: Metrics are recorded
                val counter =
                    meterRegistry
                        .find("eaf.dlq.replays")
                        .tag("status", "success")
                        .counter()
                counter shouldNotBe null
            }

            test("should mark entry as replay failed") {
                // Given: A DLQ entry
                val entry =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("test"),
                        RuntimeException("Error"),
                    )

                // When: Mark as replay failed
                val updated = service.markReplayFailed(entry.id)

                // Then: Status is updated
                updated shouldNotBe null
                updated!!.status shouldBe DLQStatus.REPLAY_FAILED
                updated.replayCount shouldBe 1
            }

            test("should increment replay count on multiple attempts") {
                // Given: A DLQ entry
                val entry =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("test"),
                        RuntimeException("Error"),
                    )

                // When: Multiple replay attempts
                service.markReplayFailed(entry.id)
                service.markReplayFailed(entry.id)
                val updated = service.markReplayed(entry.id)

                // Then: Replay count is incremented
                updated!!.replayCount shouldBe 3
            }
        }

        context("Discarding Entries") {
            test("should discard entry") {
                // Given: A DLQ entry
                val entry =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("test"),
                        RuntimeException("Error"),
                    )

                // When: Discard entry
                val updated = service.discard(entry.id)

                // Then: Status is DISCARDED
                updated shouldNotBe null
                updated!!.status shouldBe DLQStatus.DISCARDED
            }
        }

        context("Deleting Entries") {
            test("should delete entry") {
                // Given: A DLQ entry
                val entry =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("test"),
                        RuntimeException("Error"),
                    )

                // When: Delete entry
                val deleted = service.delete(entry.id)

                // Then: Entry is deleted
                deleted shouldBe true
                service.findById(entry.id) shouldBe null
            }

            test("should return false for non-existent entry") {
                // Given: Non-existent ID
                val randomId = java.util.UUID.randomUUID()

                // When: Try to delete
                val deleted = service.delete(randomId)

                // Then: Returns false
                deleted shouldBe false
            }
        }

        context("Statistics") {
            test("should return statistics by status") {
                // Given: Multiple entries with different statuses
                val entry1 =
                    service.storeFailed(
                        OperationType.COMMAND,
                        TestCommand("1"),
                        RuntimeException("Error"),
                    )
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("2"),
                    RuntimeException("Error"),
                )
                service.markReplayed(entry1.id)

                // When: Get statistics
                val stats = service.getStatistics()

                // Then: Stats are correct
                stats[DLQStatus.PENDING] shouldBe 1L
                stats[DLQStatus.REPLAYED] shouldBe 1L
            }

            test("should return statistics by tenant") {
                // Given: Entries from different tenants
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("1"),
                    RuntimeException("Error"),
                    tenantId = "tenant-a",
                )
                service.storeFailed(
                    OperationType.COMMAND,
                    TestCommand("2"),
                    RuntimeException("Error"),
                    tenantId = "tenant-b",
                )

                // When: Get statistics by tenant
                val stats = service.getStatisticsByTenant()

                // Then: Stats are grouped by tenant
                stats.size shouldBe 2
                stats["tenant-a"]!![DLQStatus.PENDING] shouldBe 1L
                stats["tenant-b"]!![DLQStatus.PENDING] shouldBe 1L
            }
        }
    })

// Test classes
data class TestCommand(
    val id: String,
)

data class TestEvent(
    val id: String,
)
