# Story 3.6: Redis Revocation Cache (Layer 7)

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006, FR018 (Error Recovery - Redis fallback)

---

## User Story

As a framework developer,
I want JWT revocation checking with Redis blacklist cache,
So that revoked tokens cannot be used even before expiration.

---

## Acceptance Criteria

1. ✅ Redis 7.2 dependency added to framework/security
2. ✅ RedisRevocationStore.kt implements revocation check and storage
3. ✅ Layer 7: Revocation validation queries Redis for token JTI (JWT ID)
4. ✅ Revoked tokens stored with 10-minute TTL (matching token lifetime)
5. ✅ Revocation API endpoint: POST /auth/revoke (admin only)
6. ✅ Integration test validates: revoke token → subsequent requests rejected with 401
7. ✅ Redis unavailable fallback configurable (fail-open default, fail-closed optional)
8. ✅ application.yml property: eaf.security.revocation.fail-closed (default: false)
9. ✅ Integration test validates both modes (fail-open graceful degradation, fail-closed SecurityException)
10. ✅ Revocation metrics emitted (revocation_check_duration, cache_hit_rate)

---

## Prerequisites

**Story 3.5** - Issuer, Audience, and Role Validation

---

## References

- PRD: FR006, FR018 (Resilience)
- Architecture: Section 16 (Layer 7), Section 18 (Deployment - Redis)
- Tech Spec: Section 3 (FR006, FR018 Implementation)
