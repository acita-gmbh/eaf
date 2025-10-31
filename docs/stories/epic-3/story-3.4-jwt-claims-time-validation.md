# Story 3.4: JWT Claims Schema and Time-Based Validation (Layers 3-5)

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006, NFR002

---

## User Story

As a framework developer,
I want JWT claims schema and time-based validation,
So that tokens have required claims and are not expired or used before valid.

---

## Acceptance Criteria

1. ✅ Layer 3: Algorithm validation (RS256 only, hardcoded)
2. ✅ Layer 4: Claim schema validation (required: sub, iss, aud, exp, iat, tenant_id, roles)
3. ✅ Layer 5: Time-based validation (exp, iat, nbf with 30s clock skew tolerance)
4. ✅ Missing or invalid claims rejected with 401 and specific error message
5. ✅ Expired tokens rejected with 401
6. ✅ Unit tests for each validation layer
7. ✅ Integration test with intentionally invalid tokens (missing claims, expired)

---

## Prerequisites

**Story 3.3** - JWT Format and Signature Validation

---

## References

- PRD: FR006, NFR002
- Architecture: Section 16 (Layers 3-5)
- Tech Spec: Section 7.1 (10-Layer JWT)
