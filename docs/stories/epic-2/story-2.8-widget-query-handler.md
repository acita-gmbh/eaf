# Story 2.8: Widget Query Handler

**Story Context:** [2-8-widget-query-handler.context.xml](2-8-widget-query-handler.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR011 (Performance)

---

## User Story

As a framework developer,
I want query handlers that retrieve Widget data from projections,
So that read operations are fast and don't load full event history.

---

## Acceptance Criteria

1. ✅ Queries implemented: FindWidgetQuery (by ID), ListWidgetsQuery (with cursor pagination)
2. ✅ WidgetQueryHandler.kt with @QueryHandler methods
3. ✅ Query handlers use jOOQ for type-safe SQL queries on widget_view
4. ✅ Cursor-based pagination implemented (no offset-limit)
5. ✅ Unit tests with Nullable Pattern for query logic
6. ✅ Integration test validates: store events → query returns projected data
7. ✅ Query performance <50ms for single widget, <200ms for paginated list

---

## Prerequisites

**Story 2.7** - Widget Projection Event Handler (projections populated)

---

## Technical Notes

### Query DTOs

**Queries:**
```kotlin
data class FindWidgetQuery(val widgetId: WidgetId)

data class ListWidgetsQuery(
    val limit: Int = 50,
    val cursor: String? = null
)
```

**Response:**
```kotlin
data class WidgetProjection(
    val id: WidgetId,
    val name: String,
    val published: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PaginatedWidgetResponse(
    val widgets: List<WidgetProjection>,
    val nextCursor: String?,
    val hasMore: Boolean
)
```

### Widget Query Handler

**products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/query/WidgetQueryHandler.kt:**
```kotlin
@Component
class WidgetQueryHandler(
    private val dsl: DSLContext
) {

    @QueryHandler
    fun handle(query: FindWidgetQuery): WidgetProjection? {
        return dsl.selectFrom(WIDGET_VIEW)
            .where(WIDGET_VIEW.ID.eq(UUID.fromString(query.widgetId.value)))
            .fetchOneInto(WidgetProjection::class.java)
    }

    @QueryHandler
    fun handle(query: ListWidgetsQuery): PaginatedWidgetResponse {
        val cursor = query.cursor?.let { decodeCursor(it) }

        val widgets = dsl.selectFrom(WIDGET_VIEW)
            .where(cursor?.let { WIDGET_VIEW.CREATED_AT.lt(it.timestamp) } ?: noCondition())
            .orderBy(WIDGET_VIEW.CREATED_AT.desc())
            .limit(query.limit + 1)  // Fetch one extra to check hasMore
            .fetchInto(WidgetProjection::class.java)

        val hasMore = widgets.size > query.limit
        val items = if (hasMore) widgets.take(query.limit) else widgets

        val nextCursor = if (hasMore) {
            encodeCursor(widgets[query.limit].createdAt)
        } else null

        return PaginatedWidgetResponse(items, nextCursor, hasMore)
    }

    private fun encodeCursor(timestamp: Instant): String {
        return Base64.getEncoder().encodeToString(timestamp.toString().toByteArray())
    }

    private fun decodeCursor(cursor: String): Cursor {
        val timestamp = String(Base64.getDecoder().decode(cursor))
        return Cursor(Instant.parse(timestamp))
    }

    data class Cursor(val timestamp: Instant)
}
```

### Nullable Pattern Unit Test

```kotlin
class WidgetQueryHandlerTest : FunSpec({

    test("find widget by ID returns projection") {
        val dsl = createNullableDSLContext()  // Nullable Pattern
        val handler = WidgetQueryHandler(dsl)

        val widgetId = WidgetId(UUID.randomUUID())
        val query = FindWidgetQuery(widgetId)

        val result = handler.handle(query)

        result shouldNotBe null
        result?.id shouldBe widgetId
    }

    test("list widgets with cursor pagination") {
        val dsl = createNullableDSLContext()
        val handler = WidgetQueryHandler(dsl)

        val query = ListWidgetsQuery(limit = 50)
        val result = handler.handle(query)

        result.widgets.size shouldBeLessThanOrEqual 50
    }
})
```

---

## Implementation Checklist

- [ ] Create FindWidgetQuery and ListWidgetsQuery DTOs
- [ ] Create WidgetProjection response DTO
- [ ] Create PaginatedWidgetResponse DTO
- [ ] Create WidgetQueryHandler.kt with @QueryHandler methods
- [ ] Implement cursor encoding/decoding (Base64)
- [ ] Implement jOOQ queries (type-safe)
- [ ] Write unit tests with Nullable Pattern
- [ ] Write integration test: command → projection → query
- [ ] Measure query performance (<50ms single, <200ms list)
- [ ] Commit: "Add Widget query handlers with cursor pagination"

---

## Test Evidence

- [ ] Unit tests with Nullable Pattern pass
- [ ] Integration test: query returns projected data
- [ ] Single widget query <50ms
- [ ] Paginated list query <200ms
- [ ] Cursor pagination works correctly

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Unit and integration tests pass
- [ ] Query performance validated
- [ ] Cursor pagination working
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.7 - Widget Projection Event Handler
**Next Story:** Story 2.9 - REST API Foundation

---

## References

- PRD: FR003 (Projections), FR011 (Performance targets)
- Architecture: Section 15 (API Contracts - Pagination)
- Tech Spec: Section 5.4 (Cursor Pagination)
