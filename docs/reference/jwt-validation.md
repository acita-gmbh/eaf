# JWT Validation Reference

## Overview

The EAF Security Framework implements a comprehensive **10-Layer JWT Validation System** that provides defense-in-depth security for API authentication. This system validates JWT tokens through sequential layers, each addressing specific security threats with fail-fast behavior.

**Architecture Decision**: Complete 10-Layer JWT Validation (Architecture Section 16)

## 10-Layer Validation Pipeline

### Layer 1: Format Validation
**Purpose**: Validates JWT structure and Authorization header format
**Implementation**: `JwtValidationFilter.extractAndValidateTokenFormat()`
**Security Threats**: Malformed tokens, missing authentication
**Failure Response**: 401 Unauthorized
**Performance Impact**: Minimal (<1ms)

**Validation Rules:**
- Authorization header must be present
- Header must start with "Bearer "
- JWT token must not be empty or blank
- Basic structure validation

### Layer 2: Signature Validation
**Purpose**: Cryptographic verification of token integrity
**Implementation**: `JwtDecoder.decode()` with JWKS
**Security Threats**: Token tampering, forged tokens
**Failure Response**: 401 Unauthorized
**Performance Impact**: Medium (RSA verification ~5-10ms)

**Validation Rules:**
- RS256 signature verification using Keycloak JWKS
- Public key retrieval from JWKS endpoint
- Cryptographic signature validation
- Key ID (kid) validation

### Layer 3: Algorithm Validation
**Purpose**: Enforces secure signing algorithms
**Implementation**: `JwtAlgorithmValidator`
**Security Threats**: Algorithm confusion attacks (HS256 with public key)
**Failure Response**: 401 Unauthorized
**Performance Impact**: Minimal (<1ms)

**Validation Rules:**
- Algorithm header must be present
- Only "RS256" allowed
- Rejects: HS256, HS384, HS512, "none"
- Prevents downgrade attacks

### Layer 4: Claim Schema Validation
**Purpose**: Ensures required claims are present
**Implementation**: `JwtClaimSchemaValidator`
**Security Threats**: Incomplete tokens, missing identity information
**Failure Response**: 401 Unauthorized
**Performance Impact**: Minimal (<1ms)

**Required Claims:**
- `sub` (Subject) - User identifier
- `iss` (Issuer) - Token issuer
- `aud` (Audience) - Intended recipient
- `exp` (Expiration) - Token expiry
- `iat` (Issued At) - Token creation time
- `tenant_id` - Multi-tenant isolation
- `roles` - User permissions

### Layer 5: Time-Based Validation
**Purpose**: Prevents replay attacks and expired token usage
**Implementation**: `JwtTimeBasedValidator`
**Security Threats**: Token replay, expired credentials
**Failure Response**: 401 Unauthorized
**Performance Impact**: Minimal (<1ms)

**Validation Rules:**
- `exp` must be in the future
- `iat` must not be too far in the future (30s skew tolerance)
- `nbf` (if present) must be in the past
- Clock skew tolerance: 30 seconds
- Timezone-aware validation

### Layer 6: Issuer/Audience Validation
**Purpose**: Trust boundary enforcement
**Implementation**: `JwtIssuerValidator`, `JwtAudienceValidator`
**Security Threats**: Tokens from untrusted issuers, wrong audience
**Failure Response**: 401 Unauthorized
**Performance Impact**: Minimal (<1ms)

**Validation Rules:**
- `iss` must match configured Keycloak issuer URI
- `aud` must contain configured audience
- Exact string matching
- Trust domain validation

### Layer 7: Revocation Check
**Purpose**: Invalidates compromised tokens
**Implementation**: `JwtRevocationValidator` with Redis
**Security Threats**: Stolen tokens, account compromise
**Failure Response**: 401 Unauthorized
**Performance Impact**: Low (Redis lookup ~1-2ms)

**Validation Rules:**
- JTI (JWT ID) checked against Redis blacklist
- Fast negative lookups
- Configurable cache TTL
- Fail-open/fail-closed modes

### Layer 8: Role Validation
**Purpose**: Privilege escalation prevention
**Implementation**: `JwtValidationFilter.validateRoles()`
**Security Threats**: Malformed roles, privilege escalation
**Failure Response**: 403 Forbidden
**Performance Impact**: Minimal (<1ms)

**Validation Rules:**
- `roles` claim must be present and non-empty
- No blank or empty role strings
- Roles must be normalizable (valid format)
- Prevents injection through role claims

### Layer 9: User Validation (Optional)
**Purpose**: Active user verification
**Implementation**: `JwtUserValidator` with Keycloak Admin API
**Security Threats**: Tokens for deactivated users
**Failure Response**: 401 Unauthorized
**Performance Impact**: High (HTTP call to Keycloak ~20-50ms)

**Validation Rules:**
- User exists in Keycloak directory
- User account is active/enabled
- Cached lookups with TTL
- Configurable via `eaf.keycloak.user-validation-enabled`
- **Disabled by default** for performance

### Layer 10: Injection Detection
**Purpose**: Prevents malicious payload injection
**Implementation**: `JwtInjectionValidator` with `InjectionDetector`
**Security Threats**: SQL injection, XSS, JNDI attacks through JWT
**Failure Response**: 403 Forbidden
**Performance Impact**: Low (regex matching ~1-2ms)

**Detected Patterns:**
- **SQL Injection**: `'; DROP TABLE`, `UNION SELECT`, etc.
- **XSS**: `<script>`, `javascript:`, `onload=`
- **JNDI**: `${jndi:ldap://}`, `#{...}`
- **Expression Language**: `${...}`, `#{...}`
- **Path Traversal**: `../../../`, `%2e%2e%2f`

## Implementation Details

### JwtValidationFilter
The `JwtValidationFilter` orchestrates all 10 layers with:
- **Fail-Fast Behavior**: Stops at first validation failure
- **Metrics Emission**: Per-layer timing and failure counters
- **Performance Monitoring**: Total validation time tracking
- **Security Context**: Populates Spring Security context on success

### Configuration
```yaml
eaf:
  keycloak:
    user-validation-enabled: false  # Performance trade-off
  security:
    jwt:
      issuer-uri: https://keycloak.example.com/realms/eaf
      audience: eaf-api
```

### Metrics
- `jwt_validation_layer_duration`: Per-layer execution time
- `jwt_validation_failures_total`: Failures by layer
- `jwt_validation_total_duration`: End-to-end validation time

### Performance Targets
- **Total Validation Time**: <50ms (Architecture Decision #10)
- **Per-Layer Budget**: Layer 9 (when enabled) is the bottleneck
- **Caching**: JWKS, user lookups, and revocation checks are cached

## Security Considerations

### Defense-in-Depth
Each layer addresses different attack vectors:
- **Cryptographic**: Layers 2-3 prevent forging
- **Temporal**: Layer 5 prevents replay
- **Trust**: Layers 6-7 enforce boundaries
- **Authorization**: Layers 8-9 validate permissions
- **Injection**: Layer 10 prevents exploitation

### Fail-Safe Design
- **Fail-Fast**: Immediate rejection on any failure
- **Secure Defaults**: Conservative validation rules
- **Error Handling**: Generic error messages prevent enumeration
- **Logging**: Security events logged without sensitive data

### Performance vs Security Trade-offs
- **Layer 9**: User validation provides strongest security but highest cost
- **Layer 7**: Revocation checking requires Redis infrastructure
- **Layer 10**: Comprehensive injection detection may have false positives

## Testing Strategy

### Unit Tests
- Individual validator testing with Kotest
- Mock dependencies for isolation
- Edge case coverage

### Integration Tests
- `JwtValidationFilterIntegrationTest`: All 10 layers end-to-end
- Testcontainers for Redis/Keycloak
- Performance validation (<50ms target)

### Fuzz Testing
- `InjectionDetectionFuzzer`: Layer 10 pattern robustness
- Random input generation
- Security vulnerability detection

### Property-Based Testing
- Kotest property tests for validation invariants
- 1000+ iterations for statistical confidence

## Migration Notes

### From Individual Validators
Previous implementation used individual `OAuth2TokenValidator`s in `DelegatingOAuth2TokenValidator`. The new `JwtValidationFilter` provides:
- Unified error handling
- Per-layer metrics
- Fail-fast behavior
- Security context population

### Backward Compatibility
The filter maintains compatibility with existing Spring Security OAuth2 Resource Server configuration while adding comprehensive validation and monitoring.

## Troubleshooting

### Common Issues
- **Layer 1 Failures**: Check Authorization header format
- **Layer 2 Failures**: Verify JWKS endpoint accessibility
- **Layer 5 Failures**: Check system clock synchronization
- **Layer 7 Failures**: Verify Redis connectivity
- **Layer 9 Failures**: Check Keycloak Admin API configuration

### Performance Issues
- Enable Layer 9 only when required
- Monitor `jwt_validation_total_duration` metric
- Check Redis latency for Layer 7
- Verify JWKS caching configuration

### Debug Logging
Enable debug logging for detailed validation flow:
```yaml
logging:
  level:
    com.axians.eaf.framework.security.filter: DEBUG
```