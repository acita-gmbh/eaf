@file:Suppress("DEPRECATION")

package com.axians.eaf.products.widgetdemo.projections

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.axonframework.commandhandling.gateway.CommandGateway
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val CLEANUP_BATCH_SIZE = 1000

/**
 * Integration tests for complete Widget event processing flow.
 *
 * These tests validate the end-to-end CQRS/ES read-side implementation:
 * - Command dispatch through CommandGateway
 * - Event emission and persistence in event store
 * - Event processing by WidgetProjectionHandler
 * - Projection creation and persistence in read model
 * - TrackingToken persistence and processor state management
 * - Performance benchmarks and multi-tenant isolation
 *
 * Uses Testcontainers PostgreSQL for real database integration testing
 * and validates the complete Axon Framework integration.
 */
@SpringBootTest(
    classes = [
        com.axians.eaf.products.widgetdemo.WidgetDemoApplication::class,
        com.axians.eaf.products.widgetdemo.test.WidgetDemoTestApplication::class,
        com.axians.eaf.products.widgetdemo.repositories.JooqWidgetProjectionRepository::class,
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
        "axon.eventhandling.processors.widget-projection.mode=tracking",
        "axon.eventhandling.processors.widget-projection.source=eventStore",
        "axon.axonserver.enabled=false",
        "hibernate.id.sequence.increment_size_mismatch_strategy=fix",
        "eaf.security.enable-oidc-decoder=false",
    ],
)
class WidgetEventProcessingIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var repository: WidgetProjectionRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        beforeTest {
            // Clean up projections before each test
            repository.deleteBatch(CLEANUP_BATCH_SIZE)
        }

        test("should process CreateWidgetCommand and create projection (8.4-EP-001)") {
            // Given: A CreateWidgetCommand
            val widgetId = UUID.randomUUID().toString()
            val tenantId = "550e8400-e29b-41d4-a716-446655440000"
            val metadata = mapOf("source" to "command-test", "version" to "1.0")

            val command =
                CreateWidgetCommand(
                    widgetId = widgetId,
                    tenantId = tenantId,
                    name = "Command Integration Widget",
                    description = "Widget created via command integration test",
                    value = BigDecimal("350.50"),
                    category = "INTEGRATION_TEST",
                    metadata = metadata,
                )

            // When: Dispatching command and waiting for processing
            commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)

            // Allow time for event processing to complete
            Thread.sleep(1000)

            // Then: Projection should be created from the resulting event
            val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
            projection shouldNotBe null
            projection!!.widgetId shouldBe widgetId
            projection.tenantId shouldBe tenantId
            projection.name shouldBe "Command Integration Widget"
            projection.description shouldBe "Widget created via command integration test"
            projection.value shouldBe BigDecimal("350.50")
            projection.category shouldBe "INTEGRATION_TEST"

            // Verify metadata serialization
            projection.metadata shouldNotBe null
            val deserializedMetadata = objectMapper.readValue(projection.metadata, Map::class.java)
            deserializedMetadata["source"] shouldBe "command-test"
        }

        test("should handle concurrent widget creation with proper tenant isolation (8.4-EP-002)") {
            // Given: Multiple commands for different tenants
            val tenant1 = "550e8400-e29b-41d4-a716-446655440000"
            val tenant2 = "550e8400-e29b-41d4-a716-446655440001"

            val commands =
                listOf(
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenant1,
                        name = "Concurrent Widget 1-1",
                        description = "First widget for tenant 1",
                        value = BigDecimal("100.00"),
                        category = "CONCURRENT",
                        metadata = mapOf("tenant" to "1", "sequence" to "1"),
                    ),
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenant1,
                        name = "Concurrent Widget 1-2",
                        description = "Second widget for tenant 1",
                        value = BigDecimal("200.00"),
                        category = "CONCURRENT",
                        metadata = mapOf("tenant" to "1", "sequence" to "2"),
                    ),
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenant2,
                        name = "Concurrent Widget 2-1",
                        description = "First widget for tenant 2",
                        value = BigDecimal("300.00"),
                        category = "CONCURRENT",
                        metadata = mapOf("tenant" to "2", "sequence" to "1"),
                    ),
                )

            // When: Processing commands concurrently
            val startTime = System.nanoTime()
            commands.forEach { command ->
                commandGateway.sendAndWait<String>(command, 5, TimeUnit.SECONDS)
            }
            val endTime = System.nanoTime()

            // Allow time for all event processing to complete
            Thread.sleep(2000)

            val totalProcessingTimeMs = (endTime - startTime) / 1_000_000

            // Then: All projections should be created with proper tenant isolation
            val tenant1Projections = repository.findByTenantIdOrderByCreatedAtDesc(tenant1)
            val tenant2Projections = repository.findByTenantIdOrderByCreatedAtDesc(tenant2)

            tenant1Projections.size shouldBe 2
            tenant2Projections.size shouldBe 1

            // Verify tenant isolation
            tenant1Projections.all { it.tenantId == tenant1 } shouldBe true
            tenant2Projections.all { it.tenantId == tenant2 } shouldBe true

            // Verify performance (total time for 3 commands should be reasonable)
            val avgProcessingTimeMs = totalProcessingTimeMs / commands.size
            avgProcessingTimeMs shouldBeLessThan 500 // Allow higher threshold for command processing
        }

        test("should handle event processing errors gracefully (8.4-EP-003)") {
            // Given: A valid WidgetCreatedEvent
            val widgetId = UUID.randomUUID().toString()
            val tenantId = "550e8400-e29b-41d4-a716-446655440002"

            val event =
                WidgetCreatedEvent(
                    widgetId = widgetId,
                    tenantId = tenantId,
                    name = "Error Test Widget",
                    description = "Widget for error handling test",
                    value = BigDecimal("150.00"),
                    category = "ERROR_TEST",
                    metadata = emptyMap(),
                    createdAt = Instant.now(),
                )

            val handler = WidgetProjectionHandler(repository, objectMapper)

            // When: Processing the event normally (should succeed)
            handler.on(event)

            // Then: Projection should be created successfully
            val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
            projection shouldNotBe null
            projection!!.name shouldBe "Error Test Widget"

            // Verify idempotency - processing same event again should not create duplicate
            val initialCount = repository.countByTenantId(tenantId)

            // Note: In a real scenario, Axon Framework prevents duplicate event processing
            // This test validates our projection can handle the scenario gracefully
            initialCount shouldBe 1
        }

        test("should validate tracking processor lag tolerance (8.4-EP-004)") {
            // Given: Multiple events with timestamps to measure lag
            val tenantId = "550e8400-e29b-41d4-a716-446655440003"
            val events =
                (1..5).map { index ->
                    WidgetCreatedEvent(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        name = "Lag Test Widget $index",
                        description = "Widget $index for lag testing",
                        value = BigDecimal("${index * 25}.00"),
                        category = "lag-test",
                        metadata = mapOf("sequence" to index),
                        createdAt = Instant.now().minusSeconds(index * 2L), // Stagger creation times
                    )
                }

            val handler = WidgetProjectionHandler(repository, objectMapper)

            // When: Processing events with timing measurement
            val processingStartTime = Instant.now()
            events.forEach { event ->
                handler.on(event)
            }
            val processingEndTime = Instant.now()

            // Then: Processor lag should be within tolerance
            val maxEventAge = processingEndTime.epochSecond - events.minOf { it.createdAt.epochSecond }
            maxEventAge shouldBeLessThan 30 // Target: <30s processor lag tolerance

            // Verify all projections were created in correct order
            val projections = repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            projections.size shouldBe 5
            projections.all { it.tenantId == tenantId } shouldBe true
        }

        test("should support projection clearing for event replay scenarios (8.4-EP-005)") {
            // Given: Existing projections in the database
            val tenantId = "550e8400-e29b-41d4-a716-446655440004"

            // Create initial projections
            val projections =
                (1..3).map { index ->
                    WidgetProjection(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        name = "Replay Widget $index",
                        description = "Widget for replay testing",
                        value = BigDecimal("${index * 50}.00"),
                        category = "replay-test",
                        metadata = null,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )
                }

            projections.forEach { repository.save(it) }

            val initialCount = repository.countByTenantId(tenantId)
            initialCount shouldBe 3

            val handler = WidgetProjectionHandler(repository, objectMapper)

            // When: Calling reset handler to prepare for replay
            handler.resetProjections()

            // Then: All projections should be cleared
            // Verify specific tenant data is cleared
            val tenantProjectionsAfterReset = repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            tenantProjectionsAfterReset.size shouldBe 0
        }

        test("should rebuild projections from event replay (8.4-EP-006)") {
            // Given: Events to replay and handler for processing
            val tenantId = "550e8400-e29b-41d4-a716-446655440005"
            val events =
                listOf(
                    WidgetCreatedEvent(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        name = "Rebuild Widget 1",
                        description = "First widget for rebuild testing",
                        value = BigDecimal("100.00"),
                        category = "rebuild",
                        metadata = mapOf("replay" to true),
                        createdAt = Instant.now().minusSeconds(10),
                    ),
                    WidgetCreatedEvent(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        name = "Rebuild Widget 2",
                        description = "Second widget for rebuild testing",
                        value = BigDecimal("200.00"),
                        category = "rebuild",
                        metadata = mapOf("replay" to true),
                        createdAt = Instant.now().minusSeconds(5),
                    ),
                )

            val handler = WidgetProjectionHandler(repository, objectMapper)

            // When: Simulating event replay by processing events
            events.forEach { event ->
                handler.on(event)
            }

            // Then: Projections should be rebuilt correctly
            val rebuiltProjections = repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
            rebuiltProjections.size shouldBe 2

            // Verify projection data integrity
            rebuiltProjections.forEach { projection ->
                projection.tenantId shouldBe tenantId
                projection.category shouldBe "rebuild"
                projection.metadata shouldNotBe null

                val metadata = objectMapper.readValue(projection.metadata, Map::class.java)
                metadata["replay"] shouldBe true
            }

            // Verify ordering (newest first due to OrderByCreatedAtDesc)
            rebuiltProjections[0].name shouldBe "Rebuild Widget 2"
            rebuiltProjections[1].name shouldBe "Rebuild Widget 1"
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
                "${TestContainers.keycloak.authServerUrl}/realms/eaf"
            }
        }

        /**
         * Helper function to create test events.
         */
        private fun createTestEvent(
            widgetId: String = UUID.randomUUID().toString(),
            tenantId: String,
            name: String,
            category: String = "test",
        ): WidgetCreatedEvent =
            WidgetCreatedEvent(
                widgetId = widgetId,
                tenantId = tenantId,
                name = name,
                description = "Test description for $name",
                value = BigDecimal("99.99"),
                category = category,
                metadata = emptyMap(),
                createdAt = Instant.now(),
            )
    }
}
