@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.products.widgetdemo.test.WidgetDemoTestApplication
import com.axians.eaf.testing.containers.TestContainers
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [
        com.axians.eaf.products.widgetdemo.WidgetDemoApplication::class,
        com.axians.eaf.products.widgetdemo.test.WidgetDemoTestApplication::class,
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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
        "axon.axonserver.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:/db/axon/schema.sql",
        "hibernate.id.sequence.increment_size_mismatch_strategy=fix",
        "eaf.security.enable-oidc-decoder=false",
    ],
)
class WidgetIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    init {
        extension(SpringExtension())

        test("8.4-INT-001: should handle complete widget lifecycle") {
            val widgetId = UUID.randomUUID().toString()

            val createCommand =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "550e8400-e29b-41d4-a716-446655440000",
                    name = "Integration Test Widget",
                    description = "Created during integration test",
                    value = BigDecimal("250.00"),
                    category = "INTEGRATION_TEST",
                    metadata = mapOf("test" to "lifecycle"),
                )

            val result = commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)
            result shouldNotBe null
        }

        test("8.4-INT-002: should handle concurrent widget creation") {
            val widgets =
                (1..10).map { index ->
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "550e8400-e29b-41d4-a716-446655440001",
                        name = "Concurrent Widget $index",
                        description = "Widget number $index",
                        value = BigDecimal(index * 100),
                        category = "CONCURRENT_TEST",
                        metadata = mapOf("index" to index),
                    )
                }

            val futures =
                widgets.map { command ->
                    commandGateway.send<String>(command)
                }

            futures.forEach { future ->
                future.get(10, TimeUnit.SECONDS).shouldNotBeNull()
            }
        }

        test("8.4-INT-003: should validate business rules during command handling") {
            val invalidCommand =
                CreateWidgetCommand(
                    widgetId = UUID.randomUUID().toString(),
                    tenantId = "550e8400-e29b-41d4-a716-446655440002",
                    name = "!!!",
                    description = null,
                    value = BigDecimal("-100"),
                    category = "invalid",
                    metadata = emptyMap(),
                )

            try {
                commandGateway.sendAndWait<String>(invalidCommand, 5, TimeUnit.SECONDS)
                // Should not reach here
                false shouldBe true
            } catch (e: Exception) {
                e.message?.contains("validation failed", ignoreCase = true) shouldBe true
            }
        }

        test("8.4-INT-004: should support partial updates") {
            val widgetId = UUID.randomUUID().toString()

            val createCommand =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "550e8400-e29b-41d4-a716-446655440003",
                    name = "Original Name",
                    description = "Original Description",
                    value = BigDecimal("100.00"),
                    category = "ORIGINAL",
                    metadata = mapOf("version" to 1),
                )

            commandGateway.sendAndWait<String>(createCommand, 5, TimeUnit.SECONDS)

            val partialUpdate =
                UpdateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = "550e8400-e29b-41d4-a716-446655440003",
                    description = "Only updating description",
                )

            commandGateway.sendAndWait<Unit>(partialUpdate, 5, TimeUnit.SECONDS)
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.postgres.start()
            TestContainers.redis.start()
            TestContainers.keycloak.start()

            registry.add("spring.datasource.url", TestContainers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestContainers.postgres::getUsername)
            registry.add("spring.datasource.password", TestContainers.postgres::getPassword)
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                "${TestContainers.keycloak.authServerUrl}/realms/eaf-test"
            }
        }
    }
}
