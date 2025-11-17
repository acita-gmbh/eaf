# Spring Boot 4 and Spring Framework 7 Migration Plan

**Document Version:** 1.0
**Created:** 2025-11-17
**Status:** DRAFT - Awaiting Dependency Compatibility Confirmation
**Owner:** Architecture Team

---

## Executive Summary

This document provides a comprehensive migration plan for upgrading the **Enterprise Application Framework (EAF) v1.0** from:
- **Spring Boot 3.5.7** → **Spring Boot 4.0.x**
- **Spring Framework 6.2.12** → **Spring Framework 7.0.x**
- **Spring Modulith 1.4.4** → **Spring Modulith 2.0.x**

### Critical Status: MIGRATION CURRENTLY BLOCKED ⚠️

**The migration is currently BLOCKED due to a critical dependency incompatibility:**

- **Axon Framework** (CQRS/Event Sourcing core) has **NO CONFIRMED SUPPORT** for Spring Boot 4 or Spring Framework 7
- Current stable: Axon 4.12.1 (supports Spring Boot 3.x)
- Future: Axon 5.0 (in milestone, not production-ready, no confirmed Spring Boot 4 support)

**Recommendation:** **DEFER MIGRATION** until Axon Framework officially supports Spring Boot 4 and Spring Framework 7.

---

## Table of Contents

1. [Current State vs Target State](#1-current-state-vs-target-state)
2. [Critical Dependencies Analysis](#2-critical-dependencies-analysis)
3. [Breaking Changes Impact Assessment](#3-breaking-changes-impact-assessment)
4. [Migration Phases](#4-migration-phases)
5. [Risk Assessment and Mitigation](#5-risk-assessment-and-mitigation)
6. [Testing Strategy](#6-testing-strategy)
7. [Rollback Plan](#7-rollback-plan)
8. [Timeline Estimates](#8-timeline-estimates)
9. [Recommendations](#9-recommendations)
10. [Appendices](#10-appendices)

---

## 1. Current State vs Target State

### 1.1 Current State (Verified 2025-11-01)

| Component | Current Version | Status |
|-----------|----------------|--------|
| **Spring Boot** | 3.5.7 | Current GA (released 2025-10-23) |
| **Spring Framework** | 6.2.12 | Current stable (CVE-2025-24928 fix) |
| **Spring Modulith** | 1.4.4 | Current stable |
| **Spring Security** | 6.5.6 | Current stable |
| **Jakarta EE** | 10 | Servlet 6.0, JPA 3.1, Bean Validation 3.0 |
| **JVM** | 21 LTS | Kotlin 2.2.21 |
| **Axon Framework** | 4.12.1 | Stable, supports Spring Boot 3.x |
| **jOOQ** | 3.20.8 | Current stable, Jakarta EE 9+ |
| **Kotest** | 6.0.4 | Current stable, Spring Boot 3.x confirmed |
| **PostgreSQL** | 16.10 | Current stable |

### 1.2 Target State

| Component | Target Version | Availability | Compatibility Status |
|-----------|---------------|--------------|---------------------|
| **Spring Boot** | 4.0.x | RC2 (GA: Nov 2025) | ⚠️ Not fully released |
| **Spring Framework** | 7.0.x | GA (released Nov 2025) | ✅ Available |
| **Spring Modulith** | 2.0.x | RC1 (Oct 2025) | ✅ Compatible with Spring Boot 4 |
| **Spring Security** | 7.0.x | Expected with Boot 4 | ⚠️ Version TBD |
| **Jakarta EE** | 11 | Servlet 6.1, JPA 3.2, Bean Validation 3.1 | ✅ Available |
| **JVM** | 17+ (25 LTS recommended) | JVM 21 already exceeds minimum | ✅ Compatible |
| **Axon Framework** | 5.0.x or compatible 4.x | Milestone (not GA) | ❌ **NO CONFIRMED SUPPORT** |
| **jOOQ** | 3.21+ | 3.20.8 current (no 4.0 exists) | ✅ Likely compatible (Jakarta EE since 3.16) |
| **Kotest** | 6.x+ | 6.0.4 current | ⚠️ No Spring Boot 4 confirmation yet |
| **PostgreSQL** | 16.10+ | Current stable | ✅ No changes required |

### 1.3 Baseline Requirements for Spring Boot 4 / Framework 7

**Minimum Requirements:**
- ✅ **Java 17+** (JVM 25 LTS recommended) - **EAF uses JVM 21: COMPATIBLE**
- ✅ **Kotlin 2.2+** - **EAF uses 2.2.21: COMPATIBLE**
- ✅ **Gradle 8.14+ or 9.x** - **EAF uses Gradle 9.1.0: COMPATIBLE**
- ⚠️ **Servlet 6.1** (Tomcat 11+, Jetty 12.1+) - **Requires container upgrade**
- ⚠️ **Jakarta EE 11** - **Requires dependency updates**

---

## 2. Critical Dependencies Analysis

### 2.1 Dependency Compatibility Matrix

| Dependency | Current Version | Spring Boot 4 Status | Migration Impact | Risk Level |
|------------|----------------|---------------------|------------------|------------|
| **Axon Framework** | 4.12.1 | ❌ No confirmed support | **BLOCKING** | 🔴 **CRITICAL** |
| **Spring Modulith** | 1.4.4 | ✅ 2.0 RC1 available | Minor API changes expected | 🟡 Medium |
| **jOOQ** | 3.20.8 | ✅ Likely compatible | Jakarta EE 11 updates | 🟢 Low |
| **Kotest** | 6.0.4 | ⚠️ No confirmation yet | Extension updates required | 🟡 Medium |
| **Arrow** | 2.1.2 | ✅ Framework-agnostic | No impact | 🟢 Low |
| **Micrometer** | 1.16.0 | ✅ Spring-managed BOM | Auto-upgraded | 🟢 Low |
| **Testcontainers** | 1.20.6 | ✅ Framework-agnostic | No impact | 🟢 Low |
| **Detekt** | 1.23.8 | ✅ Framework-agnostic | No impact | 🟢 Low |
| **ktlint** | 1.7.1 | ✅ Framework-agnostic | No impact | 🟢 Low |

### 2.2 Critical Blocker: Axon Framework

**Issue:** Axon Framework is the **core CQRS/Event Sourcing engine** for EAF. Without Axon support for Spring Boot 4, migration is impossible.

**Current Status (as of 2025-11-17):**
- **Axon 4.12.1**: Supports Spring Boot 3.x (current stable)
- **Axon 5.0**: In milestone (M2 released), **NOT production-ready**
- **Spring Boot 4 Support**: **NO OFFICIAL ANNOUNCEMENT** from AxonIQ

**Research Sources:**
- [Axon Framework Releases](https://github.com/AxonFramework/AxonFramework/releases)
- [AxonIQ Blog - 2025 Outlook](https://www.axoniq.io/blog/2025-axoniq-forecast)
- [Axon Framework 5 Roadmap](https://discuss.axoniq.io/t/axon-framework-5-roadmap/6032)

**Action Required:**
1. **Monitor AxonIQ announcements** for Spring Boot 4 compatibility
2. **Engage with AxonIQ support** to confirm roadmap and timeline
3. **Consider alternative timeline**: Wait for Axon 5.0 GA + Spring Boot 4 support

---

## 3. Breaking Changes Impact Assessment

### 3.1 Spring Framework 7.0 Breaking Changes

#### 3.1.1 Jakarta EE 11 Baseline (HIGH IMPACT)

**Change:** Upgrade from Jakarta EE 10 → Jakarta EE 11

**Required Updates:**
- Servlet 6.0 → 6.1
- JPA 3.1 → 3.2
- Bean Validation 3.0 → 3.1
- WebSocket 2.1 → 2.2

**EAF Impact:**
- ✅ **Low Impact**: EAF uses Spring Boot starters (auto-managed)
- ⚠️ **Container Upgrade**: Embedded Tomcat auto-upgraded (Tomcat 10 → 11)
- 🔍 **Review**: Hibernate ORM upgrade (6.x → 7.x) may affect JPA entities

**Affected Modules:**
- `framework/persistence` - JPA entities, Hibernate usage
- `framework/web` - Servlet filters (TenantContextFilter)
- `products/widget-demo` - Embedded Tomcat container

**Migration Tasks:**
- [ ] Verify Hibernate ORM 7 compatibility with jOOQ projections
- [ ] Test Tomcat 11 with existing Servlet filters
- [ ] Update Jakarta Validation constraints if API changes

---

#### 3.1.2 Package Migration: javax.* → jakarta.* (MEDIUM IMPACT)

**Change:** Complete removal of `javax.annotation` and `javax.inject` support

**EAF Impact:**
- ✅ **Likely Complete**: Spring Boot 3.x already migrated to jakarta.*
- 🔍 **Verification Required**: Scan codebase for any remaining javax.* imports

**Migration Tasks:**
- [ ] Scan entire codebase for `javax.annotation.*` imports
- [ ] Scan entire codebase for `javax.inject.*` imports
- [ ] Replace with `jakarta.annotation.*` and `jakarta.inject.*`
- [ ] Update Detekt rules to forbid javax.* imports

**Search Commands:**
```bash
# Find any remaining javax.* imports
grep -r "import javax\.annotation\." --include="*.kt" .
grep -r "import javax\.inject\." --include="*.kt" .
```

---

#### 3.1.3 Null Safety: JSR-305 → JSpecify (MEDIUM IMPACT)

**Change:** Spring nullness annotations deprecated in favor of JSpecify

**Current EAF Usage:**
- EAF uses Kotlin's null safety (non-null by default)
- No explicit JSR-305 annotations detected in codebase

**EAF Impact:**
- ✅ **Low Impact**: Kotlin null safety is language-level
- ⚠️ **Library Dependencies**: Third-party libraries may use JSR-305

**Migration Tasks:**
- [ ] Review deprecation warnings during compilation
- [ ] Evaluate JSpecify adoption for framework modules (optional)
- [ ] Update coding standards if JSpecify recommended

---

#### 3.1.4 Web MVC Path Matching Changes (HIGH IMPACT)

**Change:** Removal of legacy path matching features:
- `suffixPatternMatch` - removed
- `trailingSlashMatch` - removed
- `favorPathExtension` - removed
- `AntPathMatcher` - deprecated (use PathPatternParser)

**EAF Impact:**
- ✅ **Low Impact**: EAF uses modern `@RequestMapping` without suffix patterns
- 🔍 **Verification Required**: Confirm no custom path matching configuration

**Affected Files:**
- `framework/web/src/main/kotlin/.../RestConfiguration.kt`
- `products/widget-demo/src/main/kotlin/.../WidgetController.kt`

**Migration Tasks:**
- [ ] Audit all `@RequestMapping` annotations for suffix patterns
- [ ] Verify CORS configuration doesn't rely on path extension
- [ ] Test API endpoints: `/api/widgets`, `/api/widgets/{id}`, etc.
- [ ] Ensure OpenAPI 3.0 generation works with new path matching

---

#### 3.1.5 Logging Infrastructure: spring-jcl Retirement (LOW IMPACT)

**Change:** `spring-jcl` module retired in favor of Apache Commons Logging

**EAF Impact:**
- ✅ **No Impact**: Spring Boot manages logging dependencies
- 🔍 **Verification**: Confirm no explicit `spring-jcl` dependency

**Migration Tasks:**
- [ ] Search `gradle/libs.versions.toml` for `spring-jcl`
- [ ] Verify Logback/SLF4J configuration unchanged

---

### 3.2 Spring Boot 4.0 Breaking Changes

#### 3.2.1 Modularization of spring-boot-autoconfigure (HIGH IMPACT)

**Change:** Monolithic `spring-boot-autoconfigure` split into focused modules:
- `org.springframework.boot.autoconfigure.web`
- `org.springframework.boot.autoconfigure.data`
- `org.springframework.boot.autoconfigure.security`
- etc.

**EAF Impact:**
- ✅ **Low Impact**: EAF uses starter POMs (transitively include modules)
- ⚠️ **Custom Configuration**: Review explicit autoconfigure imports

**Affected Files:**
- `build-logic/src/main/kotlin/conventions/SpringBootConventionPlugin.kt`
- Any `@Import` or `@EnableAutoConfiguration(exclude = ...)` usage

**Migration Tasks:**
- [ ] Audit all `@EnableAutoConfiguration` exclusions
- [ ] Update exclusion package names if changed
- [ ] Test autoconfiguration with `--debug` flag
- [ ] Verify no explicit `spring-boot-autoconfigure` dependency

**Current Exclusions (from application-test.yml):**
```yaml
spring.autoconfigure.exclude:
  - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
  - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
  - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

---

#### 3.2.2 Starter Renames and Deprecations (MEDIUM IMPACT)

**Change:** Several starter POMs renamed for module alignment

**EAF Starters in Use:**
- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- `spring-boot-starter-validation`
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-aop`
- `spring-boot-starter-test`
- `spring-boot-testcontainers`

**Migration Tasks:**
- [ ] Review Spring Boot 4.0 release notes for starter renames
- [ ] Update `gradle/libs.versions.toml` with new starter names
- [ ] Ensure deprecated starters have migration path
- [ ] Test dependency resolution with Gradle dependency insight

---

#### 3.2.3 Minimum Gradle Version: 8.14+ or 9.x (NO IMPACT)

**Change:** Gradle 8.14 minimum required

**EAF Status:**
- ✅ **Compatible**: EAF uses Gradle 9.1.0

**Migration Tasks:**
- None required

---

#### 3.2.4 XML Configuration Deprecation (NO IMPACT)

**Change:** Spring MVC XML configuration deprecated

**EAF Status:**
- ✅ **No XML**: EAF uses Java/Kotlin configuration exclusively

**Migration Tasks:**
- None required

---

### 3.3 Spring Modulith 2.0 Changes

#### 3.3.1 API Changes (MEDIUM IMPACT)

**Change:** Spring Modulith 2.0 introduces API refinements

**EAF Modulith Usage:**
- `@ApplicationModule` annotations (3 instances)
- Module boundary testing with Konsist
- Event publication disabled (using Axon events)

**Affected Files:**
- `framework/security/src/main/kotlin/.../SecurityModule.kt`
- `framework/web/src/main/kotlin/.../WebModule.kt`
- `framework/multi-tenancy/src/main/kotlin/.../MultiTenancyModule.kt`

**Migration Tasks:**
- [ ] Review Spring Modulith 2.0 release notes
- [ ] Update `@ApplicationModule` annotations if API changed
- [ ] Re-run Konsist architecture tests
- [ ] Verify module dependency graph unchanged

---

## 4. Migration Phases

**IMPORTANT:** These phases are **CONTINGENT** on Axon Framework Spring Boot 4 support.

### Phase 0: Pre-Migration (CURRENT - BLOCKED)

**Duration:** Ongoing until dependencies ready
**Status:** ⚠️ **BLOCKED - Awaiting Axon Framework support**

**Objectives:**
1. Monitor critical dependency compatibility
2. Prepare codebase for migration
3. Establish baseline metrics

**Tasks:**

#### 4.0.1 Dependency Monitoring
- [ ] **Weekly check**: AxonIQ blog and GitHub releases for Spring Boot 4 support
- [ ] **Subscribe**: Axon Framework discussion forums for announcements
- [ ] **Engage**: Contact AxonIQ support for roadmap clarity
- [ ] Monitor Spring Modulith 2.0 GA release
- [ ] Monitor Kotest Spring Boot 4 compatibility announcements

#### 4.0.2 Codebase Preparation (Can Start Now)
- [ ] Audit codebase for `javax.*` imports
- [ ] Document all custom Spring configurations
- [ ] Review all `@EnableAutoConfiguration` exclusions
- [ ] Identify any deprecated Spring APIs in use
- [ ] Run comprehensive test suite for baseline metrics

#### 4.0.3 Environment Preparation
- [ ] Set up isolated Spring Boot 4 test environment
- [ ] Install Tomcat 11 for compatibility testing
- [ ] Prepare rollback scripts and procedures
- [ ] Document current production configuration

#### 4.0.4 Team Preparation
- [ ] Review Spring Framework 7 release notes (team training)
- [ ] Review Spring Boot 4 migration guide (team training)
- [ ] Identify migration champions and responsibilities
- [ ] Schedule migration sprint (post-dependency availability)

---

### Phase 1: Dependency Updates (FUTURE)

**Duration:** 1-2 weeks
**Prerequisites:** ✅ Axon Framework Spring Boot 4 support confirmed
**Risk Level:** 🔴 **HIGH**

**Objectives:**
1. Update all Spring dependencies to target versions
2. Update critical framework dependencies
3. Resolve dependency conflicts

**Tasks:**

#### 4.1.1 Version Catalog Updates
**File:** `gradle/libs.versions.toml`

```toml
# BEFORE (Current)
spring-boot = "3.5.7"
spring-framework = "6.2.12"
spring-modulith = "1.4.4"
spring-security = "6.5.6"
axon-framework = "4.12.1"

# AFTER (Target)
spring-boot = "4.0.x"           # GA version TBD
spring-framework = "7.0.x"      # GA version TBD
spring-modulith = "2.0.x"       # GA version TBD
spring-security = "7.0.x"       # Version TBD
axon-framework = "5.0.x or 4.x" # BLOCKED - Version TBD
```

**Migration Steps:**
1. [ ] Update `spring-boot` to 4.0.x GA
2. [ ] Update `spring-framework` to 7.0.x GA
3. [ ] Update `spring-modulith` to 2.0.x GA
4. [ ] Update `axon-framework` to compatible version
5. [ ] Run `./gradlew dependencies --write-locks` to refresh lock files
6. [ ] Run `./gradlew dependencyInsight` to resolve conflicts

#### 4.1.2 BOM and Platform Dependencies
```kotlin
dependencies {
    // Spring Boot BOM (auto-manages Spring Framework, Security, etc.)
    implementation(platform(libs.spring.boot.bom))

    // Axon Framework BOM (auto-manages Axon modules)
    implementation(platform(libs.axon.framework.bom))
}
```

**Migration Steps:**
1. [ ] Verify BOM compatibility (Spring Boot BOM vs Axon BOM)
2. [ ] Resolve version conflicts using `enforcedPlatform()` if needed
3. [ ] Test dependency resolution in isolation

#### 4.1.3 Plugin Updates
**File:** `gradle/libs.versions.toml`

```toml
# BEFORE
[plugins]
spring-boot = { id = "org.springframework.boot", version = "3.5.7" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version = "2.2.21" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version = "2.2.21" }

# AFTER
[plugins]
spring-boot = { id = "org.springframework.boot", version = "4.0.x" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.2.x" } # Version TBD
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version = "2.2.21" } # No change
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version = "2.2.21" } # No change
```

**Migration Steps:**
1. [ ] Update Spring Boot Gradle plugin to 4.0.x
2. [ ] Update Spring Dependency Management plugin (check compatibility)
3. [ ] Verify Kotlin plugin compatibility with Spring Boot 4
4. [ ] Test Gradle sync and build configuration

#### 4.1.4 Transitive Dependency Verification
**Migration Steps:**
1. [ ] Run `./gradlew dependencies > deps-before.txt` (baseline)
2. [ ] Update versions
3. [ ] Run `./gradlew dependencies > deps-after.txt`
4. [ ] Diff and review all transitive changes
5. [ ] Identify any unexpected version jumps
6. [ ] Check for Jakarta EE 11 library updates

---

### Phase 2: Code Migration (FUTURE)

**Duration:** 2-3 weeks
**Prerequisites:** ✅ Phase 1 complete, build successful
**Risk Level:** 🟡 **MEDIUM**

**Objectives:**
1. Migrate all breaking API changes
2. Update configuration for Spring Boot 4
3. Resolve compilation errors

**Tasks:**

#### 4.2.1 Package Migration: javax.* → jakarta.*
**Scope:** Entire codebase

**Search Strategy:**
```bash
# Find remaining javax.* imports
find . -name "*.kt" -exec grep -l "import javax\.annotation\." {} \;
find . -name "*.kt" -exec grep -l "import javax\.inject\." {} \;
```

**Migration Pattern:**
```kotlin
// BEFORE
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.validation.Valid

// AFTER
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.validation.Valid
```

**Migration Steps:**
1. [ ] Run automated find-replace for common patterns
2. [ ] Manually review and verify changes
3. [ ] Update import ordering in `.editorconfig` if needed
4. [ ] Run `./gradlew ktlintFormat`
5. [ ] Commit: `refactor: migrate javax.* to jakarta.* for Jakarta EE 11`

#### 4.2.2 Spring Modulith API Updates
**Affected Files:**
- `framework/security/src/main/kotlin/.../SecurityModule.kt`
- `framework/web/src/main/kotlin/.../WebModule.kt`
- `framework/multi-tenancy/src/main/kotlin/.../MultiTenancyModule.kt`

**Migration Steps:**
1. [ ] Review Spring Modulith 2.0 migration guide
2. [ ] Update `@ApplicationModule` annotations if API changed
3. [ ] Verify module dependency declarations
4. [ ] Re-generate module documentation (if applicable)

#### 4.2.3 Autoconfiguration Exclusion Updates
**File:** `products/widget-demo/src/main/resources/application-test.yml`

**Current Exclusions:**
```yaml
spring.autoconfigure.exclude:
  - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
  - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
  - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

**Migration Steps:**
1. [ ] Verify new package names for autoconfigure classes
2. [ ] Update exclusion list if package names changed
3. [ ] Test autoconfiguration with `./gradlew bootRun --debug`
4. [ ] Confirm expected beans loaded/excluded

#### 4.2.4 Path Matching Configuration
**File:** `framework/web/src/main/kotlin/.../RestConfiguration.kt`

**Review:**
```kotlin
@Configuration
class RestConfiguration {
    @Bean
    fun webMvcConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun configurePathMatch(configurer: PathMatchConfigurer) {
                // Check for deprecated API usage:
                // - suffixPatternMatch (removed)
                // - trailingSlashMatch (removed)
                // - favorPathExtension (removed)
            }
        }
    }
}
```

**Migration Steps:**
1. [ ] Audit `PathMatchConfigurer` usage
2. [ ] Remove any deprecated path matching configuration
3. [ ] Switch to `PathPatternParser` if using `AntPathMatcher`
4. [ ] Test all API endpoints for path matching behavior

#### 4.2.5 Deprecated API Removal
**Migration Steps:**
1. [ ] Enable deprecation warnings: `kotlinOptions.allWarningsAsErrors = true`
2. [ ] Compile project and review all deprecation warnings
3. [ ] Replace deprecated APIs with recommended alternatives
4. [ ] Document any breaking API changes in release notes

---

### Phase 3: Testing and Validation (FUTURE)

**Duration:** 3-4 weeks
**Prerequisites:** ✅ Phase 2 complete, compilation successful
**Risk Level:** 🟡 **MEDIUM**

**Objectives:**
1. Execute comprehensive test suite
2. Fix all test failures and regressions
3. Validate functional and non-functional requirements

**Tasks:**

#### 4.3.1 Unit Test Execution
**Scope:** All framework and product modules

**Commands:**
```bash
# Run all unit tests
./gradlew test

# Run with coverage
./gradlew test koverHtmlReport
```

**Migration Steps:**
1. [ ] Run unit test suite (target: 0 failures)
2. [ ] Fix Kotest Spring Boot 4 compatibility issues (if any)
3. [ ] Update `@SpringBootTest` configurations if needed
4. [ ] Verify Nullable Design Pattern implementations still work
5. [ ] Achieve 85%+ line coverage (current target)

**Expected Issues:**
- Kotest SpringExtension compatibility
- `@DynamicPropertySource` behavior changes
- Autoconfiguration differences in tests

#### 4.3.2 Integration Test Execution
**Scope:** Testcontainers-based integration tests

**Commands:**
```bash
# Run integration tests
./gradlew integrationTest

# Run with Testcontainers
./gradlew :products:widget-demo:integrationTest
```

**Migration Steps:**
1. [ ] Start Testcontainers (PostgreSQL, Keycloak, Redis)
2. [ ] Run integration test suite (target: 0 failures)
3. [ ] Verify JWT validation with Keycloak integration
4. [ ] Verify multi-tenancy enforcement (3-layer)
5. [ ] Verify CQRS/Event Sourcing with Axon Framework
6. [ ] Test Actuator endpoints (`/actuator/health`, `/actuator/prometheus`)

**Critical Test Cases:**
- Story 4.1: TenantContext ThreadLocal management
- Story 4.2: Command handler tenant validation
- Story 3.x: 10-layer JWT validation system
- Story 3.6: Redis token revocation
- Epic 5: Widget CRUD operations via REST API

#### 4.3.3 End-to-End Testing
**Scope:** Full application stack

**Test Scenarios:**
1. [ ] **Widget Creation Flow**
   - POST `/api/widgets` with valid JWT
   - Verify command handling, event publishing, projection update
   - Verify tenant isolation (cannot access other tenant's widgets)

2. [ ] **Authentication & Authorization**
   - Obtain JWT from Keycloak
   - Access protected endpoints with valid/invalid tokens
   - Verify role-based access control (`@PreAuthorize`)

3. [ ] **Multi-Tenancy Enforcement**
   - Create widgets for Tenant A
   - Attempt to access Tenant A widgets with Tenant B JWT (should fail)
   - Verify PostgreSQL RLS enforcement

4. [ ] **Error Handling**
   - Trigger domain errors (DomainError.ValidationError)
   - Verify RFC 7807 ProblemDetail responses
   - Verify error catalog consistency

5. [ ] **Observability**
   - Check Prometheus metrics (`/actuator/prometheus`)
   - Verify custom metrics (command latency, event lag)
   - Verify health checks (`/actuator/health`)

#### 4.3.4 Performance Testing
**Scope:** Non-functional requirements (NFR-01, NFR-02)

**Test Cases:**
1. [ ] **Command Latency (NFR-01)**
   - Target: p95 <200ms
   - Load test: 100 concurrent widget creation requests
   - Measure: Command handler execution time

2. [ ] **Event Processor Lag (NFR-02)**
   - Target: <10 seconds
   - Publish 1000 events
   - Measure: Time until all projections updated

3. [ ] **Database Performance**
   - Verify BRIN indexes on event store
   - Verify time-based partitioning working
   - Check snapshot strategy (every 100 events)

**Migration Steps:**
1. [ ] Establish baseline metrics (pre-migration)
2. [ ] Run performance tests post-migration
3. [ ] Compare metrics (target: no regression)
4. [ ] Investigate any performance degradation

#### 4.3.5 Security Testing
**Scope:** 10-layer JWT validation, multi-tenancy isolation

**Test Cases:**
1. [ ] **JWT Validation Layers**
   - Layer 1: Format validation
   - Layer 2: Signature validation (RS256)
   - Layer 3: Algorithm validation
   - Layer 4: Claim schema validation
   - Layer 5: Time-based validation (exp/iat/nbf)
   - Layer 6: Issuer/audience validation
   - Layer 7: Token revocation check
   - Layer 8: Role validation
   - Layer 9: User validation (if enabled)
   - Layer 10: Injection detection

2. [ ] **Tenant Isolation**
   - Layer 1: TenantContextFilter extracts tenant_id
   - Layer 2: Command validation (tenant context match)
   - Layer 3: PostgreSQL RLS enforcement

3. [ ] **Security Vulnerabilities**
   - Run OWASP Dependency Check
   - Verify no known CVEs in dependencies
   - Test for SQL injection, XSS, JNDI injection

**Migration Steps:**
1. [ ] Re-run security test suite
2. [ ] Verify no security regressions
3. [ ] Update security documentation if changes

#### 4.3.6 Architecture Compliance Testing
**Scope:** Konsist rules, Spring Modulith boundaries

**Commands:**
```bash
# Run Konsist architecture tests
./gradlew :konsist-tests:test

# Run Spring Modulith verification
./gradlew :products:widget-demo:modulithTest
```

**Migration Steps:**
1. [ ] Run Konsist rules (zero violations policy)
2. [ ] Verify module boundaries (framework ↔ products)
3. [ ] Verify coding standards enforcement
4. [ ] Update Konsist rules if Spring Boot 4 API changes

#### 4.3.7 Quality Gate Verification
**Scope:** All quality tools (ktlint, Detekt, Kover, Pitest)

**Commands:**
```bash
# Full quality check
./gradlew clean build

# Individual checks
./gradlew ktlintCheck
./gradlew detekt
./gradlew koverHtmlReport
./gradlew pitest # Nightly only
```

**Migration Steps:**
1. [ ] Run ktlint (zero violations)
2. [ ] Run Detekt (zero violations)
3. [ ] Verify Kover coverage ≥85%
4. [ ] Run Pitest mutation testing (60-70% target)
5. [ ] Fix any new violations introduced by migration

---

### Phase 4: Documentation and Deployment (FUTURE)

**Duration:** 1 week
**Prerequisites:** ✅ Phase 3 complete, all tests passing
**Risk Level:** 🟢 **LOW**

**Objectives:**
1. Update all documentation
2. Deploy to staging environment
3. Prepare production rollout

**Tasks:**

#### 4.4.1 Documentation Updates

**Files to Update:**
- [ ] `CLAUDE.md` - Update Spring Boot/Framework versions
- [ ] `docs/architecture.md` - Update Section 2 (Version Verification Log)
- [ ] `docs/PRD.md` - Update technology stack references
- [ ] `docs/tech-spec.md` - Update dependency matrix
- [ ] `gradle/libs.versions.toml` - Document version changes
- [ ] `README.md` - Update Quick Start guide

**New Documentation:**
- [ ] Create `docs/spring-boot-4-migration-guide.md` (this document)
- [ ] Create `docs/spring-boot-4-breaking-changes.md` (team reference)
- [ ] Update API documentation (OpenAPI 3.0 spec)

#### 4.4.2 Release Notes

**Create:** `RELEASE-NOTES-v1.1.md`

**Sections:**
- **What's New**: Spring Boot 4, Spring Framework 7, Spring Modulith 2
- **Breaking Changes**: API changes, dependency updates
- **Migration Guide**: Step-by-step for existing deployments
- **Known Issues**: Any unresolved issues or workarounds
- **Deprecations**: Features deprecated in this release

#### 4.4.3 Deployment Preparation

**Staging Deployment:**
1. [ ] Build production artifacts: `./gradlew clean build`
2. [ ] Build Docker images: `./gradlew bootBuildImage`
3. [ ] Deploy to staging environment
4. [ ] Run smoke tests on staging
5. [ ] Verify external integrations (Keycloak, PostgreSQL, Redis)
6. [ ] Monitor logs and metrics for 24 hours

**Production Checklist:**
1. [ ] Backup production database
2. [ ] Prepare rollback scripts
3. [ ] Schedule maintenance window
4. [ ] Notify stakeholders
5. [ ] Deploy with blue-green strategy (zero downtime)
6. [ ] Monitor KPIs for 48 hours post-deployment

#### 4.4.4 Rollback Verification
1. [ ] Test rollback procedure on staging
2. [ ] Document rollback steps
3. [ ] Verify data compatibility (forward/backward)
4. [ ] Prepare communication plan for rollback scenario

---

## 5. Risk Assessment and Mitigation

### 5.1 Critical Risks

| Risk ID | Description | Likelihood | Impact | Mitigation Strategy |
|---------|-------------|------------|--------|---------------------|
| **R1** | **Axon Framework incompatibility** | 🔴 HIGH | 🔴 **CRITICAL** | **BLOCKER**: Defer migration until Axon support confirmed. Monitor AxonIQ announcements weekly. |
| **R2** | Breaking changes in Axon 5.0 | 🟡 MEDIUM | 🔴 **HIGH** | Thoroughly test CQRS/ES functionality. Allocate 2 weeks for Axon migration if required. |
| **R3** | Jakarta EE 11 incompatibilities | 🟡 MEDIUM | 🟡 MEDIUM | Comprehensive integration testing. Verify Hibernate ORM 7 with jOOQ. |
| **R4** | Kotest Spring Boot 4 issues | 🟡 MEDIUM | 🟡 MEDIUM | Monitor Kotest releases. Prepare fallback to manual SpringExtension updates. |
| **R5** | Performance regression | 🟢 LOW | 🟡 MEDIUM | Baseline metrics before migration. Load testing before production. |
| **R6** | Security regression | 🟢 LOW | 🔴 **HIGH** | Re-run full security test suite. OWASP Dependency Check. Penetration testing. |
| **R7** | Multi-tenancy isolation failure | 🟢 LOW | 🔴 **CRITICAL** | Dedicated tenant isolation test suite. Verify all 3 layers. |
| **R8** | Production deployment failure | 🟡 MEDIUM | 🔴 **HIGH** | Blue-green deployment strategy. Automated rollback procedure. |

### 5.2 Mitigation Strategies

#### R1: Axon Framework Incompatibility (BLOCKER)

**Current Status:** 🔴 **ACTIVE BLOCKER**

**Mitigation:**
1. **Monitor AxonIQ Announcements**
   - Weekly check: [AxonIQ Blog](https://www.axoniq.io/blog/)
   - Subscribe: [Axon Discuss Forum](https://discuss.axoniq.io/)
   - GitHub: [AxonFramework Releases](https://github.com/AxonFramework/AxonFramework/releases)

2. **Engage AxonIQ Support**
   - Contact: AxonIQ support or discussion forum
   - Question: "What is the timeline for Spring Boot 4 / Spring Framework 7 support in Axon Framework 4.x or 5.x?"
   - Escalate: If no roadmap available, consider enterprise support engagement

3. **Alternative Scenarios**
   - **Scenario A**: Axon 4.x backports Spring Boot 4 support → Proceed with migration
   - **Scenario B**: Axon 5.0 GA supports Spring Boot 4 → Wait for Axon 5 GA (Q3-Q4 2026 planned in architecture.md)
   - **Scenario C**: No support timeline → **Defer Spring Boot 4 migration indefinitely**

4. **Fallback Plan**
   - Continue with Spring Boot 3.x (supported until November 2026 OSS, May 2028 commercial)
   - Re-evaluate migration in 6 months (May 2026)

**Decision Point:** Do NOT proceed to Phase 1 without Axon Framework support confirmation.

---

#### R2: Breaking Changes in Axon 5.0

**Trigger:** If Axon 5.0 required for Spring Boot 4 support

**Mitigation:**
1. **Dedicated Axon 5 Migration Sprint**
   - Allocate 2 weeks for Axon 4 → 5 migration
   - Review [Axon 5 Release Notes](https://www.axoniq.io/blog/release-of-axon-framework-5-0)
   - Focus on configuration changes ("Configuring Axon made easy")

2. **Parallel Testing**
   - Set up isolated environment with Axon 5
   - Test CQRS/ES functionality in isolation
   - Verify event store compatibility

3. **Incremental Rollout**
   - Migrate Axon 5 first (separate PR)
   - Stabilize on Spring Boot 3.x + Axon 5
   - Then migrate to Spring Boot 4 (separate PR)

---

#### R3: Jakarta EE 11 Incompatibilities

**Mitigation:**
1. **Hibernate ORM 7 Validation**
   - Test JPA entities with Hibernate 7
   - Verify jOOQ projections still work
   - Check for breaking changes in JPA 3.2

2. **Servlet 6.1 Validation**
   - Test TenantContextFilter with Servlet 6.1
   - Verify filter ordering and lifecycle
   - Test embedded Tomcat 11

3. **Bean Validation 3.1**
   - Verify Jakarta Validation constraints
   - Test custom validators
   - Check for API changes in constraint annotations

---

#### R4: Kotest Spring Boot 4 Issues

**Mitigation:**
1. **Kotest Compatibility Check**
   - Monitor: [Kotest Releases](https://github.com/kotest/kotest/releases)
   - Check: [kotest-extensions-spring](https://github.com/kotest/kotest-extensions-spring)

2. **Manual SpringExtension Updates**
   - If Kotest extension lags, update SpringExtension locally
   - Test `@SpringBootTest` lifecycle with Spring Boot 4
   - Verify `@DynamicPropertySource` behavior

3. **Fallback to JUnit 5** (LAST RESORT)
   - EAF has "Kotest only" policy, but if critical blocker:
   - Evaluate JUnit 5 + Kotest hybrid approach
   - Update coding standards if exception granted

---

#### R6: Security Regression

**Mitigation:**
1. **Security Test Suite Re-Execution**
   - All 10 JWT validation layers
   - All 3 tenant isolation layers
   - Token revocation cache functionality

2. **Dependency Vulnerability Scan**
   - Run OWASP Dependency Check
   - Review CVE database for new Spring vulnerabilities
   - Update dependencies to patched versions

3. **Penetration Testing**
   - Schedule security audit post-migration
   - Test for SQL injection, XSS, JNDI injection
   - Verify CWE-209 compliance (generic error messages)

---

#### R7: Multi-Tenancy Isolation Failure

**Mitigation:**
1. **Dedicated Tenant Isolation Test Suite**
   - Layer 1: TenantContextFilter tests
   - Layer 2: Command handler validation tests
   - Layer 3: PostgreSQL RLS tests

2. **Cross-Tenant Access Tests**
   - Attempt to access Tenant A resources with Tenant B JWT
   - Verify 403 Forbidden responses
   - Check for information leakage in error messages

3. **Database-Level Verification**
   - Query PostgreSQL system tables for RLS policies
   - Verify RLS policies active after migration
   - Test RLS bypass attempts (should fail)

---

#### R8: Production Deployment Failure

**Mitigation:**
1. **Blue-Green Deployment Strategy**
   - Deploy new version (Spring Boot 4) to "green" environment
   - Route 10% traffic to green (canary testing)
   - Monitor KPIs for 1 hour
   - If healthy, route 100% traffic to green
   - Keep "blue" (Spring Boot 3) running for quick rollback

2. **Automated Rollback Procedure**
   - Script: `scripts/rollback-spring-boot-4.sh`
   - Steps: Route traffic to blue, stop green, alert team
   - Test rollback procedure on staging

3. **Database Migration Safety**
   - Flyway migrations must be backward-compatible
   - No destructive schema changes (no DROP TABLE, DROP COLUMN)
   - Test database rollback on staging

---

## 6. Testing Strategy

### 6.1 Test Coverage Requirements

| Test Type | Target | Scope |
|-----------|--------|-------|
| **Unit Tests** | 85% line coverage | All business logic with Nullable Pattern |
| **Integration Tests** | All critical flows | Testcontainers (PostgreSQL, Keycloak, Redis) |
| **E2E Tests** | 100% user stories | Widget CRUD, auth, multi-tenancy |
| **Performance Tests** | No regression | Command latency <200ms, event lag <10s |
| **Security Tests** | 100% layers | 10-layer JWT, 3-layer tenant isolation |
| **Architecture Tests** | Zero violations | Konsist rules, Spring Modulith boundaries |
| **Mutation Tests** | 60-70% mutation coverage | Pitest (nightly) |

### 6.2 Test Environment Configuration

**Staging Environment:**
- Spring Boot 4.0.x + Spring Framework 7.0.x
- Axon Framework (compatible version)
- PostgreSQL 16.10 with event store schema
- Keycloak 26.4.2 for OIDC/JWT
- Redis 7.x for token revocation
- Prometheus + Grafana for observability

**Test Data:**
- Multi-tenant test data (3 tenants: tenant-a, tenant-b, tenant-c)
- 1000+ events in event store for performance testing
- JWT tokens for all 3 tenants
- Revoked tokens in Redis for revocation testing

### 6.3 Critical Test Scenarios

**Priority 1 (MUST PASS):**
1. Widget creation flow (command → event → projection)
2. JWT validation (all 10 layers)
3. Tenant isolation (cross-tenant access denied)
4. Token revocation (revoked tokens rejected)
5. PostgreSQL RLS enforcement
6. Actuator health checks
7. Prometheus metrics export

**Priority 2 (SHOULD PASS):**
1. Role-based access control (`@PreAuthorize`)
2. Error handling (RFC 7807 ProblemDetail)
3. Performance benchmarks (command latency, event lag)
4. Concurrent request handling
5. Database connection pooling
6. Event snapshots (every 100 events)

**Priority 3 (NICE TO HAVE):**
1. OpenAPI 3.0 documentation generation
2. CORS configuration
3. Nullable Design Pattern performance
4. Mutation testing (Pitest)

---

## 7. Rollback Plan

### 7.1 Rollback Triggers

Initiate rollback if any of the following occur:

1. **Critical Test Failures**
   - Multi-tenancy isolation failure (cross-tenant access)
   - Security regression (JWT validation bypass)
   - Data corruption or loss

2. **Performance Degradation**
   - Command latency p95 >500ms (2.5x threshold)
   - Event processor lag >30 seconds (3x threshold)

3. **Production Incidents**
   - Application crashes or OOM errors
   - Database connection pool exhaustion
   - Integration failures (Keycloak, PostgreSQL, Redis)

4. **Business Impact**
   - User-reported critical bugs
   - API downtime >5 minutes
   - Data integrity issues

### 7.2 Rollback Procedure

**Pre-Rollback:**
1. [ ] Identify root cause (if time permits)
2. [ ] Notify stakeholders of rollback decision
3. [ ] Capture logs and metrics for post-mortem

**Rollback Steps:**

**Step 1: Route Traffic to Blue Environment** (if using blue-green)
```bash
# Update load balancer to route to Spring Boot 3 (blue)
./scripts/route-traffic-to-blue.sh
```

**Step 2: Stop Green Environment**
```bash
# Stop Spring Boot 4 application (green)
docker-compose down
```

**Step 3: Verify Blue Environment Health**
```bash
# Check health endpoint
curl https://staging.eaf.example.com/actuator/health

# Check metrics
curl https://staging.eaf.example.com/actuator/prometheus | grep "http_requests_total"
```

**Step 4: Database Rollback** (if schema changes)
```bash
# Flyway rollback to previous version
./gradlew flywayUndo -Pflyway.target=V1.0
```

**Step 5: Monitoring**
```bash
# Monitor for 1 hour post-rollback
# Check:
# - Error rates
# - Response times
# - Database query performance
# - External integration health
```

### 7.3 Rollback SLA

- **Detection to Decision:** <15 minutes
- **Decision to Rollback Execution:** <5 minutes
- **Rollback Execution to Stable:** <10 minutes
- **Total Rollback Time:** <30 minutes

### 7.4 Post-Rollback Actions

1. [ ] Post-mortem meeting (within 24 hours)
2. [ ] Root cause analysis documentation
3. [ ] Update migration plan with lessons learned
4. [ ] Re-test failing scenarios in isolation
5. [ ] Schedule re-attempt (if issues resolved)

---

## 8. Timeline Estimates

**IMPORTANT:** Timeline is **CONTINGENT** on Axon Framework Spring Boot 4 support availability.

### 8.1 Optimistic Scenario (Axon Support Available)

| Phase | Duration | Dependencies | Start Date | End Date |
|-------|----------|--------------|------------|----------|
| **Phase 0: Pre-Migration** | Ongoing | Axon support announced | Now | TBD |
| **Phase 1: Dependency Updates** | 1-2 weeks | Phase 0 complete | TBD | TBD+2w |
| **Phase 2: Code Migration** | 2-3 weeks | Phase 1 complete | TBD+2w | TBD+5w |
| **Phase 3: Testing** | 3-4 weeks | Phase 2 complete | TBD+5w | TBD+9w |
| **Phase 4: Documentation & Deployment** | 1 week | Phase 3 complete | TBD+9w | TBD+10w |
| **Total** | **10 weeks** | | | |

**Contingency Buffer:** +2 weeks for unforeseen issues
**Total with Buffer:** **12 weeks (3 months)**

### 8.2 Realistic Scenario (Axon 5.0 Migration Required)

| Phase | Duration | Dependencies | Start Date | End Date |
|-------|----------|--------------|------------|----------|
| **Phase 0: Pre-Migration** | Ongoing | Axon 5.0 GA released | Now | TBD |
| **Phase 0.5: Axon 4 → 5 Migration** | 2-3 weeks | Axon 5.0 GA | TBD | TBD+3w |
| **Phase 1: Dependency Updates** | 2-3 weeks | Axon 5 stable | TBD+3w | TBD+6w |
| **Phase 2: Code Migration** | 3-4 weeks | Phase 1 complete | TBD+6w | TBD+10w |
| **Phase 3: Testing** | 4-5 weeks | Phase 2 complete | TBD+10w | TBD+15w |
| **Phase 4: Documentation & Deployment** | 1 week | Phase 3 complete | TBD+15w | TBD+16w |
| **Total** | **16 weeks** | | | |

**Contingency Buffer:** +4 weeks for Axon 5 breaking changes
**Total with Buffer:** **20 weeks (5 months)**

### 8.3 Pessimistic Scenario (No Axon Support)

**Status:** ⚠️ **Migration Indefinitely Deferred**

**Action:** Continue with Spring Boot 3.x until Axon support confirmed.

**Re-Evaluation:** Q2 2026 (May 2026)

---

## 9. Recommendations

### 9.1 Primary Recommendation: DEFER MIGRATION ⚠️

**Recommendation:** **DO NOT PROCEED** with Spring Boot 4 migration at this time.

**Rationale:**
1. **Critical Blocker:** Axon Framework has **no confirmed support** for Spring Boot 4 or Spring Framework 7
2. **Production Risk:** Axon is the **core CQRS/ES engine** for EAF - migration impossible without support
3. **Timeline Uncertainty:** Axon 5.0 roadmap does not specify Spring Boot 4 compatibility
4. **Spring Boot 3 Support:** Spring Boot 3.x has OSS support until **November 2026** (20 months remaining)

**Action Items:**
1. **Monitor AxonIQ announcements weekly** for Spring Boot 4 support
2. **Engage AxonIQ support** to understand roadmap and timeline
3. **Continue development on Spring Boot 3.5.x** (current stable platform)
4. **Re-evaluate migration in Q2 2026** (May 2026) or when Axon support confirmed

---

### 9.2 Preparatory Actions (Can Start Now)

While waiting for Axon Framework support, the following low-risk tasks can be started:

#### 9.2.1 Codebase Audit (1 week effort)
- [ ] Scan for `javax.*` imports (prepare for jakarta.* migration)
- [ ] Document all custom Spring configurations
- [ ] Review deprecated Spring APIs in use
- [ ] Identify any path matching configuration issues

#### 9.2.2 Environment Preparation (1 week effort)
- [ ] Set up isolated Spring Boot 4 test environment (non-production)
- [ ] Install Tomcat 11 for exploratory testing
- [ ] Test basic Spring Boot 4 application (Hello World)
- [ ] Document environment setup procedure

#### 9.2.3 Team Training (2 weeks effort)
- [ ] Review Spring Framework 7 release notes (team session)
- [ ] Review Spring Boot 4 migration guide (team session)
- [ ] Hands-on workshop: Deploy sample app on Spring Boot 4
- [ ] Identify migration champions and responsibilities

#### 9.2.4 Baseline Metrics (Ongoing)
- [ ] Establish performance baselines (command latency, event lag)
- [ ] Document current test suite execution times
- [ ] Capture dependency resolution times
- [ ] Monitor current production KPIs

**Total Effort:** 4-5 weeks (can be done in parallel with current development)
**Risk:** 🟢 **LOW** (no production impact)
**Benefit:** Reduces Phase 0 duration when migration becomes viable

---

### 9.3 When to Reconsider Migration

**Trigger Conditions:**
1. ✅ **Axon Framework announces Spring Boot 4 support** (Axon 4.x or 5.x)
2. ✅ **Axon 5.0 GA released** with confirmed Spring Boot 4 compatibility
3. ✅ **Spring Boot 3.x approaching EOL** (November 2026 for OSS)
4. ✅ **Critical Spring Framework 7 features required** for new functionality

**Decision Matrix:**

| Scenario | Axon Support | Spring Boot 3 EOL | Action |
|----------|-------------|------------------|--------|
| 1 | ✅ Confirmed | >12 months away | **Proceed** with migration (low urgency) |
| 2 | ✅ Confirmed | <6 months away | **Proceed** with migration (high urgency) |
| 3 | ❌ Not confirmed | >12 months away | **Defer** migration, continue monitoring |
| 4 | ❌ Not confirmed | <6 months away | **Escalate** to AxonIQ, consider alternatives |

---

### 9.4 Alternative Strategies (If Axon Support Unavailable)

**If Axon Framework does NOT support Spring Boot 4 by Q3 2026:**

#### Option A: Stay on Spring Boot 3.x Long-Term
- **Action:** Continue with Spring Boot 3.x through commercial support (until May 2028)
- **Cost:** Commercial support fees
- **Risk:** 🟢 **LOW** - Maintains current stable platform
- **Recommendation:** Preferred option if Axon support unavailable

#### Option B: Migrate Away from Axon Framework
- **Action:** Replace Axon with alternative CQRS/ES framework
- **Alternatives:** EventStore, self-built CQRS/ES infrastructure
- **Effort:** 6-12 months (major architectural change)
- **Risk:** 🔴 **CRITICAL** - Requires rewriting all command handlers, event handlers, aggregates
- **Recommendation:** **NOT RECOMMENDED** - Too disruptive, high risk

#### Option C: Fork and Maintain Axon Spring Boot 4 Support
- **Action:** Fork Axon Framework, add Spring Boot 4 compatibility patches
- **Effort:** 4-8 weeks + ongoing maintenance burden
- **Risk:** 🔴 **HIGH** - Maintenance overhead, security vulnerabilities, loss of official support
- **Recommendation:** **AVOID** unless critical business need

**Recommended Path:** **Option A** (Stay on Spring Boot 3.x with commercial support)

---

### 9.5 Communication Plan

**Stakeholders:**
- Product Owner
- Architecture Team
- Development Team
- Operations Team
- Security Team

**Communication:**
1. **Now (2025-11-17):**
   - Share this migration plan document
   - Present findings: Axon Framework blocker
   - Recommendation: Defer migration

2. **Monthly Updates:**
   - Axon Framework Spring Boot 4 support status
   - Spring Boot 3 EOL timeline
   - Preparatory task progress

3. **When Axon Support Confirmed:**
   - Kick-off meeting for migration project
   - Updated timeline and resource allocation
   - Migration sprint planning

---

## 10. Appendices

### 10.1 Reference Links

**Spring Framework 7:**
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Spring Framework 7.0 GA Announcement](https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability/)
- [From Spring Framework 6.2 to 7.0](https://spring.io/blog/2024/10/01/from-spring-framework-6-2-to-7-0/)

**Spring Boot 4:**
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot 4.0.0-RC2 Announcement](https://spring.io/blog/2025/10/24/spring-boot-4-0-0-RC2-available-now/)

**Spring Modulith 2:**
- [Spring Modulith 2.0 RC1 Release](https://spring.io/blog/2025/10/27/spring-modulith-2-0-rc1-1-4-4-and-1-3-10-released/)
- [Spring Modulith Documentation](https://docs.spring.io/spring-modulith/reference/index.html)

**Axon Framework:**
- [Axon Framework Releases](https://github.com/AxonFramework/AxonFramework/releases)
- [Axon Framework 5.0 Release](https://www.axoniq.io/blog/release-of-axon-framework-5-0)
- [Axon Discuss Forum](https://discuss.axoniq.io/)
- [AxonIQ 2025 Outlook](https://www.axoniq.io/blog/2025-axoniq-forecast)

**Jakarta EE 11:**
- [Jakarta EE 11 Release](https://jakarta.ee/release/11/)

**jOOQ:**
- [jOOQ Release Notes](https://www.jooq.org/notes)
- [jOOQ and Jakarta EE](https://blog.jooq.org/jooq-3-16-and-java-ee-vs-jakarta-ee/)

**Kotest:**
- [Kotest Spring Extension](https://kotest.io/docs/extensions/spring.html)
- [Kotest Spring GitHub](https://github.com/kotest/kotest-extensions-spring)

---

### 10.2 EAF Current Dependency Snapshot

**Generated:** 2025-11-17
**Source:** `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.2.21"
spring-boot = "3.5.7"
spring-framework = "6.2.12"
spring-modulith = "1.4.4"
spring-security = "6.5.6"
spring-dependency-management = "1.1.7"

axon-framework = "4.12.1"
jooq = "3.20.8"
kotest = "6.0.4"
kotest-extensions-spring = "1.1.3"
arrow = "2.1.2"
micrometer = "1.16.0"

postgresql = "42.7.5"
testcontainers = "1.20.6"
keycloak = "26.4.2"
redis = "3.5.7"

detekt = "1.23.8"
ktlint = "1.7.1"
kover = "0.9.3"
pitest = "1.19.0"
konsist = "0.17.3"
```

---

### 10.3 Spring Boot 3 Support Timeline

**Official Support:**
- **OSS Support Ends:** November 2026 (24 months from Spring Boot 4 GA)
- **Commercial Support:** Available through Spring Boot 3.5.x until May 2028

**Reference:** [Spring Boot Lifecycle](https://endoflife.date/spring-boot)

**Implication:** EAF has **20 months** (until November 2026) to complete migration without requiring commercial support.

---

### 10.4 Glossary

| Term | Definition |
|------|------------|
| **CQRS** | Command Query Responsibility Segregation - architectural pattern separating write (commands) and read (queries) operations |
| **Event Sourcing** | Pattern where state changes are stored as a sequence of events rather than current state |
| **BOM** | Bill of Materials - Maven/Gradle mechanism for managing dependency versions |
| **RLS** | Row-Level Security - PostgreSQL feature for database-level tenant isolation |
| **JWT** | JSON Web Token - compact, URL-safe means of representing claims between parties |
| **OIDC** | OpenID Connect - identity layer on top of OAuth 2.0 |
| **Nullable Pattern** | EAF testing pattern for fast infrastructure substitutes with null/no-op implementations |
| **Constitutional TDD** | EAF development practice mandating test-first development |
| **Blue-Green Deployment** | Deployment strategy with two identical environments for zero-downtime releases |

---

## Document Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-17 | Architecture Team | Initial migration plan created |

---

## Approval and Sign-off

**Status:** 🟡 **DRAFT - Awaiting Review**

**Approvers:**
- [ ] **Product Owner** - Business impact review
- [ ] **Lead Architect** - Technical approach review
- [ ] **Development Lead** - Effort estimation review
- [ ] **Operations Lead** - Deployment strategy review
- [ ] **Security Lead** - Security impact review

**Next Steps:**
1. Review this document with stakeholders
2. Decision: Approve recommendation to defer migration
3. If approved: Begin Phase 0 preparatory tasks
4. Schedule quarterly reviews (Q1 2026, Q2 2026, Q3 2026)

---

**Document End**
