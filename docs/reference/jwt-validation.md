# 10-Layer JWT Validation System

**Enterprise Application Framework v1.0**  
**Story:** 3.9 - Complete 10-Layer JWT Validation Integration  
**Last Updated:** 2025-11-13

---

## Overview

EAF implements a defense-in-depth JWT validation system with **10 layers** of security checks. Every API request passes through all layers sequentially, with **fail-fast** behavior ensuring that any validation failure immediately rejects the request.

**Performance:** All 10 layers complete in **~0.17ms average** with Nullable Pattern (target: <50ms).

---

## 10-Layer Validation Sequence

### Layer 1: Format Validation
- **Implementation:** Spring Security `BearerTokenAuthenticationFilter`
- **Validates:** JWT structure (header.payload.signature), 3 parts, non-empty
- **Failure:** `401 Unauthorized` - "Missing or malformed Authorization header"

### Layer 2: Signature Validation  
- **Implementation:** `NimbusJwtDecoder` with Keycloak JWKS
- **Validates:** RS256 cryptographic signature using Keycloak public key
- **Failure:** `401 Unauthorized` - "JWT signature validation failed"
- **Performance:** ~5-15ms (includes JWKS fetch on cache miss)

### Layer 3: Algorithm Validation
- **Implementation:** `JwtAlgorithmValidator`
- **Validates:** Only RS256 allowed, rejects HS256/HS384/HS512/none
- **Failure:** `401 Unauthorized` - "JWT algorithm 'HS256' not allowed"
- **Security:** Prevents CVE-2018-0114 (algorithm confusion attack)
- **Performance:** ~0.001ms
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer3_algorithm"}`

### Layer 4: Claim Schema Validation
- **Implementation:** `JwtClaimSchemaValidator`
- **Validates:** Required claims present (sub, iss, exp, iat, jti), non-blank
- **Failure:** `401 Unauthorized` - "JWT missing required claims: jti, sub"
- **Performance:** ~0.01ms
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer4_claim_schema"}`

### Layer 5: Time-Based Validation
- **Implementation:** `JwtTimeBasedValidator`
- **Validates:** exp/iat/nbf with 30s clock skew tolerance
- **Failure:** `401 Unauthorized` - "JWT expired at ..."
- **Performance:** ~0.015ms
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer5_time_based"}`

### Layer 6: Issuer and Audience Validation
- **Implementation:** `JwtIssuerValidator`, `JwtAudienceValidator`
- **Validates:** Issuer matches Keycloak realm, Audience includes API identifier
- **Failure:** `401 Unauthorized` - "Invalid issuer: ..."
- **Performance:** ~0.02ms combined
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer6_issuer"|"layer6_audience"}`

### Layer 7: Token Revocation Check
- **Implementation:** `JwtRevocationValidator` with `RedisRevocationStore`
- **Validates:** JTI not in Redis blacklist
- **Failure:** `401 Unauthorized` - "JWT has been revoked"
- **Performance:** ~0.006ms (mock), ~1-3ms (real Redis)
- **Config:** `eaf.security.revocation.fail-closed` (true=fail-closed, false=fail-open)
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer7_revocation"}`

### Layer 8: Role Normalization
- **Implementation:** `RoleNormalizer` (via `JwtAuthenticationConverter`)
- **Validates:** Extract realm_access.roles and resource_access.{client}.roles
- **Normalizes:** Add ROLE_ prefix, flatten nested structures
- **Performance:** ~0.02ms (handled during SecurityContext population)

### Layer 9: User Validation (Optional)
- **Implementation:** `JwtUserValidator` with `KeycloakUserDirectory`
- **Validates:** User exists and is active in Keycloak
- **Failure:** `401 Unauthorized` - "JWT subject user is invalid"
- **Performance:** ~0.01ms (cache hit), ~10-50ms (cache miss)
- **Config:** `eaf.security.jwt.validate-user=false` (disabled by default)
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer9_user_validation"}`

### Layer 10: Injection Detection
- **Implementation:** `JwtInjectionValidator` with `InjectionDetector`
- **Validates:** SQL/XSS/JNDI/Expression Injection/Path Traversal patterns in all claims
- **Failure:** `400 Bad Request` - "JWT claim contains potential injection pattern"
- **Performance:** ~0.047ms (regex patterns compiled once)
- **Metrics:** `jwt_validation_layer_duration_seconds{layer="layer10_injection_detection"}`

---

## Fail-Fast Behavior

`DelegatingOAuth2TokenValidator` stops at **first validation failure** for optimal performance and security.

**Example:**
```
Request with expired JWT:
Layer 1-4: ✅ PASS
Layer 5: ❌ FAIL (expired)
Layers 6-10: ⏭️ SKIPPED
→ 401 Unauthorized
```

---

## Metrics

### Per-Layer Timing
**Metric:** `jwt_validation_layer_duration_seconds{layer}`  
**Type:** Histogram with percentiles (p50, p95, p99)  
**Layers:** layer3_algorithm, layer4_claim_schema, layer5_time_based, layer6_issuer, layer6_audience, layer7_revocation, layer9_user_validation, layer10_injection_detection

### Per-Layer Failures
**Metric:** `jwt_validation_layer_failures_total{layer,reason}`  
**Type:** Counter  
**Use:** Attack pattern detection, misconfiguration monitoring

### Grafana Queries

**p95 Latency:**
```promql
histogram_quantile(0.95,
  sum(rate(jwt_validation_layer_duration_seconds_bucket[5m])) by (layer, le)
)
```

**Failure Rate:**
```promql
sum(rate(jwt_validation_layer_failures_total[5m])) by (layer)
```

---

## Performance Benchmarks

Based on `JwtValidationPerformanceTest` results:

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| **Total (all 10 layers)** | <50ms | ~0.17ms (mock) | ✅ 295x better |
| **p95 Latency** | <30ms | ~0.038ms | ✅ 789x better |
| **1000 validations avg** | <10ms | ~0.031ms | ✅ 320x better |
| **Layers 3-6** | <20ms | ~0.087ms | ✅ |
| **Layer 7 (Revocation)** | <5ms | ~0.006ms (mock) | ✅ |
| **Layer 10 (Injection)** | <3ms | ~0.047ms | ✅ |

**Real Infrastructure:** ~20-40ms total (includes Redis ~1-3ms, JWKS fetch ~5-15ms)

---

## Configuration

```yaml
eaf:
  security:
    jwt:
      issuer-uri: http://keycloak:8080/realms/eaf
      jwks-uri: http://keycloak:8080/realms/eaf/protocol/openid-connect/certs
      audience: eaf-api
      validate-user: false  # Layer 9 toggle

    revocation:
      fail-closed: false  # Layer 7 fail-open (default)
      key-prefix: "revoke:"
      default-ttl: PT1H

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${eaf.security.jwt.issuer-uri}
          jwk-set-uri: ${eaf.security.jwt.jwks-uri}
```

---

## Troubleshooting

### All requests return 401 Unauthorized

**Causes:**
1. Keycloak not reachable → Check JWKS URI connectivity
2. Wrong issuer URI → JWT `iss` must match `eaf.security.jwt.issuer-uri`
3. Wrong audience → JWT `aud` must include `eaf.security.jwt.audience`
4. Redis unavailable (fail-closed) → Check `eaf.security.revocation.fail-closed=false`

### Slow requests (>100ms)

**Diagnostics:**
1. Check metrics: `/actuator/metrics/jwt_validation_layer_duration_seconds`
2. Common causes: Redis latency (Layer 7), JWKS cache miss (Layer 2), Layer 9 enabled

**Optimizations:**
- Increase JWKS cache: `jwks-cache-duration=PT30M`
- Disable Layer 9: `validate-user=false`
- Use fail-open: `fail-closed=false`

### False positives (injection detection)

**Resolution:**
- Check logs for matched pattern
- Review claim values (avoid SQL keywords in user metadata)
- Keycloak claim mappers should sanitize inputs

---

## Testing

### Unit Tests (Nullable Pattern)
- `JwtAlgorithmValidatorTest`, `JwtClaimSchemaValidatorTest`, etc.
- 100-1000x performance improvement vs integration tests
- Fast TDD feedback loop

### Integration Tests (Testcontainers)
- `Jwt10LayerValidationIntegrationTest` - All 10 layers E2E (16 test cases)
- Real Keycloak + Redis infrastructure
- 45 integration tests, 0 failures ✅

### Performance Tests
- `JwtValidationPerformanceTest` - <50ms validation target
- 95 total unit tests, 0 failures ✅

### Fuzz Tests
- `InjectionDetectionFuzzer` - SQL/XSS/JNDI robustness

---

## Security Best Practices

- **Do NOT disable layers** without security review (especially 3, 5, 7, 10)
- **Recommended token lifetime:** 5-15 minutes access, 8 hours refresh
- **JWKS cache:** 10 minutes (balance security vs performance)
- **Layer 9:** Disable for performance (safe with short token lifetimes)

---

## References

- [Architecture](../architecture.md) - Section 16 (10-Layer JWT Validation)
- [PRD](../PRD.md) - FR006, NFR002, FR011
- [Tech Spec](../tech-spec-epic-3.md) - Section 7.1
- [Story 3.9](../sprint-artifacts/epic-3/story-3.9-complete-jwt-integration.md)

---

**Document Version:** 1.0  
**Last Validated:** 2025-11-13  
**Maintained By:** EAF Security Team
