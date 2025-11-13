# Story 4.6: Multi-Tenant Widget Demo Enhancement

**Epic:** Epic 4 - Multi-Tenancy & Data Isolation
**Status:** TODO
**Related Requirements:** FR004

---

## User Story

As a framework developer,
I want Widget aggregate enhanced with tenant context validation,
So that the demo application demonstrates multi-tenancy correctly.

---

## Acceptance Criteria

1. ✅ Widget.kt commands include tenantId field
2. ✅ CreateWidgetCommand includes tenant_id from TenantContext
3. ✅ Command handler validates tenant context (Layer 2)
4. ✅ Widget events include tenant_id in metadata
5. ✅ widget_view projection table includes tenant_id column
6. ✅ Integration test creates widgets for multiple tenants
7. ✅ Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
8. ✅ All Widget tests pass with tenant context

---

## Prerequisites

**Story 4.5** - Tenant Context Propagation

---

## References

- PRD: FR004
- Architecture: Section 16 (Multi-Tenancy Example)
- Tech Spec: Section 3 (FR004)
