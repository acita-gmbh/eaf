package com.axians.eaf.products.widgetdemo.persistence

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class WidgetEventStoreIntegrationTest : FunSpec() {

    @Autowired
    private lateinit var commandGateway: CommandGateway

    init {
        extension(SpringExtension())

        context("Axon Event Store Persistence Validation") {
            test("should persist WidgetCreatedEvent in domain_event_entry table") {
                val widgetId = UUID.randomUUID().toString()
                val command = CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "persistence-test",
                    name = "Event Store Test Widget",
                    description = "Testing event persistence",
                    value = BigDecimal("75.00"),
                    category = "PERSISTENCE_TEST",
                    metadata = emptyMap()
                )

                val result = commandGateway.sendAndWait<String>(command, 10, TimeUnit.SECONDS)
                result shouldBe widgetId

                // Verify event was persisted in database
                val connection = DriverManager.getConnection(
                    TestContainers.postgres.jdbcUrl,
                    TestContainers.postgres.username,
                    TestContainers.postgres.password
                )

                val statement = connection.createStatement()
                val resultSet = statement.executeQuery(
                    "SELECT COUNT(*) FROM domain_event_entry WHERE aggregate_identifier = '$widgetId'"
                )

                resultSet.next()
                val eventCount = resultSet.getInt(1)
                eventCount shouldBe 1

                connection.close()
            }

            test("should handle event serialization correctly") {
                val widgetId = UUID.randomUUID().toString()
                val command = CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "serialization-test",
                    name = "Serialization Test Widget",
                    description = "Testing JSON serialization",
                    value = BigDecimal("123.45"),
                    category = "SERIALIZATION",
                    metadata = mapOf("test" to "serialization", "complex" to mapOf("nested" to "value"))
                )

                commandGateway.sendAndWait<String>(command, 10, TimeUnit.SECONDS)

                // Verify event payload can be retrieved and is valid JSON
                val connection = DriverManager.getConnection(
                    TestContainers.postgres.jdbcUrl,
                    TestContainers.postgres.username,
                    TestContainers.postgres.password
                )

                val statement = connection.createStatement()
                val resultSet = statement.executeQuery(
                    "SELECT payload FROM domain_event_entry WHERE aggregate_identifier = '$widgetId'"
                )

                resultSet.next()
                val payload = resultSet.getString("payload")
                payload shouldNotBe null
                payload.contains("Serialization Test Widget") shouldBe true

                connection.close()
            }
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()

            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestContainers.postgres.username }
            registry.add("spring.datasource.password") { TestContainers.postgres.password }
        }
    }
}