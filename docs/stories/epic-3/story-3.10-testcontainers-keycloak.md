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
2. ✅ KeycloakTestContainer.kt utility creates container with realm import
3. ✅ Test realm includes: users (admin, viewer), roles, client configuration
4. ✅ Container reuse enabled for performance (start once per test class)
5. ✅ Integration tests use container-generated JWTs for authentication
6. ✅ Container startup time <30 seconds
7. ✅ All security integration tests pass using Testcontainers Keycloak

---

## Prerequisites

**Story 3.8** - Complete 10-Layer JWT Validation Integration

---

## References

- PRD: FR006
- Architecture: Section 11 (Testing Strategy - Testcontainers)
- Tech Spec: Section 2.2 (Testcontainers 1.21.3)
