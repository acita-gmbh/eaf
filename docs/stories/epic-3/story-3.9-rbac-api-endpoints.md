# Story 3.9: Role-Based Access Control on API Endpoints

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

**Story 3.8** - Complete 10-Layer JWT Validation Integration

---

## References

- PRD: FR006
- Architecture: Section 16 (Role-Based Access Control)
- Tech Spec: Section 7.1
