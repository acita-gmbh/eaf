# Story 2.5: Demo Widget Aggregate with Commands and Events

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR010 (Hexagonal Architecture)

---

## User Story

As a framework developer,
I want a simple Widget aggregate with CQRS commands and events,
So that I have a working example demonstrating the CQRS pattern.

---

## Acceptance Criteria

1. ✅ products/widget-demo module created
2. ✅ Widget.kt aggregate with WidgetId value object
3. ✅ Commands implemented: CreateWidgetCommand, UpdateWidgetCommand, PublishWidgetCommand
4. ✅ Events implemented: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
5. ✅ Command handlers in Widget aggregate with business logic validation
6. ✅ Event sourcing handlers for state reconstruction
7. ✅ Axon Test Fixtures tests for all command scenarios (success and validation failures)
8. ✅ All tests pass in <10 seconds
9. ✅ Aggregate documented with inline KDoc

---

## Prerequisites

**Story 2.2** - PostgreSQL Event Store Setup (events need storage)

---

## Technical Notes

### Widget Aggregate

**products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/domain/Widget.kt:**
```kotlin
@Aggregate
class Widget {

    @AggregateIdentifier
    private lateinit var widgetId: WidgetId
    private lateinit var name: String
    private var published: Boolean = false

    // Required no-arg constructor for Axon
    constructor()

    // Command handlers
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        // Validation
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // Emit event
        AggregateLifecycle.apply(
            WidgetCreatedEvent(command.widgetId, command.name)
        )
    }

    @CommandHandler
    fun handle(command: UpdateWidgetCommand) {
        require(!published) { "Cannot update published widget" }

        AggregateLifecycle.apply(
            WidgetUpdatedEvent(widgetId, command.name)
        )
    }

    @CommandHandler
    fun handle(command: PublishWidgetCommand) {
        require(!published) { "Widget already published" }

        AggregateLifecycle.apply(
            WidgetPublishedEvent(widgetId)
        )
    }

    // Event sourcing handlers (state reconstruction)
    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.published = false
    }

    @EventSourcingHandler
    fun on(event: WidgetUpdatedEvent) {
        this.name = event.name
    }

    @EventSourcingHandler
    fun on(event: WidgetPublishedEvent) {
        this.published = true
    }
}
```

### Commands

```kotlin
data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String
)

data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String
)

data class PublishWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId
)
```

### Events

```kotlin
data class WidgetCreatedEvent(
    val widgetId: WidgetId,
    val name: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID()
) : DomainEvent

data class WidgetUpdatedEvent(
    val widgetId: WidgetId,
    val name: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID()
) : DomainEvent

data class WidgetPublishedEvent(
    val widgetId: WidgetId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: UUID = UUID.randomUUID()
) : DomainEvent
```

### Value Object

```kotlin
data class WidgetId(override val value: String) : Identifier {
    constructor(uuid: UUID) : this(uuid.toString())
}
```

### Axon Test Fixtures

```kotlin
class WidgetAggregateTest : FunSpec({

    test("create widget with valid name succeeds") {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture<Widget>()
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, "Test Widget"))
            .expectEvents(WidgetCreatedEvent(widgetId, "Test Widget"))
    }

    test("create widget with blank name fails") {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture<Widget>()
            .givenNoPriorActivity()
            .`when`(CreateWidgetCommand(widgetId, ""))
            .expectException(IllegalArgumentException::class.java)
    }

    test("publish widget succeeds when not published") {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture<Widget>()
            .given(WidgetCreatedEvent(widgetId, "Test"))
            .`when`(PublishWidgetCommand(widgetId))
            .expectEvents(WidgetPublishedEvent(widgetId))
    }

    test("publish already published widget fails") {
        val widgetId = WidgetId(UUID.randomUUID())

        fixture<Widget>()
            .given(
                WidgetCreatedEvent(widgetId, "Test"),
                WidgetPublishedEvent(widgetId)
            )
            .`when`(PublishWidgetCommand(widgetId))
            .expectException(IllegalArgumentException::class.java)
    }
})
```

---

## Implementation Checklist

- [ ] Create products/widget-demo module
- [ ] Create WidgetId.kt value object
- [ ] Create Widget.kt aggregate with @Aggregate annotation
- [ ] Implement CreateWidgetCommand, UpdateWidgetCommand, PublishWidgetCommand
- [ ] Implement WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
- [ ] Add @CommandHandler methods with validation
- [ ] Add @EventSourcingHandler methods for state reconstruction
- [ ] Write Axon Test Fixtures tests (success + validation failures)
- [ ] Run `./gradlew :products:widget-demo:test` - verify <10s
- [ ] Add KDoc to all classes
- [ ] Commit: "Add Widget aggregate demonstrating CQRS pattern"

---

## Test Evidence

- [ ] All command scenarios tested (create, update, publish)
- [ ] Validation failures tested (blank name, already published)
- [ ] Axon Test Fixtures tests pass
- [ ] Test execution <10 seconds
- [ ] State reconstruction verified (event sourcing handlers)

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All tests pass in <10 seconds
- [ ] Aggregate documented with KDoc
- [ ] Business logic validated
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.4 - Snapshot Support
**Next Story:** Story 2.6 - jOOQ Configuration and Projection Tables

---

## References

- PRD: FR003 (Event Store), FR010 (Hexagonal Architecture - Domain layer)
- Architecture: Section 5 (products/widget-demo structure)
- Tech Spec: Section 3 (FR003 Implementation)
