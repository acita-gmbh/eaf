# Story 10.2: Widget Projection with Advanced Queries

**Epic:** Epic 10 - Reference Application for MVP Validation
**Status:** TODO
**Related Requirements:** FR003, FR011

---

## User Story

As a framework developer,
I want Widget projection supporting advanced queries,
So that the reference app demonstrates complex read model patterns.

---

## Acceptance Criteria

1. ✅ WidgetProjection enhanced with: full-text search, filtering by status, sorting
2. ✅ Queries: FindWidget, ListWidgets (with cursor pagination), SearchWidgets, CountWidgetsByStatus
3. ✅ jOOQ queries for all scenarios
4. ✅ Query performance optimized (<50ms for single, <200ms for paginated)
5. ✅ Integration tests for all query types
6. ✅ Multi-tenant query isolation validated
7. ✅ Query patterns documented as reference

---

## Prerequisites

**Story 10.1**

---

## References

- PRD: FR003, FR011
- Tech Spec: Section 4.2 (Projection Schema), Section 5.4 (Cursor Pagination)
