# ADR-002: IdP-Agnostic Authentication & Modular Starters

**Status:** Accepted
**Date:** 2025-11-25
**Author:** DVMM Team
**Deciders:** Architecture Team
**Origin:** First Principles Analysis

---

## Context

The original architecture was tightly coupled to Keycloak. First Principles Analysis showed:
1. Not all products use Keycloak (Azure AD, Okta in other enterprises)
2. A monolithic starter forces products to load all modules

## Decision

### 1. Identity Provider Abstraction

```kotlin
// eaf-auth/src/main/kotlin/com/eaf/auth/IdentityProvider.kt
package com.eaf.auth

/**
 * Abstraction for identity providers (Keycloak, Azure AD, Okta, etc.)
 * Products choose their implementation via eaf-auth-{provider} modules.
 */
interface IdentityProvider {
    /**
     * Validates an access token and extracts claims.
     * @throws InvalidTokenException if token is invalid or expired
     */
    suspend fun validateToken(token: String): TokenClaims

    /**
     * Retrieves user information from the IdP.
     * May use token introspection or userinfo endpoint.
     */
    suspend fun getUserInfo(accessToken: String): UserInfo

    /**
     * Checks if the user has the required role.
     */
    fun hasRole(claims: TokenClaims, role: String): Boolean

    /**
     * Extracts tenant ID from token claims.
     * @throws MissingTenantClaimException if tenant claim is missing
     */
    fun extractTenantId(claims: TokenClaims): TenantId
}

// eaf-auth/src/main/kotlin/com/eaf/auth/TokenClaims.kt
data class TokenClaims(
    val subject: String,
    val email: String?,
    val roles: Set<String>,
    val tenantId: TenantId?,
    val expiresAt: Instant,
    val customClaims: Map<String, Any> = emptyMap()
)

// eaf-auth/src/main/kotlin/com/eaf/auth/UserInfo.kt
data class UserInfo(
    val userId: UserId,
    val email: String,
    val displayName: String,
    val tenantId: TenantId,
    val roles: Set<String>,
    val attributes: Map<String, Any> = emptyMap()
)
```

### 2. Keycloak Implementation (eaf-auth-keycloak)

```kotlin
// eaf-auth-keycloak/src/main/kotlin/com/eaf/auth/keycloak/KeycloakIdentityProvider.kt
package com.eaf.auth.keycloak

@Component
@ConditionalOnProperty("eaf.auth.provider", havingValue = "keycloak")
class KeycloakIdentityProvider(
    private val keycloakClient: KeycloakClient,
    private val jwtDecoder: JwtDecoder
) : IdentityProvider {

    override suspend fun validateToken(token: String): TokenClaims {
        val jwt = jwtDecoder.decode(token)
        return TokenClaims(
            subject = jwt.subject,
            email = jwt.getClaimAsString("email"),
            roles = extractRoles(jwt),
            tenantId = jwt.getClaimAsString("tenant_id")?.let { TenantId(it) },
            expiresAt = jwt.expiresAt!!
        )
    }

    private fun extractRoles(jwt: Jwt): Set<String> {
        // Keycloak stores roles in realm_access.roles
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        return (realmAccess?.get("roles") as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet() ?: emptySet()
    }

    // ... further implementations
}
```

### 3. Modular Starters

Instead of a monolith, products can selectively choose:

```kotlin
// Minimal product (Core + Observability only)
implementation("com.eaf:eaf-starter-core")

// DVMM (Full Stack with Keycloak)
implementation("com.eaf:eaf-starter-core")
implementation("com.eaf:eaf-starter-cqrs")
implementation("com.eaf:eaf-starter-eventsourcing")
implementation("com.eaf:eaf-starter-tenant")
implementation("com.eaf:eaf-starter-auth")
implementation("com.eaf:eaf-auth-keycloak")

// Future product with Azure AD
implementation("com.eaf:eaf-starter-auth")
implementation("com.eaf:eaf-auth-azure-ad")  // To be implemented later
```

## Consequences

### Positive

- Products can switch IdP without EAF changes
- Smaller deployment artifacts through à-la-carte starters
- Testable with Mock-IdentityProvider
- Future-proof for multi-cloud scenarios

### Negative

- More modules to manage
- Abstraction must cover all IdP features

### Mitigation

- `customClaims` map for IdP-specific extensions
- Regular reviews for new IdP requirements

## References

- [ADR-001: EAF Framework-First Architecture](ADR-001-eaf-framework-first-architecture.md)
