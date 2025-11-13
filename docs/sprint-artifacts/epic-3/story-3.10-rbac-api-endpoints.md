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
- [ ] Add @PreAuthorize annotations to WidgetController endpoints (POST, GET, PUT)
- [ ] Configure Keycloak realm with WIDGET_ADMIN and WIDGET_VIEWER roles
- [ ] Create test users with role assignments in Keycloak testcontainer setup
- [ ] Document role requirements in OpenAPI annotations (@Operation, @SecurityRequirement)

### Testing Tasks
- [ ] Write integration test: WIDGET_ADMIN can create widgets
- [ ] Write integration test: WIDGET_ADMIN can update widgets
- [ ] Write integration test: WIDGET_VIEWER can read widgets
- [ ] Write integration test: WIDGET_VIEWER cannot create/update (403 Forbidden)
- [ ] Verify 403 responses follow RFC 7807 ProblemDetail format
- [ ] Create comprehensive authorization test suite covering all permission combinations

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
*(To be filled during implementation)*

### Completion Notes
*(To be filled at story completion)*

---

## File List

*(To be updated during implementation)*

---

## Change Log

- 2025-11-13: Tasks/Subtasks section added by Dev Agent (story structure completion)

---

## Status

**Current Status:** ready-for-dev
**Last Updated:** 2025-11-13
