# Sprint Change Proposal: Epic 3 Story Resequencing

**Date:** 2025-11-09
**Author:** Amelia (Dev Agent) + Wall-E
**Epic:** Epic 3 - Authentication & Authorization
**Triggering Story:** Story 3.2 (Keycloak OIDC Discovery and JWKS Integration)
**Change Type:** Story Sequencing Correction
**Scope:** Minor (Direct Adjustment)

---

## 1. Issue Summary

### Problem Statement

Story 3.10 (Testcontainers Keycloak for Integration Tests) is incorrectly sequenced as the 10th story in Epic 3, positioned AFTER Stories 3.3-3.9. This creates a critical testing gap that violates Constitutional TDD principles and prevents real test feedback during implementation of 8 consecutive stories.

### Discovery Context

**When Discovered:** During Story 3.2 implementation (2025-11-09)

**How Discovered:** Story 3.2 acceptance criteria analysis revealed:
- AC5: "Integration test validates signature verification **with Keycloak-signed JWT**"
- AC6: "Test uses **Testcontainers Keycloak (26.4.2)**"

These criteria require Testcontainers Keycloak infrastructure, which is only provided by Story 3.10.

### Evidence

**Story Dependencies:**

| Story | Requires Keycloak JWTs? | Current Sequence Position |
|-------|------------------------|---------------------------|
| 3.2 | ✅ YES (AC5, AC6) | Position 2 |
| 3.3 | ✅ YES (AC7: "Integration test with real Keycloak tokens") | Position 3 |
| 3.4 | ✅ YES (AC7: "Integration test with invalid tokens") | Position 4 |
| 3.5 | ✅ YES (AC6: "Wrong issuer → 401") | Position 5 |
| 3.6 | ✅ YES (AC6: "Integration test: revoke → subsequent reject") | Position 6 |
| 3.7 | ✅ YES (AC6: "Integration test with injection payloads") | Position 7 |
| 3.8 | ✅ YES (AC7: "End-to-end test with valid/invalid JWTs") | Position 8 |
| 3.9 | ✅ YES (AC6: "Integration tests for all permission combinations") | Position 9 |
| **3.10** | ✅ **PROVIDES** Testcontainers Keycloak | **Position 10** ❌ |

**Architectural Violation:**

From `docs/architecture.md` Section 11 (Testing Strategy):
> "Testcontainers for stateful dependencies - PostgreSQL, Redis, **Keycloak** (H2 explicitly forbidden)"

From `CLAUDE.md` Critical Testing Rules:
> "Use Testcontainers for stateful dependencies - PostgreSQL, Redis, **Keycloak**"

**Constitutional TDD Violation:**

Current sequencing forces Stories 3.2-3.9 to either:
1. **Mock Keycloak JWT validation** (violates "Real Dependencies via Testcontainers")
2. **Skip integration tests** (violates Constitutional TDD "test-first mandatory")
3. **Implement configuration without validation** (violates "Red-Green-Refactor cycle")

---

## 2. Impact Analysis

### Epic Impact: Epic 3 - Authentication & Authorization

**Epic Scope:** ✅ **UNCHANGED**
- Same 12 stories
- Same acceptance criteria
- Same deliverables

**Epic Sequencing:** ⚠️ **CHANGES REQUIRED**

**Current Sequence (INCORRECT):**
```
3.1  Spring Security OAuth2 Foundation ✅ DONE
3.2  Keycloak OIDC Discovery         ⏳ IN-PROGRESS
3.3  JWT Format & Signature
3.4  Claims & Time Validation
3.5  Issuer, Audience, Role
3.6  Redis Revocation
3.7  User & Injection Detection
3.8  Complete JWT Integration
3.9  RBAC API Endpoints
3.10 Testcontainers Keycloak         ← PROVIDES TEST INFRASTRUCTURE
3.11 Keycloak ppc64le
3.12 Security Fuzz Testing
```

**Proposed Sequence (CORRECT):**
```
3.1  Spring Security OAuth2 Foundation ✅ DONE
3.2  Keycloak OIDC Discovery         ⏳ IN-PROGRESS
3.10 Testcontainers Keycloak         ← MOVE HERE (new position 3)
3.3  JWT Format & Signature           (renumber to 3.4)
3.4  Claims & Time Validation         (renumber to 3.5)
3.5  Issuer, Audience, Role           (renumber to 3.6)
3.6  Redis Revocation                 (renumber to 3.7)
3.7  User & Injection Detection       (renumber to 3.8)
3.8  Complete JWT Integration         (renumber to 3.9)
3.9  RBAC API Endpoints                (renumber to 3.10)
3.11 Keycloak ppc64le                  (unchanged)
3.12 Security Fuzz Testing             (unchanged)
```

### Story Impact

**Stories Requiring Updates:**

1. **Story 3.2** (current): Update AC5, AC6 to reference Story 3.10 correctly
2. **Story 3.10** (resequenced): Update prerequisites (3.8 → 3.2)
3. **Stories 3.3-3.9** (renumbered): Update story IDs and file names

### Artifact Conflicts

**PRD Impact:** ✅ **NONE**
- FR006 (Authentication, Security, and Compliance) unchanged

**Architecture Impact:** ✅ **NONE**
- Section 11 (Testing Strategy) already mandates Testcontainers Keycloak
- Sequencing correction ALIGNS with architecture, doesn't conflict

**Tech Spec Impact:** ⚠️ **MINOR UPDATES**
- docs/tech-spec-epic-3.md: Update story sequencing section
- Acceptance criteria mapping unchanged

**Sprint Status Impact:** ⚠️ **REQUIRES UPDATE**
- docs/sprint-status.yaml: Reorder Story 3.10 → position 3
- Story files: Rename 3.3-3.9 → 3.4-3.10

### Technical Impact

**Code Impact:** ✅ **NONE**
- No implemented code requires changes
- Story 3.1 ✅ complete (no dependencies on Story 3.10)
- Story 3.2 ⏳ in-progress (will benefit from Story 3.10)

**Infrastructure Impact:** ✅ **POSITIVE**
- Early Testcontainers setup enables better testing for Stories 3.3-3.9
- Aligns with "Real Dependencies via Testcontainers" principle

---

## 3. Recommended Approach

### Selected Path: **Option 1 - Direct Adjustment**

**Justification:**
1. **Low Effort:** Story resequencing only (no scope changes)
2. **Low Risk:** No technical implementation changes required
3. **High Value:** Enables Constitutional TDD compliance for Stories 3.3-3.9
4. **Timeline Neutral:** Same total stories, same total effort
5. **Team Momentum:** Improves developer experience with real test feedback

**Effort Estimate:** 1-2 hours
- Update sprint-status.yaml (15 min)
- Rename story files 3.3-3.9 → 3.4-3.10 (30 min)
- Update prerequisites in story files (30 min)
- Update tech-spec-epic-3.md sequencing (15 min)

**Risk Assessment:** **LOW**
- No code changes required
- No scope changes
- Administrative changes only
- Story 3.2 already in-progress can continue seamlessly

**Timeline Impact:** ✅ **NONE**
- Same number of stories
- Same acceptance criteria
- Same total implementation effort

---

## 4. Detailed Change Proposals

### Change 1: Resequence Story 3.10 → Position 3

**File:** `docs/sprint-status.yaml`

**OLD (Lines 72-84):**
```yaml
3-1-spring-security-oauth2: done
3-2-keycloak-oidc-jwks: in-progress
3-3-jwt-format-signature: ready-for-dev
3-4-jwt-claims-time-validation: ready-for-dev
3-5-issuer-audience-role: ready-for-dev
3-6-redis-revocation-cache: ready-for-dev
3-7-user-injection-detection: ready-for-dev
3-8-complete-jwt-integration: ready-for-dev
3-9-rbac-api-endpoints: ready-for-dev
3-10-testcontainers-keycloak: ready-for-dev
3-11-keycloak-ppc64le: ready-for-dev
3-12-security-fuzz-testing: ready-for-dev
```

**NEW:**
```yaml
3-1-spring-security-oauth2: done
3-2-keycloak-oidc-jwks: in-progress
3-3-testcontainers-keycloak: ready-for-dev        # ← MOVED from position 10
3-4-jwt-format-signature: ready-for-dev           # ← RENUMBERED from 3.3
3-5-jwt-claims-time-validation: ready-for-dev     # ← RENUMBERED from 3.4
3-6-issuer-audience-role: ready-for-dev           # ← RENUMBERED from 3.5
3-7-redis-revocation-cache: ready-for-dev         # ← RENUMBERED from 3.6
3-8-user-injection-detection: ready-for-dev       # ← RENUMBERED from 3.7
3-9-complete-jwt-integration: ready-for-dev       # ← RENUMBERED from 3.8
3-10-rbac-api-endpoints: ready-for-dev            # ← RENUMBERED from 3.9
3-11-keycloak-ppc64le: ready-for-dev              # ← UNCHANGED
3-12-security-fuzz-testing: ready-for-dev         # ← UNCHANGED
```

**Rationale:** Move Testcontainers setup immediately after OIDC configuration to enable real JWT testing for all subsequent stories.

---

### Change 2: Rename Story Files (3.3-3.9 → 3.4-3.10)

**File Operations:**

```bash
# Rename story files to reflect new numbering
mv docs/stories/epic-3/story-3.3-jwt-format-signature.md \
   docs/stories/epic-3/story-3.4-jwt-format-signature.md

mv docs/stories/epic-3/story-3.4-jwt-claims-time-validation.md \
   docs/stories/epic-3/story-3.5-jwt-claims-time-validation.md

mv docs/stories/epic-3/story-3.5-issuer-audience-role.md \
   docs/stories/epic-3/story-3.6-issuer-audience-role.md

mv docs/stories/epic-3/story-3.6-redis-revocation-cache.md \
   docs/stories/epic-3/story-3.7-redis-revocation-cache.md

mv docs/stories/epic-3/story-3.7-user-injection-detection.md \
   docs/stories/epic-3/story-3.8-user-injection-detection.md

mv docs/stories/epic-3/story-3.8-complete-jwt-integration.md \
   docs/stories/epic-3/story-3.9-complete-jwt-integration.md

mv docs/stories/epic-3/story-3.9-rbac-api-endpoints.md \
   docs/stories/epic-3/story-3.10-rbac-api-endpoints.md

mv docs/stories/epic-3/story-3.10-testcontainers-keycloak.md \
   docs/stories/epic-3/story-3.3-testcontainers-keycloak.md

# Rename context files
mv docs/stories/epic-3/story-3.3-context.xml \
   docs/stories/epic-3/story-3.4-context.xml

mv docs/stories/epic-3/story-3.4-context.xml \
   docs/stories/epic-3/story-3.5-context.xml

# ... (continue for all context files)

mv docs/stories/epic-3/story-3.10-context.xml \
   docs/stories/epic-3/story-3.3-context.xml
```

**Rationale:** File names must match sprint-status.yaml story keys for workflow automation.

---

### Change 3: Update Story 3.3 (Testcontainers) Prerequisites

**File:** `docs/stories/epic-3/story-3.3-testcontainers-keycloak.md` (renamed from 3.10)

**OLD:**
```markdown
## Prerequisites

**Story 3.8** - Complete 10-Layer JWT Validation Integration
```

**NEW:**
```markdown
## Prerequisites

**Story 3.2** - Keycloak OIDC Discovery and JWKS Integration
```

**Rationale:** Story 3.3 (Testcontainers) now depends only on OIDC configuration (Story 3.2), not on complete JWT validation.

---

### Change 4: Update Stories 3.4-3.10 Internal Story IDs

**Files:** All renamed story files (3.4-3.10)

**Changes Required:**

Each story file header:
```markdown
OLD: # Story 3.3: JWT Format and Signature Validation
NEW: # Story 3.4: JWT Format and Signature Validation

OLD: **Epic:** Epic 3 - Authentication & Authorization
     **Story ID:** 3.3
NEW: **Epic:** Epic 3 - Authentication & Authorization
     **Story ID:** 3.4
```

**Rationale:** Story IDs in file content must match file names for traceability.

---

### Change 5: Update Tech Spec Story Sequencing

**File:** `docs/tech-spec-epic-3.md`

**OLD (Lines 1280-1350):**
```markdown
**Story 3.2: Keycloak OIDC Discovery** (7 ACs)
**Story 3.3: JWT Format & Signature** (7 ACs)
**Story 3.4: Claims & Time Validation** (7 ACs)
...
**Story 3.10: Testcontainers Keycloak** (9 ACs)
```

**NEW:**
```markdown
**Story 3.2: Keycloak OIDC Discovery** (7 ACs)
**Story 3.3: Testcontainers Keycloak** (9 ACs)  ← MOVED
**Story 3.4: JWT Format & Signature** (7 ACs)   ← RENUMBERED
**Story 3.5: Claims & Time Validation** (7 ACs) ← RENUMBERED
...
**Story 3.10: RBAC API Endpoints** (6 ACs)      ← RENUMBERED
```

**Rationale:** Tech spec must reflect actual story sequence for implementation guidance.

---

### Change 6: Update Story Context File References

**Files:** `docs/stories/epic-3/story-3.4-context.xml` through `story-3.10-context.xml`

**Changes:**
- Update `<story-id>` XML tags to match new numbering
- Update `<related-stories>` references

**Example:**
```xml
OLD: <story-id>3.3</story-id>
NEW: <story-id>3.4</story-id>

OLD: <next-story>Story 3.4: Claims and Time Validation</next-story>
NEW: <next-story>Story 3.5: Claims and Time Validation</next-story>
```

---

## 5. Implementation Handoff

### Change Scope Classification

**MINOR** - Direct implementation by development team

### Handoff Recipients

**Primary:** Amelia (Dev Agent)
**Backup:** Wall-E (manual execution if needed)

### Implementation Tasks

**Task 1: File Renaming (15 min)**
- Rename story-3.3.md → story-3.4.md (and .3-.9 accordingly)
- Rename story-3.10.md → story-3.3.md
- Rename context files (story-3.3-context.xml → story-3.4-context.xml, etc.)

**Task 2: Update sprint-status.yaml (10 min)**
- Reorder story keys (3.10 → position 3)
- Renumber 3.3-3.9 → 3.4-3.10

**Task 3: Update Story Content (30 min)**
- Update story IDs in headers (# Story 3.X → # Story 3.Y)
- Update prerequisites (Story 3.10: prerequisite 3.8 → 3.2)
- Update context file story-id tags

**Task 4: Update Tech Spec (15 min)**
- Resequence story list in tech-spec-epic-3.md
- Verify acceptance criteria mapping still correct

**Task 5: Validation (10 min)**
- Run workflow-status to verify sprint-status.yaml correctness
- Verify all story files load without errors
- Confirm story-context references resolve correctly

**Total Effort:** 1h 20m

### Success Criteria

- ✅ sprint-status.yaml: Story 3.10 at position 3
- ✅ Story files: All renamed and IDs updated
- ✅ Context files: All references updated
- ✅ Tech spec: Sequencing matches sprint-status.yaml
- ✅ No broken file references
- ✅ Story 3.3 (Testcontainers) ready-for-dev
- ✅ Stories 3.4-3.10 ready-for-dev with correct prerequisites

### Next Steps After Implementation

1. **Complete Story 3.2** (current in-progress)
2. **Implement Story 3.3** (Testcontainers Keycloak) - NEW NEXT STORY
3. **Continue Epic 3** with Stories 3.4-3.10 (now with real JWT testing)

---

## 6. Alternative Approaches Considered

### Alternative 1: Keep Current Sequence, Skip Testcontainers

**Description:** Continue Stories 3.2-3.9 without Testcontainers, add integration tests in Story 3.10

**Pros:**
- No file renaming needed
- No administrative overhead

**Cons:**
- ❌ Violates Constitutional TDD (no real dependency testing)
- ❌ Violates Architecture mandate (Testcontainers required)
- ❌ Stories 3.2-3.9 cannot validate JWT signature with real Keycloak
- ❌ Bug discovery delayed until Story 3.10
- ❌ Potential rework of Stories 3.2-3.9 after Testcontainers integration

**Decision:** ❌ REJECTED - Architectural violation, high rework risk

### Alternative 2: Mock Keycloak for Stories 3.2-3.9

**Description:** Use WireMock or similar to mock Keycloak JWKS/token endpoints

**Pros:**
- No story resequencing
- Some test coverage without Testcontainers

**Cons:**
- ❌ Violates "Real Dependencies via Testcontainers" principle
- ❌ Mock behavior may not match real Keycloak (false confidence)
- ❌ Mocks require maintenance when Keycloak updates
- ❌ Architecture explicitly forbids mocking security components

**Decision:** ❌ REJECTED - Architectural violation, false confidence risk

### Alternative 3: Implement Story 3.10 Out-of-Sequence (Ad-Hoc)

**Description:** Implement Story 3.10 now, keep numbering unchanged

**Pros:**
- Provides Testcontainers immediately
- No file renaming

**Cons:**
- ❌ Breaks story sequencing (3.1, 3.2, 3.10, 3.3...)
- ❌ Confusing for tracking and automation
- ❌ Prerequisites backward reference (Story 3.10 → Story 3.8 not yet done)
- ❌ Sprint status becomes misleading

**Decision:** ❌ REJECTED - Breaks workflow automation, confusing sequencing

---

## 7. Recommendation Summary

### Approved Approach: **Direct Adjustment (Story Resequencing)**

**Benefits:**
1. ✅ **Constitutional TDD Compliance:** Real Keycloak JWTs from Story 3.3 onward
2. ✅ **Early Bug Detection:** Integration tests catch issues in Stories 3.4-3.10
3. ✅ **Architectural Alignment:** Matches "Testcontainers for stateful dependencies"
4. ✅ **Developer Experience:** Real test feedback, not mock-based false confidence
5. ✅ **Low Risk:** Administrative changes only, no code impact
6. ✅ **Timeline Neutral:** No schedule impact

**Trade-offs:**
- ⚠️ File renaming overhead (1h administrative work)
- ⚠️ Story context files need XML updates
- ⚠️ Git history shows renamed files (acceptable)

**Decision Confidence:** ⭐⭐⭐⭐⭐ (5/5) - Clear architectural mandate, low risk, high value

---

## 8. Approval Required

This Sprint Change Proposal requires approval from: **Wall-E (Product Owner)**

**Approval Statement:**
> "I approve the resequencing of Epic 3 stories to move Story 3.10 (Testcontainers Keycloak) to position 3, immediately after Story 3.2, with renumbering of Stories 3.3-3.9 to 3.4-3.10 accordingly."

**Post-Approval Actions:**
1. Amelia (Dev Agent) implements file renaming and updates
2. Story 3.2 continues to completion
3. Story 3.3 (Testcontainers) becomes next story
4. Epic 3 proceeds with Constitutional TDD compliance

---

**Document saved:** `docs/sprint-change-proposal-2025-11-09.md`
