# Epic Technical Specification: Multi-Tenancy & Data Isolation

Date: 2025-11-16
Author: Wall-E (Generated via BMM Epic-Tech-Context Workflow)
Epic ID: 4
Status: Draft

---

## Overview

Epic 4 implements comprehensive multi-tenancy with defense-in-depth isolation through three layers: Layer 1 extracts tenant context from JWT claims into ThreadLocal storage, Layer 2 validates tenant context in command handlers, and Layer 3 enforces PostgreSQL Row-Level Security policies at the database level. This epic includes ThreadLocal context propagation to async Axon event processors, cross-tenant leak detection and monitoring, per-tenant resource quotas with automatic throttling, and LitmusKt concurrency testing to prevent race conditions in tenant context management. The 3-layer approach provides fail-closed security where missing tenant context immediately rejects requests, ensuring absolute data isolation between tenants.

**Prerequisites:**
- Epic 3 complete (10-layer JWT validation with tenant_id claim)
- Story 4.0 complete (Epic 4 preparation with technical research)

**Value Proposition:**
- Absolute tenant data isolation (zero cross-tenant access)
- Fail-closed security design (missing context = rejection)
- Defense-in-depth (3 independent validation layers)
- Production-validated patterns from Story 4.0 research

## Objectives and Scope

### In Scope
- Layer 1: TenantContextFilter extracts tenant_id from JWT → ThreadLocal
- Layer 2: Axon interceptors validate tenant context in command/event handlers
- Layer 3: PostgreSQL RLS policies enforce database-level isolation
- ThreadLocal propagation to async event processors via metadata enrichment
- Cross-tenant leak detection and security audit logging
- Per-tenant resource quotas and automatic throttling
- LitmusKt concurrency testing for race condition detection

### Out of Scope
- Multi-database multi-tenancy (separate DB per tenant) - not required for MVP
- Tenant provisioning UI/API - deferred to Epic 10 (reference application)
- Tenant migration tools - post-MVP feature
- White-label customization per tenant - not in MVP scope

## System Architecture Alignment

Epic 4 aligns with **Architecture Decision #2: 3-Layer Multi-Tenancy Isolation** (docs/architecture.md lines 3318-3783).

**Architectural Components:**
- framework/multi-tenancy module (NEW - Story 4.1)
- framework/security (Epic 3 JWT validation - dependency)
- framework/cqrs (Axon interceptors - Story 4.3, 4.5)
- framework/persistence (jOOQ RLS integration - Story 4.4)
- framework/observability (Metrics - Story 4.8)
- products/widget-demo (Multi-tenant demo - Story 4.6)

**Patterns from Story 4.0 Research:**
- Axon ThreadLocal Propagation via CorrelationDataProvider and EventInterceptor
- PostgreSQL RLS with jOOQ ExecuteListener and SET LOCAL
- LitmusKt concurrency testing (or JCStress fallback)

## Detailed Design

### Services and Modules

| Module | Responsibility | Key Components | Dependencies |
|--------|---------------|----------------|--------------|
| **framework/multi-tenancy** | Tenant context management, validation, monitoring | TenantContext, TenantId, TenantContextFilter, TenantValidationInterceptor, TenantContextEventInterceptor | framework/core, framework/security (JWT), framework/cqrs (Axon) |
| **framework/persistence** | PostgreSQL RLS integration | TenantRLSExecuteListener, RLS Flyway migrations | framework/multi-tenancy, jOOQ 3.20.8 |
| **framework/observability** | Tenant metrics and leak detection | TenantMetricsCollector, TenantLeakDetector | framework/multi-tenancy, Micrometer |
| **products/widget-demo** | Multi-tenant reference implementation | Widget aggregate with tenant validation, multi-tenant integration tests | framework/multi-tenancy, framework/cqrs |

### Data Models and Contracts

**TenantId Value Object:**
```kotlin
data class TenantId(val value: String) {
    init {
        require(value.isNotBlank()) { "Tenant ID cannot be blank" }
        require(value.matches(Regex("^[a-z0-9-]{1,64}$"))) {
            "Invalid tenant ID format"
        }
    }
}
```

**TenantAwareCommand Interface:**
```kotlin
interface TenantAwareCommand {
    val tenantId: String
}

// Example usage
data class CreateWidgetCommand(
    val widgetId: WidgetId,
    override val tenantId: String,
    val name: String
) : TenantAwareCommand
```

**Widget Projection Table (Enhanced for Multi-Tenancy):**
```sql
CREATE TABLE widget_view (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,  -- NEW: Tenant isolation
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- RLS Policy (Story 4.4)
ALTER TABLE widget_view ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_widget_view ON widget_view
  FOR ALL
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- Performance index
CREATE INDEX idx_widget_view_tenant_id ON widget_view(tenant_id);
```

### APIs and Interfaces

**TenantContext API (Story 4.1):**

```kotlin
object TenantContext {
    private val context = ThreadLocal<TenantId>()

    // Fail-closed (throws if missing)
    fun getCurrentTenantId(): String {
        return context.get()?.value
            ?: throw TenantContextMissingException("No tenant context available")
    }

    // Nullable (returns null if missing)
    fun current(): String? = context.get()?.value

    // Stack operations
    fun setCurrentTenantId(tenantId: String) {
        context.set(TenantId(tenantId))
    }

    fun clearCurrentTenant() {
        context.remove()
    }
}
```

**TenantContextFilter API (Story 4.2):**

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)  // After JWT validation
class TenantContextFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val jwt = SecurityContextHolder.getContext()
            .authentication as? JwtAuthenticationToken
            ?: throw UnauthorizedException("No JWT present")

        val tenantId = jwt.token.getClaim<String>("tenant_id")
            ?: throw BadRequestException("Missing tenant_id in JWT")

        TenantContext.setCurrentTenantId(tenantId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clearCurrentTenant()
        }
    }
}
```

**Axon Interceptor APIs (Stories 4.3, 4.5):**

```kotlin
// Story 4.3: Command validation interceptor
@Component
class TenantValidationInterceptor : MessageHandlerInterceptor<CommandMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any? {
        val command = unitOfWork.message.payload

        if (command is TenantAwareCommand) {
            val contextTenant = TenantContext.getCurrentTenantId()
            require(command.tenantId == contextTenant) {
                "Access denied: tenant context mismatch"
            }
        }

        return interceptorChain.proceed()
    }
}

// Story 4.5: Event handler context restoration
@Component
class TenantContextEventInterceptor : MessageHandlerInterceptor<EventMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any? {
        val tenantId = unitOfWork.message.metaData["tenant_id"] as? String

        return if (tenantId != null) {
            TenantContext.setCurrentTenantId(tenantId)
            try {
                interceptorChain.proceed()
            } finally {
                TenantContext.clearCurrentTenant()
            }
        } else {
            interceptorChain.proceed()  // System events without tenant
        }
    }
}
```

### Workflows and Sequencing

**Multi-Tenant Request Flow:**

```
1. HTTP Request → TLS Termination
2. Spring Security Filter Chain
   ├─ JWT Validation (Epic 3, 10 layers)
   └─ TenantContextFilter (Story 4.2)
       ├─ Extract tenant_id from JWT claim
       ├─ TenantContext.set(tenantId)
       └─ Set PostgreSQL session variable
3. Controller → Command Gateway
4. Axon Command Bus
   └─ TenantValidationInterceptor (Story 4.3)
       ├─ Validate command.tenantId == TenantContext.get()
       └─ Reject if mismatch (Layer 2)
5. Command Handler (Aggregate)
   └─ Apply events with tenant_id in metadata
6. Event Bus → TrackingEventProcessor
   └─ TenantContextEventInterceptor (Story 4.5)
       ├─ Extract tenant_id from event metadata
       ├─ TenantContext.set(tenantId)
       └─ Restore context in async thread
7. Event Handler (Projection)
   └─ jOOQ Query with RLS (Story 4.4)
       ├─ SET LOCAL app.tenant_id before query
       └─ PostgreSQL RLS enforces isolation (Layer 3)
8. Response → Client
   └─ TenantContext.clear() in filter finally block
```

**Tenant Isolation Validation (All 3 Layers):**

```
Layer 1: TenantContextFilter
├─ Extract tenant_id from JWT
├─ Populate ThreadLocal
└─ Missing claim → 400 Bad Request ✅

Layer 2: TenantValidationInterceptor
├─ Validate context matches command
├─ Mismatch → TenantIsolationException
└─ Missing context → Reject (fail-closed) ✅

Layer 3: PostgreSQL RLS
├─ Session variable: app.tenant_id
├─ RLS policy filters by tenant_id
└─ Cross-tenant query → Empty result ✅
```

## Non-Functional Requirements

### Performance

| Metric | Target | Measurement | Story |
|--------|--------|-------------|-------|
| **Tenant context extraction** | <1ms p95 | Micrometer histogram | 4.2 |
| **Layer 2 validation** | <0.5ms p95 | Micrometer histogram | 4.3 |
| **PostgreSQL RLS overhead** | <2ms per query | Integration test baseline | 4.4 |
| **Event metadata enrichment** | <1ms per event | Micrometer histogram | 4.5 |
| **End-to-end multi-tenant request** | <200ms p95 | NFR from PRD | 4.6 |
| **Tenant isolation tests** | <3 minutes total | CI/CD timing | 4.7 |
| **Concurrency test execution** | 20-30 minutes | Nightly pipeline | 4.10 |

### Security

| Requirement | Implementation | Story |
|-------------|----------------|-------|
| **Fail-Closed Design** | Missing tenant context = immediate rejection | 4.1, 4.2, 4.3 |
| **Defense-in-Depth** | 3 independent validation layers | 4.1-4.4 |
| **Cross-Tenant Leak Detection** | Metrics + audit logging | 4.8 |
| **Security Audit Trail** | All tenant access logged | 4.8 |
| **Privilege Escalation Prevention** | BYPASSRLS privilege management | 4.4 |
| **Attack Scenario Testing** | JWT tampering, SQL injection, cross-tenant attempts | 4.7 |
| **CWE-209 Compliance** | Generic error messages (no tenant exposure) | 4.2, 4.3 |

### Reliability/Availability

| Requirement | Implementation | Story |
|-------------|----------------|-------|
| **ThreadLocal Memory Safety** | WeakReference for cleanup | 4.1 |
| **Context Cleanup Guarantee** | Try-finally blocks in all filters/interceptors | 4.2, 4.5 |
| **Race Condition Prevention** | LitmusKt concurrency testing | 4.10 |
| **Graceful Degradation** | Per-tenant quotas prevent resource exhaustion | 4.9 |
| **Context Leak Prevention** | Automatic leak detection and alerting | 4.8 |

### Observability

| Metric | Type | Story |
|--------|------|-------|
| **tenant_context_extraction_duration** | Histogram | 4.2 |
| **missing_tenant_failures** | Counter | 4.2 |
| **tenant_validation_failures** | Counter | 4.3 |
| **tenant_mismatch_attempts** | Counter | 4.3 |
| **tenant_context_propagation_failures** | Counter | 4.5 |
| **cross_tenant_access_attempts** | Counter (CRITICAL alert) | 4.8 |
| **tenant_quota_exceeded** | Counter | 4.9 |
| **tenant_context_leaks_detected** | Counter (CRITICAL alert) | 4.8 |

## Dependencies and Integrations

### Internal Dependencies (Prerequisites)
- **Epic 3:** 10-layer JWT validation with tenant_id claim extraction
- **Epic 2:** CQRS/Event Sourcing infrastructure (Axon Framework)
- **Epic 1:** Foundation (DDD base classes, Docker Compose stack)

### External Dependencies
- **Axon Framework 4.12.1:** MessageHandlerInterceptor for context propagation
- **PostgreSQL 16.10:** Row-Level Security policies
- **jOOQ 3.20.8:** ExecuteListener for session variable propagation
- **Keycloak 26.4.2:** JWT tenant_id claim provisioning
- **Micrometer 1.15.5:** Metrics for tenant operations
- **LitmusKt/JCStress:** Concurrency testing (Story 4.10)

### Integration Points
- JWT Claims → TenantContext (Story 4.2)
- TenantContext → Axon Commands (Story 4.3)
- Event Metadata → Async Processors (Story 4.5)
- TenantContext → PostgreSQL Session Variables (Story 4.4)
- Metrics → Prometheus/Grafana (Story 4.8)

## Acceptance Criteria (Authoritative)

### Story 4.1: TenantContext and ThreadLocal Management
1. framework/multi-tenancy module created
2. TenantId.kt value object with validation
3. TenantContext.kt manages ThreadLocal storage with stack-based context
4. TenantContextHolder.kt provides static access (get/set/clear methods)
5. WeakReference used for memory safety (prevent ThreadLocal leaks)
6. Unit tests validate: set context → retrieve → clear
7. Thread isolation validated (context not shared between threads)
8. Context cleared after request completion (filter cleanup)

### Story 4.2: TenantContextFilter - Layer 1 Tenant Extraction
1. TenantContextFilter.kt created as @Component with @Order(Ordered.HIGHEST_PRECEDENCE + 10)
2. Filter extracts tenant_id from JWT claim (after JWT validation in Epic 3)
3. TenantContext.set(tenantId) populates ThreadLocal
4. Missing tenant_id claim rejects request with 400 Bad Request
5. Filter ensures cleanup in finally block (TenantContext.clear())
6. Integration test validates tenant extraction from real Keycloak JWT
7. Metrics emitted: tenant_context_extraction_duration, missing_tenant_failures

### Story 4.3: Axon Command Interceptor - Layer 2 Tenant Validation
1. TenantValidationInterceptor.kt implements CommandHandlerInterceptor
2. Interceptor validates: TenantContext.get() matches command.tenantId
3. All commands must include tenantId field
4. Mismatch rejects command with TenantIsolationException
5. Missing context rejects command (fail-closed)
6. Integration test validates: tenant A cannot modify tenant B aggregates
7. Validation metrics: tenant_validation_failures, tenant_mismatch_attempts

### Story 4.4: PostgreSQL Row-Level Security Policies - Layer 3
1. Flyway migration V004__rls_policies.sql enables RLS on all tenant-scoped tables
2. RLS policies created: widget_view table requires tenant_id = current_setting('app.tenant_id')
3. PostgreSQL session variable set by JooqConfiguration before queries
4. RLS policies tested: attempt cross-tenant query → returns empty result
5. Integration test validates Layer 3 blocks unauthorized access
6. Performance impact measured (<2ms overhead per query)
7. RLS policies documented in docs/reference/multi-tenancy.md

### Story 4.5: Tenant Context Propagation to Async Event Processors
1. AxonTenantInterceptor.kt implements EventMessageHandlerInterceptor
2. Interceptor extracts tenant_id from event metadata
3. TenantContext.set(tenantId) before event handler execution
4. Context cleared after handler completion
5. Event metadata enriched with tenant_id during command processing
6. Integration test validates: dispatch command → event handler has tenant context
7. Async event processors (TrackingEventProcessor) receive correct context

### Story 4.6: Multi-Tenant Widget Demo Enhancement
1. Widget.kt commands include tenantId field
2. CreateWidgetCommand includes tenant_id from TenantContext
3. Command handler validates tenant context (Layer 2)
4. Widget events include tenant_id in metadata
5. widget_view projection table includes tenant_id column
6. Integration test creates widgets for multiple tenants
7. Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
8. All Widget tests pass with tenant context

### Story 4.7: Tenant Isolation Integration Test Suite
1. TenantIsolationIntegrationTest.kt validates all 3 layers
2. Test scenarios: Layer 1 (missing claim → 400), Layer 2 (mismatch → exception), Layer 3 (SQL bypass → RLS blocks)
3. Cross-tenant attack scenarios tested (JWT with wrong tenant_id)
4. Test uses multiple Keycloak users with different tenant_id claims
5. All isolation tests pass
6. Test execution time <3 minutes
7. Test documented as security validation reference

### Story 4.8: Tenant Context Leak Detection and Monitoring
1. TenantLeakDetector component monitors context lifecycle
2. Prometheus metrics: cross_tenant_access_attempts, context_leaks_detected
3. Security audit log for all tenant boundary violations
4. Alerting rules for CRITICAL tenant isolation failures
5. Integration test validates leak detection
6. Metrics dashboards in Grafana
7. Documentation in docs/reference/multi-tenancy.md

### Story 4.9: Per-Tenant Resource Quotas
1. TenantQuotaService manages per-tenant limits
2. Token bucket rate limiting with Bucket4j
3. Quotas: 100 req/sec, 1000 commands/hour, 10GB storage per tenant
4. Exceeded quotas return 429 Too Many Requests
5. Hot tenant detection and automatic throttling
6. Integration test validates quota enforcement
7. Quota metrics: tenant_quota_exceeded, tenant_throttled_requests

### Story 4.10: LitmusKt Concurrency Testing for TenantContext
1. LitmusKt or JCStress concurrency testing framework setup
2. 5 concurrency test scenarios for TenantContext
3. Tests validate: ThreadLocal isolation, context cleanup, nested context, race conditions
4. Concurrency tests integrated into nightly CI/CD pipeline
5. Forbidden outcomes detection (cross-tenant leaks)
6. Test execution time 20-30 minutes
7. Documentation in docs/architecture.md

## Traceability Mapping

| FR (PRD) | Epic | Stories | Architecture Decision |
|----------|------|---------|----------------------|
| **FR004: Multi-Tenancy with Isolation and Quotas** | Epic 4 | 4.1-4.10 | Decision #2: 3-Layer Multi-Tenancy Isolation |
| FR004: Three-layer tenant isolation | Epic 4 | 4.1 (Context), 4.2 (Filter), 4.3 (Interceptor), 4.4 (RLS) | Architecture lines 3318-3783 |
| FR004: Continuous monitoring | Epic 4 | 4.8 (Leak Detection) | Observability patterns |
| FR004: Security audit logging | Epic 4 | 4.8 (Audit Trail) | Security Architecture |
| FR004: Cross-tenant leak detection | Epic 4 | 4.7 (Tests), 4.8 (Monitoring), 4.10 (Concurrency) | Testing Strategy Layer 7 |
| FR004: Per-tenant resource quotas | Epic 4 | 4.9 (Quotas) | Performance Considerations |
| FR004: Automatic throttling | Epic 4 | 4.9 (Rate Limiting) | Resilience patterns |

## Risks, Assumptions, Open Questions

### Risks

| Risk | Likelihood | Impact | Mitigation | Owner |
|------|-----------|--------|------------|-------|
| **ThreadLocal context leaks** | Medium | CRITICAL | WeakReference + mandatory cleanup + leak detection (Story 4.8) | Charlie |
| **Race conditions in TenantContext** | Medium | HIGH | LitmusKt concurrency testing (Story 4.10) | Dana |
| **Performance degradation from RLS** | Low | MEDIUM | Indexes on tenant_id, <2ms overhead target (Story 4.4) | Charlie |
| **Async context propagation failure** | Medium | CRITICAL | Event metadata enrichment + interceptor testing (Story 4.5) | Charlie |
| **LitmusKt experimental stability** | High | LOW | JCStress fallback documented (Story 4.0) | Dana |

### Assumptions

1. **JWT tenant_id claim available:** Epic 3 Keycloak configuration includes tenant_id in access tokens ✅
2. **Single database multi-tenancy:** All tenants share same PostgreSQL instance (separate DB per tenant out of scope)
3. **UUID tenant IDs:** Tenant identifiers are UUIDs (validated in TenantId value object)
4. **ThreadLocal acceptable:** Axon 4.12.1 uses ThreadLocal (Axon 5.x migration planned Q3-Q4 2026)
5. **Test profiles isolate security:** Epic 3 established @Profile patterns for Spring Security in tests

### Open Questions

1. **Tenant provisioning API:** Deferred to Epic 10 (reference application) - how will tenants be created/managed?
2. **Tenant migration:** If tenant needs to move between environments, what's the process? (Post-MVP)
3. **Super admin bypass:** Should SUPER_ADMIN role bypass tenant isolation for support scenarios?
4. **Tenant suspension:** What happens to in-flight requests when tenant is suspended? (Epic 5+ error handling)

## Test Strategy Summary

### Story-Level Testing (Constitutional TDD)

**Story 4.1: TenantContext Unit Tests**
- WeakReference memory safety validation
- Stack-based context push/pop operations
- Thread isolation (parallel thread tests)
- Execution: <5 seconds (fast unit tests)

**Story 4.2: TenantContextFilter Integration Tests**
- Real Keycloak JWT with tenant_id claim
- Missing claim rejection (400 Bad Request)
- Context cleanup in finally block
- Execution: <1 minute (Testcontainers)

**Story 4.3: Tenant Validation Integration Tests**
- Tenant A command → Tenant A aggregate ✅
- Tenant A command → Tenant B aggregate → TenantIsolationException ❌
- Missing context → Rejection ❌
- Execution: <2 minutes (Testcontainers + Axon)

**Story 4.4: PostgreSQL RLS Integration Tests**
- Direct SQL cross-tenant query → Empty result
- Application user WITHOUT BYPASSRLS privilege
- Performance measurement (<2ms overhead)
- Execution: <2 minutes (Testcontainers PostgreSQL)

**Story 4.5: Async Propagation Integration Tests**
- Command dispatched → Event handler has correct tenant context
- Metadata enrichment validated
- TrackingEventProcessor context restoration
- Execution: <3 minutes (Testcontainers + Axon)

**Story 4.6: Multi-Tenant Widget Demo Tests**
- Create widgets for tenant-A and tenant-B
- Verify tenant-A cannot see tenant-B widgets
- All 3 layers working together
- Execution: <3 minutes (comprehensive E2E)

**Story 4.7: Comprehensive Isolation Test Suite**
- All 3 layers validated independently
- Cross-tenant attack scenarios
- Multiple Keycloak users with different tenant_id claims
- Security validation reference
- Execution: <3 minutes

**Story 4.8: Leak Detection Tests**
- Simulate context leaks (intentional test scenarios)
- Verify detection and alerting
- Audit log validation
- Execution: <2 minutes

**Story 4.9: Quota Enforcement Tests**
- Exceed rate limits (100 req/sec)
- Verify 429 Too Many Requests
- Token bucket behavior validation
- Execution: <2 minutes

**Story 4.10: Concurrency Tests (Nightly)**
- 5 LitmusKt/JCStress test scenarios
- ThreadLocal isolation under high concurrency
- Forbidden outcome detection (cross-tenant leaks)
- Execution: 20-30 minutes (nightly only)

### Epic-Level Validation

**Integration Test Coverage:** 40-50% of Epic 4 test suite (integration-first approach)
**Target Line Coverage:** 85%+ (Kover)
**Target Mutation Coverage:** 60-70% (Pitest, nightly)
**CI/CD Fast Feedback:** <15 minutes (Stories 4.1-4.9 tests)
**Nightly Deep Validation:** ~2.5 hours (includes Story 4.10 concurrency)

---

**Epic 4 Tech Spec Complete**

This technical specification provides comprehensive implementation guidance for all 10 Epic 4 stories, leveraging research from Story 4.0 and patterns established in Epic 2-3.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
