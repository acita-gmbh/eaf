# Story 5.4: Prometheus Metrics with Micrometer

**Epic:** Epic 5 - Observability & Monitoring
**Status:** TODO
**Related Requirements:** FR005, FR011 (Performance Monitoring)

---

## User Story

As a framework developer,
I want Prometheus metrics collection for all system components,
So that I can monitor performance, resource usage, and business metrics.

---

## Acceptance Criteria

1. ✅ Micrometer 1.15.5 dependency added to framework/observability
2. ✅ MicrometerConfiguration.kt configures Prometheus registry
3. ✅ Spring Boot Actuator endpoint /actuator/prometheus exposed
4. ✅ Default metrics enabled: JVM (memory, GC, threads), HTTP (request duration, status codes), Axon (command/event processing)
5. ✅ All metrics tagged with: service_name, tenant_id (where applicable)
6. ✅ Custom metrics API provided (MeterRegistry injectable)
7. ✅ Integration test validates metrics endpoint returns Prometheus format
8. ✅ Metrics scraped successfully by Prometheus container

---

## Prerequisites

**Story 5.1** - Structured JSON Logging

---

## Technical Notes

### Projection Lag Monitoring Pattern (from Story 2.7)

Story 2.7 implemented basic projection metrics (`projection.widget.created`, `projection.widget.updated`, `projection.widget.errors`) but deferred **real-time lag monitoring** to this story.

**Projection Lag Monitor Pattern:**

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

    private fun calculateLag(): Double {
        // Implementation: Query token_entry for last processed position
        // Compare with latest event timestamp in domain_event_entry
        // Return lag in seconds
    }
}
```

**Acceptance Criteria Coverage:**
- AC4: Default metrics enabled → Add projection lag gauges
- AC6: Custom metrics API → Example implementation above

**Implementation Checklist Item:**
- [ ] Create ProjectionLagMonitor component for each projection handler
- [ ] Configure @Scheduled lag measurement (5-10s interval)
- [ ] Emit gauge: `projection.{name}.lag_seconds`
- [ ] Test lag metric appears in /actuator/prometheus

**Context:** Story 2.7 validated <10s lag requirement in integration tests. This story adds **production-ready continuous monitoring** via scheduled metrics.

---

## References

- PRD: FR005, FR011, FR027 (Business Metrics API)
- Architecture: Section 17 (Performance Monitoring)
- Tech Spec: Section 2.3 (Micrometer 1.15.5), Section 3 (FR005, FR027)
