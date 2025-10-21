# Story [TBD]: Fix widget-demo QueryHandler ExecutionException

## Status
Draft (Blocker for Story 9.1 Completion)

## Story
**As a** Developer,
**I want** the widget-demo QueryGateway to execute FindWidgetsQuery successfully,
**so that** the React-Admin portal can display widget data and complete Story 9.1 validation.

## Business Context

**Blocking Story**: 9.1 - Implement React-Admin Consumer Application
**Impact**: Frontend integration is 100% functional but cannot validate CRUD operations (AC 20-27), error handling (AC 28-32), or accessibility (AC 33-37) without functional backend API.

**Current State**:
- ✅ Frontend sends authenticated requests correctly (JWT with tenant_id, roles)
- ✅ Backend accepts requests (CORS working, authentication passing)
- ❌ QueryGateway.query(FindWidgetsQuery, Page::class.java) throws ExecutionException
- ❌ Returns 500 Internal Server Error to frontend

**Diagnostic Evidence** (from Story 9.1 manual testing):
```http
GET /widgets?filter=%7B%7D&range=%5B0,24%5D&sort=%5B%22createdAt%22,%22DESC%22%5D
Status: 500 Internal Server Error

Response:
{"type":"/problems/generic-error","title":"Server Error","status":500,
 "detail":"An unexpected error occurred: ExecutionException",
 "instance":"/widgets","exceptionType":"ExecutionException"}

Request validated:
- ✅ JWT valid (tenant_id, USER role, widget:read authority)
- ✅ Authorization header present
- ✅ CORS headers correct
- ✅ Path correct (/widgets matches @RequestMapping)
```

## Acceptance Criteria

1. QueryGateway executes FindWidgetsQuery without ExecutionException
2. WidgetController GET /widgets returns 200 OK with empty list (database is empty)
3. Response headers include Content-Range and X-Total-Count for pagination
4. TenantDatabaseSessionInterceptor executes before @Transactional methods
5. PostgreSQL session variable `app.current_tenant` is set correctly
6. Row-Level Security policies allow SELECT with valid tenant context
7. WidgetProjectionRepository.search() returns WidgetPage successfully
8. Frontend React-Admin portal displays "No Widgets found" (not error message)
9. Create widget succeeds and appears in list (end-to-end validation)
10. All Story 9.1 manual testing phases (5-7) can proceed

## Tasks / Subtasks

- [ ] **Task 1: Investigate QueryGateway ExecutionException**
  - [ ] Enable DEBUG logging for Axon QueryGateway
  - [ ] Capture full stack trace from ExecutionException
  - [ ] Identify root cause (timeout? handler not found? serialization?)
  - [ ] Check if WidgetQueryHandler is registered in Axon registry

- [ ] **Task 2: Verify TenantDatabaseSessionInterceptor Activation**
  - [ ] Confirm @EnableAspectJAutoProxy is present (already added in Story 9.1)
  - [ ] Check if TenantDatabaseSessionInterceptor bean is created (Spring Boot logs)
  - [ ] Add debug logging to interceptor to confirm execution
  - [ ] Verify PostgreSQL session variable is set (query `SHOW app.current_tenant`)
  - [ ] Test RLS policies with manually set session variable

- [ ] **Task 3: Test WidgetProjectionRepository Directly**
  - [ ] Create unit test calling repository.search() with test criteria
  - [ ] Verify jOOQ query generates valid SQL
  - [ ] Test with RLS policies disabled (ALTER TABLE ... DISABLE ROW LEVEL SECURITY)
  - [ ] Test with RLS policies enabled and session variable set
  - [ ] Check for SQL syntax errors or data type mismatches

- [ ] **Task 4: Validate Axon QueryGateway Configuration**
  - [ ] Verify WidgetQueryHandler @Component is scanned
  - [ ] Check Axon configuration for query bus setup
  - [ ] Test query dispatch with simple query (no database access)
  - [ ] Add logging to WidgetQueryHandler.handle() method
  - [ ] Confirm @QueryHandler annotation is recognized

- [ ] **Task 5: End-to-End Validation**
  - [ ] GET /widgets returns 200 OK with empty array
  - [ ] POST /widgets creates widget successfully
  - [ ] GET /widgets returns 200 OK with created widget
  - [ ] Frontend displays widget in list
  - [ ] Complete Story 9.1 Task 5-7 validation

## Dev Notes

### Known Facts

**From Story 9.1 Implementation**:
1. ✅ AspectJ enabled via @EnableAspectJAutoProxy (added to WidgetDemoApplication)
2. ✅ TenantDatabaseSessionInterceptor exists in framework/security
3. ✅ Component scanning includes com.axians.eaf.framework
4. ✅ widget_projection table exists with correct schema
5. ✅ RLS policies defined and active on widget_projection
6. ✅ WidgetProjectionRepository implements search() method (jOOQ-based)
7. ✅ WidgetQueryHandler @Component with @QueryHandler method
8. ✅ Database is empty (0 rows in widget_projection)
9. ✅ TenantContext is populated from JWT (tenant_id claim present)

### Potential Root Causes

**Hypothesis 1: AspectJ Pointcut Not Matching**
- TenantDatabaseSessionInterceptor uses: `@Around("@annotation(org.springframework.transaction.annotation.Transactional)")`
- WidgetQueryHandler.handle() has: `@Transactional(readOnly = true)`
- **Test**: Add debug logging to interceptor, verify it executes

**Hypothesis 2: RLS Policy Rejection**
- Policies require `current_setting('app.current_tenant', true)`
- If session variable not set, RLS blocks SELECT
- **Test**: Temporarily disable RLS, test query execution

**Hypothesis 3: Query Serialization Issue**
- Axon may fail to serialize Page<WidgetResponse> return type
- **Test**: Change return type to List<WidgetResponse>, see if error persists

**Hypothesis 4: Transaction Propagation**
- QueryHandler @Transactional(readOnly = true) may conflict with jOOQ DSLContext
- **Test**: Remove @Transactional from QueryHandler, let controller manage transaction

**Hypothesis 5: Timeout or Deadlock**
- QueryGateway.query().get(5, TimeUnit.SECONDS) may timeout
- **Test**: Increase timeout to 30 seconds, check for TimeoutException

### Investigation Steps

**Step 1: Enable Debug Logging**
```yaml
# application.yml
logging:
  level:
    org.axonframework.queryhandling: DEBUG
    com.axians.eaf.framework.security.tenant: TRACE
    com.axians.eaf.products.widgetdemo.query: DEBUG
```

**Step 2: Capture Full Exception**
```kotlin
// In WidgetController.getWidgets()
try {
    val response = queryGateway.query(query, Page::class.java).get(5, TimeUnit.SECONDS)
    return ResponseEntity.ok(response as Page<WidgetResponse>)
} catch (e: Exception) {
    logger.error("Query execution failed", e)
    throw e
}
```

**Step 3: Test RLS Independently**
```sql
-- In PostgreSQL
SET app.current_tenant = '550e8400-e29b-41d4-a716-446655440000';
SELECT * FROM widget_projection;  -- Should succeed

-- Reset
RESET app.current_tenant;
SELECT * FROM widget_projection;  -- Should fail or return 0 rows
```

**Step 4: Test Query Handler Directly**
```kotlin
// Integration test
@SpringBootTest
class WidgetQueryHandlerTest {
    @Autowired
    lateinit var queryHandler: WidgetQueryHandler

    @Test
    fun `should handle FindWidgetsQuery`() {
        val query = FindWidgetsQuery(
            tenantId = "550e8400-e29b-41d4-a716-446655440000",
            page = 0,
            size = 20
        )
        val result = queryHandler.handle(query)
        result.content shouldBe emptyList()  // Database is empty
    }
}
```

### Configuration Fixes Already Applied (Story 9.1)

1. ✅ @EnableAspectJAutoProxy added to WidgetDemoApplication
2. ✅ CORS configuration added to WidgetSecurityConfiguration
3. ✅ JwtAuthenticationConverter created and wired
4. ✅ JWT validation issuer/audience updated for eaf-test realm
5. ✅ OpenTelemetry disabled (prevents ClassNotFoundException)
6. ✅ Database credentials corrected (eaf/eaf/eaf)

### Dependencies

**Blocks**:
- Story 9.1 (Task 5-7 validation)

**Requires**:
- PostgreSQL running with widget_projection table
- Keycloak eaf-test realm configured
- Test user with widget:read authority

**References**:
- Story 9.1: React-Admin consumer application (frontend integration)
- Story 4.4: Tenant async propagation (TenantDatabaseSessionInterceptor)
- Story 8.3: jOOQ migration (WidgetProjectionRepository implementation)

---

**Priority**: P0 (blocking Story 9.1 completion)
**Estimated Effort**: 2-4 hours (debugging + testing)
**Epic**: 2 (Widget Domain)
