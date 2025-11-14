# Story 6.2: Tenant-Aware Process Engine

**Epic:** Epic 6 - Workflow Orchestration
**Status:** TODO
**Related Requirements:** FR007, FR004 (Multi-Tenancy)

---

## User Story

As a framework developer,
I want Flowable processes to be tenant-aware,
So that workflow instances are isolated per tenant.

---

## Acceptance Criteria

1. ✅ TenantAwareProcessEngine.kt wraps RuntimeService
2. ✅ All process starts include tenant_id as process variable
3. ✅ Process instances queryable by tenant_id
4. ✅ Integration test validates: start process → tenant_id stored in process variables
5. ✅ Cross-tenant process access blocked (tenant A cannot query tenant B processes)
6. ✅ Tenant context available in BPMN service tasks and listeners
7. ✅ Tenant-aware queries documented

---

## Prerequisites

**Story 6.1**, **Epic 4 complete**

---

## References

- PRD: FR007, FR004
- Architecture: Section 8 (Tenant-Aware Workflows)
- Tech Spec: Section 3 (FR007)
