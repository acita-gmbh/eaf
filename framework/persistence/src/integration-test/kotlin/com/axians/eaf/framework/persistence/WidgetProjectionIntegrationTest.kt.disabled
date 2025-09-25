package com.axians.eaf.framework.persistence

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.persistence.entities.WidgetProjection
import com.axians.eaf.framework.persistence.repositories.WidgetProjectionRepository
import com.axians.eaf.framework.widget.projections.WidgetProjectionHandler
import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.perSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for Widget projection persistence and event processing.
 *
 * These tests validate the complete projection lifecycle including:
 * - WidgetProjection entity JPA mappings
 * - WidgetProjectionRepository operations with tenant isolation
 * - WidgetProjectionHandler event processing
 * - TrackingToken persistence (implicitly tested via Axon)
 *
 * Uses Testcontainers PostgreSQL for real database integration testing.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class WidgetProjectionIntegrationTest(
    private val repository: WidgetProjectionRepository,
) : FunSpec({

        extension(SpringExtension)

        // Use shared TestContainers with Kotest lifecycle management
        listener(TestContainers.postgres.perSpec())

        private val objectMapper = ObjectMapper()

        beforeTest {
            // Clean up before each test to ensure isolation
            repository.deleteAll()
        }

        context("WidgetProjection Entity JPA Mapping") {

            test("should persist WidgetProjection entity with all fields") {
                // Given: A widget projection with all fields populated
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "test-tenant-001"
                val metadata = mapOf("key1" to "value1", "key2" to 42)
                val metadataJson = objectMapper.writeValueAsString(metadata)
                val now = Instant.now()

                val projection =
                    WidgetProjection(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Test Widget",
                        description = "A test widget for projection testing",
                        value = BigDecimal("123.45"),
                        category = "test-category",
                        metadata = metadataJson,
                        createdAt = now,
                        updatedAt = now,
                    )

                // When: Saving the projection
                val savedProjection = repository.save(projection)

                // Then: All fields should be persisted correctly
                savedProjection shouldNotBe null
                savedProjection.widgetId shouldBe widgetId
                savedProjection.tenantId shouldBe tenantId
                savedProjection.name shouldBe "Test Widget"
                savedProjection.description shouldBe "A test widget for projection testing"
                savedProjection.value shouldBe BigDecimal("123.45")
                savedProjection.category shouldBe "test-category"
                savedProjection.metadata shouldBe metadataJson
                savedProjection.createdAt shouldBe now
                savedProjection.updatedAt shouldBe now
            }

            test("should implement TenantAware interface correctly") {
                // Given: A widget projection with tenant ID
                val tenantId = "test-tenant-002"
                val projection =
                    WidgetProjection(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        name = "Tenant Test Widget",
                        description = null,
                        value = BigDecimal("100.00"),
                        category = "tenant-test",
                        metadata = null,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )

                // When: Calling getTenantId method
                val returnedTenantId = projection.getTenantId()

                // Then: Should return correct tenant ID
                returnedTenantId shouldBe tenantId
            }

            test("should handle nullable fields correctly") {
                // Given: A widget projection with minimal required fields
                val projection =
                    WidgetProjection(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant-003",
                        name = "Minimal Widget",
                        description = null, // nullable
                        value = BigDecimal("50.00"),
                        category = "minimal",
                        metadata = null, // nullable
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                    )

                // When: Saving the projection
                val savedProjection = repository.save(projection)

                // Then: Nullable fields should be handled correctly
                savedProjection.description shouldBe null
                savedProjection.metadata shouldBe null
                savedProjection.name shouldBe "Minimal Widget"
            }
        }

        context("WidgetProjectionRepository Tenant Isolation") {

            test("should find widget by widgetId and tenantId") {
                // Given: Widget projections for different tenants
                val widgetId = UUID.randomUUID().toString()
                val tenant1 = "tenant-001"
                val tenant2 = "tenant-002"

                val projection1 = createTestProjection(widgetId, tenant1, "Widget for Tenant 1")
                val projection2 = createTestProjection(UUID.randomUUID().toString(), tenant2, "Widget for Tenant 2")

                repository.save(projection1)
                repository.save(projection2)

                // When: Finding widget by ID and tenant
                val foundProjection = repository.findByWidgetIdAndTenantId(widgetId, tenant1)

                // Then: Should return only the correct tenant's widget
                foundProjection shouldNotBe null
                foundProjection!!.widgetId shouldBe widgetId
                foundProjection.tenantId shouldBe tenant1
                foundProjection.name shouldBe "Widget for Tenant 1"
            }

            test("should isolate widgets by tenant") {
                // Given: Multiple widgets for different tenants
                val tenant1 = "tenant-001"
                val tenant2 = "tenant-002"

                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget 1-1"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget 1-2"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant2, "Widget 2-1"))

                // When: Finding widgets for tenant 1
                val tenant1Widgets = repository.findByTenantIdOrderByCreatedAtDesc(tenant1)

                // Then: Should return only tenant 1's widgets
                tenant1Widgets.size shouldBe 2
                tenant1Widgets.all { it.tenantId == tenant1 } shouldBe true
                tenant1Widgets.map { it.name }.toSet() shouldBe setOf("Widget 1-1", "Widget 1-2")
            }

            test("should filter by category and tenant") {
                // Given: Widgets in different categories for different tenants
                val tenant1 = "tenant-001"
                val tenant2 = "tenant-002"

                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget A", "category-a"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget B", "category-b"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant2, "Widget C", "category-a"))

                // When: Finding widgets by tenant and category
                val categoryAWidgets = repository.findByTenantIdAndCategoryOrderByCreatedAtDesc(tenant1, "category-a")

                // Then: Should return only tenant 1's category A widgets
                categoryAWidgets.size shouldBe 1
                categoryAWidgets[0].name shouldBe "Widget A"
                categoryAWidgets[0].tenantId shouldBe tenant1
                categoryAWidgets[0].category shouldBe "category-a"
            }

            test("should count widgets per tenant correctly") {
                // Given: Multiple widgets for different tenants
                val tenant1 = "tenant-001"
                val tenant2 = "tenant-002"

                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget 1"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget 2"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant1, "Widget 3"))
                repository.save(createTestProjection(UUID.randomUUID().toString(), tenant2, "Widget 4"))

                // When: Counting widgets per tenant
                val tenant1Count = repository.countByTenantId(tenant1)
                val tenant2Count = repository.countByTenantId(tenant2)

                // Then: Counts should be correct per tenant
                tenant1Count shouldBe 3
                tenant2Count shouldBe 1
            }
        }

        context("WidgetProjectionHandler Event Processing") {

            test("should process WidgetCreatedEvent and create projection") {
                // Given: A WidgetCreatedEvent
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "test-tenant-004"
                val metadata = mapOf("source" to "integration-test", "priority" to "high")

                val event =
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Event-Sourced Widget",
                        description = "Created via event processing",
                        value = BigDecimal("250.75"),
                        category = "event-test",
                        metadata = metadata,
                        createdAt = Instant.now(),
                    )

                val handler = WidgetProjectionHandler(repository, objectMapper)

                // When: Processing the event
                handler.on(event)

                // Then: Projection should be created in database
                val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
                projection shouldNotBe null
                projection!!.widgetId shouldBe widgetId
                projection.tenantId shouldBe tenantId
                projection.name shouldBe "Event-Sourced Widget"
                projection.description shouldBe "Created via event processing"
                projection.value shouldBe BigDecimal("250.75")
                projection.category shouldBe "event-test"
                projection.metadata shouldNotBe null

                // Verify metadata JSON serialization
                val deserializedMetadata = objectMapper.readValue(projection.metadata, Map::class.java)
                deserializedMetadata["source"] shouldBe "integration-test"
                deserializedMetadata["priority"] shouldBe "high"
            }

            test("should handle events with empty metadata") {
                // Given: A WidgetCreatedEvent with empty metadata
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "test-tenant-005"

                val event =
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "No Metadata Widget",
                        description = "Widget without metadata",
                        value = BigDecimal("75.25"),
                        category = "no-meta",
                        metadata = emptyMap(),
                        createdAt = Instant.now(),
                    )

                val handler = WidgetProjectionHandler(repository, objectMapper)

                // When: Processing the event
                handler.on(event)

                // Then: Projection should be created with null metadata
                val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
                projection shouldNotBe null
                projection!!.metadata shouldBe null
            }

            test("should maintain tenant isolation during event processing") {
                // Given: Events for different tenants with same widget ID
                val widgetId = UUID.randomUUID().toString()
                val tenant1 = "tenant-001"
                val tenant2 = "tenant-002"

                val event1 =
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = tenant1,
                        name = "Tenant 1 Widget",
                        description = "Widget for tenant 1",
                        value = BigDecimal("100.00"),
                        category = "multi-tenant-test",
                        metadata = emptyMap(),
                        createdAt = Instant.now(),
                    )

                val event2 =
                    WidgetCreatedEvent(
                        widgetId = widgetId, // Same widget ID, different tenant
                        tenantId = tenant2,
                        name = "Tenant 2 Widget",
                        description = "Widget for tenant 2",
                        value = BigDecimal("200.00"),
                        category = "multi-tenant-test",
                        metadata = emptyMap(),
                        createdAt = Instant.now(),
                    )

                val handler = WidgetProjectionHandler(repository, objectMapper)

                // When: Processing events for different tenants
                handler.on(event1)
                handler.on(event2)

                // Then: Both projections should exist but be isolated by tenant
                val projection1 = repository.findByWidgetIdAndTenantId(widgetId, tenant1)
                val projection2 = repository.findByWidgetIdAndTenantId(widgetId, tenant2)

                projection1 shouldNotBe null
                projection2 shouldNotBe null

                projection1!!.name shouldBe "Tenant 1 Widget"
                projection1.value shouldBe BigDecimal("100.00")

                projection2!!.name shouldBe "Tenant 2 Widget"
                projection2.value shouldBe BigDecimal("200.00")

                // Verify tenant isolation - each tenant should only see their own widget
                val tenant1Widgets = repository.findByTenantIdOrderByCreatedAtDesc(tenant1)
                val tenant2Widgets = repository.findByTenantIdOrderByCreatedAtDesc(tenant2)

                tenant1Widgets.size shouldBe 1
                tenant2Widgets.size shouldBe 1
                tenant1Widgets[0].tenantId shouldBe tenant1
                tenant2Widgets[0].tenantId shouldBe tenant2
            }
        }

        context("Performance Validation") {

            test("should process WidgetCreatedEvent within performance targets") {
                // Given: A WidgetCreatedEvent and performance measurement setup
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "perf-test-tenant"

                val event =
                    WidgetCreatedEvent(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Performance Test Widget",
                        description = "Widget for performance validation",
                        value = BigDecimal("500.00"),
                        category = "performance",
                        metadata = mapOf("test" to "performance"),
                        createdAt = Instant.now(),
                    )

                val handler = WidgetProjectionHandler(repository, objectMapper)

                // When: Processing event with timing measurement
                val startTime = System.nanoTime()
                handler.on(event)
                val endTime = System.nanoTime()

                val processingTimeMs = (endTime - startTime) / 1_000_000

                // Then: Processing should complete within performance target
                processingTimeMs shouldBe { it < 200 } // Target: <200ms p95 latency

                // Verify projection was created correctly
                val projection = repository.findByWidgetIdAndTenantId(widgetId, tenantId)
                projection shouldNotBe null
                projection!!.name shouldBe "Performance Test Widget"
            }

            test("should handle multiple concurrent projections efficiently") {
                // Given: Multiple widget events for processing
                val tenantId = "concurrent-test-tenant"
                val events =
                    (1..10).map { index ->
                        WidgetCreatedEvent(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = tenantId,
                            name = "Concurrent Widget $index",
                            description = "Widget $index for concurrent testing",
                            value = BigDecimal("${index * 10}.00"),
                            category = "concurrent",
                            metadata = mapOf("index" to index),
                            createdAt = Instant.now(),
                        )
                    }

                val handler = WidgetProjectionHandler(repository, objectMapper)

                // When: Processing events sequentially (simulating batch processing)
                val startTime = System.nanoTime()
                events.forEach { event ->
                    handler.on(event)
                }
                val endTime = System.nanoTime()

                val totalProcessingTimeMs = (endTime - startTime) / 1_000_000
                val avgProcessingTimeMs = totalProcessingTimeMs / events.size

                // Then: Average processing time should be within targets
                avgProcessingTimeMs shouldBe { it < 200 } // Target: <200ms average

                // Verify all projections were created
                val projections = repository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                projections.size shouldBe 10
                projections.all { it.tenantId == tenantId } shouldBe true
            }
        }

        context("TrackingToken and Processor State Management") {

            test("should verify database setup supports tracking token persistence") {
                // Given: Repository is initialized with PostgreSQL
                // When: Checking database schema (implicitly tested by Axon)
                // Then: Verify basic database connectivity and table creation

                // This test validates that the database setup supports Axon's
                // TrackingToken persistence by ensuring our projections work
                val widgetId = UUID.randomUUID().toString()
                val tenantId = "token-test-tenant"

                val projection = createTestProjection(widgetId, tenantId, "Token Test Widget")
                val savedProjection = repository.save(projection)

                // Verify basic database operations work (prerequisite for tracking tokens)
                savedProjection shouldNotBe null
                savedProjection.widgetId shouldBe widgetId

                // Note: Actual TrackingToken persistence is handled by Axon Framework
                // and tested in the full integration tests with event processing
            }
        }
    }) {
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

        /**
         * Helper function to create test projections.
         */
        private fun createTestProjection(
            widgetId: String,
            tenantId: String,
            name: String,
            category: String = "test-category",
        ): WidgetProjection =
            WidgetProjection(
                widgetId = widgetId,
                tenantId = tenantId,
                name = name,
                description = "Test description for $name",
                value = BigDecimal("99.99"),
                category = category,
                metadata = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
    }
}
