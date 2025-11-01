@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.persistence

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.products.widgetdemo.WidgetDemoApplication
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

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
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
        "otel.instrumentation.spring-boot-starter.enabled=false",
        "otel.instrumentation.common.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "axon.eventhandling.processors.widget-projection.mode=tracking",
        "axon.eventhandling.processors.widget-projection.source=eventStore",
        "axon.serializer.events=jackson",
        "axon.serializer.messages=jackson",
        "axon.axonserver.enabled=false",
        "hibernate.id.sequence.increment_size_mismatch_strategy=fix",
        "eaf.security.enable-oidc-decoder=false",
    ],
)
class WidgetEventStoreIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    init {
        extension(SpringExtension())

        context("Axon Event Store Persistence Validation") {
            beforeTest {
                val regClass =
                    jdbcTemplate.queryForObject(
                        "select to_regclass('public.domain_event_entry')",
                        String::class.java,
                    )
                regClass shouldNotBe null
            }

            test("8.4-INT-015: should persist WidgetCreatedEvent in domain_event_entry table") {
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "550e8400-e29b-41d4-a716-446655440010"
                val command =
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Event Store Test Widget",
                        description = "Testing event persistence",
                        value = BigDecimal("300.00"),
                        category = "PERSISTENCE_TEST",
                        metadata = mapOf("test" to "event-store", "validation" to true),
                    )

                val result = commandGateway.sendAndWait<String>(command, 10, TimeUnit.SECONDS)
                result shouldBe widgetId

                val events = eventStore.readEvents(widgetId).asStream().toList()
                events.shouldHaveSize(1)

                val createdEvent = events.first() as DomainEventMessage<*>
                createdEvent.aggregateIdentifier shouldBe widgetId
                createdEvent.sequenceNumber shouldBe 0L

                val payload = createdEvent.payload as com.axians.eaf.api.widget.events.WidgetCreatedEvent
                payload.widgetId shouldBe widgetId
                payload.tenantId shouldBe tenantId
                payload.name shouldBe "Event Store Test Widget"
                payload.description shouldBe "Testing event persistence"
                payload.value shouldBe BigDecimal("300.00")
                payload.category shouldBe "PERSISTENCE_TEST"
                payload.metadata shouldBe mapOf("test" to "event-store", "validation" to true)
                payload.createdAt shouldNotBe null
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
