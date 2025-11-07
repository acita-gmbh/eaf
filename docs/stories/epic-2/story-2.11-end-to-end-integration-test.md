# Story 2.11: End-to-End Integration Test

**Story Context:** [2-11-end-to-end-integration-test.context.xml](2-11-end-to-end-integration-test.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** review
**Story Points:** TBD
**Related Requirements:** FR003, FR011 (Performance validation)

---

## User Story

As a framework developer,
I want a comprehensive end-to-end test validating the complete CQRS flow,
So that I can prove the Walking Skeleton architecture works correctly.

---

## Acceptance Criteria

1. [x] WalkingSkeletonIntegrationTest.kt created using Testcontainers
2. [x] Test scenario: POST /widgets → CreateWidgetCommand → WidgetCreatedEvent → Projection updated → GET /widgets/:id returns data
3. [x] Test validates: Command dispatch, Event persistence, Projection update, Query retrieval
4. [x] Test measures and validates: API latency <200ms, Projection lag <10s
5. [x] Test uses real PostgreSQL (Testcontainers), not mocks
6. [x] Test passes consistently (no flakiness)
7. [x] Test execution time <2 minutes
8. [x] Test documented as reference example

---

## Prerequisites

**Story 2.10** - Widget REST API Controller

---

## Technical Notes

### Walking Skeleton Integration Test

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class WalkingSkeletonIntegrationTest(
    @Autowired val restTemplate: TestRestTemplate,
    @Autowired val dsl: DSLContext
) : FunSpec({

    @Container
    val postgres = PostgreSQLContainer("postgres:16.10")
        .withDatabaseName("eaf_test")
        .withUsername("test")
        .withPassword("test")

    test("complete CQRS flow: API → Command → Event → Projection → Query") {
        // 1. CREATE: POST /api/v1/widgets
        val createRequest = CreateWidgetRequest("Test Widget")

        val createStart = System.currentTimeMillis()
        val createResponse = restTemplate.postForEntity(
            "/api/v1/widgets",
            createRequest,
            WidgetResponse::class.java
        )
        val createLatency = System.currentTimeMillis() - createStart

        // Validate API latency <200ms
        createLatency shouldBeLessThan 200
        createResponse.statusCode shouldBe HttpStatus.CREATED

        val widgetId = createResponse.body!!.id

        // 2. Wait for projection update (max 10s)
        await.atMost(Duration.ofSeconds(10)).until {
            dsl.selectFrom(WIDGET_VIEW)
                .where(WIDGET_VIEW.ID.eq(UUID.fromString(widgetId.value)))
                .fetchOne() != null
        }

        // 3. RETRIEVE: GET /api/v1/widgets/:id
        val getResponse = restTemplate.getForEntity(
            "/api/v1/widgets/${widgetId.value}",
            WidgetResponse::class.java
        )

        getResponse.statusCode shouldBe HttpStatus.OK
        getResponse.body!!.name shouldBe "Test Widget"
        getResponse.body!!.published shouldBe false

        // 4. UPDATE: PUT /api/v1/widgets/:id
        val updateRequest = UpdateWidgetRequest("Updated Widget")
        val updateResponse = restTemplate.exchange(
            "/api/v1/widgets/${widgetId.value}",
            HttpMethod.PUT,
            HttpEntity(updateRequest),
            WidgetResponse::class.java
        )

        updateResponse.statusCode shouldBe HttpStatus.OK
        updateResponse.body!!.name shouldBe "Updated Widget"

        // 5. LIST: GET /api/v1/widgets
        val listResponse = restTemplate.getForEntity(
            "/api/v1/widgets?limit=10",
            PaginatedResponse::class.java
        )

        listResponse.statusCode shouldBe HttpStatus.OK
        listResponse.body!!.data.size shouldBeGreaterThan 0
    }

    test("command validation failure returns 400 with RFC 7807") {
        val invalidRequest = CreateWidgetRequest("")  // Blank name

        val response = restTemplate.postForEntity(
            "/api/v1/widgets",
            invalidRequest,
            ProblemDetail::class.java
        )

        response.statusCode shouldBe HttpStatus.BAD_REQUEST
        response.body!!.type shouldBe URI.create("https://eaf.axians.com/errors/validation-error")
        response.body!!.title shouldBe "Validation Error"
    }
})
```

---

## Implementation Checklist

- [x] Create WalkingSkeletonIntegrationTest.kt
- [x] Add Testcontainers PostgreSQL container
- [x] Implement complete CQRS flow test (API → Command → Event → Projection → Query)
- [x] Measure API latency (<200ms)
- [x] Measure projection lag (<10s)
- [x] Test validation failure (400 Bad Request)
- [x] Test not found scenario (404 Not Found)
- [x] Verify test uses real PostgreSQL (no mocks)
- [x] Ensure test passes consistently
- [x] Verify test execution <2 minutes
- [x] Document test as reference example
- [x] Commit: "Add end-to-end Walking Skeleton integration test"

---

## Test Evidence

- [x] Complete CQRS flow validated (API → Command → Event → Projection → Query)
- [x] API latency <200ms (measured after warmup call)
- [x] Projection lag <10s (eventually polling pattern)
- [x] Testcontainers PostgreSQL starts successfully (postgres:16.10-alpine)
- [x] Test passes consistently (verified multiple runs)
- [x] Test execution <2 minutes (actual: ~16s)
- [x] No flakiness detected (eventually pattern ensures deterministic behavior)

---

## Definition of Done

- [x] All acceptance criteria met
- [x] Integration test passes consistently
- [x] Performance targets validated
- [x] Test documented as reference
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.10 - Widget REST API Controller
**Next Story:** Story 2.12 - OpenAPI Documentation and Swagger UI

---

## References

- PRD: FR003, FR011 (Performance targets)
- Architecture: Section 11 (Testing Strategy - Integration Tests)
- Tech Spec: Section 9.1 (7-Layer Testing - Integration layer)

---

## Dev Agent Record

### Context Reference
- Story Context: [2-11-end-to-end-integration-test.context.xml](2-11-end-to-end-integration-test.context.xml)

### Debug Log
**Implementation Plan:**
1. Created WalkingSkeletonIntegrationTest.kt in integration-test module
2. Used existing PostgreSQL Testcontainer pattern (singleton, @ServiceConnection)
3. Implemented complete CQRS flow: POST → Command → Event → Projection → GET
4. Added warmup call to avoid cold-start latency penalty
5. Measured API latency (<200ms after warmup)
6. Measured projection lag (<10s with eventually polling)
7. Added validation failure test (400 Bad Request)
8. Added not found test (404 Not Found)
9. Used @Autowired field injection + init block pattern (Kotest + @SpringBootTest)
10. Fixed ktlint violations (chain-method-continuation)

**Technical Decisions:**
- Warmup call necessary to exclude Spring context initialization from latency measurement
- Eventually polling pattern ensures deterministic behavior (no flakiness)
- Direct jOOQ query to validate projection table update
- Testcontainers PostgreSQL 16.10-alpine for realistic database testing

### Completion Notes
✅ **Story 2.11 Complete**

**Implemented:**
- WalkingSkeletonIntegrationTest.kt with 3 test scenarios
- Complete CQRS flow validation (API → Command → Event → Projection → Query)
- Performance measurements (API <200ms, Projection lag <10s)
- Error handling tests (400 Bad Request, 404 Not Found)
- Real PostgreSQL via Testcontainers (no mocks)
- Eventually polling pattern for projection updates (no flakiness)

**Test Results:**
- All tests pass consistently ✅
- API latency: <200ms (measured after warmup)
- Projection lag: <10s (eventually pattern with 10s timeout)
- Test execution time: ~16s (well under 2 minute target)
- No flakiness detected (deterministic with eventually polling)

**Files Modified:**
- products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt (created)

**Quality Checks:**
- ✅ ktlint check passed
- ✅ Detekt passed
- ✅ Integration tests passed (3/3)
- ✅ Unit tests passed (15/15, Kotest XML bug ignored)
- ✅ No regressions detected

### File List
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt` (created)

### Change Log
- 2025-11-07: Created Walking Skeleton E2E integration test validating complete CQRS flow with performance assertions
