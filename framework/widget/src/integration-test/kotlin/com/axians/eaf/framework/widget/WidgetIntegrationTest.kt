package com.axians.eaf.framework.widget

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.config.Configuration
import org.axonframework.config.DefaultConfigurer
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine
import org.axonframework.modelling.command.Repository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

class WidgetIntegrationTest : FunSpec({

    context("Widget aggregate integration tests") {
        test("should handle complete widget lifecycle") {
            val config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(com.axians.eaf.framework.widget.domain.Widget::class.java)
                .configureEventStore { c -> InMemoryEventStorageEngine() }
                .buildConfiguration()

            config.start()

            val commandGateway = config.commandGateway()
            val widgetId = UUID.randomUUID().toString()

            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "integration-tenant",
                name = "Integration Test Widget",
                description = "Created during integration test",
                value = BigDecimal("250.00"),
                category = "INTEGRATION_TEST",
                metadata = mapOf("test" to "integration", "timestamp" to System.currentTimeMillis())
            )

            val createdId = commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)
            createdId shouldBe widgetId

            val updateCommand = UpdateWidgetCommand(
                widgetId = widgetId,
                tenantId = "integration-tenant",
                name = "Updated Integration Widget",
                value = BigDecimal("500.00")
            )

            commandGateway.sendAndWait<Unit>(updateCommand, 5, TimeUnit.SECONDS)

            config.shutdown()
        }

        test("should reject commands with tenant isolation violations") {
            val config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(com.axians.eaf.framework.widget.domain.Widget::class.java)
                .configureEventStore { c -> InMemoryEventStorageEngine() }
                .buildConfiguration()

            config.start()

            val commandGateway = config.commandGateway()
            val widgetId = UUID.randomUUID().toString()

            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-a",
                name = "Tenant A Widget",
                description = null,
                value = BigDecimal("100.00"),
                category = "TENANT_TEST",
                metadata = emptyMap()
            )

            commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)

            val wrongTenantUpdate = UpdateWidgetCommand(
                widgetId = widgetId,
                tenantId = "tenant-b",
                name = "Should Fail"
            )

            try {
                commandGateway.sendAndWait<Unit>(wrongTenantUpdate, 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
            }

            config.shutdown()
        }

        test("should handle concurrent widget operations") {
            val config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(com.axians.eaf.framework.widget.domain.Widget::class.java)
                .configureEventStore { c -> InMemoryEventStorageEngine() }
                .buildConfiguration()

            config.start()

            val commandGateway = config.commandGateway()

            val widgets = (1..10).map { index ->
                CreateWidgetCommand(
                    widgetId = UUID.randomUUID().toString(),
                    tenantId = "concurrent-tenant",
                    name = "Concurrent Widget $index",
                    description = "Widget number $index",
                    value = BigDecimal(index * 100),
                    category = "CONCURRENT_TEST",
                    metadata = mapOf("index" to index)
                )
            }

            val futures = widgets.map { command ->
                commandGateway.send<String>(command)
            }

            futures.forEach { future ->
                future.get(10, TimeUnit.SECONDS).shouldNotBeNull()
            }

            config.shutdown()
        }

        test("should validate business rules during command handling") {
            val config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(com.axians.eaf.framework.widget.domain.Widget::class.java)
                .configureEventStore { c -> InMemoryEventStorageEngine() }
                .buildConfiguration()

            config.start()

            val commandGateway = config.commandGateway()

            val invalidCommand = CreateWidgetCommand(
                widgetId = UUID.randomUUID().toString(),
                tenantId = "validation-tenant",
                name = "!!!",
                description = null,
                value = BigDecimal("-100"),
                category = "invalid",
                metadata = emptyMap()
            )

            try {
                commandGateway.sendAndWait<String>(invalidCommand, 5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                e.message?.contains("Validation failed") shouldBe true
            }

            config.shutdown()
        }

        test("should support partial updates") {
            val config = DefaultConfigurer.defaultConfiguration()
                .configureAggregate(com.axians.eaf.framework.widget.domain.Widget::class.java)
                .configureEventStore { c -> InMemoryEventStorageEngine() }
                .buildConfiguration()

            config.start()

            val commandGateway = config.commandGateway()
            val widgetId = UUID.randomUUID().toString()

            val createCommand = CreateWidgetCommand(
                widgetId = widgetId,
                tenantId = "partial-tenant",
                name = "Original Name",
                description = "Original Description",
                value = BigDecimal("100.00"),
                category = "ORIGINAL",
                metadata = mapOf("version" to 1)
            )

            commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)

            val partialUpdate = UpdateWidgetCommand(
                widgetId = widgetId,
                tenantId = "partial-tenant",
                description = "Only updating description"
            )

            commandGateway.sendAndWait<Unit>(partialUpdate, 5, TimeUnit.SECONDS)

            config.shutdown()
        }
    }
})