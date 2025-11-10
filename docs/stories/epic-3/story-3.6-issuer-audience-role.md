# Story 3.6: Issuer, Audience, and Role Validation (Layers 6-8)

**Epic:** Epic 3 - Authentication & Authorization
**Status:** done
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

- [x] Implement Layer 6 issuer validation (expected issuer: http://keycloak:8080/realms/eaf)
- [x] Implement Layer 6 audience validation (expected audience: eaf-api)
- [x] Implement Layer 8 role normalization logic in `RoleNormalizer.kt`
- [x] Verify `@PreAuthorize` annotations work with normalized roles
- [x] Add unit/property-based tests for role normalization edge cases
- [x] Add Jazzer fuzz test for role extraction
- [x] Ensure 401 response for tokens with wrong issuer/audience

---

## Dev Agent Record

**Context Reference:** `docs/stories/epic-3/story-3.6-context.xml`

### Debug Log

- 2025-11-10: Plan →
  - Implement dedicated Layer 6 validators (issuer & audience) wired into `SecurityConfiguration`.
  - Build `RoleNormalizer` + hook via `JwtAuthenticationConverter` so `@PreAuthorize` works on normalized authorities.
  - Expand unit/integration coverage plus add property-based + Jazzer fuzz tests per AC7/AC8; document and update File/Change logs.
- 2025-11-10: Implemented issuer/audience validators, RoleNormalizer + converter wiring, and refreshed controller security to enforce `@PreAuthorize` checks with admin/viewer coverage.
- 2025-11-10: Added unit + integration suites (issuer/audience failure scenarios, role-based enforcement), property-based campaign, and four Jazzer fuzz targets; all Gradle test/property/fuzz/integration tasks passing.
- 2025-11-10: Addressed review P1 – resource roles are now scoped to the configured API client and tests updated (unit/property/fuzz + Keycloak integration) to prevent privilege escalation.

### Completion Notes

- Layer-6 validators new: issuer normalization w/ trailing-slash tolerance + audience/azp enforcement, both wired into the JWT decoder.
- New `RoleNormalizer` extracts realm/resource roles, uppercases/prefixes, preserves permission styles, and feeds a custom `JwtAuthenticationConverter`.
- Controller now protected via `@PreAuthorize`; admin tokens succeed while viewer tokens produce 403, validated by Keycloak-backed integration tests.
- Coverage expanded with Kotest unit specs, property-based suite, targeted integration tests (issuer/audience mismatches), and Jazzer fuzzers for role extraction edge cases.
- Gradle runs executed: `:framework:security:test`, `:framework:security:integrationTest`, `:framework:security:propertyTest`, and four `:framework:security:fuzzTest --tests RoleNormalizationFuzzer.*` invocations – all green.
- Review follow-up: restricted `resource_access` normalization to `eaf-api` only, added regression tests (unit/property/fuzz) and re-ran `:framework:security:test`, `:framework:security:integrationTest`, `:framework:security:propertyTest` to confirm fix.

---

## File List

- framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidator.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidator.kt
- framework/security/src/main/kotlin/com/axians/eaf/framework/security/role/RoleNormalizer.kt
- framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/test/TestController.kt
- framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtFormatSignatureIntegrationTest.kt
- framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidationIntegrationTest.kt
- framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidationIntegrationTest.kt
- framework/security/src/test/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidatorTest.kt
- framework/security/src/test/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidatorTest.kt
- framework/security/src/test/kotlin/com/axians/eaf/framework/security/role/RoleNormalizerTest.kt
- framework/security/src/propertyTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationPropertyTest.kt
- framework/security/src/fuzzTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationFuzzer.kt
- docs/stories/epic-3/story-3.6-issuer-audience-role.md
- docs/sprint-status.yaml

---

## Change Log

- 2025-11-10: Added issuer/audience validators, RoleNormalizer wiring, and updated SecurityConfiguration/TestController for role-aware enforcement.
- 2025-11-10: Introduced full test suite coverage (unit, integration, property, fuzz) plus new story documentation updates; marked story ready for review.
- 2025-11-10: Review follow-up – constrained resource role normalization to the API client, expanded test coverage, and documented the security fix.

---

## Status

- done

## Senior Developer Review (AI)

**Reviewer:** Wall-E

**Date:** 2025-11-10

**Outcome:** Approve – All Layer-6/Layer-8 controls and associated tests are present with client-scoped role normalization; no defects found.

### Summary
- Validated every acceptance criterion and task; issuer/audience validators, RoleNormalizer, and @PreAuthorize gating all operate as documented.
- Negative-path integration tests (issuer/audience mismatch, viewer access) reliably enforce 401/403 outcomes.
- Property-based and Jazzer fuzz suites stress the normalizer without crashes, aligning with the epic’s defensive requirements.

### Key Findings
- No blocking or cautionary findings – implementation matches spec and security posture.

### Acceptance Criteria Coverage

| AC | Description | Status | Evidence |
| --- | --- | --- | --- |
| AC1 | Layer 6: Issuer validation (expected realm) | Implemented | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidator.kt:1-33`; `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt:102-115`; `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidationIntegrationTest.kt:21-44` |
| AC2 | Layer 6: Audience validation (eaf-api) | Implemented | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidator.kt:1-52`; `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt:102-115`; `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidationIntegrationTest.kt:21-44` |
| AC3 | Layer 8: Role validation & normalization | Implemented | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/role/RoleNormalizer.kt:1-82` |
| AC4 | RoleNormalizer.kt outputs Set<GrantedAuthority> with ROLE_ prefix | Implemented | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/role/RoleNormalizer.kt:33-82`; `framework/security/src/test/kotlin/com/axians/eaf/framework/security/role/RoleNormalizerTest.kt:10-109` |
| AC5 | @PreAuthorize annotations work with normalized roles | Implemented | `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/test/TestController.kt:16-26`; `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtFormatSignatureIntegrationTest.kt:43-62` |
| AC6 | Wrong issuer rejected with 401 | Implemented | `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidationIntegrationTest.kt:36-44` |
| AC7 | Property-based tests for role normalization edge cases | Implemented | `framework/security/src/propertyTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationPropertyTest.kt:17-188` |
| AC8 | Jazzer fuzz test for role extraction | Implemented | `framework/security/src/fuzzTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationFuzzer.kt:10-121` |

**Summary:** 8 of 8 acceptance criteria fully implemented.

### Task Completion Validation

| Task | Marked As | Verified As | Evidence |
| --- | --- | --- | --- |
| Implement Layer 6 issuer validation | [x] | Verified | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidator.kt:1-33`; `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt:102-115` |
| Implement Layer 6 audience validation | [x] | Verified | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidator.kt:1-52`; `framework/security/src/main/kotlin/com/axians/eaf/framework/security/config/SecurityConfiguration.kt:102-115` |
| Implement Layer 8 role normalization logic in `RoleNormalizer.kt` | [x] | Verified | `framework/security/src/main/kotlin/com/axians/eaf/framework/security/role/RoleNormalizer.kt:1-82` |
| Verify `@PreAuthorize` annotations work with normalized roles | [x] | Verified | `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/test/TestController.kt:16-26`; `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtFormatSignatureIntegrationTest.kt:43-62` |
| Add unit/property-based tests for role normalization edge cases | [x] | Verified | `framework/security/src/test/kotlin/com/axians/eaf/framework/security/role/RoleNormalizerTest.kt:10-109`; `framework/security/src/propertyTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationPropertyTest.kt:17-188` |
| Add Jazzer fuzz test for role extraction | [x] | Verified | `framework/security/src/fuzzTest/kotlin/com/axians/eaf/framework/security/role/RoleNormalizationFuzzer.kt:10-121` |
| Ensure 401 response for tokens with wrong issuer/audience | [x] | Verified | `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtIssuerValidationIntegrationTest.kt:36-44`; `framework/security/src/integration-test/kotlin/com/axians/eaf/framework/security/validation/JwtAudienceValidationIntegrationTest.kt:36-44` |

**Summary:** 7 of 7 completed tasks verified; 0 questionable; 0 falsely marked complete.

### Test Coverage and Gaps
- Reviewer executed `./gradlew :framework:security:test`, `./gradlew :framework:security:integrationTest`, and `./gradlew :framework:security:propertyTest`; all suites passed without flaky behavior.
- Fuzz coverage remains handled by the committed Jazzer targets; no additional gaps identified for the story scope.

### Architectural Alignment
- Implementation follows Epic 3 tech spec guidance for Layers 6-8 (docs/tech-spec-epic-3.md) by chaining custom validators into the `JwtDecoder` and scoping `resource_access` to the API client, matching the architecture pattern described for normalized RBAC.

### Security Notes
- Client-specific filtering in `RoleNormalizer` prevents cross-tenant privilege escalation, satisfying the critical alert in the story context.

### Best-Practices and References
- `docs/tech-spec-epic-3.md` (Layer 6-8 guidance) and `architecture.md` role normalization pattern remain the canonical references for future changes.

### Action Items

**Code Changes Required:**
- None.

**Advisory Notes:**
- Note: Keep `KeycloakOidcConfiguration.audience` synchronized with the Keycloak client id in each environment to preserve role scoping guarantees.
