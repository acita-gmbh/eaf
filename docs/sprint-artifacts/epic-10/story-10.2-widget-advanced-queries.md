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

## Technical Notes

### Future Enhancements from Story 2.7

**GIN Index for JSONB Tags Querying:**

The `widget_projection` table (created in Story 2.7, migration V100) includes a `tags JSONB` column that currently has no index. If tag-based filtering is planned (e.g., `WHERE tags @> '{"color":"blue"}'`), add a GIN index:

```sql
CREATE INDEX idx_widget_projection_tags ON widget_projection USING GIN (tags);
```

**Context:** Story 2.7 created the widget_projection table with basic indexes (BRIN on created_at, B-tree on category/value) but deferred JSONB indexing to this story where advanced querying patterns are implemented.

**Decision:** Implement when tag queries are added to query service. GIN indexes are optimal for JSONB containment operators (@>, @?, @@).

---

## References

- PRD: FR003, FR011
- Tech Spec: Section 4.2 (Projection Schema), Section 5.4 (Cursor Pagination)
