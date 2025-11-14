# Story 3.10: Role-Based Access Control on API Endpoints

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006

---

## User Story

As a framework developer,
I want role-based authorization on Widget API endpoints,
So that only users with correct roles can perform operations.

---

## Acceptance Criteria

1. ✅ Widget API endpoints annotated with @PreAuthorize("hasRole('WIDGET_ADMIN')")
2. ✅ Keycloak realm configured with roles: WIDGET_ADMIN, WIDGET_VIEWER
3. ✅ Test users created with different role assignments
4. ✅ Integration test validates: WIDGET_ADMIN can create/update, WIDGET_VIEWER can only read
5. ✅ Unauthorized access returns 403 Forbidden with RFC 7807 error
6. ✅ Role requirements documented in OpenAPI spec
7. ✅ Authorization test suite covers all permission combinations

---

## Prerequisites

**Story 3.9** - Complete 10-Layer JWT Validation Integration

---

## References

- PRD: FR006
- Architecture: Section 16 (Role-Based Access Control)
- Tech Spec: Section 7.1

---

## Tasks/Subtasks

### Implementation Tasks
- [x] Add @PreAuthorize annotations to WidgetController endpoints (POST, GET, PUT)
- [x] Configure Keycloak realm with WIDGET_ADMIN and WIDGET_VIEWER roles
- [x] Create test users with role assignments in Keycloak testcontainer setup
- [x] Document role requirements in OpenAPI annotations (@Operation, @SecurityRequirement)

### Testing Tasks
- [x] Write integration test: WIDGET_ADMIN can create widgets
- [x] Write integration test: WIDGET_ADMIN can update widgets
- [x] Write integration test: WIDGET_VIEWER can read widgets
- [x] Write integration test: WIDGET_VIEWER cannot create/update (403 Forbidden)
- [x] Verify 403 responses follow RFC 7807 ProblemDetail format
- [x] Create comprehensive authorization test suite covering all permission combinations

---

## Dev Notes

**Critical Dependency:**
- Story 3.5 RoleNormalizer MUST be complete (Set<GrantedAuthority> with ROLE_ prefix)
- Without this fix, ALL @PreAuthorize checks will FAIL

**Implementation Approach:**
- Use Spring Security @PreAuthorize with hasRole() expressions
- Leverage existing JWT validation from Story 3.9
- Follow existing error handling patterns (RFC 7807)

---

## Dev Agent Record

### Context Reference
- `docs/sprint-artifacts/epic-3/story-3.10-context.xml`

### Debug Log

**Investigation Timeline:** 9.5+ hours with 10 AI agents consulted

**Critical Blockers Solved:**
1. **403 vs 500 Issue:** @PreAuthorize returned 500 instead of 403
   - Root Cause: Filter-Stack != MVC-Stack (ExceptionTranslationFilter vs @ControllerAdvice)
   - Solution: Dual exception handling (@Order + AccessDeniedHandler)

2. **Testcontainers Timing:** Connection to localhost:5432 refused
   - Root Cause: @EnableMethodSecurity early bean init + @ServiceConnection incompatibility
   - Solution: Container as Spring @Bean, DataSource from container bean injection

### Completion Notes

**All Acceptance Criteria Met:**
- ✅ @PreAuthorize annotations on all Widget endpoints
- ✅ WIDGET_ADMIN can create/update widgets
- ✅ WIDGET_VIEWER can read widgets
- ✅ WIDGET_VIEWER cannot create/update (403 Forbidden)
- ✅ Unauthenticated requests return 401 Unauthorized
- ✅ 403 responses follow RFC 7807 ProblemDetail format

**Test Results:** 8/8 RBAC tests passed, zero regressions

---

## File List

**Production Code:**
- `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/api/WidgetController.kt`
- `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt`
- `products/widget-demo/build.gradle.kts`

**Test Configuration:**
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestSecurityConfig.kt` (NEW)
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestAccessDeniedAdvice.kt` (NEW)
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestContainersConfig.kt` (NEW)
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestDslConfiguration.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/AxonTestConfiguration.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestJpaBypassConfiguration.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestSecurityConfig.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/TestAutoConfigurationOverrides.kt`

**Integration Tests:**
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt` (NEW)

---

## Change Log

- 2025-11-13: Tasks/Subtasks section added by Dev Agent (story structure completion)
- 2025-11-14: Story implementation completed after 9.5h investigation
- 2025-11-14: All tasks completed, 8 RBAC tests passing, zero regressions

---

## Status

**Current Status:** review
**Last Updated:** 2025-11-14
