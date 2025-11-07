# Story 2.7: Widget Projection Event Handler

**Story Context:** [2-7-widget-projection-handler.context.xml](2-7-widget-projection-handler.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** REVIEW
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store - Projections), FR011 (Performance - Event lag <10s)

---

## User Story

As a framework developer,
I want an event handler that projects Widget events into the widget_projection read model,
So that queries can retrieve current Widget state efficiently.

---

## Acceptance Criteria

1. ✅ WidgetProjectionEventHandler.kt created as @Component with @EventHandler methods
2. ✅ Event handlers for: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
3. ✅ Handlers use jOOQ DSLContext to insert/update widget_projection table
4. ✅ TrackingEventProcessor configured for projection updates (real-time, <10s lag)
5. ✅ Integration test validates: dispatch command → event published → projection updated
6. ✅ Projection lag measured and meets <10s target
7. ✅ Error handling for projection failures (logged, metrics emitted)

---

## Prerequisites

**Story 2.5** - Demo Widget Aggregate (events to handle)
**Story 2.6** - jOOQ Configuration (query infrastructure)

---

## Technical Notes

### Widget Projection Event Handler

**products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/query/WidgetProjectionEventHandler.kt:**
```kotlin
@Component
class WidgetProjectionEventHandler(
    private val dsl: DSLContext,
    private val meterRegistry: MeterRegistry
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        try {
            val table = DSL.table("widget_projection")
            val insertedRows = dsl
                .insertInto(table)
                .columns(DSL.field("id"), DSL.field("name"), DSL.field("published"),
                         DSL.field("created_at"), DSL.field("updated_at"))
                .values(UUID.fromString(event.widgetId.value), event.name, false,
                        event.occurredAt.atOffset(ZoneOffset.UTC),
                        event.occurredAt.atOffset(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute()

            // Only increment metric if row was actually inserted (idempotency)
            if (insertedRows > 0) {
                meterRegistry.counter("projection.widget.created").increment()
            }
        } catch (e: Exception) {
            logger.error("Failed to project WidgetCreatedEvent for widgetId=${event.widgetId.value}", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e  // Retry via Axon tracking processor
        }
    }

    @EventHandler
    fun on(event: WidgetUpdatedEvent) {
        try {
            val table = DSL.table("widget_projection")
            val updatedRows = dsl
                .update(table)
                .set(DSL.field("name", String::class.java), event.name)
                .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                .execute()

            require(updatedRows == 1) {
                "Projection row missing for widgetId=${event.widgetId.value}"
            }
            meterRegistry.counter("projection.widget.updated").increment()
        } catch (e: Exception) {
            logger.error("Failed to project WidgetUpdatedEvent", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e
        }
    }

    @EventHandler
    fun on(event: WidgetPublishedEvent) {
        try {
            val table = DSL.table("widget_projection")
            val publishedRows = dsl
                .update(table)
                .set(DSL.field("published", Boolean::class.java), true)
                .set(DSL.field("updated_at"), event.occurredAt.atOffset(ZoneOffset.UTC))
                .where(DSL.field("id").eq(UUID.fromString(event.widgetId.value)))
                .execute()

            require(publishedRows == 1) {
                "Projection row missing for widgetId=${event.widgetId.value}"
            }
            meterRegistry.counter("projection.widget.published").increment()
        } catch (e: Exception) {
            logger.error("Failed to project WidgetPublishedEvent", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e
        }
    }
}
```

### TrackingEventProcessor Configuration

**application.yml:**
```yaml
axon:
  eventhandling:
    processors:
      widget-projection:
        mode: tracking
        source: eventStore
        batch-size: 100
```

### Projection Lag Measurement

```kotlin
@Component
class ProjectionLagMonitor(
    private val meterRegistry: MeterRegistry,
    private val eventStore: EventStore
) {

    @Scheduled(fixedRate = 5000)  // Every 5 seconds
    fun measureProjectionLag() {
        // Compare last processed event timestamp vs current time
        val lag = calculateLag()
        meterRegistry.gauge("projection.widget.lag_seconds", lag)
    }
}
```

---

## Implementation Checklist

- [x] Create WidgetProjectionEventHandler.kt with @Component
- [x] Implement @EventHandler for WidgetCreatedEvent (INSERT)
- [x] Implement @EventHandler for WidgetUpdatedEvent (UPDATE)
- [x] Implement @EventHandler for WidgetPublishedEvent (UPDATE)
- [x] Use jOOQ DSLContext for all database operations
- [x] Configure TrackingEventProcessor in application.yml
- [x] Add error handling (log + metrics)
- [x] Add projection lag monitoring
- [x] Write integration test: dispatch command → verify projection updated
- [x] Measure projection lag (<10s target)
- [x] Commit: "Add Widget projection event handler with jOOQ"

---

## Test Evidence

- [x] Integration test: CreateWidgetCommand → widget_projection row inserted
- [x] Integration test: UpdateWidgetCommand → widget_projection row updated
- [x] Integration test: PublishWidgetCommand → published flag set to true
- [x] Projection lag <10 seconds validated
- [x] Error metrics emitted on failure
- [x] TrackingEventProcessor processes events

---

## Definition of Done

- [x] All acceptance criteria met
- [x] Integration test passes
- [x] Projection lag <10s validated
- [x] Error handling tested
- [x] Metrics emitted
- [x] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.6 - jOOQ Configuration
**Next Story:** Story 2.8 - Widget Query Handler

---

## References

- PRD: FR003 (jOOQ projections), FR011 (Performance - Event lag <10s)
- Architecture: Section 14 (Projection Schema Design)
- Tech Spec: Section 3 (FR003 - Projections), Section 8.1 (Performance Targets)
