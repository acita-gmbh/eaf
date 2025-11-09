# Security Architecture - Spring Security OAuth2 Resource Server

## Overview

The EAF framework uses Spring Security as an OAuth2 Resource Server with JWT-based authentication. All API endpoints require valid JWT tokens from Keycloak, except explicitly whitelisted public endpoints.

## Security Configuration (Story 3.1)

### SecurityModule

The security module is enforced with Spring Modulith boundaries:

```kotlin
@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core"]
)
class SecurityModule
```

**Boundary Rules:**
- `framework/security` can only depend on `framework/core`
- Enforced at compile-time via Konsist and Spring Modulith

### Security Filter Chain

The security filter chain is configured in `SecurityConfiguration.kt`:

**Default Policy:** All endpoints require authentication

**Exceptions:**
- `/actuator/health` - Public health check endpoint (no authentication required)

**Configuration:**

```kotlin
@Bean
open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
        .authorizeHttpRequests { auth ->
            auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.jwt { }
        }
        .csrf { it.disable() } // Stateless API, CSRF not needed

    return http.build()
}
```

### JWT Decoder

JWT tokens are validated using Keycloak's JWKS endpoint:

```kotlin
@Bean
open fun jwtDecoder(): JwtDecoder {
    return NimbusJwtDecoder
        .withJwkSetUri(keycloakJwksUri)
        .build()
}
```

**Validation performed:**
1. JWT signature verification (RS256)
2. Standard JWT claims validation (exp, iat, iss, aud)
3. JWKS public key retrieval from Keycloak

### Configuration Properties

Security configuration is externalized in `application.yml`:

```yaml
eaf:
  security:
    jwt:
      issuer-uri: ${KEYCLOAK_ISSUER_URI:http://keycloak:8080/realms/eaf}
      jwks-uri: ${KEYCLOAK_JWKS_URI:http://keycloak:8080/realms/eaf/protocol/openid-connect/certs}
      audience: ${JWT_AUDIENCE:eaf-api}

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${eaf.security.jwt.issuer-uri}
          jwk-set-uri: ${eaf.security.jwt.jwks-uri}
```

**Environment Variables:**
- `KEYCLOAK_ISSUER_URI` - Keycloak realm issuer URI
- `KEYCLOAK_JWKS_URI` - JWKS endpoint for public key retrieval
- `JWT_AUDIENCE` - Expected JWT audience claim

## Security Flow

1. **Request arrives** → Security filter chain intercepts
2. **Public endpoint check** → If `/actuator/health`, allow without authentication
3. **JWT extraction** → Extract JWT from `Authorization: Bearer <token>` header
4. **JWT validation** → NimbusJwtDecoder validates signature and claims
5. **Authentication** → On success, request proceeds; on failure, return 401 Unauthorized

## Testing

Integration tests validate:
- ✅ Unauthenticated requests return 401 Unauthorized
- ✅ Public endpoints are accessible without authentication
- ✅ Security configuration loads correctly

**Test Location:** `framework/security/src/integration-test/kotlin/.../SecurityConfigurationIntegrationTest.kt`

## Next Steps (Subsequent Stories)

Story 3.1 establishes the foundation. Future stories will add:

- **Story 3.2** - Keycloak OIDC Discovery and JWKS Integration
- **Story 3.3** - JWT Format and Signature Validation (enhanced)
- **Story 3.4** - JWT Claims and Time-based Validation
- **Story 3.5** - Issuer, Audience, and Role Validation
- **Story 3.6** - Redis-backed Token Revocation Cache
- **Story 3.7** - User Validation and Injection Detection
- **Story 3.8** - Complete 10-Layer JWT Validation
- **Story 3.9** - RBAC for API Endpoints
- **Story 3.10** - Testcontainers Keycloak Integration

## References

- **PRD:** FR006 (Authentication, Security, and Compliance), NFR002 (Security)
- **Architecture:** Section 11 (Security Stack), Section 12 (10-Layer JWT Validation)
- **Tech Spec:** Epic 3 Technical Specification
- **Story:** docs/stories/epic-3/story-3.1-spring-security-oauth2.md
