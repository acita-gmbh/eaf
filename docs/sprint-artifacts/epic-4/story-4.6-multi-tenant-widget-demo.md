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

## Tasks / Subtasks

- [ ] AC1: Widget.kt commands include tenantId field
- [ ] AC2: CreateWidgetCommand includes tenant_id from TenantContext
- [ ] AC3: Command handler validates tenant context (Layer 2)
- [ ] AC4: Widget events include tenant_id in metadata
- [ ] AC5: widget_view projection table includes tenant_id column
- [ ] AC6: Integration test creates widgets for multiple tenants
- [ ] AC7: Cross-tenant access test validates isolation (tenant A cannot see tenant B widgets)
- [ ] AC8: All Widget tests pass with tenant context

---

## Dev Agent Record

### Context Reference

- Widget aggregate serves as reference implementation for multi-tenancy
- Demonstrates all 3 layers of tenant isolation
- Commands extract tenant_id from TenantContext
- Events include tenant_id in metadata for async processors
- Projection schema includes tenant_id column with RLS policies

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
- Architecture: Section 16 (Multi-Tenancy Example)
- Tech Spec: Section 3 (FR004)
