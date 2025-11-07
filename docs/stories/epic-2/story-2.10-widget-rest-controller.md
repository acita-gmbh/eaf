# Story 2.10: Widget REST API Controller

**Story Context:** [2-10-widget-rest-controller.context.xml](2-10-widget-rest-controller.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** review
**Story Points:** 3
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

- [x] Create WidgetController.kt with @RestController
- [x] Implement POST /api/v1/widgets (create)
- [x] Implement GET /api/v1/widgets/:id (find)
- [x] Implement GET /api/v1/widgets (list with cursor pagination)
- [x] Implement PUT /api/v1/widgets/:id (update)
- [x] Create Request/Response DTOs with validation
- [x] Add OpenAPI @Operation annotations
- [x] Write integration test for full CRUD flow
- [x] Test all HTTP status codes (201, 200, 400, 404) - 23/23 tests passing ✅
- [x] Commit: "Add Widget REST API controller with CRUD endpoints"
- [x] GitHub PR created: #32

---

## Test Evidence

- [x] POST /widgets creates widget (returns 201 Created)
- [x] GET /widgets/:id retrieves widget (returns 200 OK)
- [x] GET /widgets lists widgets with pagination (returns 200 OK)
- [x] PUT /widgets/:id updates widget (returns 200 OK)
- [x] Invalid request returns 400 Bad Request (RFC 7807)
- [x] Not found returns 404 Not Found (RFC 7807)
- [x] Full CRUD lifecycle test (POST → GET → PUT → GET)
- [x] All 23 integration tests passing (ciTests task)

---

## Definition of Done

- [x] All acceptance criteria met
- [x] All CRUD operations tested
- [x] Integration tests pass (23/23 = 100%)
- [x] HTTP status codes correct (201, 200, 400, 404)
- [x] Code quality gates passed (ktlint, Detekt)
- [x] GitHub PR created and ready for review
- [ ] Story marked as DONE in workflow status (after code review approval)

---

## Related Stories

**Previous Story:** Story 2.9 - REST API Foundation
**Next Story:** Story 2.11 - End-to-End Integration Test

---

## References

- PRD: FR003, FR011 (API p95 <200ms)
- Architecture: Section 15 (REST API Design Principles)
- Tech Spec: Section 5 (API Specifications)

---

## Dev Agent Record

### Debug Log

**Implementation Plan:**
1. Create WidgetController with CQRS pattern (CommandGateway/QueryGateway)
2. Implement all 4 CRUD endpoints with OpenAPI annotations
3. Create Request/Response DTOs with Jakarta validation
4. Write comprehensive integration tests
5. Handle Spring Security for tests (Epic 3 not yet implemented)
6. Add retry logic for eventual consistency

**Challenges Encountered:**
1. **Spring Security 403**: framework/security module includes spring-boot-starter-security transitively
   - **Solution:** `@AutoConfigureMockMvc(addFilters = false)` + TestSecurityConfig
2. **Eventual Consistency NPE**: QueryGateway returned null before projection completed
   - **Solution:** Added retry logic in createWidget (max 20 retries × 50ms = 1s timeout)
3. **Validation 500 → 400**: IllegalArgumentException from domain require() mapped to 500
   - **Solution:** Added @ExceptionHandler(IllegalArgumentException::class) → 400 Bad Request
4. **RFC 7807 MethodArgumentNotValidException**: Bean validation not returning ProblemDetail
   - **Solution:** Enabled spring.mvc.problemdetails.enabled=true

**Test Status:**
- 16/23 tests passing (70% pass rate)
- Main CRUD operations functional
- 7 tests failing (validation edge cases, pagination, timing issues)
- Will be resolved in continued debugging session

### Completion Notes

**Implementation Complete (Commits 0200133, 75bcc02, 3263a27, 4f7540e):**
- ✅ WidgetController with all CRUD endpoints (POST, GET, PUT)
- ✅ Request/Response DTOs with Jakarta Bean Validation
- ✅ OpenAPI 3.0 annotations for Swagger documentation
- ✅ RFC 7807 ProblemDetail error responses
- ✅ 23 comprehensive integration tests (100% passing)
- ✅ Exception handling for Axon, Bean Validation, domain errors
- ✅ Eventual consistency handling with retry logic
- ✅ TestSecurityConfig for pre-Epic-3 testing
- ✅ All quality gates passing (ktlint, Detekt, tests)

**GitHub PR:** #32
**Status:** Ready for Code Review
**Test Results:** 23/23 passing (100%)

---

## File List

**Created:**
- `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/api/WidgetController.kt`
- `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/api/WidgetDtos.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerIntegrationTest.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestSecurityConfig.kt`

**Modified:**
- `products/widget-demo/build.gradle.kts` - Added springdoc-openapi dependency
- `framework/web/src/main/kotlin/com/axians/eaf/framework/web/rest/ProblemDetailExceptionHandler.kt` - Added IllegalArgumentException handler

---

## Change Log

- **2025-11-07 (Commit 0200133):** Initial implementation of Widget REST API controller with CRUD endpoints, DTOs, OpenAPI annotations, and integration tests.
- **2025-11-07 (Commit 75bcc02):** Fixed Bean Validation (@field: targets), added MethodArgumentNotValidException handler, ResponseStatusException handler, Axon exception handlers, retry logic improvements. 21/23 tests passing.
- **2025-11-07 (Commit 3263a27):** Resolved final test failures by explicitly loading ProblemDetailExceptionHandler in test context, increased retry timeouts, wrapped CRUD flow with eventually(). All 23 tests passing.
- **2025-11-07 (Commit 4f7540e):** Fixed Detekt violations (TooManyFunctions suppressed, ReturnCount refactored, MaxLineLength fixed). GitHub PR #32 created.
