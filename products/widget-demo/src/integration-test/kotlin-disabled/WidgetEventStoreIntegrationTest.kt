package com.axians.eaf.products.widgetdemo.integration.persistence

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.perSpec
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class WidgetEventStoreIntegrationTest(
    private val commandGateway: CommandGateway,
) : FunSpec({

    extension(SpringExtension)

    // Use shared TestContainers with Kotest lifecycle management
    listener(TestContainers.postgres.perSpec())

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Ensure containers are started
            TestContainers.startAll()

            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
        }
    }

    context("Axon Event Store Persistence Validation") {
        test("should persist WidgetCreatedEvent in domain_event_entry table") {
            val widgetId = UUID.randomUUID().toString()
            val command = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "persistence-test",
                name = "Event Store Test Widget",
                description = "Testing event persistence",
                value = BigDecimal("300.00"),
                category = "PERSISTENCE_TEST",
                metadata = mapOf("test" to "event-store", "validation" to true)
            )

            // Dispatch command and wait for completion
            val result = commandGateway.sendAndWait<String>(command, 10, TimeUnit.SECONDS)
            result shouldBe widgetId

            // Direct database verification
            DriverManager.getConnection(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            ).use { connection ->
                val query = """
                    SELECT aggregate_identifier, sequence_number, type, payload_type, payload
                    FROM domain_event_entry
                    WHERE aggregate_identifier = ?
                    ORDER BY sequence_number
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, widgetId)
                    val resultSet = stmt.executeQuery()

                    var eventFound = false
                    while (resultSet.next()) {
                        val aggregateId = resultSet.getString("aggregate_identifier")
                        val sequenceNumber = resultSet.getLong("sequence_number")
                        val eventType = resultSet.getString("type")
                        val payloadType = resultSet.getString("payload_type")
                        val payload = resultSet.getString("payload")

                        aggregateId shouldBe widgetId
                        sequenceNumber shouldBe 0L  // First event
                        eventType shouldBe "WidgetCreatedEvent"
                        payloadType shouldBe "com.axians.eaf.api.widget.events.WidgetCreatedEvent"
                        payload shouldNotBe null
                        payload shouldContain "Event Store Test Widget"
                        payload shouldContain command.tenantId
                        eventFound = true
                    }
                    eventFound shouldBe true
                }
            }
        }

        test("should maintain correct event sequence numbers for multiple events") {
            val widgetId = UUID.randomUUID().toString()

            // Create widget
            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "sequence-test",
                name = "Sequence Test Widget",
                description = "Testing event sequences",
                value = BigDecimal("100.00"),
                category = "SEQUENCE_TEST",
                metadata = mapOf("sequence" to "test")
            )

            commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)

            // Verify sequence number in database
            DriverManager.getConnection(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            ).use { connection ->
                val query = """
                    SELECT COUNT(*) as event_count, MAX(sequence_number) as max_sequence
                    FROM domain_event_entry
                    WHERE aggregate_identifier = ?
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, widgetId)
                    val resultSet = stmt.executeQuery()

                    if (resultSet.next()) {
                        val eventCount = resultSet.getInt("event_count")
                        val maxSequence = resultSet.getLong("max_sequence")

                        eventCount shouldBe 1
                        maxSequence shouldBe 0L  // First event has sequence 0
                    }
                }
            }
        }

        test("should preserve tenant context in persisted events") {
            val widgetId = UUID.randomUUID().toString()
            val tenantId = "tenant-preservation-test"

            val command = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = tenantId,
                name = "Tenant Context Widget",
                description = "Testing tenant preservation",
                value = BigDecimal("250.00"),
                category = "TENANT_TEST",
                metadata = mapOf("tenant" to tenantId)
            )

            commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)

            // Verify tenant context is preserved in event payload
            DriverManager.getConnection(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            ).use { connection ->
                val query = """
                    SELECT payload
                    FROM domain_event_entry
                    WHERE aggregate_identifier = ?
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, widgetId)
                    val resultSet = stmt.executeQuery()

                    if (resultSet.next()) {
                        val payload = resultSet.getString("payload")
                        payload shouldContain tenantId
                        payload shouldContain "Tenant Context Widget"
                    }
                }
            }
        }

        test("should handle concurrent widget creation correctly") {
            val widget1Id = UUID.randomUUID().toString()
            val widget2Id = UUID.randomUUID().toString()

            val command1 = CreateWidgetCommand(
                widgetId = widget1Id,
                tenantId = "concurrent-test-1",
                name = "Concurrent Widget 1",
                description = "First concurrent widget",
                value = BigDecimal("100.00"),
                category = "CONCURRENT_TEST",
                metadata = mapOf("order" to 1)
            )

            val command2 = CreateWidgetCommand(
                widgetId = widget2Id,
                tenantId = "concurrent-test-2",
                name = "Concurrent Widget 2",
                description = "Second concurrent widget",
                value = BigDecimal("200.00"),
                category = "CONCURRENT_TEST",
                metadata = mapOf("order" to 2)
            )

            // Send commands concurrently
            val future1 = commandGateway.send<String>(command1)
            val future2 = commandGateway.send<String>(command2)

            val result1 = future1.get(10, TimeUnit.SECONDS)
            val result2 = future2.get(10, TimeUnit.SECONDS)

            result1 shouldBe widget1Id
            result2 shouldBe widget2Id

            // Verify both events persisted correctly
            DriverManager.getConnection(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            ).use { connection ->
                val query = """
                    SELECT aggregate_identifier, sequence_number
                    FROM domain_event_entry
                    WHERE aggregate_identifier IN (?, ?)
                    ORDER BY aggregate_identifier, sequence_number
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    stmt.setString(1, widget1Id)
                    stmt.setString(2, widget2Id)
                    val resultSet = stmt.executeQuery()

                    var eventsFound = 0
                    while (resultSet.next()) {
                        val aggregateId = resultSet.getString("aggregate_identifier")
                        val sequenceNumber = resultSet.getLong("sequence_number")

                        sequenceNumber shouldBe 0L  // First event for each aggregate
                        (aggregateId == widget1Id || aggregateId == widget2Id) shouldBe true
                        eventsFound++
                    }
                    eventsFound shouldBe 2  // Both events should be persisted
                }
            }
        }

        test("should validate event store table structure") {
            // Verify Axon creates required tables automatically
            DriverManager.getConnection(
                TestContainers.postgres.jdbcUrl,
                TestContainers.postgres.username,
                TestContainers.postgres.password
            ).use { connection ->
                val query = """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                    AND table_name LIKE '%event%'
                """.trimIndent()

                connection.prepareStatement(query).use { stmt ->
                    val resultSet = stmt.executeQuery()
                    var domainEventTableFound = false

                    while (resultSet.next()) {
                        val tableName = resultSet.getString("table_name")
                        if (tableName == "domain_event_entry") {
                            domainEventTableFound = true
                        }
                    }
                    domainEventTableFound shouldBe true
                }
            }
        }
    }
})