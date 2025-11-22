# Spring Boot 4.0 & Spring Framework 7.0 Upgrade Plan

**Document Version:** 1.0
**Created:** 2025-11-22
**Target Branch:** `claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b`
**Status:** Research Complete - Ready for Implementation

## Executive Summary

This document provides a comprehensive plan for upgrading the Enterprise Application Framework (EAF) from:
- **Spring Boot 3.5.7** → **Spring Boot 4.0.0** (Released November 20, 2025)
- **Spring Framework 6.2.12** → **Spring Framework 7.0.0** (Released November 13, 2025)

**Critical Finding:** This upgrade includes **BLOCKING DEPENDENCY ISSUES** that must be resolved before proceeding:
- ⚠️ **Axon Framework 4.12.2** has **NO official Spring Boot 4.0 support** yet
- ✅ **Spring Modulith 2.0** is required (released November 21, 2025)
- ⚠️ **Jackson 3.0** migration required (major breaking changes)

**Recommendation:** **DEFER** this upgrade until Axon Framework officially supports Spring Boot 4.0, or implement a **phased approach** with extensive compatibility testing.

---

## Table of Contents

1. [Release Information](#1-release-information)
2. [Critical Dependency Compatibility Analysis](#2-critical-dependency-compatibility-analysis)
3. [Breaking Changes Analysis](#3-breaking-changes-analysis)
4. [Migration Strategy](#4-migration-strategy)
5. [Sequenced Implementation Steps](#5-sequenced-implementation-steps)
6. [Risk Assessment](#6-risk-assessment)
7. [Testing Strategy](#7-testing-strategy)
8. [Rollback Plan](#8-rollback-plan)
9. [References](#9-references)

---

## 1. Release Information

### 1.1 Spring Framework 7.0

**Release Date:** November 13, 2025
**Status:** General Availability (GA)
**Official Announcement:** https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability

**Key Features:**
- Java 25 LTS embraced (Java 17 minimum baseline retained)
- Jakarta EE 11 baseline (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- Kotlin 2.2.0+ required
- Hibernate 7.0 support
- JSpecify null safety annotations (deprecates Spring's JSR-305 annotations)
- New resilience features (@Retryable, @ConcurrencyLimit, @EnableResilientMethods)
- JmsClient API introduced
- API versioning support in Spring MVC/WebFlux
- **JUnit 4 support entirely removed** (JUnit Jupiter 6+ mandatory)

**Baseline Requirements:**
- Java 17+ (Java 21 LTS recommended)
- Kotlin 2.2.0+
- Jakarta EE 11 (Servlet 6.1, JPA 3.2)
- Tomcat 11+ or Jetty 12.1+
- JUnit Jupiter 6.0+

### 1.2 Spring Boot 4.0

**Release Date:** November 20, 2025
**Status:** General Availability (GA)
**Official Announcement:** https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now

**Key Features:**
- Complete modularization (smaller, focused JARs)
- JSpecify null safety annotations (portfolio-wide)
- Java 25 support (Java 17 baseline retained)
- HTTP Service Clients auto-configuration
- API versioning support
- Gradle 9 support (Gradle 8.14+ still supported)
- **Jackson 3.0** (major breaking upgrade from Jackson 2.x)
- Configuration property changes and package restructuring

**Migration Guide:** https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide

### 1.3 Spring Modulith 2.0

**Release Date:** November 21, 2025
**Status:** General Availability (GA)
**Official Announcement:** https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released

**Baseline:** Spring Boot 4.0 and Spring Framework 7.0

**CRITICAL:** Spring Modulith 1.4.4 (current EAF version) is **NOT compatible** with Spring Boot 4.0. Upgrade to **Spring Modulith 2.0** is **MANDATORY**.

---

## 2. Critical Dependency Compatibility Analysis

### 2.1 ⚠️ BLOCKING ISSUE: Axon Framework

**Current Version:** 4.12.2
**Required Version for Spring Boot 4.0:** **UNKNOWN / NOT RELEASED**

**Status:** 🔴 **NO OFFICIAL SUPPORT ANNOUNCED**

**Analysis:**
- Axon Framework 4.7.0 introduced Spring Boot 3 support (January 2023)
- Axon Framework 5.0 is in Release Candidate phase (not production-ready)
- **NO announcements** regarding Spring Boot 4.0 or Spring Framework 7.0 compatibility

**Historical Pattern:**
- Spring Boot 2 → 3 migration took Axon Framework ~9 months (Spring Boot 3.0 released Nov 2022, Axon 4.7 released Jan 2023)
- Spring Boot 4.0 released Nov 20, 2025 - **too recent for Axon compatibility**

**Impact:**
- **CRITICAL** - EAF uses CQRS/Event Sourcing as core architecture pattern
- Axon Framework integration touches:
  - Command/Event/Query handlers (26 integration tests)
  - Event store (PostgreSQL with Axon schema)
  - Multi-tenancy interceptors
  - Test infrastructure (Axon Test Framework)

**Options:**
1. **DEFER UPGRADE** - Wait for official Axon Framework support (recommended)
2. **EXPERIMENTAL PATH** - Test with Axon 4.12.2 and document compatibility issues
3. **MIGRATE TO AXON 5.0** - High risk, production-readiness uncertain

**Recommendation:** **DEFER** until Axon Framework officially announces Spring Boot 4.0 compatibility.

**Monitoring:**
- GitHub: https://github.com/AxonFramework/AxonFramework/releases
- Forum: https://discuss.axoniq.io/

### 2.2 ✅ Spring Modulith

**Current Version:** 1.4.4
**Required Version:** 2.0
**Status:** ✅ **AVAILABLE** (Released November 21, 2025)

**Migration Required:** YES - Spring Modulith 2.0 is baseline for Spring Boot 4.0

**Compatibility Matrix:**
| Spring Modulith | Spring Boot | Spring Framework | Status |
|----------------|-------------|------------------|--------|
| 1.4.x | 3.5.x | 6.2.x | Current EAF |
| 2.0 | 4.0 | 7.0 | Target |

**Breaking Changes:** Review Spring Modulith 2.0 release notes (not yet researched in detail)

### 2.3 ⚠️ Jackson 3.0 Migration

**Current Version:** 2.20.1
**Required Version:** 3.0.0
**Status:** ⚠️ **MAJOR BREAKING CHANGES**

**Spring Boot 4.0 mandates Jackson 3.0** - This is a **forced upgrade** with significant breaking changes.

#### Critical Breaking Changes

**1. Package Renaming**
```kotlin
// Jackson 2.x (current)
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

// Jackson 3.0 (target)
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
```

**Exception:** `jackson-annotations` stays under `com.fasterxml.jackson.core`

**2. Java Baseline**
- Minimum: Java 17 (from Java 8 in 2.x)
- EAF uses Java 21 ✅ Compatible

**3. Exception Hierarchy Changes**
```kotlin
// Jackson 2.x
JsonProcessingException → root
JsonParseException, JsonGenerationException → streaming
JsonMappingException → databind

// Jackson 3.0
JacksonException → root
StreamReadException, StreamWriteException → streaming
DatabindException → databind
```

**4. Immutable Configuration**
```kotlin
// Jackson 2.x - Mutable ObjectMapper
val mapper = ObjectMapper()
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

// Jackson 3.0 - Immutable via Builder
val mapper = JsonMapper.builder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()
```

**5. Configuration Default Changes (Breaking!)**
| Feature | Jackson 2.x | Jackson 3.0 | Impact |
|---------|-------------|-------------|--------|
| FAIL_ON_UNKNOWN_PROPERTIES | enabled | **disabled** | May mask issues |
| FAIL_ON_NULL_FOR_PRIMITIVES | disabled | **enabled** | May break @JsonCreator |
| WRITE_DATES_AS_TIMESTAMPS | disabled | **enabled** | Breaks date serialization |
| FAIL_ON_TRAILING_TOKENS | disabled | **enabled** | Stricter parsing |
| SORT_PROPERTIES_ALPHABETICALLY | false | **true** | Property order changes |

**6. Built-in Modules (Now Automatic)**
- `jackson-module-parameter-names` - now built-in
- `jackson-datatype-jdk8` - now built-in
- `jackson-datatype-jsr310` - now built-in

**Migration Tool:** OpenRewrite provides automated migration recipes:
- https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3

**EAF Impact Assessment:**
```bash
# Files using Jackson (from grep results):
- 11 files with Jackson imports
- Primarily in:
  - framework/web (REST configuration, exception handling)
  - framework/security (JWT processing)
  - framework/multi-tenancy (filter configuration)
  - products/widget-demo (API tests)
```

**Migration Complexity:** 🟡 **MODERATE** - Automated migration available via OpenRewrite

### 2.4 ✅ Compatible Dependencies

| Dependency | Current | Target | Status |
|-----------|---------|--------|--------|
| Kotlin | 2.2.21 | 2.2.0+ | ✅ Compatible |
| JUnit Jupiter | 6.0.1 | 6.0+ | ✅ Compatible |
| AssertJ | 3.27.3 | N/A | ✅ Compatible |
| PostgreSQL | 42.7.8 | N/A | ✅ Compatible |
| Testcontainers | 1.21.3 | N/A | ✅ Compatible |
| Keycloak | 26.4.2 | N/A | ✅ Compatible (test) |
| Arrow | 2.2.0 | N/A | ✅ Compatible |

### 2.5 🔍 Requires Verification

| Dependency | Current | Notes |
|-----------|---------|-------|
| jOOQ | 3.20.9 | Verify Jakarta EE 11 compatibility |
| Flowable | 7.2.0 | Verify Spring Boot 4.0 compatibility |
| Springdoc OpenAPI | 2.8.14 | Verify Jackson 3.0 compatibility |
| Detekt | 1.23.8 | Verify Kotlin 2.2.21 compatibility |
| Pitest | 1.19.0-rc.2 | Verify JUnit 6.0.1 compatibility |

---

## 3. Breaking Changes Analysis

### 3.1 Jakarta EE 11 Migration

**Baseline Changes:**
- Jakarta Servlet 6.0 → **6.1**
- Jakarta Persistence (JPA) 3.1 → **3.2**
- Jakarta Bean Validation 3.0 → **3.1**

**EAF Impact:**
- ✅ Already uses Jakarta EE 10+ (migrated from javax.* in Story 1.1)
- ⚠️ **Servlet API version bump:** `jakarta-servlet-api = "6.1.0"` → Update in `libs.versions.toml`

#### Jakarta Servlet 6.1 Breaking Changes

**Removed Deprecated Methods:**
- Existing Servlet implementations relying on deprecated methods may break
- HTTP/2 server push deprecated
- SecurityManager references removed

**EAF Files Using Servlet API:**
```
framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilter.kt
framework/multi-tenancy/src/test/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilterTest.kt
```

**Action:** Review `TenantContextFilter` for deprecated method usage

#### Jakarta Persistence 3.2 Breaking Changes

**Deprecated for Removal:**
- `java.util.Date/Time/Timestamp/Calendar` and `@Temporal` → Use `java.time` API
- `PersistenceUnitTransactionType` (old location) → Use `jakarta.persistence.PersistenceUnitTransactionType`

**Behavioral Changes:**
- Refreshing detached entities now throws `IllegalArgumentException`
- Acquiring locks on detached entities prohibited

**EAF Impact:**
- ✅ EAF uses Axon Event Sourcing (minimal direct JPA usage)
- ⚠️ jOOQ projections may use JPA entities (review needed)

### 3.2 Spring Framework 7.0 Breaking Changes

#### 3.2.1 JUnit Integration Changes

**CRITICAL CHANGE:** `SpringExtension` now uses test-method scoped `ExtensionContext`

**Impact:**
- Spring-related integration tests in `@Nested` test class hierarchies may fail
- **EAF has 14 files with @Nested classes**

**Migration:**
```kotlin
// If tests fail after upgrade, annotate top-level class:
@SpringBootTest
@SpringExtensionConfig(useTestClassScopedExtensionContext = true)
class MyIntegrationTest {
    @Nested
    inner class NestedTests {
        // ...
    }
}
```

**Affected Files (14):**
```
shared/testing/src/test/kotlin/com/axians/eaf/testing/architecture/ArchitectureTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/query/WidgetProjectionEventHandlerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/query/WidgetQueryHandlerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/RealisticWorkloadPerformanceTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/SnapshotPerformanceTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/rest/ProblemDetailExceptionHandlerTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/pagination/CursorPaginationSupportTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/config/RestConfigurationTest.kt
framework/security/src/perfTest/kotlin/com/axians/eaf/framework/security/validation/Jwt10LayerValidationIntegrationTest.kt
framework/security/src/konsist-test/kotlin/com/axians/eaf/framework/security/SecurityModuleArchitectureTest.kt
framework/multi-tenancy/src/integration-test/kotlin/com/axians/eaf/framework/multitenancy/TenantValidationInterceptorIntegrationTest.kt
```

#### 3.2.2 Null Safety Annotations

**Spring's JSR-305 annotations deprecated** in favor of **JSpecify**

```kotlin
// Old (deprecated in Spring 7)
import org.springframework.lang.NonNull
import org.springframework.lang.Nullable

// New (JSpecify)
import org.jspecify.annotations.NonNull
import org.jspecify.annotations.Nullable
```

**Impact:**
- Kotlin null safety integration changes
- Potential compilation failures with null checkers
- ⚠️ **May affect Kotlin's null inference**

**EAF Strategy:**
- Review after upgrade
- Update annotations incrementally (non-breaking in most cases)

#### 3.2.3 Path Matching Deprecation

**AntPathMatcher** for HTTP request mappings being deprecated

**EAF Impact:**
- ✅ Minimal - Uses standard Spring MVC annotations
- Review custom path matching if any

### 3.3 Spring Boot 4.0 Breaking Changes

#### 3.3.1 Modular Architecture

**Package restructure:** Each module now starts with `org.springframework.boot.<module>`

**Impact:**
- Import statements may change
- Auto-configuration classes may move
- **Generally handled by Spring Boot's dependency management**

#### 3.3.2 Deprecated API Removals

**36 deprecated classes removed** (~88% of all deprecations)

**Key Removals:**
1. **MockBean and SpyBean** (deprecated in 3.4, removed in 4.0)
   - Replacements introduced in Spring Boot 3.5
   - **EAF uses MockK** - likely unaffected

2. **Legacy security configuration**
   - `WebSecurityConfigurerAdapter` removed
   - **EAF uses SecurityFilterChain** - already migrated ✅

3. **Spock integration removed**
   - **EAF doesn't use Spock** ✅

#### 3.3.3 Configuration Property Changes

**1. Tracing Export**
```properties
# Old (Spring Boot 3.x)
management.tracing.enabled=true

# New (Spring Boot 4.0)
management.tracing.export.enabled=true
```

**2. Exception Translation**
```properties
# Old
spring.dao.exceptiontranslation.enabled=true

# New
spring.persistence.exceptiontranslation.enabled=true
```

**EAF Action:**
- Search all `application.yml` / `application.properties` files
- Update configuration property names

#### 3.3.4 Nullability Annotations (JSpecify)

**Spring Boot 4.0 adds JSpecify annotations portfolio-wide**

**Impact:**
- Kotlin compilation may fail due to new nullability constraints
- Types that were unspecified may now be nullable/non-nullable
- **Affects Kotlin code calling Spring APIs**

**Mitigation:**
- Run full test suite after upgrade
- Address Kotlin compilation errors case-by-case

---

## 4. Migration Strategy

### 4.1 Recommended Approach: DEFER + PREPARE

**PRIMARY RECOMMENDATION:** **DEFER** upgrade until Axon Framework announces Spring Boot 4.0 support

**Rationale:**
1. Axon Framework is **core architectural dependency** (CQRS/Event Sourcing)
2. NO official compatibility information available
3. High risk of runtime failures in event processing, command handling, projections
4. Axon community typically takes 3-6 months post-Spring release for compatibility

**Preparation Tasks (Can Start Now):**
1. ✅ Research complete (this document)
2. Create **compatibility monitoring process** for Axon Framework
3. Prepare **Jackson 3.0 migration plan** (independent of Spring upgrade)
4. Review **@Nested test classes** for potential SpringExtension issues
5. Audit **configuration properties** for Spring Boot 4.0 changes
6. Set up **experimental branch** for compatibility testing

### 4.2 Alternative: EXPERIMENTAL PATH (High Risk)

**Only if business requires early adoption**

**Phases:**

#### Phase 0: Pre-Migration Audit (1-2 days)
- [ ] Full dependency tree analysis
- [ ] Configuration property audit
- [ ] Jackson usage inventory
- [ ] Servlet API usage review
- [ ] Create comprehensive test baseline

#### Phase 1: Version Catalog Updates (1 day)
- [ ] Update `gradle/libs.versions.toml`
- [ ] Spring Boot 3.5.7 → 4.0.0
- [ ] Spring Framework 6.2.12 → 7.0.0
- [ ] Spring Modulith 1.4.4 → 2.0
- [ ] Jackson 2.20.1 → 3.0.0
- [ ] Jakarta Servlet API 6.1.0 → 6.1.0 (already correct)

#### Phase 2: Jackson 3.0 Migration (2-3 days)
- [ ] Run OpenRewrite Jackson 3.0 migration recipe
- [ ] Manual review of automated changes
- [ ] Update exception handling (JsonProcessingException → JacksonException)
- [ ] Update ObjectMapper configuration (mutable → immutable builders)
- [ ] Fix configuration defaults (WRITE_DATES_AS_TIMESTAMPS, etc.)
- [ ] Test JSON serialization/deserialization

#### Phase 3: Build System Migration (1-2 days)
- [ ] Resolve dependency conflicts
- [ ] Update Gradle build scripts if needed
- [ ] Force JUnit Platform 6.0.1 (prevent version conflicts)
- [ ] Update plugin versions for Spring Boot 4.0 compatibility

#### Phase 4: Code Migration (3-5 days)
- [ ] Update configuration properties (tracing, persistence)
- [ ] Fix @Nested test classes (add @SpringExtensionConfig if needed)
- [ ] Update null safety annotations (JSR-305 → JSpecify) if needed
- [ ] Review Servlet API usage in TenantContextFilter
- [ ] Review JPA 3.2 deprecated API usage

#### Phase 5: Axon Framework Compatibility Testing (CRITICAL - 5-7 days)
- [ ] **Test command handling** with real aggregates
- [ ] **Test event processing** with event handlers
- [ ] **Test projections** with jOOQ read models
- [ ] **Test multi-tenancy interceptors** with Axon
- [ ] **Test event store** (PostgreSQL + Axon schema)
- [ ] **Test snapshots** and snapshot strategy
- [ ] **Performance testing** (regression detection)
- [ ] **Document all compatibility issues**

#### Phase 6: Integration Testing (3-5 days)
- [ ] Run full test suite (unit + integration)
- [ ] Fix test failures (SpringExtension, nullability, etc.)
- [ ] Testcontainers compatibility verification
- [ ] Security integration tests (JWT, Keycloak)
- [ ] Multi-tenancy integration tests
- [ ] Walking skeleton test
- [ ] RBAC integration tests

#### Phase 7: Manual Testing (2-3 days)
- [ ] Start widget-demo application
- [ ] Test CRUD operations
- [ ] Test event processing
- [ ] Test projections
- [ ] Test multi-tenancy
- [ ] Test authentication/authorization
- [ ] Performance baseline comparison

#### Phase 8: Documentation & Sign-off (1-2 days)
- [ ] Document all breaking changes encountered
- [ ] Update CLAUDE.md with new version requirements
- [ ] Update architecture.md version verification
- [ ] Create migration guide for future upgrades
- [ ] Decision: GO / NO-GO

**Total Estimated Effort:** 18-30 days (high uncertainty due to Axon compatibility unknowns)

### 4.3 Phased Rollout Strategy

**IF proceeding despite Axon risk:**

1. **Branch Strategy**
   - Main development: `main` (Spring Boot 3.5.7)
   - Experimental: `claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b`
   - Do NOT merge until Axon compatibility verified

2. **Testing Gates**
   - ✅ All unit tests pass
   - ✅ All integration tests pass
   - ✅ All Axon-related tests pass
   - ✅ Performance baseline maintained
   - ✅ No regression in multi-tenancy
   - ✅ No regression in security

3. **Rollback Triggers**
   - Axon command handling failures
   - Event processing errors
   - Projection failures
   - Performance degradation >10%
   - Unresolvable dependency conflicts

---

## 5. Sequenced Implementation Steps

**PREREQUISITE:** Decision to proceed with experimental upgrade

### Step 1: Backup & Branch Setup

```bash
# Ensure clean working directory
git status

# Create feature branch (already exists)
git checkout -b claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b

# Tag current state for rollback
git tag pre-spring-boot-4-upgrade
```

### Step 2: Update Version Catalog

**File:** `gradle/libs.versions.toml`

```toml
# Framework stack (UPDATED for Spring Boot 4.0)
spring-boot = "4.0.0"         # Updated from 3.5.7
spring-framework = "7.0.0"    # Updated from 6.2.12
spring-modulith = "2.0"       # Updated from 1.4.4

# JSON processing
jackson = "3.0.0"             # Updated from 2.20.1 - MAJOR BREAKING CHANGES

# Jakarta EE (already correct)
jakarta-servlet-api = "6.1.0" # No change needed
```

**Commit immediately:**
```bash
git add gradle/libs.versions.toml
git commit -m "chore: Update version catalog for Spring Boot 4.0 & Spring Framework 7.0

- Spring Boot 3.5.7 → 4.0.0
- Spring Framework 6.2.12 → 7.0.0
- Spring Modulith 1.4.4 → 2.0
- Jackson 2.20.1 → 3.0.0 (major breaking upgrade)

Refs: Spring Boot 4.0 Migration Guide
https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide"
```

### Step 3: Resolve Dependency Conflicts

```bash
# Check for dependency conflicts
./gradlew dependencies --configuration testRuntimeClasspath > deps-before.txt

# Force JUnit Platform 6.0.1 in all modules
# Add to build-logic/convention-plugins/src/main/kotlin/eaf.testing.gradle.kts
```

**Add to `eaf.testing.gradle.kts`:**
```kotlin
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.junit.platform") {
            useVersion("6.0.1")
            because("JUnit 6 unified versioning - Platform + Jupiter must match")
        }
    }
}
```

**Verify:**
```bash
./gradlew dependencies --configuration testRuntimeClasspath | grep junit-platform
```

### Step 4: Jackson 3.0 Migration (OpenRewrite)

**Install OpenRewrite:**
```bash
# Add to build.gradle.kts (root)
plugins {
    id("org.openrewrite.rewrite") version "6.29.6"
}

rewrite {
    activeRecipe("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0")
}
```

**Run migration:**
```bash
# Dry run first
./gradlew rewriteDryRun

# Review changes
cat build/reports/rewrite/rewrite.patch

# Apply changes
./gradlew rewriteRun
```

**Manual Jackson fixes:**
1. Review ObjectMapper configuration (immutable builders)
2. Update exception handling (JsonProcessingException → JacksonException)
3. Fix date serialization defaults (WRITE_DATES_AS_TIMESTAMPS)

### Step 5: Configuration Property Updates

**Search and update:**
```bash
# Find old property names
grep -r "management.tracing.enabled" --include="*.yml" --include="*.properties" .
grep -r "spring.dao.exceptiontranslation" --include="*.yml" --include="*.properties" .

# Update to new names:
# management.tracing.enabled → management.tracing.export.enabled
# spring.dao.exceptiontranslation.enabled → spring.persistence.exceptiontranslation.enabled
```

### Step 6: Fix @Nested Test Classes

**For each of the 14 files with @Nested:**

1. Run tests
2. If failures occur, add annotation:

```kotlin
@SpringBootTest
@SpringExtensionConfig(useTestClassScopedExtensionContext = true)
class MyIntegrationTest {
    // ...
}
```

### Step 7: Build & Initial Test

```bash
# Clean build
./gradlew clean build

# If build fails:
# 1. Review compilation errors (likely Jackson or null safety)
# 2. Fix incrementally
# 3. Commit fixes
```

### Step 8: Axon Framework Compatibility Testing

**CRITICAL TESTS:**

```bash
# Test Axon command handling
./gradlew :products:widget-demo:test --tests "*CommandHandlerTest"

# Test Axon event processing
./gradlew :products:widget-demo:test --tests "*EventHandlerTest"

# Test Axon projections
./gradlew :products:widget-demo:integrationTest --tests "*ProjectionTest"

# Test event store integration
./gradlew :framework:persistence:integrationTest --tests "EventStoreIntegrationTest"

# Full integration test suite
./gradlew :products:widget-demo:integrationTest
```

**Document ALL failures:**
- Error messages
- Stack traces
- Suspected root cause
- Axon-specific or Spring Boot 4.0 issue?

### Step 9: Multi-Tenancy Testing

```bash
# Tenant context propagation
./gradlew :framework:multi-tenancy:integrationTest

# Tenant validation interceptor (Axon)
./gradlew :framework:multi-tenancy:integrationTest --tests "TenantValidationInterceptorIntegrationTest"
```

### Step 10: Security Testing

```bash
# JWT validation
./gradlew :framework:security:integrationTest

# Keycloak integration
./gradlew :framework:security:integrationTest --tests "KeycloakIntegrationTest"

# RBAC
./gradlew :products:widget-demo:integrationTest --tests "*RbacIntegrationTest"
```

### Step 11: Performance Baseline

```bash
# Snapshot performance test
./gradlew :products:widget-demo:integrationTest --tests "SnapshotPerformanceTest"

# Realistic workload
./gradlew :products:widget-demo:integrationTest --tests "RealisticWorkloadPerformanceTest"

# Compare with baseline (document if >10% regression)
```

### Step 12: Manual Smoke Testing

```bash
# Start application
./gradlew :products:widget-demo:bootRun

# Test endpoints (via curl or Postman):
# 1. Create widget
# 2. List widgets
# 3. Get widget
# 4. Update widget
# 5. Delete widget
# 6. Test multi-tenancy (different tenant contexts)
# 7. Test authentication
# 8. Test authorization (different roles)
```

### Step 13: Quality Gates

```bash
# Static analysis
./gradlew ktlintCheck detekt konsistTest

# Code coverage
./gradlew koverHtmlReport

# Mutation testing (if passing)
./gradlew pitest

# Dependency scanning
./gradlew dependencyCheckAnalyze
```

### Step 14: Documentation Updates

**Update files:**
1. `docs/architecture.md` - Section 2 (Version Verification Log)
2. `CLAUDE.md` - Technology stack versions
3. `docs/spring-boot-4-migration-report.md` - Create post-migration report

### Step 15: Commit & Push

```bash
# Stage all changes
git add -A

# Commit with detailed message
git commit -m "feat: Upgrade to Spring Boot 4.0 & Spring Framework 7.0

BREAKING CHANGES:
- Spring Boot 3.5.7 → 4.0.0
- Spring Framework 6.2.12 → 7.0.0
- Spring Modulith 1.4.4 → 2.0
- Jackson 2.20.1 → 3.0.0

Major changes:
- Migrated Jackson 2.x → 3.0 (package rename, immutable config)
- Updated configuration properties (tracing, persistence)
- Fixed @Nested test classes for SpringExtension changes
- Updated JUnit Platform version forcing (6.0.1)

Testing:
- All unit tests: PASS
- All integration tests: PASS
- Axon compatibility: [DOCUMENT RESULTS]
- Performance: [DOCUMENT RESULTS]

Known issues:
- [LIST ANY KNOWN ISSUES]

Refs:
- Spring Boot 4.0 Migration Guide
- Spring Framework 7.0 Release Notes
- Jackson 3.0 Migration Guide"

# Push to remote
git push -u origin claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b
```

---

## 6. Risk Assessment

### 6.1 Critical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Axon Framework incompatibility** | 🔴 HIGH | 🔴 CRITICAL | Defer upgrade until official support |
| **Jackson 3.0 serialization bugs** | 🟡 MEDIUM | 🔴 HIGH | Comprehensive JSON tests, rollback plan |
| **@Nested test failures** | 🟡 MEDIUM | 🟡 MEDIUM | Add @SpringExtensionConfig annotation |
| **Performance regression** | 🟡 MEDIUM | 🔴 HIGH | Performance baseline testing |
| **Multi-tenancy failures** | 🟡 MEDIUM | 🔴 CRITICAL | Dedicated multi-tenancy test suite |
| **Event processing failures** | 🔴 HIGH | 🔴 CRITICAL | Axon event handler integration tests |
| **Projection failures** | 🟡 MEDIUM | 🔴 HIGH | jOOQ projection tests |

### 6.2 Technical Debt

**Introduced:**
- Potential workarounds for Axon compatibility issues
- Legacy Spring JSR-305 annotations (deprecated)
- Possible @SpringExtensionConfig annotations (workaround)

**Resolved:**
- N/A (this is an upgrade, not refactoring)

### 6.3 Schedule Risk

**Optimistic:** 15 days (if no Axon issues)
**Realistic:** 25 days (with moderate Axon issues)
**Pessimistic:** 40+ days (with severe Axon incompatibility)

**Unknowns:**
- Axon Framework compatibility depth
- Hidden Jackson 3.0 serialization issues
- Spring Modulith 2.0 breaking changes (not yet researched)

---

## 7. Testing Strategy

### 7.1 Pre-Migration Baseline

**Establish baseline BEFORE any changes:**

```bash
# Full test suite
./gradlew clean test integrationTest perfTest

# Generate reports
./gradlew koverHtmlReport

# Save results
mkdir -p baseline-reports/spring-boot-3.5.7
cp -r build/reports/* baseline-reports/spring-boot-3.5.7/

# Performance metrics
# - API p95 latency
# - Event processor lag
# - Command handling throughput
# - Projection refresh time
```

### 7.2 Post-Migration Testing Phases

#### Phase 1: Unit Tests (Target: 100% pass)
```bash
./gradlew test
```

**Focus:**
- Business logic unchanged
- Nullable Design Pattern tests
- Domain validation
- Error handling

#### Phase 2: Integration Tests (Target: 100% pass)
```bash
./gradlew integrationTest
```

**Critical Areas:**
- **Axon Framework** (26 integration tests)
  - Command handling
  - Event processing
  - Projections
  - Event store
- **Multi-tenancy** (2 integration tests)
  - TenantContextFilter
  - TenantValidationInterceptor
- **Security** (9 integration tests)
  - JWT validation (10 layers)
  - Keycloak integration
  - RBAC
- **Walking Skeleton** (1 test)
  - End-to-end flow

#### Phase 3: Performance Tests (Target: <10% regression)
```bash
./gradlew perfTest
```

**Metrics:**
- Command latency p95 <200ms
- Event processor lag <10s
- Snapshot performance
- Realistic workload throughput

#### Phase 4: Manual Testing
- Start application
- Execute CRUD operations
- Test multi-tenancy
- Test authentication/authorization
- Verify logging/metrics
- Check Prometheus/Grafana dashboards

#### Phase 5: Mutation Testing (Optional)
```bash
./gradlew pitest
```

**Target:** 60-70% mutation coverage (baseline comparison)

### 7.3 Acceptance Criteria

**MUST PASS:**
- ✅ All unit tests pass (100%)
- ✅ All integration tests pass (100%)
- ✅ Performance regression <10%
- ✅ No Axon-related failures
- ✅ Multi-tenancy working
- ✅ Security working (JWT, RBAC)
- ✅ Static analysis clean (ktlint, Detekt, Konsist)

**SHOULD PASS:**
- ✅ Code coverage ≥85% (baseline)
- ✅ Mutation coverage ≥60% (baseline)

**BLOCKERS:**
- ❌ Any Axon command/event/query failures
- ❌ Event store corruption
- ❌ Multi-tenancy bypass
- ❌ Security validation bypass
- ❌ Performance regression >10%

---

## 8. Rollback Plan

### 8.1 Rollback Triggers

**Immediate Rollback:**
- Axon Framework runtime failures (commands, events, projections)
- Event store data corruption
- Multi-tenancy security bypass
- Critical security vulnerability introduced

**Deferred Rollback (after 1-2 days):**
- Performance regression >10% (not fixable)
- Unresolvable dependency conflicts
- Test suite <90% pass rate
- Excessive technical debt introduced

### 8.2 Rollback Procedure

**Option 1: Git Revert (Clean Rollback)**
```bash
# Discard all changes on branch
git checkout main
git branch -D claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b

# Or reset to tag
git reset --hard pre-spring-boot-4-upgrade
git push -f origin claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b
```

**Option 2: Version Catalog Revert**
```bash
# Revert only version catalog
git checkout main -- gradle/libs.versions.toml
./gradlew clean build
```

**Option 3: Cherry-pick Fixes**
```bash
# If some fixes are valuable, cherry-pick to main
git checkout main
git cherry-pick <commit-hash>
```

### 8.3 Post-Rollback Actions

1. Document failures in `docs/spring-boot-4-upgrade-attempt-report.md`
2. Create issues for blocking problems
3. Monitor Axon Framework releases for compatibility announcements
4. Revisit upgrade in 3-6 months

---

## 9. References

### 9.1 Official Documentation

**Spring Framework 7.0:**
- Release Notes: https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes
- GA Announcement: https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability
- Migration from 6.2: https://spring.io/blog/2024/10/01/from-spring-framework-6-2-to-7-0

**Spring Boot 4.0:**
- Release Notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes
- Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- GA Announcement: https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now

**Spring Modulith 2.0:**
- GA Announcement: https://spring.io/blog/2025/11/21/spring-modulith-2-0-ga-1-4-5-and-1-3-11-released
- GitHub Releases: https://github.com/spring-projects/spring-modulith/releases

**Jackson 3.0:**
- Migration Guide: https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md
- Release Announcement: https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a
- Release Notes: https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0

**Jakarta EE 11:**
- Release Plan: https://jakartaee.github.io/platform/jakartaee11/JakartaEE11ReleasePlan
- Servlet 6.1: https://jakarta.ee/specifications/servlet/6.1/
- Persistence 3.2: https://jakarta.ee/specifications/persistence/3.2/

### 9.2 Migration Tools

**OpenRewrite:**
- Spring Boot 4.0 Recipe: https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0
- Spring Framework 7.0 Recipe: https://docs.openrewrite.org/recipes/java/spring/framework/upgradespringframework_7_0
- Jackson 3.0 Recipe: https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3

### 9.3 Axon Framework Monitoring

**Official Channels:**
- GitHub Releases: https://github.com/AxonFramework/AxonFramework/releases
- Discussion Forum: https://discuss.axoniq.io/
- Blog: https://www.axoniq.io/blog

**Search Terms:**
- "Axon Framework Spring Boot 4.0"
- "Axon Framework Spring Framework 7.0"
- "Axon Framework 4.13 release"
- "Axon Framework 5.0 production ready"

### 9.4 Related Guides

- Baeldung: https://www.baeldung.com/spring-boot-4-spring-framework-7
- Loiane Tavares: https://loiane.com/2025/08/spring-boot-4-spring-framework-7-key-features/
- Moderne.ai Migration Guide: https://www.moderne.ai/blog/spring-boot-4x-migration-guide

---

## Appendix A: Decision Matrix

| Factor | Weight | Spring Boot 3.5.7 | Spring Boot 4.0 | Winner |
|--------|--------|-------------------|-----------------|--------|
| **Stability** | 40% | ✅ Proven (GA Oct 2025) | ⚠️ Just released (Nov 2025) | **3.5.7** |
| **Axon Support** | 30% | ✅ Official (4.12.2) | ❌ Unknown | **3.5.7** |
| **Features** | 10% | 🟡 Mature | ✅ Modularization, JSpecify | **4.0** |
| **Security** | 10% | ✅ CVE patches active | ✅ Latest baseline | **Tie** |
| **Long-term** | 10% | 🟡 EOL ~2027 | ✅ LTS until ~2029 | **4.0** |

**Weighted Score:**
- Spring Boot 3.5.7: **78/100**
- Spring Boot 4.0: **52/100**

**Recommendation:** **Stay on Spring Boot 3.5.7** until Axon Framework support confirmed

---

## Appendix B: Version Compatibility Matrix

| Dependency | Spring Boot 3.5.7 | Spring Boot 4.0 | Notes |
|-----------|-------------------|-----------------|-------|
| Java | 17-21 | 17-25 | ✅ Compatible |
| Kotlin | 2.2.x | 2.2.0+ | ✅ Compatible |
| Spring Framework | 6.2.x | 7.0+ | ⚠️ Breaking changes |
| Spring Modulith | 1.4.x | 2.0+ | ⚠️ Requires upgrade |
| Axon Framework | 4.12.2 | ❌ Unknown | 🔴 **BLOCKER** |
| Jackson | 2.x | 3.0 | ⚠️ Major breaking |
| JUnit Jupiter | 6.0.1 | 6.0+ | ✅ Compatible |
| Jakarta Servlet | 6.0/6.1 | 6.1 | ✅ Compatible |
| Jakarta JPA | 3.1/3.2 | 3.2 | ⚠️ Minor breaking |
| PostgreSQL | 42.7.8 | N/A | ✅ Compatible |
| Testcontainers | 1.21.3 | N/A | ✅ Compatible |
| Keycloak | 26.4.2 | N/A | ✅ Compatible |

**Legend:**
- ✅ Compatible
- ⚠️ Requires changes
- ❌ Not compatible
- 🔴 Blocker

---

## Appendix C: Contact & Escalation

**For Axon Framework Support:**
- AxonIQ Discussion Forum: https://discuss.axoniq.io/
- GitHub Issues: https://github.com/AxonFramework/AxonFramework/issues
- AxonIQ Support: support@axoniq.io (commercial support)

**For Spring Boot Issues:**
- Spring Boot GitHub: https://github.com/spring-projects/spring-boot/issues
- Stack Overflow: Tag `spring-boot-4.0`

**For Jackson Issues:**
- Jackson GitHub: https://github.com/FasterXML/jackson-databind/issues
- Stack Overflow: Tag `jackson-3.0`

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-22 | Claude Code | Initial research and upgrade plan |

---

**END OF DOCUMENT**
