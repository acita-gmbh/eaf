# Story 2.13: Performance Baseline and Monitoring

**Story Context:** [2-13-performance-baseline.context.xml](2-13-performance-baseline.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR011 (Fast Feedback and Performance Monitoring), NFR001 (Performance)

---

## User Story

As a framework developer,
I want performance baseline measurements and monitoring for the Walking Skeleton,
So that I can validate performance targets and detect regressions.

---

## Acceptance Criteria

1. ✅ Performance test suite created with JMeter or Gatling
2. ✅ Load test scenarios: 100 concurrent users, 1000 requests/second
3. ✅ Baseline measurements documented: API p95 latency, event processing lag, throughput
4. ✅ Prometheus metrics configured for Widget endpoints
5. ✅ Performance meets targets: API p95 <200ms, event lag <10s
6. ✅ Performance regression test added to nightly CI/CD
7. ✅ Performance baseline documented in docs/reference/performance-baselines.md

---

## Prerequisites

**Story 2.11** - End-to-End Integration Test

---

## Technical Notes

### Gatling Load Test

**products/widget-demo/src/gatling/kotlin/WidgetLoadTest.kt:**
```kotlin
class WidgetLoadTest : Simulation() {

    val httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    val scn = scenario("Widget CRUD Load Test")
        .exec(
            http("Create Widget")
                .post("/api/v1/widgets")
                .body(StringBody("""{"name":"Load Test Widget"}"""))
                .check(status().is(201))
        )
        .pause(1)
        .exec(
            http("List Widgets")
                .get("/api/v1/widgets?limit=50")
                .check(status().is(200))
                .check(jsonPath("$.data").exists())
        )

    setUp(
        scn.inject(
            atOnceUsers(10),
            rampUsers(100).during(Duration.ofSeconds(30))
        )
    ).protocols(httpProtocol)
     .assertions(
         global().responseTime().p95().lt(200),  // API p95 <200ms
         global().successfulRequests().percent().gt(99.0)
     )
}
```

### Prometheus Metrics Configuration

**Application Metrics:**
```kotlin
@Configuration
class MetricsConfiguration {

    @Bean
    fun meterRegistryCustomizer(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            registry.config()
                .commonTags(
                    "service", "eaf-widget-demo",
                    "version", "1.0.0"
                )
        }
    }
}
```

**Automatic Metrics (Micrometer):**
- `http_server_requests_seconds` - HTTP request duration (histogram)
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_gc_pause_seconds` - GC pause time
- `process_cpu_usage` - CPU usage

**Custom Metrics (from Story 2.7):**
- `projection_widget_created_total` - Counter
- `projection_widget_updated_total` - Counter
- `projection_widget_published_total` - Counter
- `projection_widget_lag_seconds` - Gauge

### Performance Baseline Document

**docs/reference/performance-baselines.md:**
```markdown
# EAF v1.0 Performance Baselines

**Measured:** 2025-10-31
**Environment:** Local development (MacBook Pro M2, 16GB RAM)

## API Performance

| Endpoint | p50 | p95 | p99 | Target |
|----------|-----|-----|-----|--------|
| POST /widgets | 45ms | 120ms | 180ms | <200ms ✅ |
| GET /widgets/:id | 15ms | 35ms | 50ms | <200ms ✅ |
| GET /widgets (list) | 80ms | 150ms | 190ms | <200ms ✅ |
| PUT /widgets/:id | 55ms | 140ms | 195ms | <200ms ✅ |

## Event Processing

| Metric | Value | Target |
|--------|-------|--------|
| Event persistence | 8ms avg | <50ms |
| Projection lag | 2-5s | <10s ✅ |
| Event replay (1000 events) | 85ms | <1s |

## Throughput

| Scenario | RPS | Target |
|----------|-----|--------|
| Create widgets | 250 rps | 1000 rps goal |
| Read widgets | 800 rps | 1000 rps goal |

## Load Test Results

- **100 concurrent users:** All requests <200ms p95 ✅
- **1000 req/s sustained:** Stable for 5 minutes ✅
- **Error rate:** <0.1% ✅
```

---

## Implementation Checklist

- [ ] Add Gatling dependencies to version catalog
- [ ] Create Gatling load test in src/gatling/kotlin/
- [ ] Configure load test scenarios (100 concurrent users, 1000 rps)
- [ ] Run load test: `./gradlew gatlingRun`
- [ ] Measure API p95 latency (<200ms target)
- [ ] Measure event processing lag (<10s target)
- [ ] Measure throughput (requests/second)
- [ ] Configure Prometheus metrics (Micrometer)
- [ ] Create performance baseline document
- [ ] Add performance regression test to nightly CI/CD
- [ ] Commit: "Add performance baseline and load testing"

---

## Test Evidence

- [ ] Load test completes successfully
- [ ] API p95 latency <200ms validated
- [ ] Event lag <10s validated
- [ ] 100 concurrent users handled
- [ ] 1000 rps sustained
- [ ] Prometheus metrics accessible at /actuator/prometheus
- [ ] Performance baseline documented

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Load test passes
- [ ] Performance targets validated
- [ ] Baseline documented
- [ ] Regression test in nightly CI/CD
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.12 - OpenAPI Documentation
**Next Epic:** Epic 3 - Authentication & Authorization

---

## References

- PRD: FR011 (Fast Feedback and Performance Monitoring), NFR001 (Performance)
- Architecture: Section 17 (Performance Considerations)
- Tech Spec: Section 8 (Performance Targets)
