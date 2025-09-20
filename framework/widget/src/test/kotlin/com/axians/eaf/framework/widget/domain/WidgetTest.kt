package com.axians.eaf.framework.widget.domain

import com.axians.eaf.api.widget.commands.CreateWidgetCommand
import com.axians.eaf.api.widget.commands.UpdateWidgetCommand
import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.api.widget.events.WidgetUpdatedEvent
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.axonframework.test.aggregate.AggregateTestFixture
import java.math.BigDecimal
import java.util.UUID

class WidgetTest :
    BehaviorSpec({

        Given("Widget aggregate creation") {
            val fixture = AggregateTestFixture(Widget::class.java)

            When("creating a widget with valid data") {
                val widgetId = UUID.randomUUID().toString()
                val command =
                    CreateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "test-tenant",
                        name = "Test Widget 01",
                        description = "A test widget for demonstration",
                        value = BigDecimal("100.00"),
                        category = "TEST_CATEGORY",
                        metadata = mapOf("key" to "value"),
                    )

                Then("widget should be created successfully") {
                    fixture
                        .givenNoPriorActivity()
                        .`when`(command)
                        .expectSuccessfulHandlerExecution()
                        .expectEventsMatching(
                            org.axonframework.test.matchers.Matchers.sequenceOf(
                                org.axonframework.test.matchers.Matchers.matches { event ->
                                    val payload = event.payload
                                    payload is WidgetCreatedEvent &&
                                        payload.widgetId == widgetId &&
                                        payload.tenantId == "test-tenant" &&
                                        payload.name == "Test Widget 01" &&
                                        payload.description == "A test widget for demonstration" &&
                                        payload.value == BigDecimal("100.00") &&
                                        payload.category == "TEST_CATEGORY" &&
                                        payload.metadata == mapOf("key" to "value")
                                },
                            ),
                        )
                }
            }

            When("creating a widget with invalid name pattern") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "!!!Invalid!!!",
                        description = null,
                        value = BigDecimal("50.00"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "name"
                }
            }

            When("creating a widget with empty name") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "",
                        description = null,
                        value = BigDecimal("50.00"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "name"
                    (result.leftOrNull() as WidgetError.ValidationError).constraint shouldBe "length"
                }
            }

            When("creating a widget with invalid category") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "Valid Name",
                        description = null,
                        value = BigDecimal("50.00"),
                        category = "invalid-category",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "category"
                }
            }

            When("creating a widget with negative value") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "Test Widget",
                        description = null,
                        value = BigDecimal("-10.00"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "value"
                    (result.leftOrNull() as WidgetError.ValidationError).constraint shouldBe "range"
                }
            }

            When("creating a widget with value exceeding maximum") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "Test Widget",
                        description = null,
                        value = BigDecimal("1000001"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "value"
                }
            }

            When("creating a widget with description exceeding max length") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "Test Widget",
                        description = "A".repeat(1001),
                        value = BigDecimal("100.00"),
                        category = "TEST_CATEGORY",
                        metadata = emptyMap(),
                    )

                Then("should return validation error") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "description"
                    (result.leftOrNull() as WidgetError.ValidationError).constraint shouldBe "max_length"
                }
            }
        }

        Given("Widget aggregate update") {
            val fixture = AggregateTestFixture(Widget::class.java)
            val widgetId = UUID.randomUUID().toString()
            val tenantId = "test-tenant"

            val createdEvent =
                WidgetCreatedEvent(
                    widgetId = widgetId,
                    tenantId = tenantId,
                    name = "Original Widget",
                    description = "Original description",
                    value = BigDecimal("100.00"),
                    category = "ORIGINAL_CAT",
                    metadata = mapOf("version" to 1),
                )

            When("updating a widget with valid data") {
                val updateCommand =
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "Updated Widget",
                        value = BigDecimal("200.00"),
                    )

                Then("widget should be updated successfully") {
                    fixture
                        .given(createdEvent)
                        .`when`(updateCommand)
                        .expectSuccessfulHandlerExecution()
                        .expectEventsMatching(
                            org.axonframework.test.matchers.Matchers.sequenceOf(
                                org.axonframework.test.matchers.Matchers.matches { event ->
                                    val payload = event.payload
                                    payload is WidgetUpdatedEvent &&
                                        payload.widgetId == widgetId &&
                                        payload.tenantId == tenantId &&
                                        payload.name == "Updated Widget" &&
                                        payload.description == null &&
                                        payload.value == BigDecimal("200.00") &&
                                        payload.category == null &&
                                        payload.metadata == null
                                },
                            ),
                        )
                }
            }

            When("updating a widget with wrong tenant ID") {
                val updateCommand =
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = "wrong-tenant",
                        name = "Updated Widget",
                    )

                Then("should fail with tenant isolation violation") {
                    fixture
                        .given(createdEvent)
                        .`when`(updateCommand)
                        .expectNoEvents()
                        .expectSuccessfulHandlerExecution()
                }
            }

            When("updating with invalid name") {
                val command =
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        name = "",
                    )

                Then("should return validation error") {
                    val result = Widget.validateUpdateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "name"
                }
            }

            When("updating with invalid value") {
                val command =
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        value = BigDecimal("-100"),
                    )

                Then("should return validation error") {
                    val result = Widget.validateUpdateCommand(command)
                    result.shouldBeLeft()
                    result.leftOrNull().shouldBeInstanceOf<WidgetError.ValidationError>()
                    (result.leftOrNull() as WidgetError.ValidationError).field shouldBe "value"
                }
            }

            When("partial update with only description") {
                val updateCommand =
                    UpdateWidgetCommand(
                        widgetId = widgetId,
                        tenantId = tenantId,
                        description = "New description only",
                    )

                Then("should update only the description field") {
                    fixture
                        .given(createdEvent)
                        .`when`(updateCommand)
                        .expectSuccessfulHandlerExecution()
                        .expectEventsMatching(
                            org.axonframework.test.matchers.Matchers.sequenceOf(
                                org.axonframework.test.matchers.Matchers.matches { event ->
                                    val payload = event.payload
                                    payload is WidgetUpdatedEvent &&
                                        payload.widgetId == widgetId &&
                                        payload.tenantId == tenantId &&
                                        payload.name == null &&
                                        payload.description == "New description only" &&
                                        payload.value == null &&
                                        payload.category == null &&
                                        payload.metadata == null
                                },
                            ),
                        )
                }
            }
        }

        Given("Widget validation helpers") {
            When("validating a complete valid command") {
                val command =
                    CreateWidgetCommand(
                        widgetId = UUID.randomUUID().toString(),
                        tenantId = "test-tenant",
                        name = "Valid Widget 99",
                        description = "A perfectly valid widget",
                        value = BigDecimal("999.99"),
                        category = "VALID_CATEGORY",
                        metadata = mapOf("test" to true),
                    )

                Then("should pass all validations") {
                    val result = Widget.validateCreateCommand(command)
                    result.shouldBeRight()
                    result.getOrNull() shouldBe Unit
                }
            }

            When("validating edge cases for name pattern") {
                Then("minimum length name should be valid") {
                    val command =
                        CreateWidgetCommand(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = "test",
                            name = "AB",
                            description = null,
                            value = BigDecimal("10"),
                            category = "TEST",
                            metadata = emptyMap(),
                        )
                    Widget.validateCreateCommand(command).shouldBeRight()
                }

                Then("maximum length name should be valid") {
                    val command =
                        CreateWidgetCommand(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = "test",
                            name = "A" + "B".repeat(98) + "C",
                            description = null,
                            value = BigDecimal("10"),
                            category = "TEST",
                            metadata = emptyMap(),
                        )
                    Widget.validateCreateCommand(command).shouldBeRight()
                }

                Then("name with spaces and dashes should be valid") {
                    val command =
                        CreateWidgetCommand(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = "test",
                            name = "Test Widget-01_Name",
                            description = null,
                            value = BigDecimal("10"),
                            category = "TEST",
                            metadata = emptyMap(),
                        )
                    Widget.validateCreateCommand(command).shouldBeRight()
                }
            }

            When("validating edge cases for value range") {
                Then("zero value should be valid") {
                    val command =
                        CreateWidgetCommand(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = "test",
                            name = "Zero Widget",
                            description = null,
                            value = BigDecimal.ZERO,
                            category = "TEST",
                            metadata = emptyMap(),
                        )
                    Widget.validateCreateCommand(command).shouldBeRight()
                }

                Then("maximum value should be valid") {
                    val command =
                        CreateWidgetCommand(
                            widgetId = UUID.randomUUID().toString(),
                            tenantId = "test",
                            name = "Max Widget",
                            description = null,
                            value = BigDecimal("1000000"),
                            category = "TEST",
                            metadata = emptyMap(),
                        )
                    Widget.validateCreateCommand(command).shouldBeRight()
                }
            }
        }
    })
