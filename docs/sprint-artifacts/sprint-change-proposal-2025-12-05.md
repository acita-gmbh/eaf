# Sprint Change Proposal: VCF SDK Migration

**Date:** 2025-12-05
**Trigger:** Technical Debt / Deployment Blocker
**Status:** Draft

## 1. Issue Summary

The initial implementation of VMware integration (Story 3.1) used `yavijava`, which is deprecated (last release 2017). The proposed replacement, "vSphere Automation SDK", requires manual local installation of JARs, creating a significant CI/CD and onboarding blocker.

**Problem:**
1.  `yavijava` is unmaintained and poses security risks.
2.  `vSphere Automation SDK` is not on Maven Central, breaking standard build flows.
3.  `VcenterAdapter` lacks dedicated integration tests, relying on a stub (`VcsimAdapter`) for testing.

## 2. Impact Analysis

| Artifact | Impact | Action |
|----------|--------|--------|
| **Story 3.1.1** | Core Definition | **UPDATED:** Mandate VCF SDK (Maven Central) and new Integration Test. |
| **Tech Spec Epic 3** | Library Selection | **UPDATE:** Replace Automation SDK with VCF SDK. |
| **Epics.md** | Story Definition | **UPDATE:** Align Story 3.1.1 ACs with new direction. |
| **PRD** | None | No functional change for users. |

## 3. Recommended Approach

**Direct Adjustment:**
1.  Adopt **VCF SDK Java 9.0.0.0** as the standard. It includes necessary vSphere libraries and is available on Maven Central.
2.  Require creation of `VcenterAdapterIntegrationTest.kt` to validate the real adapter against `VcsimContainer` (using `VcsimTestFixture`).
3.  Update all planning documents to reflect this decision.

**Effort:** Low (Configuration change + Test creation).
**Risk:** Low (Solves deployment risk).

## 4. Detailed Change Proposals

### 4.1 Tech Spec Epic 3 Updates

**File:** `docs/sprint-artifacts/tech-spec-epic-3.md`

**Change 1: Section 7.2 New Libraries**
*   **OLD:** `vSphere Automation SDK | 8.0.3.0 | ...`
*   **NEW:** `VCF SDK Java | 9.0.0.0 | ... | Official VCF SDK (Maven Central) - Includes vSphere Automation`

**Change 2: Section 10 References**
*   **OLD:** `[vSphere Automation SDK](...)`
*   **NEW:** `[VCF SDK Java](https://mvnrepository.com/artifact/com.vmware.sdk/vcf-sdk-bom)`

### 4.2 Epics.md Updates

**File:** `docs/epics.md`

**Change 1: Story 3.1.1 ACs**
*   Update AC-3.1.1.1 to specify "VCF SDK (Maven Central)".
*   Add AC for "VcenterAdapterIntegrationTest".

## 5. Implementation Handoff

**Scope:** **Minor**
*   **Recipient:** Development Team
*   **Action:** Implement Story 3.1.1 with new constraints.

---

**Approval Required:**
Do you approve this change proposal?
