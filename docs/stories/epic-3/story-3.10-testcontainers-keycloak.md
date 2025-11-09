# Story 3.10: Testcontainers Keycloak for Integration Tests

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006

---

## User Story

As a framework developer,
I want Keycloak Testcontainer provisioned for security integration tests,
So that authentication flows are tested against real Keycloak instance.

---

## Acceptance Criteria

1. ✅ Testcontainers Keycloak 26.4.2 configured in test dependencies
2. ✅ KeycloakTestContainer.kt utility creates container with realm import and generateToken() method
3. ✅ realm-export.json configured (eaf realm, eaf-api client, test users, roles, tenant_id mapper)
4. ✅ Test realm includes users: admin@eaf.com (WIDGET_ADMIN, ADMIN), viewer@eaf.com (WIDGET_VIEWER)
5. ✅ Test realm includes roles: WIDGET_ADMIN, WIDGET_VIEWER, ADMIN
6. ✅ application-test.yml configured with Keycloak Admin Client properties (admin-cli, admin password)
7. ✅ Container reuse enabled for performance (start once per test class)
8. ✅ Container-generated JWTs for authentication tests (via KeycloakBuilder with password grant)
9. ✅ All security integration tests pass using Testcontainers Keycloak

---

## Prerequisites

**Story 3.8** - Complete 10-Layer JWT Validation Integration

---

## References

- PRD: FR006
- Architecture: Section 11 (Testing Strategy - Testcontainers)
- Tech Spec: Section 2.2 (Testcontainers 1.21.3)
