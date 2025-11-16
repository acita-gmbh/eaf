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

## Tasks / Subtasks

- [ ] AC1: AxonTenantInterceptor.kt implements EventMessageHandlerInterceptor
- [ ] AC2: Interceptor extracts tenant_id from event metadata
- [ ] AC3: TenantContext.set(tenantId) before event handler execution
- [ ] AC4: Context cleared after handler completion
- [ ] AC5: Event metadata enriched with tenant_id during command processing
- [ ] AC6: Integration test validates: dispatch command → event handler has tenant context
- [ ] AC7: Async event processors (TrackingEventProcessor) receive correct context

---

## Dev Agent Record

### Context Reference

- Async event processors run on different threads than command handlers
- Tenant context must be extracted from event metadata and restored
- Critical for projection updates and async event handlers
- Context cleanup required after handler completion

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

To be populated during implementation

### Completion Notes List

To be populated during implementation

### File List

To be populated during implementation

### Change Log

To be populated during implementation

---

## References

- PRD: FR004
- Architecture: Section 16 (Context Propagation to Async Processors)
- Tech Spec: Section 3 (FR004)
