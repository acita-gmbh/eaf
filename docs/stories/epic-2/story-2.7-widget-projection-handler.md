# Story 2.7: Widget Projection Event Handler

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store - Projections), FR011 (Performance - Event lag <10s)

---

## User Story

As a framework developer,
I want an event handler that projects Widget events into the widget_view read model,
So that queries can retrieve current Widget state efficiently.

---

## Acceptance Criteria

1. ✅ WidgetProjectionEventHandler.kt created as @Component with @EventHandler methods
2. ✅ Event handlers for: WidgetCreatedEvent, WidgetUpdatedEvent, WidgetPublishedEvent
3. ✅ Handlers use jOOQ DSLContext to insert/update widget_view table
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
            dsl.insertInto(WIDGET_VIEW)
                .set(WIDGET_VIEW.ID, UUID.fromString(event.widgetId.value))
                .set(WIDGET_VIEW.NAME, event.name)
                .set(WIDGET_VIEW.PUBLISHED, false)
                .set(WIDGET_VIEW.CREATED_AT, event.occurredAt)
                .set(WIDGET_VIEW.UPDATED_AT, event.occurredAt)
                .execute()

            meterRegistry.counter("projection.widget.created").increment()

        } catch (e: Exception) {
            logger.error("Failed to project WidgetCreatedEvent", e)
            meterRegistry.counter("projection.widget.errors").increment()
            throw e  // Retry via Axon tracking processor
        }
    }

    @EventHandler
    fun on(event: WidgetUpdatedEvent) {
        dsl.update(WIDGET_VIEW)
            .set(WIDGET_VIEW.NAME, event.name)
            .set(WIDGET_VIEW.UPDATED_AT, event.occurredAt)
            .where(WIDGET_VIEW.ID.eq(UUID.fromString(event.widgetId.value)))
            .execute()

        meterRegistry.counter("projection.widget.updated").increment()
    }

    @EventHandler
    fun on(event: WidgetPublishedEvent) {
        dsl.update(WIDGET_VIEW)
            .set(WIDGET_VIEW.PUBLISHED, true)
            .set(WIDGET_VIEW.UPDATED_AT, event.occurredAt)
            .where(WIDGET_VIEW.ID.eq(UUID.fromString(event.widgetId.value)))
            .execute()

        meterRegistry.counter("projection.widget.published").increment()
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

- [ ] Create WidgetProjectionEventHandler.kt with @Component
- [ ] Implement @EventHandler for WidgetCreatedEvent (INSERT)
- [ ] Implement @EventHandler for WidgetUpdatedEvent (UPDATE)
- [ ] Implement @EventHandler for WidgetPublishedEvent (UPDATE)
- [ ] Use jOOQ DSLContext for all database operations
- [ ] Configure TrackingEventProcessor in application.yml
- [ ] Add error handling (log + metrics)
- [ ] Add projection lag monitoring
- [ ] Write integration test: dispatch command → verify projection updated
- [ ] Measure projection lag (<10s target)
- [ ] Commit: "Add Widget projection event handler with jOOQ"

---

## Test Evidence

- [ ] Integration test: CreateWidgetCommand → widget_view row inserted
- [ ] Integration test: UpdateWidgetCommand → widget_view row updated
- [ ] Integration test: PublishWidgetCommand → published flag set to true
- [ ] Projection lag <10 seconds validated
- [ ] Error metrics emitted on failure
- [ ] TrackingEventProcessor processes events

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Integration test passes
- [ ] Projection lag <10s validated
- [ ] Error handling tested
- [ ] Metrics emitted
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.6 - jOOQ Configuration
**Next Story:** Story 2.8 - Widget Query Handler

---

## References

- PRD: FR003 (jOOQ projections), FR011 (Performance - Event lag <10s)
- Architecture: Section 14 (Projection Schema Design)
- Tech Spec: Section 3 (FR003 - Projections), Section 8.1 (Performance Targets)
