package com.axians.eaf.products.widget.domain

import com.axians.eaf.framework.multitenancy.TenantContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.test.aggregate.AggregateTestFixture
import org.axonframework.test.aggregate.FixtureConfiguration
import org.axonframework.test.matchers.Matchers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.UUID

/**
 * Axon Test Fixtures tests for Widget aggregate.
 *
 * Verifies CQRS command handling, event sourcing, and business logic validation
 * using Given-When-Then BDD style with Axon fixtures.
 *
 * Multi-tenancy (Story 4.6): Sets up TenantContext before each test.
 */
class WidgetAggregateTest :
    FunSpec({

        lateinit var fixture: FixtureConfiguration<Widget>

        beforeTest {
            // Set up tenant context for multi-tenant tests (Story 4.6)
            TenantContext.setCurrentTenantId(TEST_TENANT_ID)

            fixture = AggregateTestFixture(Widget::class.java)
        }

        afterTest {
            // Clean up tenant context to prevent leaks (Story 4.6)
            TenantContext.clearCurrentTenant()
        }

        context("CreateWidgetCommand") {

            test("create widget with valid name succeeds") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .givenNoPriorActivity()
                    .`when`(CreateWidgetCommand(widgetId, "Test Widget", TEST_TENANT_ID))
                    .expectEventsMatching(
                        Matchers.payloadsMatching(
                            Matchers.exactSequenceOf(
                                Matchers.matches<WidgetCreatedEvent> { event ->
                                    event.widgetId == widgetId && event.name == "Test Widget"
                                },
                            ),
                        ),
                    )
            }

            test("create widget with blank name fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .givenNoPriorActivity()
                    .`when`(CreateWidgetCommand(widgetId, "", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }

            test("create widget with whitespace-only name fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .givenNoPriorActivity()
                    .`when`(CreateWidgetCommand(widgetId, "   ", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }
        }

        context("UpdateWidgetCommand") {

            test("update unpublished widget succeeds") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(WidgetCreatedEvent(widgetId, "Original Name"))
                    .`when`(UpdateWidgetCommand(widgetId, "Updated Name", TEST_TENANT_ID))
                    .expectEventsMatching(
                        Matchers.payloadsMatching(
                            Matchers.exactSequenceOf(
                                Matchers.matches<WidgetUpdatedEvent> { event ->
                                    event.widgetId == widgetId && event.name == "Updated Name"
                                },
                            ),
                        ),
                    )
            }

            test("update published widget fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(
                        WidgetCreatedEvent(widgetId, "Test"),
                        WidgetPublishedEvent(widgetId),
                    ).`when`(UpdateWidgetCommand(widgetId, "New Name", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }

            test("update widget with blank name fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(WidgetCreatedEvent(widgetId, "Original Name"))
                    .`when`(UpdateWidgetCommand(widgetId, "", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }

            test("update widget with whitespace-only name fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(WidgetCreatedEvent(widgetId, "Original Name"))
                    .`when`(UpdateWidgetCommand(widgetId, "   ", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }
        }

        context("PublishWidgetCommand") {

            test("publish unpublished widget succeeds") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(WidgetCreatedEvent(widgetId, "Test"))
                    .`when`(PublishWidgetCommand(widgetId, TEST_TENANT_ID))
                    .expectEventsMatching(
                        Matchers.payloadsMatching(
                            Matchers.exactSequenceOf(
                                Matchers.matches<WidgetPublishedEvent> { event ->
                                    event.widgetId == widgetId
                                },
                            ),
                        ),
                    )
            }

            test("publish already published widget fails") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(
                        WidgetCreatedEvent(widgetId, "Test"),
                        WidgetPublishedEvent(widgetId),
                    ).`when`(PublishWidgetCommand(widgetId, TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }
        }

        context("Event Sourcing - State Reconstruction") {

            test("WidgetCreatedEvent reconstructs initial state") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(WidgetCreatedEvent(widgetId, "Initial Widget"))
                    .`when`(PublishWidgetCommand(widgetId, TEST_TENANT_ID))
                    .expectEventsMatching(
                        Matchers.payloadsMatching(
                            Matchers.exactSequenceOf(
                                Matchers.matches<WidgetPublishedEvent> { event ->
                                    event.widgetId == widgetId
                                },
                            ),
                        ),
                    )
            }

            test("WidgetUpdatedEvent reconstructs updated name") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(
                        WidgetCreatedEvent(widgetId, "Original"),
                        WidgetUpdatedEvent(widgetId, "Updated"),
                    ).`when`(PublishWidgetCommand(widgetId, TEST_TENANT_ID))
                    .expectEventsMatching(
                        Matchers.payloadsMatching(
                            Matchers.exactSequenceOf(
                                Matchers.matches<WidgetPublishedEvent> { event ->
                                    event.widgetId == widgetId
                                },
                            ),
                        ),
                    )
            }

            test("WidgetPublishedEvent reconstructs published state") {
                val widgetId = WidgetId(UUID.randomUUID())

                fixture
                    .given(
                        WidgetCreatedEvent(widgetId, "Test"),
                        WidgetPublishedEvent(widgetId),
                    ).`when`(UpdateWidgetCommand(widgetId, "New Name", TEST_TENANT_ID))
                    .expectException(IllegalArgumentException::class.java)
            }
        }

        context("Snapshot Support - Serialization (Story 2.4 Deferred)") {

            test("Widget aggregate implements Serializable for snapshot support") {
                // Verify Widget is Serializable (prerequisite for Axon snapshots)
                Serializable::class.java.isAssignableFrom(Widget::class.java) shouldBe true
            }

            test("Widget state can be serialized and deserialized") {
                // Create widget instance (simulating Axon snapshot serialization)
                val widget = Widget::class.java.getDeclaredConstructor().newInstance()

                widget.shouldBeInstanceOf<Serializable>()

                // Verify Java serialization works (used by Axon snapshots)
                val serialized =
                    ByteArrayOutputStream().use { baos ->
                        ObjectOutputStream(baos).use { oos ->
                            oos.writeObject(widget)
                        }
                        baos.toByteArray()
                    }

                val deserialized =
                    ByteArrayInputStream(serialized).use { bais ->
                        ObjectInputStream(bais).use { ois ->
                            ois.readObject()
                        }
                    }

                deserialized.shouldBeInstanceOf<Widget>()
            }

            test("WidgetId value object is serializable for snapshot support") {
                val widgetId = WidgetId(UUID.randomUUID())

                widgetId.shouldBeInstanceOf<Serializable>()

                // Verify serialization round-trip
                val serialized =
                    ByteArrayOutputStream().use { baos ->
                        ObjectOutputStream(baos).use { oos ->
                            oos.writeObject(widgetId)
                        }
                        baos.toByteArray()
                    }

                val deserialized =
                    ByteArrayInputStream(serialized).use { bais ->
                        ObjectInputStream(bais).use { ois ->
                            ois.readObject() as WidgetId
                        }
                    }

                deserialized shouldBe widgetId
            }
        }
    }) {
    companion object {
        private const val TEST_TENANT_ID = "test-tenant"
    }
}
