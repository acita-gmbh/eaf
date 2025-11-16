# Story 4.4: PostgreSQL Row-Level Security Policies - Layer 3

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want PostgreSQL RLS policies enforcing tenant isolation at database level,
So that even SQL injection or bugs cannot breach tenant boundaries (Layer 3).

---

## Acceptance Criteria

1. ✅ Flyway migration V004__rls_policies.sql enables RLS on all tenant-scoped tables
2. ✅ RLS policies created: widget_view table requires tenant_id = current_setting('app.tenant_id')
3. ✅ PostgreSQL session variable set by JooqConfiguration before queries
4. ✅ RLS policies tested: attempt cross-tenant query → returns empty result
5. ✅ Integration test validates Layer 3 blocks unauthorized access
6. ✅ Performance impact measured (<2ms overhead per query)
7. ✅ RLS policies documented in docs/reference/multi-tenancy.md

---

## Prerequisites

**Story 4.3** - Axon Command Interceptor

---

## Tasks / Subtasks

- [ ] AC1: Flyway migration V004__rls_policies.sql enables RLS on all tenant-scoped tables
- [ ] AC2: RLS policies created: widget_view table requires tenant_id = current_setting('app.tenant_id')
- [ ] AC3: PostgreSQL session variable set by JooqConfiguration before queries
- [ ] AC4: RLS policies tested: attempt cross-tenant query → returns empty result
- [ ] AC5: Integration test validates Layer 3 blocks unauthorized access
- [ ] AC6: Performance impact measured (<2ms overhead per query)
- [ ] AC7: RLS policies documented in docs/reference/multi-tenancy.md

---

## Dev Agent Record

### Context Reference

- Implements Layer 3 of 3-layer tenant isolation defense
- PostgreSQL RLS provides database-level enforcement
- Performance target: <2ms overhead per query
- Session variable app.tenant_id set by jOOQ configuration

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

- PRD: FR004
- Architecture: Section 16 (Layer 3: PostgreSQL RLS)
- Tech Spec: Section 3 (FR004 - RLS), Section 4.2 (Projection Schema with RLS)
