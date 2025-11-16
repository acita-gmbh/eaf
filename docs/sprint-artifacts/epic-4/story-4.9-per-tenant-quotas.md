# Story 4.9: Per-Tenant Resource Quotas

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want per-tenant resource quotas (event rate, storage, query limits),
So that one tenant cannot impact system performance for others.

---

## Acceptance Criteria

1. ✅ TenantQuotaManager.kt implements quota enforcement
2. ✅ Quotas configurable per tenant: max_events_per_second, max_storage_mb, max_query_complexity
3. ✅ Rate limiting using token bucket algorithm (Bucket4j or similar)
4. ✅ Quota exceeded returns 429 Too Many Requests with retry-after header
5. ✅ Quota usage tracked in Prometheus metrics per tenant
6. ✅ Hot tenant detection (sustained quota violations)
7. ✅ Integration test validates quota enforcement

---

## Prerequisites

**Story 4.8** - Tenant Context Leak Detection

---

## Tasks / Subtasks

- [ ] AC1: TenantQuotaManager.kt implements quota enforcement
- [ ] AC2: Quotas configurable per tenant: max_events_per_second, max_storage_mb, max_query_complexity
- [ ] AC3: Rate limiting using token bucket algorithm (Bucket4j or similar)
- [ ] AC4: Quota exceeded returns 429 Too Many Requests with retry-after header
- [ ] AC5: Quota usage tracked in Prometheus metrics per tenant
- [ ] AC6: Hot tenant detection (sustained quota violations)
- [ ] AC7: Integration test validates quota enforcement

---

## Dev Agent Record

### Context Reference

- Per-tenant resource quotas prevent noisy neighbor problems
- Token bucket algorithm provides fair rate limiting
- Quotas enforce limits on events, storage, and query complexity
- 429 Too Many Requests with retry-after header for client guidance
- Hot tenant detection identifies sustained quota violators
- Prometheus metrics track quota usage per tenant

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

*To be populated during implementation*

### Completion Notes List

*To be populated during implementation*

### File List

*To be populated during implementation*

### Change Log

*To be populated during implementation*

---

## References

- PRD: FR004 (Resource Quotas)
- Architecture: Section 16 (Per-Tenant Quotas)
- Tech Spec: Section 3 (FR004)
