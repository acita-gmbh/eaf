# Story 3.5: Issuer, Audience, and Role Validation (Layers 6-8)

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006, NFR002

---

## User Story

As a framework developer,
I want issuer, audience, and role validation,
So that tokens are from trusted sources and contain required permissions.

---

## Acceptance Criteria

1. ✅ Layer 6: Issuer validation (expected: http://keycloak:8080/realms/eaf)
2. ✅ Layer 6: Audience validation (expected: eaf-api)
3. ✅ Layer 8: Role validation and normalization (handle Keycloak realm_access and resource_access structures)
4. ✅ RoleNormalizer.kt extracts and normalizes roles into flat list
5. ✅ @PreAuthorize annotations work with normalized roles
6. ✅ Tokens from wrong issuer rejected with 401
7. ✅ Property-based tests for role normalization edge cases (nested structures, missing keys)
8. ✅ Fuzz test for role extraction (Jazzer)

---

## Prerequisites

**Story 3.4** - JWT Claims Schema and Time-Based Validation

---

## References

- PRD: FR006, NFR002
- Architecture: Section 16 (Layers 6-8), Section 11 (Property + Fuzz Testing)
- Tech Spec: Section 7.1
