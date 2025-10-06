@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.persistence

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.products.widgetdemo.WidgetDemoApplication
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [
        com.axians.eaf.products.widgetdemo.WidgetDemoApplication::class,
        com.axians.eaf.products.widgetdemo.test.WidgetDemoTestApplication::class,
    ],
)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "otel.java.global-autoconfigure.enabled=false",
        "otel.sdk.disabled=true",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "otel.instrumentation.spring-boot-starter.enabled=false",
        "otel.instrumentation.common.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "axon.eventhandling.processors.widget-projection.mode=tracking",
        "axon.eventhandling.processors.widget-projection.source=eventStore",
        "axon.serializer.events=jackson",
        "axon.serializer.messages=jackson",
    ],
)
class WidgetEventStoreIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    init {
        extension(SpringExtension())

        context("Axon Event Store Persistence Validation") {
            test("8.4-INT-015: should persist WidgetCreatedEvent in domain_event_entry table") {
                val widgetId = UUID.randomUUID().toString()
                val command =
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "persistence-test",
                        name = "Event Store Test Widget",
                        description = "Testing event persistence",
                        value = BigDecimal("300.00"),
                        category = "PERSISTENCE_TEST",
                        metadata = mapOf("test" to "event-store", "validation" to true),
                    )

                // Dispatch command and wait for completion
                val result = commandGateway.sendAndWait<String>(command, 10, TimeUnit.SECONDS)
                result shouldBe widgetId

                // Direct database verification
                DriverManager
                    .getConnection(
                        TestContainers.postgres.jdbcUrl,
                        TestContainers.postgres.username,
                        TestContainers.postgres.password,
                    ).use { connection ->
                        val query =
                            """
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
                                sequenceNumber shouldBe 0L // First event
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
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.postgres.start()
            registry.add("spring.datasource.url", TestContainers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestContainers.postgres::getUsername)
            registry.add("spring.datasource.password", TestContainers.postgres::getPassword)
        }
    }
}
