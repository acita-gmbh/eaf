# Story 4.5: Tenant Context Propagation to Async Event Processors

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want tenant context propagated to async Axon event processors,
So that projection updates and event handlers have tenant context available.

---

## Acceptance Criteria

1. ✅ AxonTenantInterceptor.kt implements EventMessageHandlerInterceptor
2. ✅ Interceptor extracts tenant_id from event metadata
3. ✅ TenantContext.set(tenantId) before event handler execution
4. ✅ Context cleared after handler completion
5. ✅ Event metadata enriched with tenant_id during command processing
6. ✅ Integration test validates: dispatch command → event handler has tenant context
7. ✅ Async event processors (TrackingEventProcessor) receive correct context

---

## Prerequisites

**Story 4.4** - PostgreSQL Row-Level Security Policies

---

## References

- PRD: FR004
- Architecture: Section 16 (Context Propagation to Async Processors)
- Tech Spec: Section 3 (FR004)
