# Story 3.6: Issuer, Audience, and Role Validation (Layers 6-8)

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

---

## Tasks / Subtasks

- [ ] Implement Layer 6 issuer validation (expected issuer: http://keycloak:8080/realms/eaf)
- [ ] Implement Layer 6 audience validation (expected audience: eaf-api)
- [ ] Implement Layer 8 role normalization logic in `RoleNormalizer.kt`
- [ ] Verify `@PreAuthorize` annotations work with normalized roles
- [ ] Add unit/property-based tests for role normalization edge cases
- [ ] Add Jazzer fuzz test for role extraction
- [ ] Ensure 401 response for tokens with wrong issuer/audience

---

## Dev Agent Record

**Context Reference:** `docs/stories/epic-3/story-3.6-context.xml`

### Debug Log

- _Pending_

### Completion Notes

- _Pending_

---

## File List

- _Pending_

---

## Change Log

- _Pending_

---

## Status

- in-progress
