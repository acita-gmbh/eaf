# Story 2.10: Widget REST API Controller

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR011 (Performance - API p95 <200ms)

---

## User Story

As a framework developer,
I want REST API endpoints for Widget CRUD operations,
So that the Widget aggregate is accessible via HTTP API.

---

## Acceptance Criteria

1. ✅ WidgetController.kt created with @RestController
2. ✅ Endpoints implemented: POST /widgets (create), GET /widgets/:id (find), GET /widgets (list), PUT /widgets/:id (update)
3. ✅ Controller uses CommandGateway for writes, QueryGateway for reads
4. ✅ Request/Response DTOs with validation annotations
5. ✅ OpenAPI 3.0 annotations for API documentation
6. ✅ Integration test validates full CRUD flow via REST API
7. ✅ Swagger UI accessible at /swagger-ui.html showing Widget endpoints
8. ✅ All API operations return correct HTTP status codes

---

## Prerequisites

**Story 2.8** - Widget Query Handler
**Story 2.9** - REST API Foundation

---

## Technical Notes

### Widget REST Controller

**products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/api/WidgetController.kt:**
```kotlin
@RestController
@RequestMapping("/api/v1/widgets")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create new widget")
    fun createWidget(@Valid @RequestBody request: CreateWidgetRequest): WidgetResponse {
        val widgetId = WidgetId(UUID.randomUUID())

        commandGateway.sendAndWait<Any>(
            CreateWidgetCommand(widgetId, request.name)
        )

        return WidgetResponse(widgetId, request.name, false)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get widget by ID")
    fun getWidget(@PathVariable id: UUID): WidgetResponse {
        val query = FindWidgetQuery(WidgetId(id))
        val widget = queryGateway.query(query, WidgetProjection::class.java).get()
            ?: throw AggregateNotFoundException(id.toString(), "Widget")

        return widget.toResponse()
    }

    @GetMapping
    @Operation(summary = "List all widgets with cursor pagination")
    fun listWidgets(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?
    ): PaginatedResponse<WidgetResponse> {
        val query = ListWidgetsQuery(limit, cursor)
        val result = queryGateway.query(query, PaginatedWidgetResponse::class.java).get()

        return PaginatedResponse(
            data = result.widgets.map { it.toResponse() },
            nextCursor = result.nextCursor,
            hasMore = result.hasMore
        )
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update widget")
    fun updateWidget(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateWidgetRequest
    ): WidgetResponse {
        commandGateway.sendAndWait<Any>(
            UpdateWidgetCommand(WidgetId(id), request.name)
        )

        // Query updated state
        return getWidget(id)
    }
}
```

### Request/Response DTOs

```kotlin
data class CreateWidgetRequest(
    @field:NotBlank(message = "Name cannot be blank")
    @field:Size(min = 1, max = 255)
    val name: String
)

data class UpdateWidgetRequest(
    @field:NotBlank(message = "Name cannot be blank")
    @field:Size(min = 1, max = 255)
    val name: String
)

data class WidgetResponse(
    val id: WidgetId,
    val name: String,
    val published: Boolean,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null
)

data class PaginatedResponse<T>(
    val data: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean
)
```

---

## Implementation Checklist

- [ ] Create WidgetController.kt with @RestController
- [ ] Implement POST /api/v1/widgets (create)
- [ ] Implement GET /api/v1/widgets/:id (find)
- [ ] Implement GET /api/v1/widgets (list with cursor pagination)
- [ ] Implement PUT /api/v1/widgets/:id (update)
- [ ] Create Request/Response DTOs with validation
- [ ] Add OpenAPI @Operation annotations
- [ ] Write integration test for full CRUD flow
- [ ] Verify Swagger UI at /swagger-ui.html
- [ ] Test all HTTP status codes (201, 200, 400, 404)
- [ ] Commit: "Add Widget REST API controller with CRUD endpoints"

---

## Test Evidence

- [ ] POST /widgets creates widget (returns 201 Created)
- [ ] GET /widgets/:id retrieves widget (returns 200 OK)
- [ ] GET /widgets lists widgets with pagination (returns 200 OK)
- [ ] PUT /widgets/:id updates widget (returns 200 OK)
- [ ] Invalid request returns 400 Bad Request (RFC 7807)
- [ ] Not found returns 404 Not Found (RFC 7807)
- [ ] Swagger UI shows all endpoints

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All CRUD operations tested
- [ ] Integration test passes
- [ ] Swagger UI functional
- [ ] HTTP status codes correct
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.9 - REST API Foundation
**Next Story:** Story 2.11 - End-to-End Integration Test

---

## References

- PRD: FR003, FR011 (API p95 <200ms)
- Architecture: Section 15 (REST API Design Principles)
- Tech Spec: Section 5 (API Specifications)
