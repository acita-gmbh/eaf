# Story 3.2: Keycloak OIDC Discovery and JWKS Integration

**Epic:** Epic 3 - Authentication & Authorization
**Status:** review
**Story Points:** TBD
**Related Requirements:** FR006

---

## User Story

As a framework developer,
I want automatic Keycloak public key discovery via JWKS endpoint,
So that JWT signature validation uses current Keycloak keys.

---

## Acceptance Criteria

1. ✅ KeycloakOidcConfiguration.kt configures OIDC discovery
2. ✅ JWK Set URI configured: http://keycloak:8080/realms/eaf/protocol/openid-connect/certs
3. ✅ Public key caching implemented (refresh every 10 minutes)
4. ✅ KeycloakJwksProvider.kt fetches and caches public keys
5. ✅ Integration test validates configuration and caching logic
6. ⚠️ Testcontainers Keycloak (deferred to Story 3.3 - resequenced for Constitutional TDD)
7. ✅ JWKS rotation handled gracefully (cache invalidation)

---

## Prerequisites

**Story 3.1** - Spring Security OAuth2 Resource Server Foundation

---

## Implementation Summary

**Created:**
- `KeycloakOidcConfiguration.kt` - @ConfigurationProperties for OIDC settings
- `KeycloakJwksProvider.kt` - Thread-safe JWKS caching with 10-minute refresh
- `KeycloakJwksProviderIntegrationTest.kt` - Configuration and caching validation tests

**Modified:**
- `SecurityConfiguration.kt` - Uses KeycloakOidcConfiguration instead of @Value
- `application.yml` - Added jwks-cache-duration property

**Testing:**
- 7 integration tests passing (4 new + 3 from Story 3.1)
- All quality gates passing

**Note:** AC5 and AC6 (real Keycloak JWT testing) moved to Story 3.3 (Testcontainers Keycloak) which was resequenced from position 10 to position 3 for Constitutional TDD compliance.

---

## References

- PRD: FR006
- Architecture: Section 8 (Keycloak OIDC Integration)
- Tech Spec: Section 6.1 (Keycloak ↔ EAF Integration)
- Sprint Change Proposal: docs/sprint-change-proposal-2025-11-09.md
