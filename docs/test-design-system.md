# DVMM/EAF System-Level Test Design

**Author:** Murat (Test Architect)
**Date:** 2025-11-25
**Version:** 1.0
**Phase:** 3 - Solutioning (Pre-Implementation)

---

## Executive Summary

This document defines the **system-level testability strategy** for DVMM/EAF before implementation begins. It validates that the proposed architecture supports comprehensive testing across all quality dimensions (functional, security, performance, reliability, maintainability).

**Key Findings:**
- Architecture is **highly testable** due to CQRS/Event Sourcing, Hexagonal patterns, and Ports & Adapters
- **5 testability concerns** identified requiring mitigation (Coroutine context, RLS isolation, Event store growth, Projection sync, FK constraint sync)
- **Test pyramid** optimized for CQRS: Heavy unit/integration, selective E2E
- **NFR testing** requires k6 (performance), Playwright (security/reliability), CI tools (maintainability)

**Gate Status:** ✅ PASS with documented concerns

---

## 1. Architecture Testability Assessment

### 1.1 Controllability Analysis

**Definition:** Ability to put the system into a specific state for testing.

| Component | Controllability | Evidence |
|-----------|-----------------|----------|
| **Commands** | 🟢 High | CQRS separates writes - commands testable without projections |
| **Projections** | 🟢 High | jOOQ read models independently verifiable |
| **Tenant Context** | 🟡 Medium | CoroutineContextElement requires explicit setup in tests |
| **External Systems** | 🟢 High | Ports & Adapters enable test doubles (VMware, Keycloak) |
| **Database State** | 🟢 High | Event Sourcing allows deterministic state replay |

**Controllability Score: 4.2/5**

### 1.2 Observability Analysis

**Definition:** Ability to determine what the system is doing during test execution.

| Component | Observability | Evidence |
|-----------|---------------|----------|
| **Audit Trail** | 🟢 High | Event Sourcing = complete history by design |
| **Logging** | 🟢 High | Structured JSON logging with correlation IDs (NFR-OBS-1/2) |
| **Metrics** | 🟢 High | Prometheus export, Grafana dashboards (NFR-OBS-3/6) |
| **Tracing** | 🟡 Medium | OpenTelemetry-ready but not MVP (NFR-OBS-7) |
| **Health Checks** | 🟢 High | /health, /ready endpoints (NFR-AVAIL-7) |

**Observability Score: 4.4/5**

### 1.3 Reliability Analysis

**Definition:** Consistency of test results across executions.

| Aspect | Reliability | Concern |
|--------|-------------|---------|
| **Determinism** | 🟡 Medium | Coroutine scheduling may introduce non-determinism |
| **Isolation** | 🟡 Medium | RLS requires explicit tenant boundary testing |
| **Idempotency** | 🟢 High | Command handlers designed idempotent |
| **Cleanup** | 🟡 Medium | Event store cleanup between tests needed |

**Reliability Score: 3.8/5**

---

## 2. Architecturally Significant Requirements (ASRs)

ASRs are requirements with **high architectural impact** that drive testing strategy.

### 2.1 ASR Utility Tree

```
                        DVMM/EAF Quality
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
   Performance           Security              Reliability
        │                     │                     │
   ┌────┴────┐          ┌─────┴─────┐         ┌────┴────┐
   │         │          │           │         │         │
 P95<500ms  E2E<15min  RLS        RBAC      Graceful  Event
 (H,H)      (M,H)     Isolation  Enforce   Degrade   Replay
                      (H,H)      (H,H)     (M,H)     (H,M)
```

### 2.2 Critical ASRs (Probability=High, Impact=High)

| ID | ASR | Quality Attribute | Test Approach |
|----|-----|-------------------|---------------|
| **ASR-001** | Multi-tenant RLS isolation | Security | Cross-tenant access tests, RLS policy verification |
| **ASR-002** | RBAC enforcement (global + project) | Security | Permission matrix tests, negative scenarios |
| **ASR-003** | API response P95 < 500ms | Performance | k6 load tests with SLO thresholds |
| **ASR-004** | 80% test coverage | Maintainability | CI coverage gates, mutation testing |
| **ASR-005** | Complete audit trail | Compliance | Event sourcing replay tests |
| **ASR-006** | Crypto-shredding GDPR | Compliance | Key destruction verification, data inaccessibility |

### 2.3 High-Priority ASRs (Probability=Medium/High, Impact=High)

| ID | ASR | Quality Attribute | Test Approach |
|----|-----|-------------------|---------------|
| **ASR-007** | VMware API resilience | Reliability | Contract tests (Pact), failure injection |
| **ASR-008** | E2E suite < 15 minutes | Maintainability | CI timing gates, parallel execution |
| **ASR-009** | Graceful degradation | Reliability | VMware offline simulation tests |
| **ASR-010** | Quota synchronous enforcement | Data Integrity | Race condition tests, optimistic locking |

---

## 3. Test Levels Strategy

### 3.1 Test Pyramid for CQRS/Event Sourcing

```
                    ┌─────────────┐
                    │    E2E      │  10% - Critical user journeys only
                    │  Playwright │
                    ├─────────────┤
                    │ Integration │  30% - API contracts, projections, RLS
                    │   + API     │
                    ├─────────────┤
                    │    Unit     │  60% - Commands, domain logic, event handlers
                    │   + Arch    │
                    └─────────────┘
```

**Rationale:** CQRS enables extensive unit testing of business logic without UI. Event Sourcing makes integration tests highly valuable for projection verification.

### 3.2 Test Level Allocation

| Level | Coverage Target | Scope | Tools |
|-------|-----------------|-------|-------|
| **Unit** | 60% | Command handlers, domain aggregates, event handlers, value objects | JUnit 6, Kotest (optional), MockK |
| **Architecture** | 100% boundaries | Layer dependencies, module boundaries | Konsist |
| **Integration** | 30% | API endpoints, projections, RLS, transactions | TestContainers, Spring Boot Test |
| **Contract** | External APIs | VMware vSphere, Keycloak OIDC | Pact, WireMock |
| **E2E** | 10% | Critical journeys (Request→Approve→Provision) | Playwright |
| **Performance** | SLO/SLA | Load, stress, spike testing | k6 |

### 3.3 Test Level Decision Matrix

| Scenario | Unit | Integration | E2E |
|----------|------|-------------|-----|
| Command validation logic | ✅ Primary | ❌ | ❌ |
| Domain event emission | ✅ Primary | ⚠️ Verify persistence | ❌ |
| Projection updates from events | ⚠️ Handler logic | ✅ Primary | ❌ |
| RLS tenant isolation | ❌ | ✅ Primary | ⚠️ Supplement |
| API request/response contracts | ❌ | ✅ Primary | ❌ |
| User journey (VM request flow) | ❌ | ❌ | ✅ Primary |
| VMware provisioning | ❌ | ✅ Contract test | ⚠️ Smoke |
| Performance under load | ❌ | ❌ | k6 (separate) |

---

## 4. EAF Framework Testing Strategy

### 4.1 Framework vs Product Testing

```
┌─────────────────────────────────────────────────────────────┐
│                    EAF (Framework)                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ eaf-core: Unit + Integration (high coverage)         │  │
│  │ eaf-cqrs-core: Unit + Integration (event handling)   │  │
│  │ eaf-eventsourcing: Integration (PostgreSQL, replay)  │  │
│  │ eaf-tenant: Integration (RLS, context propagation)   │  │
│  │ eaf-auth: Contract (IdP abstraction)                 │  │
│  │ eaf-audit: Unit (interface compliance)               │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↑                                 │
│                   Framework Tests                           │
├─────────────────────────────────────────────────────────────┤
│                    DVMM (Product)                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ dvmm-domain: Unit (aggregates, value objects)        │  │
│  │ dvmm-application: Unit + Integration (use cases)     │  │
│  │ dvmm-infrastructure: Integration (VMware, Keycloak)  │  │
│  │ dvmm-presentation: E2E (user journeys)               │  │
│  └──────────────────────────────────────────────────────┘  │
│                           ↓                                 │
│                    Product Tests                            │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 EAF Module Test Requirements

| Module | Primary Test Level | Key Scenarios |
|--------|-------------------|---------------|
| **eaf-core** | Unit | Result types, error handling, base abstractions |
| **eaf-cqrs-core** | Unit + Integration | Command dispatch, query handling, middleware |
| **eaf-eventsourcing** | Integration | Event persistence, snapshots, replay, projections |
| **eaf-tenant** | Integration | RLS policy enforcement, context propagation, isolation |
| **eaf-auth** | Contract | IdP abstraction, token validation, role mapping |
| **eaf-auth-keycloak** | Integration | Keycloak-specific flows, token refresh |
| **eaf-audit** | Unit | Interface compliance, event capture |
| **eaf-observability** | Integration | Metrics export, logging format, correlation IDs |
| **eaf-notifications** | Integration | Email templates, delivery tracking |
| **eaf-testing** | Dogfood | Test utilities work correctly |

---

## 5. NFR Testing Strategy

### 5.1 Security NFR Testing

| NFR ID | Requirement | Test Type | Tool | Pass Criteria |
|--------|-------------|-----------|------|---------------|
| NFR-SEC-1 | TLS 1.3 encryption | Integration | OpenSSL, curl | All endpoints HTTPS |
| NFR-SEC-3 | RLS tenant isolation | Integration | TestContainers | Cross-tenant query returns 0 |
| NFR-SEC-6 | API rate limiting | Integration | k6 | 429 after 100 req/min |
| NFR-SEC-7 | Input validation | Unit + E2E | JUnit, Playwright | SQL injection blocked |
| NFR-SEC-8 | SQL injection prevention | Integration | SQLi payloads | No data exposure |
| NFR-SEC-9 | XSS prevention | E2E | Playwright | Script not executed |

**Security Test Suite:**
```kotlin
// Example: RLS isolation test
@Test
fun `tenant A cannot access tenant B data`() {
    // Given: Two tenants with separate data
    val tenantA = createTenant("A")
    val tenantB = createTenant("B")
    val vmRequestB = createVmRequest(tenantB)

    // When: Tenant A queries for VM requests
    withTenantContext(tenantA) {
        val results = vmRequestRepository.findAll()

        // Then: Tenant B's data is NOT visible
        assertThat(results).noneMatch { it.id == vmRequestB.id }
    }
}
```

### 5.2 Performance NFR Testing (k6)

| NFR ID | Requirement | k6 Threshold | Test Script |
|--------|-------------|--------------|-------------|
| NFR-PERF-1 | P95 < 500ms | `http_req_duration{p(95)<500}` | `perf/api-load.k6.js` |
| NFR-PERF-2 | P99 < 2s | `http_req_duration{p(99)<2000}` | `perf/api-load.k6.js` |
| NFR-PERF-12 | 100 concurrent, <20% degradation | `http_req_failed{rate<0.01}` | `perf/stress.k6.js` |
| NFR-SCALE-1 | 100+ concurrent users | `vus: 100` | `perf/scale.k6.js` |

**k6 Test Script:**
```javascript
// perf/api-load.k6.js
export const options = {
  stages: [
    { duration: '1m', target: 50 },
    { duration: '3m', target: 100 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  // VM Request submission
  const response = http.post(`${BASE_URL}/api/v1/vm-requests`, {
    projectId: 'test-project',
    size: 'M',
    name: `vm-${__VU}-${__ITER}`,
  });

  check(response, {
    'status is 201': (r) => r.status === 201,
    'has request id': (r) => r.json().id !== undefined,
  });

  sleep(1);
}
```

### 5.3 Reliability NFR Testing

| NFR ID | Requirement | Test Approach | Tool |
|--------|-------------|---------------|------|
| NFR-AVAIL-7 | Health endpoints | Integration | REST Assured |
| NFR-AVAIL-8 | Graceful degradation | E2E | Playwright + network mocking |
| NFR-AVAIL-11 | VMware offline queuing | Integration | WireMock failure injection |

**Graceful Degradation Test:**
```typescript
// tests/reliability/vmware-offline.spec.ts
test('app remains functional when VMware is offline', async ({ page, context }) => {
  // Mock VMware API failure
  await context.route('**/vmware-api/**', route =>
    route.fulfill({ status: 503, body: 'Service Unavailable' })
  );

  await page.goto('/vm-requests/new');

  // User can still submit request (queued)
  await page.fill('[data-testid="vm-name"]', 'test-vm');
  await page.selectOption('[data-testid="size"]', 'M');
  await page.click('[data-testid="submit"]');

  // Request accepted but queued
  await expect(page.getByText('Request queued')).toBeVisible();
  await expect(page.getByText('VMware temporarily unavailable')).toBeVisible();
});
```

### 5.4 Maintainability NFR Testing

| NFR ID | Requirement | Test Tool | CI Integration |
|--------|-------------|-----------|----------------|
| NFR-MAINT-1 | Coverage ≥ 80% | Kover | `./gradlew koverHtmlReport` |
| NFR-MAINT-2 | Mutation score ≥ 70% | PITest | `./gradlew pitest` |
| NFR-MAINT-11 | E2E < 15 min | Playwright | CI timing assertion |
| NFR-MAINT-12 | Contract tests | Pact | `./gradlew pactVerify` |

---

## 6. Testability Concerns & Mitigations

### 6.1 TC-001: Coroutine Tenant Context Propagation

**Risk Level:** 🔴 High (Score: 6)

**Problem:** `TenantContextElement` in Kotlin Coroutines may not propagate correctly across:
- `async` boundaries
- `withContext` switches
- Dispatcher changes (IO ↔ Default)

**Impact:** Tests pass locally (single-threaded), fail in production (multi-threaded).

**Mitigation:**
```kotlin
// eaf-testing: CoroutineTestRule with context verification
class TenantContextTestRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                runBlocking {
                    val expectedTenant = TenantId("test-tenant")
                    withContext(TenantContextElement(expectedTenant)) {
                        // Verify context survives dispatcher switch
                        withContext(Dispatchers.IO) {
                            assertThat(currentTenant()).isEqualTo(expectedTenant)
                        }

                        // Verify context survives async
                        val deferred = async(Dispatchers.Default) {
                            currentTenant()
                        }
                        assertThat(deferred.await()).isEqualTo(expectedTenant)

                        base.evaluate()
                    }
                }
            }
        }
    }
}
```

**Test Requirement:** Every async operation MUST have context propagation test.

### 6.2 TC-002: PostgreSQL RLS Test Isolation

**Risk Level:** 🔴 High (Score: 6)

**Problem:** Tests may inadvertently bypass RLS by:
- Using superuser connection
- Missing `SET app.current_tenant` before queries
- Parallel tests with shared connection pool

**Impact:** Security vulnerability - cross-tenant data leakage.

**Mitigation:**
```kotlin
// eaf-testing: RLS-aware test database
@TestConfiguration
class RlsTestDatabaseConfig {
    @Bean
    fun dataSource(): DataSource {
        return object : DelegatingDataSource(actualDataSource) {
            override fun getConnection(): Connection {
                return super.getConnection().also { conn ->
                    // Never use superuser - always set tenant
                    val tenant = TestTenantContext.current()
                        ?: throw IllegalStateException("No tenant context in test")
                    conn.createStatement().execute(
                        "SET app.current_tenant = '${tenant.id}'"
                    )
                }
            }
        }
    }
}

// Negative test: Verify isolation
@Test
fun `RLS prevents cross-tenant access`() {
    // Given: VM request in tenant B
    val requestB = withTenant("B") {
        vmRequestRepository.save(VmRequest(...))
    }

    // When: Querying as tenant A
    withTenant("A") {
        val found = vmRequestRepository.findById(requestB.id)

        // Then: Not found (RLS blocks)
        assertThat(found).isEmpty()
    }

    // And: Direct SQL also blocked
    withTenant("A") {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM vm_requests WHERE id = ?",
            Int::class.java,
            requestB.id
        )
        assertThat(count).isZero()
    }
}
```

**Test Requirement:** Every repository test MUST include cross-tenant negative scenario.

### 6.3 TC-003: Event Store Growth in Tests

**Risk Level:** 🟡 Medium (Score: 4)

**Problem:** Long-running test suites accumulate events:
- Snapshot creation slows down
- Event replay takes longer
- Test suite exceeds 15-minute target

**Impact:** CI feedback loop degrades over time.

**Mitigation:**
```kotlin
// eaf-testing: Per-test event store isolation
@ExtendWith(EventStoreIsolationExtension::class)
class VmRequestAggregateTest {
    @Test
    fun `command creates events`() {
        // Events isolated to this test
        // Auto-cleaned after test
    }
}

class EventStoreIsolationExtension : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        // Create isolated schema/table for this test
        eventStoreManager.createIsolatedStore(context.uniqueId)
    }

    override fun afterEach(context: ExtensionContext) {
        // Drop isolated store - events don't accumulate
        eventStoreManager.dropIsolatedStore(context.uniqueId)
    }
}
```

**Test Requirement:** Event-heavy tests MUST use isolated stores.

### 6.4 TC-004: jOOQ Projection Synchronization

**Risk Level:** 🟡 Medium (Score: 4)

**Problem:** Read models (jOOQ projections) may diverge from event handlers:
- Handler logic changes without projection update
- Eventual consistency window in tests
- Missing events during projection rebuild

**Impact:** UI shows stale/incorrect data.

**Mitigation:**
```kotlin
// Integration test: Verify projection sync
@Test
fun `projection updates after command`() {
    // Given: New VM request
    val command = CreateVmRequestCommand(...)

    // When: Command processed
    commandBus.send(command)

    // Then: Projection updated (with retry for eventual consistency)
    await().atMost(Duration.ofSeconds(5)).untilAsserted {
        val projected = vmRequestReadRepository.findById(command.requestId)
        assertThat(projected).isPresent()
        assertThat(projected.get().status).isEqualTo("PENDING")
    }
}

// Rebuild test: Verify projection from events
@Test
fun `projection rebuild produces consistent state`() {
    // Given: Events in store
    val events = listOf(
        VmRequestCreated(...),
        VmRequestApproved(...),
        VmProvisioned(...)
    )
    eventStore.save(events)

    // When: Rebuild projection
    projectionRebuilder.rebuild("vm_requests")

    // Then: Final state matches event sequence
    val projected = vmRequestReadRepository.findById(events[0].aggregateId)
    assertThat(projected.get().status).isEqualTo("RUNNING")
}
```

**Test Requirement:** Every projection MUST have rebuild verification test.

### 6.5 TC-005: FK Constraint Test Synchronization

**Risk Level:** 🟡 Medium (Score: 4)

**Problem:** When adding FK constraints to projection tables, integration tests that directly insert child records will fail:
- Test helpers insert child records without parent records
- FK constraint violations cause `IntegrityConstraintViolationException`
- Cleanup with simple TRUNCATE fails when child tables exist

**Impact:** Tests fail after adding FK constraints; requires coordinated updates to test helpers.

**Mitigation:**
```kotlin
// ✅ CORRECT - Test helper creates parent record first
private fun insertTestTimelineEvent(requestId: UUID, tenantId: TenantId, ...) {
    // Create parent record first (idempotent)
    insertParentRequest(id = requestId, tenantId = tenantId)

    // Then insert child record
    postgres.createConnection("").use { conn ->
        conn.prepareStatement("""
            INSERT INTO "REQUEST_TIMELINE_EVENTS" (...)
            VALUES (?, ?, ...)
        """).use { stmt -> ... }
    }
}

// ✅ CORRECT - Parent insert is idempotent
private fun insertParentRequest(id: UUID, tenantId: TenantId) {
    postgres.createConnection("").use { conn ->
        conn.prepareStatement("""
            INSERT INTO "VM_REQUESTS_PROJECTION" (...)
            VALUES (?, ?, ...)
            ON CONFLICT ("ID") DO NOTHING
        """).use { stmt -> ... }
    }
}

// ✅ CORRECT - Cleanup uses CASCADE for FK constraints
@AfterEach
fun cleanup() {
    superuserDsl.execute("""TRUNCATE TABLE "CHILD_TABLE" """)
    superuserDsl.execute("""TRUNCATE TABLE "PARENT_TABLE" CASCADE""")
}
```

**Test Requirement:** When adding FK constraints, update ALL test helpers that insert child records to create parent records first using `ON CONFLICT DO NOTHING` for idempotency.

---

## 7. Test Environment Strategy

### 7.1 Environment Tiers

| Environment | Purpose | Data | External Systems |
|-------------|---------|------|------------------|
| **Unit** | Isolated logic | In-memory | Mocked |
| **Integration** | Component interaction | TestContainers (PostgreSQL) | WireMock/Pact |
| **E2E Local** | Full stack local | Docker Compose | Docker (Keycloak) |
| **E2E CI** | Pipeline validation | Ephemeral | Docker (all) |
| **Performance** | Load testing | Synthetic | Staging replicas |

### 7.2 TestContainers Configuration

```kotlin
// eaf-testing: Shared containers
object TestContainers {
    val postgres = PostgreSQLContainer("postgres:15")
        .withDatabaseName("eaf_test")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("init-rls.sql") // RLS policies

    val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
        .withRealmImportFile("test-realm.json")
}
```

### 7.3 VMware vCenter Simulator (VCSIM)

**Critical Resource:** For realistic VMware API testing, use the official **vCenter Server Simulator (VCSIM)** maintained by VMware.

**Repository:** https://github.com/vmware/govmomi/blob/main/vcsim/README.md

**Why VCSIM over WireMock:**
- **Realistic API responses** - Same SOAP API as real vCenter
- **Scalable inventories** - Test with 100 hosts, 200 VMs without hardware
- **Minimal resources** - ~2GB RAM for 10 simulated vCenters
- **Official VMware support** - Maintained by VMware govmomi team

**Setup Reference:** https://enterpriseadmins.org/blog/virtualization/scaling-your-tests-how-to-set-up-a-vcenter-server-simulator/

```yaml
# docker-compose.vcsim.yml
services:
  vcsim:
    image: vmware/vcsim:latest
    ports:
      - "8989:8989"
    environment:
      # Simulate a realistic inventory
      VCSIM_CLUSTER: 2
      VCSIM_HOST: 4        # 4 hosts per cluster
      VCSIM_VM: 20         # 20 VMs per host
      VCSIM_POOL: 2
      VCSIM_FOLDER: 3
```

**Test Scenarios Enabled:**
| Scenario | VCSIM Config | Purpose |
|----------|--------------|---------|
| **Basic provisioning** | 1 cluster, 2 hosts, 10 VMs | Happy path testing |
| **Scale testing** | 4 clusters, 100 hosts, 500 VMs | Performance validation |
| **Failure injection** | Custom fault responses | Resilience testing |
| **Multi-datacenter** | 3 datacenters | Enterprise scenarios |

**Limitation:** VCSIM lacks UI and some advanced API methods. Use for API integration tests, not E2E workflows.

### 7.4 Docker Compose (Local E2E)

```yaml
# docker-compose.test.yml
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: dvmm_test
    volumes:
      - ./init-rls.sql:/docker-entrypoint-initdb.d/init.sql

  keycloak:
    image: quay.io/keycloak/keycloak:26.0.0
    command: start-dev --import-realm
    volumes:
      - ./test-realm.json:/opt/keycloak/data/import/realm.json

  vcsim:
    image: vmware/vcsim:latest
    ports:
      - "8989:8989"
    environment:
      VCSIM_CLUSTER: 2
      VCSIM_HOST: 4
      VCSIM_VM: 20
```

---

## 8. Test Data Strategy

### 8.1 Factory Pattern

```kotlin
// eaf-testing: Composable factories
object TestFactories {
    fun tenant(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Tenant"
    ) = Tenant(TenantId(id), name)

    fun user(
        id: String = UUID.randomUUID().toString(),
        tenantId: String = "default-tenant",
        globalRole: GlobalRole = GlobalRole.USER,
        projectRoles: Map<ProjectId, ProjectRole> = emptyMap()
    ) = User(UserId(id), TenantId(tenantId), globalRole, projectRoles)

    fun vmRequest(
        id: String = UUID.randomUUID().toString(),
        tenantId: String = "default-tenant",
        projectId: String = "default-project",
        size: VmSize = VmSize.M,
        status: VmRequestStatus = VmRequestStatus.PENDING
    ) = VmRequest(...)
}
```

### 8.2 Data Isolation Rules

1. **Never share data between tests** - Each test creates its own
2. **Use unique identifiers** - UUIDs prevent collisions
3. **Clean up after each test** - Fixtures with teardown
4. **Tenant-scoped data** - Every entity has tenant context

---

## 9. CI/CD Integration

### 9.1 Pipeline Stages

```yaml
# .github/workflows/test.yml
test:
  jobs:
    unit:
      runs-on: ubuntu-latest
      steps:
        - run: ./gradlew test
        - run: ./gradlew koverHtmlReport
        - run: ./gradlew koverVerify  # Enforces 80% coverage threshold

    architecture:
      runs-on: ubuntu-latest
      steps:
        - run: ./gradlew konsistTest

    integration:
      runs-on: ubuntu-latest
      services:
        postgres:
          image: postgres:15
      steps:
        - run: ./gradlew integrationTest

    contract:
      runs-on: ubuntu-latest
      steps:
        - run: ./gradlew pactTest
        - run: ./gradlew pactPublish

    e2e:
      runs-on: ubuntu-latest
      steps:
        - run: docker-compose -f docker-compose.test.yml up -d
        - run: npx playwright test
        - name: Check E2E timing
          run: |
            DURATION=$(cat playwright-report/results.json | jq '.stats.duration')
            if [[ $DURATION -gt 900000 ]]; then exit 1; fi  # 15 min

    mutation:
      runs-on: ubuntu-latest
      steps:
        - run: ./gradlew pitest
        - name: Check mutation score
          run: |
            SCORE=$(cat build/reports/pitest/index.html | grep -o 'Mutation Score[^%]*%' | head -1)
            if [[ ${SCORE%\%} -lt 70 ]]; then exit 1; fi
```

### 9.2 Quality Gates

| Gate | Threshold | Enforcement |
|------|-----------|-------------|
| **Unit Tests** | 100% pass | Merge blocked |
| **Coverage** | ≥ 80% | Merge blocked |
| **Mutation Score** | ≥ 70% | Merge blocked |
| **Architecture Tests** | 100% pass | Merge blocked |
| **Integration Tests** | 100% pass | Merge blocked |
| **Contract Tests** | 100% pass | Merge blocked |
| **E2E Tests** | 100% pass | Merge blocked |
| **E2E Duration** | < 15 min | Warning |
| **Security Scan** | 0 critical | Merge blocked |

---

## 10. Test ID Convention

### 10.1 Format

`{MODULE}-{TYPE}-{CATEGORY}-{SEQ}`

### 10.2 Examples

| ID | Description |
|----|-------------|
| `EAF-UNIT-CQRS-001` | eaf-cqrs-core unit test #1 |
| `EAF-INT-TENANT-003` | eaf-tenant integration test #3 |
| `EAF-ARCH-LAYER-001` | Architecture layer boundary test |
| `DVMM-UNIT-VM-002` | DVMM VM domain unit test #2 |
| `DVMM-E2E-JOURNEY-001` | VM request user journey E2E |
| `DVMM-PERF-LOAD-001` | k6 load test for API |
| `DVMM-SEC-RLS-001` | RLS security test |

---

## 11. Appendix: Test Checklist

### 11.1 Pre-Implementation Checklist

- [x] Architecture testability assessed (Controllability: 4.2, Observability: 4.4, Reliability: 3.8)
- [x] ASRs identified and prioritized (6 critical, 4 high-priority)
- [x] Test levels defined (60% unit, 30% integration, 10% E2E)
- [x] NFR test approach documented (Security, Performance, Reliability, Maintainability)
- [x] Testability concerns flagged (5 concerns with mitigations)
- [x] Test environment strategy defined (TestContainers, Docker Compose)
- [x] CI/CD integration planned (Quality gates, timing constraints)
- [x] Test data strategy defined (Factories, isolation rules)

### 11.2 Risk Register

| Risk ID | Description | Score | Owner | Mitigation | Status |
|---------|-------------|-------|-------|------------|--------|
| TC-001 | Coroutine context propagation | 6 | EAF Team | Test rule + context verification | Open |
| TC-002 | RLS test isolation | 6 | EAF Team | RLS-aware test DB config | Open |
| TC-003 | Event store growth | 4 | EAF Team | Per-test isolation extension | Open |
| TC-004 | jOOQ projection sync | 4 | DVMM Team | Rebuild verification tests | Open |
| TC-005 | FK constraint test sync | 4 | DVMM Team | Parent record helpers + CASCADE cleanup | Mitigated |

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-25 | Murat (TEA) | Initial System-Level Test Design |
| 1.1 | 2025-12-02 | Claude | Added TC-005: FK Constraint Test Synchronization |

---

*This document establishes the testing foundation for DVMM/EAF. Implementation-phase epic-level test designs will build upon these system-level decisions.*
