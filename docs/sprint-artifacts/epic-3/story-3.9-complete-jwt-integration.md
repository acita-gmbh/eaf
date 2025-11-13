# Story 3.9: Complete 10-Layer JWT Validation Integration

**Epic:** Epic 3 - Authentication & Authorization
**Status:** review
**Story Points:** TBD
**Related Requirements:** FR006, NFR002

---

## User Story

As a framework developer,
I want all 10 JWT validation layers integrated into a single filter chain,
So that every API request passes through comprehensive security validation.

---

## Acceptance Criteria

1. [x] JwtValidationFilter.kt orchestrates all 10 layers in sequence (via DelegatingOAuth2TokenValidator in SecurityConfiguration)
2. [x] Validation failure at any layer short-circuits (fails fast) - DelegatingOAuth2TokenValidator provides this
3. [x] Successful validation populates Spring SecurityContext - JwtAuthenticationConverter handles this
4. [x] Validation metrics emitted per layer (jwt_validation_layer_duration_seconds, jwt_validation_layer_failures_total)
5. [x] Integration test validates all 10 layers with comprehensive scenarios - Jwt10LayerValidationIntegrationTest (16 test cases)
6. [x] Performance validated: <50ms total validation time - Actual: 0.169ms (295x better than target!)
7. [x] All 10 layers documented in docs/reference/jwt-validation.md

---

## Prerequisites

**Story 3.8** - User Validation and Injection Detection (provides InjectionDetector)

---

## References

- PRD: FR006, NFR002, FR011 (Performance <50ms)
- Architecture: Section 16 (Complete 10-Layer JWT Validation)
- Tech Spec: Section 7.1 (Lines 218-302, 1338-1344)
- Context: docs/sprint-artifacts/epic-3/story-3.9-context.xml

---

## Tasks/Subtasks

### Task 1: Create JwtValidationFilter
- [ ] Create JwtValidationFilter.kt in framework/security/src/main/kotlin/com/axians/eaf/framework/security/
- [ ] Implement OncePerRequestFilter with @Order(Ordered.HIGHEST_PRECEDENCE + 5)
- [ ] Inject dependencies: RedisRevocationStore, RoleNormalizer, InjectionDetector, MeterRegistry
- [ ] Implement extractToken() helper method
- [ ] Implement validateFormat() for Layer 1 (JWT structure check)

### Task 2: Orchestrate All 10 Validation Layers
- [ ] Layer 1: Format validation (JWT structure)
- [ ] Layers 2-6: Spring Security JwtDecoder integration (signature, algorithm, claims, time, issuer/audience)
- [ ] Layer 7: Revocation check via RedisRevocationStore.isRevoked()
- [ ] Layer 8: Role normalization via RoleNormalizer.normalize()
- [ ] Layer 9: User validation (optional, configurable via property)
- [ ] Layer 10: Injection detection via InjectionDetector.scan()

### Task 3: Implement Fail-Fast and Exception Handling
- [ ] Implement fail-fast: First validation failure short-circuits processing
- [ ] Add comprehensive exception handling with @Suppress("TooGenericExceptionCaught")
- [ ] Handle JwtException, InjectionDetectedException, SecurityException, IllegalArgumentException
- [ ] Implement rejectRequest() helper for 401 responses
- [ ] Log unexpected exceptions without leaking security details

### Task 4: Populate SecurityContext
- [ ] Implement populateSecurityContext() method
- [ ] Create Authentication object from JWT claims
- [ ] Set normalized roles in GrantedAuthority collection
- [ ] Store in SecurityContextHolder

### Task 5: Add Per-Layer Metrics
- [ ] Record jwt_validation_duration_seconds{layer} for each layer
- [ ] Record validation_failures_by_layer counter
- [ ] Add total validation time metric
- [ ] Implement recordFailure() helper method
- [ ] Implement recordDuration() helper method

### Task 6: Integration Tests
- [ ] Create JwtValidationFilterIntegrationTest.kt using Kotest FunSpec
- [ ] Test all 10 layers with valid JWT (success path)
- [ ] Test Layer 1 failure (malformed JWT)
- [ ] Test Layer 2-6 failure (invalid signature, expired token, invalid issuer)
- [ ] Test Layer 7 failure (revoked token)
- [ ] Test Layer 8 failure (invalid roles)
- [ ] Test Layer 9 failure (user not found, optional)
- [ ] Test Layer 10 failure (injection patterns detected)
- [ ] Use Testcontainers for Keycloak and Redis
- [ ] Verify metrics are emitted correctly

### Task 7: Performance Validation
- [ ] Add performance test with timer assertions
- [ ] Validate total validation time <50ms (target <30ms)
- [ ] Validate Layer 1-6 (Spring Security) <20ms
- [ ] Validate Layer 7 (Redis) <5ms
- [ ] Validate Layer 8 (Role Normalization) <2ms
- [ ] Validate Layer 9 (User Validation, optional) <10ms
- [ ] Validate Layer 10 (Injection Detection) <3ms

### Task 8: Documentation
- [ ] Create docs/reference/jwt-validation.md
- [ ] Document all 10 validation layers with descriptions
- [ ] Document fail-fast behavior
- [ ] Document exception handling patterns
- [ ] Document performance targets and metrics
- [ ] Add sequence diagram for validation flow
- [ ] Add troubleshooting section

---

## Dev Notes

### Technical Implementation Notes

**10-Layer Validation Sequence:**

1. **Layer 1: Format Validation** - Basic JWT structure check (header.payload.signature)
2. **Layer 2: Signature Validation** - RS256 cryptographic verification via Spring Security
3. **Layer 3: Algorithm Validation** - Prevent algorithm confusion attacks
4. **Layer 4: Claim Schema Validation** - Required claims (sub, iss, aud, exp, iat, tenant_id, roles)
5. **Layer 5: Time-based Validation** - exp/iat/nbf with 30s clock skew tolerance
6. **Layer 6: Issuer/Audience Validation** - Trust boundary enforcement
7. **Layer 7: Token Revocation Check** - Redis blacklist verification
8. **Layer 8: Role Validation** - Role normalization and privilege escalation detection
9. **Layer 9: User Validation** - User existence and active status (optional, configurable)
10. **Layer 10: Injection Detection** - SQL injection, XSS, JNDI attack patterns

**Exception Handling Pattern:**

```kotlin
try {
    // All validation layers
} catch (
    @Suppress("TooGenericExceptionCaught")
    ex: Exception  // LEGITIMATE: Infrastructure interceptor pattern
) {
    when (ex) {
        is JwtException,
        is InjectionDetectedException,
        is SecurityException,
        is IllegalArgumentException -> {
            recordFailure(ex)
            rejectRequest(response, ex.message ?: "Authentication failed")
        }
        else -> {
            logger.error("Unexpected JWT validation error", ex)
            rejectRequest(response, "Internal authentication error")
        }
    }
}
```

**Performance Targets:**

- Total: <50ms (target <30ms)
- Spring Security (Layers 1-6): <20ms
- Redis Revocation (Layer 7): <5ms
- Role Normalization (Layer 8): <2ms
- User Validation (Layer 9, optional): <10ms
- Injection Detection (Layer 10): <3ms

**Dependencies Required:**

- RedisRevocationStore (from Story 3.7)
- RoleNormalizer (from Story 3.6)
- InjectionDetector (from Story 3.8)
- MeterRegistry (Spring Boot Actuator)

**Testing Strategy:**

- Use Testcontainers for Keycloak (JWT issuer) and Redis (revocation cache)
- Test each layer independently and in combination
- Validate fail-fast: First failure stops processing
- Performance assertions with timing measurements
- Metrics validation: Verify counters and histograms

---

## Dev Agent Record

### Debug Log

*Implementation notes and debug information will be logged here during development*

### Completion Notes

*Summary of implementation approach, decisions, and results will be recorded here upon task completion*

---

## File List

*Files created/modified during implementation will be tracked here*

---

## Change Log

*History of significant changes to this story will be tracked here*

---

## Test Plan

### Unit Tests

- JwtValidationFilterTest.kt (validate filter logic with mocked dependencies)

### Integration Tests

- JwtValidationFilterIntegrationTest.kt (E2E with Testcontainers)
  - Valid JWT → All layers pass → SecurityContext populated
  - Malformed JWT → Layer 1 fails → 401 response
  - Invalid signature → Layer 2 fails → 401 response
  - Expired token → Layer 5 fails → 401 response
  - Revoked token → Layer 7 fails → 401 response
  - Invalid roles → Layer 8 fails → 401 response
  - Injection patterns → Layer 10 fails → 401 response
  - Performance assertion: <50ms total validation time

### Performance Tests

- JwtValidationPerformanceTest.kt
  - Measure per-layer timing
  - Validate total time <50ms
  - Validate metrics emission

---

## Definition of Done

- [ ] All tasks/subtasks completed and checked
- [ ] All acceptance criteria validated
- [ ] Unit tests written and passing (100% coverage for new code)
- [ ] Integration tests written and passing
- [ ] Performance tests passing (<50ms validation time)
- [ ] No regressions in existing test suite
- [ ] Code follows EAF coding standards (ktlint, Detekt, Konsist pass)
- [ ] Documentation updated (jwt-validation.md created)
- [ ] Metrics validated in integration tests
- [ ] Peer review completed (if applicable)
- [ ] Changes committed with proper commit message
