# Story 2.11: End-to-End Integration Test

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003, FR011 (Performance validation)

---

## User Story

As a framework developer,
I want a comprehensive end-to-end test validating the complete CQRS flow,
So that I can prove the Walking Skeleton architecture works correctly.

---

## Acceptance Criteria

1. ✅ WalkingSkeletonIntegrationTest.kt created using Testcontainers
2. ✅ Test scenario: POST /widgets → CreateWidgetCommand → WidgetCreatedEvent → Projection updated → GET /widgets/:id returns data
3. ✅ Test validates: Command dispatch, Event persistence, Projection update, Query retrieval
4. ✅ Test measures and validates: API latency <200ms, Projection lag <10s
5. ✅ Test uses real PostgreSQL (Testcontainers), not mocks
6. ✅ Test passes consistently (no flakiness)
7. ✅ Test execution time <2 minutes
8. ✅ Test documented as reference example

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

- [ ] Create WalkingSkeletonIntegrationTest.kt
- [ ] Add Testcontainers PostgreSQL container
- [ ] Implement complete CQRS flow test (API → Command → Event → Projection → Query)
- [ ] Measure API latency (<200ms)
- [ ] Measure projection lag (<10s)
- [ ] Test validation failure (400 Bad Request)
- [ ] Test not found scenario (404 Not Found)
- [ ] Verify test uses real PostgreSQL (no mocks)
- [ ] Ensure test passes consistently
- [ ] Verify test execution <2 minutes
- [ ] Document test as reference example
- [ ] Commit: "Add end-to-end Walking Skeleton integration test"

---

## Test Evidence

- [ ] Complete CQRS flow validated (API → Command → Event → Projection → Query)
- [ ] API latency <200ms
- [ ] Projection lag <10s
- [ ] Testcontainers PostgreSQL starts successfully
- [ ] Test passes consistently (run 10 times)
- [ ] Test execution <2 minutes
- [ ] No flakiness detected

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Integration test passes consistently
- [ ] Performance targets validated
- [ ] Test documented as reference
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
