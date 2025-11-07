# EAF v1.0 Performance Baselines

**Measured:** 2025-11-07
**Environment:** Local Development (MacBook Pro M2, 16GB RAM, macOS 15.0)
**Test Tool:** Gatling 3.14.0
**Story:** 2.13 - Performance Baseline and Monitoring
**Epic:** 2 - Walking Skeleton - CQRS/Event Sourcing Core

---

## Performance Targets (FR011, NFR001)

| Metric | Target | Status |
|--------|--------|--------|
| API p95 Latency | <200ms | ✅ TBD |
| Event Processing Lag | <10s | ✅ TBD |
| Throughput | 1000 req/s sustained | ✅ TBD |
| Success Rate | >99% | ✅ TBD |

---

## API Performance

### Endpoint Latency Measurements

| Endpoint | p50 | p95 | p99 | Target | Status |
|----------|-----|-----|-----|--------|--------|
| POST /api/v1/widgets | TBD | TBD | TBD | <200ms | ⏳ |
| GET /api/v1/widgets/:id | TBD | TBD | TBD | <200ms | ⏳ |
| GET /api/v1/widgets (list) | TBD | TBD | TBD | <200ms | ⏳ |
| PUT /api/v1/widgets/:id | TBD | TBD | TBD | <200ms | ⏳ |

**Test Methodology:**
- Load test with Gatling 3.14.0
- Scenario: Ramp from 10 to 100 concurrent users over 30 seconds
- Sustained load duration: 5 minutes
- Request pattern: Create → Pause 1s → List

---

## Event Processing Performance

### Event Store Metrics

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Event persistence latency | TBD | <50ms avg | ⏳ |
| Projection lag (eventual consistency) | TBD | <10s | ⏳ |
| Event replay (1000 events) | TBD | <1s | ⏳ |

**Measurement Approach:**
- Prometheus metrics: `projection_widget_lag_seconds`
- Event processor tracking token timestamp comparison
- Grafana dashboard visualization

---

## Snapshot Performance (Story 2.4 Deferred Tests)

### Snapshot Creation

| Metric | Value | Specification | Status |
|--------|-------|---------------|--------|
| Snapshot threshold | 100 events | Every 100 events | ✅ Configured |
| Snapshots created (250 events) | 2 snapshots | At seq 100, 200 | ⏳ TBD |
| Snapshot serialization format | Jackson JSON | Configured in Story 2.4 | ✅ |

### Aggregate Loading Performance

| Scenario | Baseline (No Snapshots) | With Snapshots | Improvement | Target |
|----------|------------------------|----------------|-------------|--------|
| Load 1000 events | 2-5s (estimated) | TBD | TBD | >10x |
| Load 250 events | 500ms-1s (estimated) | TBD | TBD | >5x |

**Test Methodology:**
- Integration test: Create aggregate with 1000 events
- Measure event store read time
- Compare with baseline (full replay without snapshots)
- Document improvement factor

---

## Throughput

### Sustained Load Results

| Scenario | RPS (Requests/Second) | Target | Status |
|----------|----------------------|--------|--------|
| Widget creation (POST) | TBD | 1000 rps | ⏳ |
| Widget reads (GET single) | TBD | 1000 rps | ⏳ |
| Widget list (GET paginated) | TBD | 1000 rps | ⏳ |
| Mixed CRUD | TBD | 1000 rps | ⏳ |

**Load Test Configuration:**
- 100 concurrent users
- 30-second ramp-up period
- 5-minute sustained load
- Success rate target: >99%

---

## Load Test Results

### Test Execution Summary

**Test Run:** TBD
**Duration:** TBD
**Total Requests:** TBD
**Success Rate:** TBD
**Error Rate:** TBD

**Assertions:**
- [ ] API p95 latency <200ms
- [ ] Success rate >99%
- [ ] Zero failed requests
- [ ] Event processing lag <10s
- [ ] 100 concurrent users handled
- [ ] 1000 requests/second sustained

---

## Prometheus Metrics

### Available Metrics

**HTTP Request Metrics (Micrometer):**
- `http_server_requests_seconds` - Request duration histogram
  - Tags: `method`, `uri`, `status`, `exception`
- `http_server_requests_active` - Active request count

**Widget Projection Metrics (Custom - Story 2.7):**
- `projection_widget_created_total` - Counter of created widgets
- `projection_widget_updated_total` - Counter of updated widgets
- `projection_widget_published_total` - Counter of published widgets
- `projection_widget_lag_seconds` - Projection lag gauge

**JVM Metrics:**
- `jvm_memory_used_bytes` - Memory usage
- `jvm_gc_pause_seconds` - GC pause time
- `process_cpu_usage` - CPU utilization

**Actuator Endpoint:**
- `/actuator/prometheus` - Prometheus scrape endpoint

---

## Environment Specifications

### Hardware

**Development Machine:**
- CPU: Apple M2 (8-core)
- RAM: 16GB
- Storage: SSD
- OS: macOS 15.0

### Software Stack

**Runtime:**
- JVM: OpenJDK 21 (Temurin)
- Kotlin: 2.2.21
- Spring Boot: 3.5.7

**Database:**
- PostgreSQL: 16.10 (Docker)
- Connection Pool: HikariCP (max: 10, min: 5)

**Event Store:**
- Axon Framework: 4.12.1
- Snapshot Threshold: 100 events
- Partitioning: Monthly (domain_event_entry)
- Indexes: BRIN on timestamp, aggregate_identifier

---

## Performance Regression Testing

### Nightly CI/CD Integration

**Pipeline:** `.github/workflows/nightly.yml`

**Test Schedule:**
- Daily at 02:00 UTC
- On main branch only
- Fails if targets not met

**Assertions:**
- API p95 <200ms (fails build if exceeded)
- Event lag <10s (fails build if exceeded)
- Success rate >99% (fails build if below)

**Reporting:**
- Gatling HTML report archived as build artifact
- Performance metrics logged to GitHub Actions summary
- Slack notification on performance regression

---

## Baseline Validation

### Acceptance Criteria Checklist

From Story 2.13:

- [ ] **AC1:** Performance test suite created with Gatling ✅
- [ ] **AC2:** Load test scenarios: 100 concurrent users, 1000 requests/second
- [ ] **AC3:** Baseline measurements documented
- [ ] **AC4:** Prometheus metrics configured for Widget endpoints
- [ ] **AC5:** Performance meets targets (API p95 <200ms, event lag <10s)
- [ ] **AC6:** Performance regression test added to nightly CI/CD
- [ ] **AC7:** Performance baseline documented in this file
- [ ] **AC8:** Snapshot functional tests (250+ events, 2 snapshots, >10x improvement)

---

## References

- **PRD:** FR011 (Fast Feedback and Performance Monitoring), NFR001 (Performance)
- **Architecture:** Section 10 (Performance KPIs)
- **Tech Spec:** Epic 2 Section 8 (Performance Targets)
- **Gatling Docs:** https://gatling.io/docs/
- **Prometheus Metrics:** https://micrometer.io/docs/registry/prometheus

---

## Notes

**Measurement Methodology:**
- All tests run on local development environment
- PostgreSQL via Docker Compose (not Testcontainers for load tests)
- Measurements represent single-node performance
- Production performance may vary based on infrastructure

**Known Limitations:**
- Snapshot threshold (100 events) may be tuned in production
- Load test scenarios simplified for MVP validation
- Multi-tenant performance testing deferred to Epic 4
- Distributed tracing metrics deferred to Epic 5

**Future Work:**
- Epic 4: Multi-tenant performance isolation
- Epic 5: Distributed tracing with OpenTelemetry
- Epic 8: Mutation testing performance impact
- Epic 10: Production-scale performance validation
