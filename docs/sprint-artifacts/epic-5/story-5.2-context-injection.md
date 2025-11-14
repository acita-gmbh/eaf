# Story 5.2: Automatic Context Injection (trace_id, tenant_id)

**Epic:** Epic 5 - Observability & Monitoring
**Status:** TODO
**Related Requirements:** FR005

---

## User Story

As a framework developer,
I want automatic injection of trace_id and tenant_id into all log entries,
So that logs can be correlated across distributed requests and filtered by tenant.

---

## Acceptance Criteria

1. ✅ ContextEnricher.kt implements Logback MDC (Mapped Diagnostic Context)
2. ✅ trace_id extracted from OpenTelemetry Span and added to MDC
3. ✅ tenant_id extracted from TenantContext and added to MDC
4. ✅ All log entries automatically include trace_id and tenant_id fields
5. ✅ MDC cleanup after request completion
6. ✅ Integration test validates: make request → all logs include trace_id and tenant_id
7. ✅ Null safety when tenant_id or trace_id unavailable (log field omitted, not null)

---

## Prerequisites

**Story 5.1** - Structured JSON Logging

---

## References

- PRD: FR005
- Architecture: Section 13 (Context Injection)
- Tech Spec: Section 3 (FR005 - Context Injection)
