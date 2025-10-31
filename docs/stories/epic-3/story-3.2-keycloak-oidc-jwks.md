# Story 3.2: Keycloak OIDC Discovery and JWKS Integration

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
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
5. ✅ Integration test validates signature verification with Keycloak-signed JWT
6. ✅ Test uses Testcontainers Keycloak (26.4.2)
7. ✅ JWKS rotation handled gracefully (cache invalidation)

---

## Prerequisites

**Story 3.1** - Spring Security OAuth2 Resource Server Foundation

---

## References

- PRD: FR006
- Architecture: Section 8 (Keycloak OIDC Integration)
- Tech Spec: Section 6.1 (Keycloak ↔ EAF Integration)
