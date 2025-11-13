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

## References

- PRD: FR004
- Architecture: Section 16 (Layer 3: PostgreSQL RLS)
- Tech Spec: Section 3 (FR004 - RLS), Section 4.2 (Projection Schema with RLS)
