# Story 3.1: Spring Security OAuth2 Resource Server Foundation

**Epic:** Epic 3 - Authentication & Authorization
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR006 (Authentication, Security, and Compliance), NFR002 (Security)

---

## User Story

As a framework developer,
I want Spring Security configured as an OAuth2 Resource Server,
So that JWT-based authentication is enforced on all API endpoints.

---

## Acceptance Criteria

1. ✅ framework/security module created with Spring Security OAuth2 dependencies
2. ✅ SecurityConfiguration.kt configures HTTP security with JWT authentication
3. ✅ OAuth2 Resource Server configured with Keycloak issuer URI
4. ✅ All API endpoints require authentication by default (except /actuator/health)
5. ✅ Integration test validates unauthenticated requests return 401 Unauthorized
6. ✅ Valid JWT allows API access
7. ✅ Security filter chain documented

---

## Prerequisites

**Epic 2 complete** - REST API foundation must exist

---

## Technical Notes

### Security Configuration

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwkSetUri("http://keycloak:8080/realms/eaf/protocol/openid-connect/certs")
                }
            }
            .csrf { it.disable() }  // Stateless API, CSRF not needed

        return http.build()
    }
}
```

### Application Configuration

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/eaf
          jwk-set-uri: http://keycloak:8080/realms/eaf/protocol/openid-connect/certs
```

---

## Implementation Checklist

- [ ] Create framework/security module
- [ ] Add Spring Security OAuth2 Resource Server dependencies
- [ ] Create SecurityConfiguration.kt with @EnableWebSecurity
- [ ] Configure OAuth2 Resource Server with Keycloak OIDC
- [ ] Configure security filter chain (all endpoints authenticated)
- [ ] Allow /actuator/health without authentication
- [ ] Write integration test: unauthenticated request → 401
- [ ] Write integration test: valid JWT → 200 OK
- [ ] Document security filter chain
- [ ] Commit: "Add Spring Security OAuth2 Resource Server foundation"

---

## References

- PRD: FR006, NFR002
- Architecture: Section 16 (Security Architecture)
- Tech Spec: Section 3 (FR006), Section 7.1 (10-Layer JWT Validation)
