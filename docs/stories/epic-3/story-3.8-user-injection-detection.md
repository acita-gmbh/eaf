# Story 3.8: User Validation and Injection Detection (Layers 9-10)

**Epic:** Epic 3 - Authentication & Authorization
**Status:** in-progress
**Story Points:** TBD
**Related Requirements:** FR006, NFR002 (OWASP ASVS)

---

## User Story

As a framework developer,
I want user existence validation and SQL/XSS injection detection in JWT claims,
So that tokens reference valid users and don't contain malicious payloads.

---

## Acceptance Criteria

1. ✅ Layer 9 (optional): User validation - check user exists and is active (configurable, performance trade-off)
2. ✅ Layer 10: Injection detection - regex patterns for SQL/XSS in all string claims
3. ✅ Invalid users rejected with 401
4. ✅ Injection patterns detected and rejected with 400 Bad Request
5. ✅ Fuzz test with Jazzer targets injection detection (SQL patterns, XSS payloads)
6. ✅ Performance impact measured (<5ms per request)
7. ✅ User validation can be disabled via configuration for performance

---

## Prerequisites

**Story 3.6** - Redis Revocation Cache

---

## References

- PRD: FR006, NFR002
- Architecture: Section 16 (Layers 9-10)
- Tech Spec: Section 7.1 (10-Layer JWT)

---

## Tasks / Subtasks

- [x] Layer 9: Implement optional user validation service with active-user check + config toggle (AC1, AC3, AC7)
- [ ] Layer 10: Build comprehensive InjectionDetector with SQL/XSS/JNDI/Expression/Path Traversal patterns and wire into JWT pipeline (AC2, AC4)
- [ ] HTTP handling: Map invalid-user findings to 401 and injection detections to 400 with structured telemetry (AC3, AC4)
- [ ] Jazzer fuzz suite targeting injection detector with SQL/XSS/Expression/Traversal payload corpus (AC5)
- [ ] Benchmark Layer 9+10 path, document <5ms impact, and expose configuration switches (AC6, AC7)
- [ ] Update docs/file lists/change log once implementation + tests complete (all ACs)

---

## Dev Agent Record

**Context Reference:** `docs/stories/epic-3/story-3.8-context.xml`

### Debug Log

- 2025-11-12: Layer 9 plan – add configurable flag + properties, implement Keycloak-backed user validation service with caching, introduce JwtUserValidator in validator chain, and cover behavior with unit + integration tests (Tasks 1-3, AC1/AC3/AC7).
- 2025-11-12: Implemented Layer 9: added Keycloak admin properties + toggle, `KeycloakUserDirectory` with token/user caching + fail-closed behavior, `JwtUserValidator` wiring, story config updates, and exhaustive unit/integration tests (`./gradlew :framework:security:test` + `:framework:security:integrationTest`) validating AC1/AC3/AC7.

### Completion Notes

- Layer 9 (User Validation) implemented:
  - Configurable via `eaf.security.jwt.validate-user` property (default: `false`).
  - `KeycloakUserDirectory` service integrates with Keycloak Admin API to check user existence and status, with caching for performance.
  - `JwtUserValidator` added to the JWT validation chain.
  - Invalid users are rejected with a 401 Unauthorized response.
  - Comprehensive unit and integration tests validate functionality, caching, and fail-closed behavior.

---

## File List

- docs/stories/epic-3/story-3.8-user-injection-detection.md
- products/widget-demo/src/main/resources/application.yml
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/KeycloakOidcConfiguration.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/KeycloakAdminProperties.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/KeycloakUserDirectory.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/UserDirectory.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/UserRecord.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/UserValidationException.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtUserValidator.kt
- framework/security/src/test/kotlin/com/axians/eaf/framework/security/user/KeycloakUserDirectoryTest.kt
- framework/security/src/test/kotlin/com/axians/eaf/framework/security/validation/JwtUserValidatorTest.kt
- framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtUserValidationIntegrationTest.kt

---

## Change Log

- 2025-11-12: Layer 9 user validation implemented (Keycloak admin integration, optional validator wiring, config updates, plus comprehensive unit/integration tests).
