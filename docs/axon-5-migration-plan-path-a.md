# Axon Framework 5 Migration Plan - Path A (Conservative)

**Document Version:** 1.0
**Created:** 2025-11-17
**Status:** READY FOR EXECUTION (Pending Axon 5.0 GA)
**Migration Path:** Conservative - Blocking I/O with Future Async Readiness

---

## Executive Summary

This document provides a comprehensive, step-by-step migration plan for upgrading **EAF v1.0** from **Axon Framework 4.12.1** to **Axon Framework 5.0.x** while maintaining the current **blocking I/O architecture**.

### Migration Approach: Path A (Conservative)

**Strategy:**
- ✅ **Minimal code changes** - Keep annotation-based aggregates
- ✅ **Maintain blocking I/O** - Preserve current Spring MVC architecture
- ✅ **Low risk, fast execution** - 6-8 weeks total
- ✅ **Future-ready** - Positioned for async adoption (Path B) later

**What Changes:**
- Configuration API (AxonConfiguration.kt)
- Internal processing (ThreadLocal → ProcessingContext, invisible)
- Event processor defaults (Tracking → PooledStreaming)

**What Stays the Same:**
- All aggregate code (@Aggregate, @CommandHandler, @EventSourcingHandler)
- Blocking command/query patterns (sendAndWait)
- Spring MVC REST controllers
- Multi-tenancy enforcement (3 layers)
- Test infrastructure (Kotest + Testcontainers)

**Future Path B Readiness:**
- ✅ Axon 5 async-native architecture (ProcessingContext)
- ✅ Optional immutable entities (can adopt gradually)
- ✅ Configuration API supports reactive
- ✅ No architectural barriers to async adoption

---

## Table of Contents

1. [Prerequisites and Timing](#1-prerequisites-and-timing)
2. [Pre-Migration Phase (4 weeks)](#2-pre-migration-phase-4-weeks)
3. [Migration Phases Overview](#3-migration-phases-overview)
4. [Phase 1: Environment Setup (1 week)](#4-phase-1-environment-setup-1-week)
5. [Phase 2: Dependency Updates (1 week)](#5-phase-2-dependency-updates-1-week)
6. [Phase 3: Configuration Migration (2 weeks)](#6-phase-3-configuration-migration-2-weeks)
7. [Phase 4: Code Updates (1 week)](#7-phase-4-code-updates-1-week)
8. [Phase 5: Testing & Validation (2-3 weeks)](#8-phase-5-testing--validation-2-3-weeks)
9. [Phase 6: Deployment (1 week)](#9-phase-6-deployment-1-week)
10. [Rollback Procedures](#10-rollback-procedures)
11. [Success Criteria](#11-success-criteria)
12. [Path B Preparation](#12-path-b-preparation-future-async-adoption)
13. [Appendices](#13-appendices)

---

## 1. Prerequisites and Timing

### 1.1 Migration Prerequisites

**MUST be complete before starting:**

✅ **Axon Framework 5.0 GA Released**
- Status: RC3 released Nov 6, 2025
- Expected: Late December 2025
- Monitor: https://github.com/AxonFramework/AxonFramework/releases

✅ **OpenRewrite Migration Recipes Available**
- Automated refactoring for breaking changes
- Expected: At GA or shortly after
- Check: https://github.com/AxonFramework/AxonFramework/tree/main/migration

✅ **Team Training Complete**
- All developers trained on Axon 5 features
- Migration champions identified
- Rollback procedures understood

✅ **Codebase Audit Complete**
- All Axon usage documented
- Breaking changes identified
- Migration checklist prepared

✅ **Test Environment Ready**
- Isolated Axon 5 environment configured
- Testcontainers validated
- Performance baselines established

---

### 1.2 Timeline and Scheduling

**Recommended Start Date:** **January 2026**
- Assumes Axon 5.0 GA in late December 2025
- Allows for OpenRewrite recipes availability
- Avoids holiday period

**Total Duration:** **6-8 weeks** (conservative estimate)

**Recommended Schedule:**
```
Week 1:     Environment Setup & Dependency Updates
Week 2:     Configuration Migration (Part 1)
Week 3:     Configuration Migration (Part 2)
Week 4:     Code Updates & OpenRewrite
Week 5:     Unit & Integration Testing
Week 6:     E2E Testing & Performance Validation
Week 7:     Staging Deployment & Smoke Testing
Week 8:     Production Deployment & Monitoring
```

**Avoid These Periods:**
- ❌ Holiday periods (Dec 20 - Jan 5)
- ❌ Major release cycles
- ❌ Critical business periods

---

### 1.3 Team Allocation

**Required Roles:**

| Role | Allocation | Responsibilities |
|------|-----------|------------------|
| **Migration Lead** | 100% (8 weeks) | Overall coordination, decision-making |
| **Senior Developer** | 100% (8 weeks) | Configuration migration, code changes |
| **QA Engineer** | 75% (weeks 5-8) | Testing strategy, validation |
| **DevOps Engineer** | 50% (weeks 1, 7-8) | Environment setup, deployment |
| **Architect** | 25% (8 weeks) | Technical oversight, reviews |

**Total Effort:** ~30 person-weeks

---

### 1.4 Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Event store incompatibility** | Low | Critical | Test replay in isolated environment first |
| **Configuration API breaking changes** | Medium | High | Use OpenRewrite recipes, thorough testing |
| **Multi-tenancy context propagation** | Low | Critical | Dedicated test suite for 3-layer enforcement |
| **Performance regression** | Low | High | Baseline metrics, load testing before production |
| **Axon 5 bugs (new release)** | Medium | Medium | Wait 2-4 weeks post-GA, monitor issues |

---

## 2. Pre-Migration Phase (4 weeks)

**When:** November-December 2025 (before Axon 5.0 GA)
**Status:** Can start immediately
**Purpose:** Reduce migration duration by completing preparatory work

---

### 2.1 Team Training (Week 1-2)

#### 2.1.1 Self-Study Materials

**Required Reading (All Team Members):**
- [ ] [Axon Framework 5 Getting Started Guide](https://docs.axoniq.io/axon-framework-5-getting-started/)
- [ ] [Axon 5 Configuration API Announcement](https://www.axoniq.io/blog/announcing-axon-framework-5-configuring-axon-made-easy)
- [ ] `docs/axon-framework-5-research.md` (this repository)
- [ ] [Dynamic Consistency Boundary Overview](https://www.axoniq.io/blog/dcb-in-af-5) (awareness only)

**Time Commitment:** 8 hours per developer

---

#### 2.1.2 Hands-On Workshop (4 hours)

**Objective:** Build sample Axon 5 application

**Workshop Steps:**

**Step 1: Setup (30 min)**
```bash
# Create workshop project
mkdir axon5-workshop && cd axon5-workshop

# Initialize Gradle project
gradle init --type kotlin-application --dsl kotlin

# Add Axon 5 RC3 dependency (for practice)
# Note: Use GA version when available
```

**Step 2: Build Simple Aggregate (1 hour)**
```kotlin
// Annotation-based (backward compatible)
@Aggregate
class BankAccount {
    @AggregateIdentifier
    private lateinit var accountId: String
    private var balance: BigDecimal = BigDecimal.ZERO

    constructor() // Required for Axon

    @CommandHandler
    constructor(command: CreateAccountCommand) {
        apply(AccountCreatedEvent(command.accountId, BigDecimal.ZERO))
    }

    @CommandHandler
    fun handle(command: DepositMoneyCommand) {
        apply(MoneyDepositedEvent(accountId, command.amount))
    }

    @EventSourcingHandler
    fun on(event: AccountCreatedEvent) {
        this.accountId = event.accountId
        this.balance = event.initialBalance
    }

    @EventSourcingHandler
    fun on(event: MoneyDepositedEvent) {
        this.balance = this.balance.add(event.amount)
    }
}
```

**Step 3: Configure Axon 5 (1 hour)**
```kotlin
// New Configuration API (annotation-based auto-config)
@SpringBootApplication
class BankApplication

fun main(args: Array<String>) {
    runApplication<BankApplication>(*args)
}

// application.yml
axon:
  axonserver:
    enabled: false  # Use in-memory for workshop
  eventhandling:
    processors:
      default:
        mode: pooled-streaming  # Axon 5 default
```

**Step 4: Test with Axon 5 (1.5 hours)**
```kotlin
// Test Fixture (Axon 5 enhanced)
class BankAccountTest : FunSpec({
    val fixture = AggregateTestFixture(BankAccount::class.java)

    test("should create account with zero balance") {
        fixture.givenNoPriorActivity()
            .`when`(CreateAccountCommand("ACC-001"))
            .expectEvents(AccountCreatedEvent("ACC-001", BigDecimal.ZERO))
    }

    test("should deposit money") {
        fixture.given(AccountCreatedEvent("ACC-001", BigDecimal.ZERO))
            .`when`(DepositMoneyCommand("ACC-001", BigDecimal("100.00")))
            .expectEvents(MoneyDepositedEvent("ACC-001", BigDecimal("100.00")))
    }
})
```

**Step 5: Discuss Differences (30 min)**
- Configuration API changes
- PooledStreamingEventProcessor vs TrackingEventProcessor
- ProcessingContext vs UnitOfWork (internal)
- Optional: Explore immutable entities (Path B preview)

**Deliverable:** Each developer has working Axon 5 application

---

#### 2.1.3 Migration Champions (Selected Developers)

**Additional Training for Champions:**
- [ ] Deep dive: OpenRewrite migration recipes
- [ ] Study: AxonConfiguration.kt current implementation
- [ ] Review: EAF multi-tenancy patterns (TenantContext propagation)
- [ ] Practice: Rollback procedures

**Time Commitment:** +8 hours

---

### 2.2 Codebase Audit (Week 2-3)

#### 2.2.1 Axon Usage Inventory

**Objective:** Document all Axon Framework usage in EAF

**Task 1: Aggregate Inventory**

```bash
# Find all aggregates
grep -r "@Aggregate" --include="*.kt" . > audit-aggregates.txt

# Expected location: products/widget-demo/src/main/kotlin/.../domain/
```

**Document for each aggregate:**
- [ ] Class name and location
- [ ] Number of command handlers
- [ ] Number of event sourcing handlers
- [ ] Uses entities? (@AggregateMember)
- [ ] Custom configuration?

**Example Template:**
```markdown
### WidgetAggregate
- **Location:** `products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/domain/WidgetAggregate.kt`
- **Command Handlers:** 5 (Create, Update, Activate, Deactivate, Delete)
- **Event Handlers:** 5 (corresponding events)
- **Entities:** None
- **Custom Config:** None
- **Migration Risk:** 🟢 LOW - Standard annotation-based aggregate
```

---

**Task 2: Command Handler Inventory**

```bash
# Find all command handlers
grep -r "@CommandHandler" --include="*.kt" . > audit-commands.txt
```

**Document:**
- Total count of command handlers
- Any non-standard patterns
- Custom interceptors (if any)

---

**Task 3: Event Handler Inventory**

```bash
# Event sourcing handlers
grep -r "@EventSourcingHandler" --include="*.kt" . > audit-event-sourcing.txt

# Regular event handlers (projections)
grep -r "@EventHandler" --include="*.kt" . > audit-event-handlers.txt
```

**Document:**
- Event sourcing handlers (aggregate state)
- Event handlers (projections, side effects)
- Async event processors configuration

---

**Task 4: Query Handler Inventory**

```bash
# Find all query handlers
grep -r "@QueryHandler" --include="*.kt" . > audit-queries.txt
```

---

**Task 5: Configuration Inventory**

**Review:**
- [ ] `framework/cqrs/src/main/kotlin/.../AxonConfiguration.kt`
- [ ] `products/widget-demo/src/main/resources/application.yml` (axon section)
- [ ] Any custom Configurer beans

**Document:**
- Current Configurer API usage
- Bean definitions (CommandGateway, QueryGateway, etc.)
- Event processor configuration
- Serializer configuration (Jackson)
- Token store configuration

---

#### 2.2.2 Breaking Change Detection

**Search for Axon 4.x patterns that break in Axon 5:**

**Pattern 1: UnitOfWork Usage**
```bash
grep -r "UnitOfWork" --include="*.kt" .
```

**Expected:** Should be ZERO results (EAF doesn't use UnitOfWork directly)

**If found:** Document for migration (ProcessingContext)

---

**Pattern 2: TrackingEventProcessor**
```bash
grep -r "TrackingEventProcessor" --include="*.kt" .
grep -r "tracking" products/widget-demo/src/main/resources/application.yml
```

**Expected:** May be in application.yml configuration

**Action:** Document for migration to PooledStreamingEventProcessor

---

**Pattern 3: Explicit Serializer Configuration**
```bash
grep -r "Serializer" --include="*.kt" framework/persistence/
```

**Expected:** Jackson serializer in PostgresEventStoreConfiguration

**Action:** Verify compatibility with Axon 5 Converter API

---

**Pattern 4: Custom Interceptors**
```bash
grep -r "Interceptor" --include="*.kt" framework/
```

**Expected:** TenantPropagationInterceptor (multi-tenancy)

**Action:** Verify signature compatibility with Axon 5

---

#### 2.2.3 Dependency Audit

**Review `gradle/libs.versions.toml`:**

```bash
# Extract Axon-related dependencies
grep -i "axon" gradle/libs.versions.toml
```

**Current Dependencies (Expected):**
```toml
[versions]
axon-framework = "4.12.1"

[libraries]
axon-spring-boot-starter = { module = "org.axonframework:axon-spring-boot-starter", version.ref = "axon-framework" }
# ... other Axon modules
```

**Document:**
- All Axon modules in use
- Axon extensions (if any)
- Version management strategy

---

**Deliverable:** `docs/migration-audit-report.md` with complete inventory

---

### 2.3 Test Environment Setup (Week 3)

#### 2.3.1 Create Isolated Axon 5 Branch

```bash
# Create migration branch
git checkout -b axon5-migration-path-a

# DO NOT merge to main until migration complete
```

---

#### 2.3.2 Set Up Test Infrastructure

**Docker Compose for Testing:**

Create `docker-compose.axon5-test.yml`:

```yaml
version: '3.8'

services:
  # PostgreSQL for event store testing
  postgres-axon5:
    image: postgres:16.10-alpine
    container_name: eaf-postgres-axon5-test
    environment:
      POSTGRES_DB: eaf_axon5_test
      POSTGRES_USER: eaf_test
      POSTGRES_PASSWORD: test_password
    ports:
      - "5433:5432"  # Different port to avoid conflicts
    volumes:
      - postgres-axon5-data:/var/lib/postgresql/data

  # Keycloak for JWT testing
  keycloak-axon5:
    image: quay.io/keycloak/keycloak:26.4.2
    container_name: eaf-keycloak-axon5-test
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
    ports:
      - "8081:8080"  # Different port

  # Redis for token revocation
  redis-axon5:
    image: redis:7-alpine
    container_name: eaf-redis-axon5-test
    ports:
      - "6380:6379"  # Different port

volumes:
  postgres-axon5-data:
```

**Start test environment:**
```bash
docker-compose -f docker-compose.axon5-test.yml up -d
```

---

#### 2.3.3 Axon 5 RC3 Validation (Practice Run)

**Objective:** Test migration process with RC3 before GA

**Note:** This is for learning only. Will repeat with GA version.

**Step 1: Update single module to Axon 5 RC3**

```bash
# In axon5-migration-path-a branch
# Update ONLY framework/cqrs for testing
```

**Step 2: Run tests**
```bash
./gradlew :framework:cqrs:test
```

**Step 3: Document issues**
- Compilation errors
- Test failures
- Unexpected behavior

**Step 4: Revert**
```bash
git reset --hard origin/main
```

**Deliverable:** Migration issues log (what to expect)

---

### 2.4 Baseline Metrics Collection (Week 3-4)

#### 2.4.1 Performance Baselines

**Collect Current Metrics (Axon 4.12.1):**

**1. Command Latency**
```bash
# Run load test
./scripts/load-test-commands.sh

# Measure:
# - p50 latency
# - p95 latency (target: <200ms)
# - p99 latency
```

**Document:**
```markdown
### Command Latency Baseline (Axon 4.12.1)
- CreateWidgetCommand: p95 = 145ms
- UpdateWidgetCommand: p95 = 132ms
- ActivateWidgetCommand: p95 = 98ms
```

---

**2. Event Processor Lag**
```bash
# Generate 1000 events
# Measure time until projections updated
```

**Document:**
```markdown
### Event Processor Lag Baseline (Axon 4.12.1)
- 1000 events processed in: 8.5 seconds
- Average lag per event: 8.5ms
```

---

**3. Test Suite Execution Time**
```bash
# Unit tests
time ./gradlew test

# Integration tests
time ./gradlew integrationTest

# Full suite
time ./gradlew clean build
```

**Document:**
```markdown
### Test Execution Baseline (Axon 4.12.1)
- Unit tests: 45 seconds
- Integration tests: 3 minutes 20 seconds
- Full build: 8 minutes 15 seconds
```

---

#### 2.4.2 Event Store Analysis

**1. Event Count**
```sql
-- Connect to PostgreSQL
psql -h localhost -U eaf_user -d eaf_db

-- Count events
SELECT
    aggregate_identifier,
    COUNT(*) as event_count
FROM domain_event_entry
GROUP BY aggregate_identifier
ORDER BY event_count DESC
LIMIT 10;
```

**Document:** Largest event streams (important for snapshot testing)

---

**2. Event Schema**
```sql
-- Sample events to understand structure
SELECT
    event_identifier,
    payload_type,
    payload
FROM domain_event_entry
LIMIT 5;
```

**Document:** Event payload structure (for replay testing)

---

**3. Snapshot Usage**
```sql
-- Check snapshot configuration
SELECT COUNT(*) FROM snapshot_event_entry;
```

**Document:** Current snapshot strategy

---

**Deliverable:** `docs/migration-baseline-metrics.md`

---

### 2.5 Migration Checklist Preparation (Week 4)

**Create:** `docs/axon5-migration-checklist.md`

**Sections:**
- [ ] Pre-migration prerequisites ✅
- [ ] Phase 1: Environment Setup
- [ ] Phase 2: Dependency Updates
- [ ] Phase 3: Configuration Migration
- [ ] Phase 4: Code Updates
- [ ] Phase 5: Testing & Validation
- [ ] Phase 6: Deployment
- [ ] Post-migration validation
- [ ] Rollback procedures tested

**Each item should have:**
- Description
- Responsible person
- Acceptance criteria
- Dependencies
- Estimated time
- Risk level

---

## 3. Migration Phases Overview

**Execution Timeline:** 6-8 weeks (after Axon 5.0 GA)

```
┌─────────────────────────────────────────────────────────────┐
│                  Axon 5 Migration Path A                    │
│              (Conservative - Blocking I/O)                  │
└─────────────────────────────────────────────────────────────┘

Week 1: Environment Setup (1 week)
├── Set up migration branch
├── Configure Gradle version catalogs
├── Set up Axon 5 test environment
└── Validate Testcontainers compatibility

Week 2: Dependency Updates (1 week)
├── Update Axon Framework → 5.0.x GA
├── Update Spring Boot BOM compatibility
├── Resolve dependency conflicts
└── Verify compilation

Week 3-4: Configuration Migration (2 weeks)
├── Run OpenRewrite migration recipes
├── Update AxonConfiguration.kt
├── Update application.yml (event processors)
├── Update serializer configuration
└── Verify bean wiring

Week 5: Code Updates (1 week)
├── Update TenantPropagationInterceptor
├── Fix compilation errors
├── Update import statements
└── Code review

Week 6-7: Testing & Validation (2-3 weeks)
├── Unit tests (all pass)
├── Integration tests (CQRS flows)
├── Multi-tenancy validation (3 layers)
├── Event replay testing
├── Performance benchmarking
└── Load testing

Week 8: Deployment (1 week)
├── Deploy to staging
├── Smoke tests
├── Production deployment (blue-green)
└── Monitoring (48 hours)
```

---

## 4. Phase 1: Environment Setup (1 week)

### 4.1 Git Branch Strategy

**Create Migration Branch:**

```bash
# Ensure main is up to date
git checkout main
git pull origin main

# Create migration branch
git checkout -b axon5-migration-path-a

# Push to remote
git push -u origin axon5-migration-path-a
```

**Branch Protection:**
- [ ] Enable branch protection on GitHub
- [ ] Require pull request reviews
- [ ] Require status checks to pass
- [ ] Do not allow force pushes

---

### 4.2 Gradle Configuration

#### 4.2.1 Version Catalog Preparation

**Update `gradle/libs.versions.toml`:**

```toml
[versions]
# Axon Framework 5.0
axon-framework = "5.0.x"  # Replace x with actual GA version

# Ensure compatible Spring Boot (likely no change needed)
spring-boot = "3.5.7"  # Or latest 3.x

# Other dependencies remain unchanged
kotlin = "2.2.21"
spring-modulith = "1.4.4"
# ...
```

**Commit this change:**
```bash
git add gradle/libs.versions.toml
git commit -m "build: prepare version catalog for Axon Framework 5.0 migration"
```

---

#### 4.2.2 Gradle Wrapper Verification

**Ensure Gradle 9.1.0:**
```bash
./gradlew --version

# Expected:
# Gradle 9.1.0
```

**If update needed:**
```bash
gradle wrapper --gradle-version 9.1.0
```

---

### 4.3 Test Environment Configuration

#### 4.3.1 Docker Compose Setup

**Update `docker-compose.yml` for testing:**

Add test profile configuration:

```yaml
# Add to docker-compose.yml
services:
  postgres:
    # ... existing config
    environment:
      # Add test database
      POSTGRES_DB_TEST: eaf_axon5_test
```

**Start services:**
```bash
docker-compose up -d
```

---

#### 4.3.2 Application Configuration (Test Profile)

**Create:** `products/widget-demo/src/main/resources/application-axon5-test.yml`

```yaml
# Axon 5 specific test configuration
spring:
  application:
    name: eaf-widget-demo-axon5-test

  datasource:
    url: jdbc:postgresql://localhost:5432/eaf_axon5_test
    username: eaf_test
    password: test_password

axon:
  axonserver:
    enabled: false  # Using JDBC event store

  eventhandling:
    processors:
      # Axon 5 default: PooledStreamingEventProcessor
      default:
        mode: pooled-streaming
        pool-size: 2
        max-segment-size: 100

  serializer:
    general: jackson
    events: jackson
    messages: jackson

# Logging for migration debugging
logging:
  level:
    org.axonframework: DEBUG
    com.axians.eaf: DEBUG
```

---

### 4.4 Baseline Test Execution

**Verify current tests pass (Axon 4.12.1):**

```bash
# Full test suite
./gradlew clean build

# Expected: BUILD SUCCESSFUL ✅
```

**If failures:**
- Fix before proceeding
- Baseline must be green

---

### 4.5 Deliverables - Phase 1

- [x] Migration branch created and protected
- [x] Version catalog prepared (not yet updated)
- [x] Test environment configured
- [x] Baseline tests passing
- [x] Team briefed on migration start

**Duration:** 5 working days
**Effort:** 1.0 FTE (Migration Lead + DevOps)

---

## 5. Phase 2: Dependency Updates (1 week)

### 5.1 Update Version Catalog

**File:** `gradle/libs.versions.toml`

```toml
[versions]
# BEFORE
axon-framework = "4.12.1"

# AFTER
axon-framework = "5.0.1"  # Use actual GA version
```

**Commit:**
```bash
git add gradle/libs.versions.toml
git commit -m "build: upgrade Axon Framework to 5.0.1"
```

---

### 5.2 Resolve Dependency Conflicts

#### 5.2.1 Check Dependency Tree

```bash
# Full dependency tree
./gradlew :products:widget-demo:dependencies > deps-axon5.txt

# Check for conflicts
./gradlew :products:widget-demo:dependencyInsight --dependency axon-spring-boot-starter
```

**Review for:**
- Axon version conflicts
- Spring Boot BOM conflicts
- Transitive dependency issues

---

#### 5.2.2 Expected Changes

**Axon Modules (Auto-Updated):**
```
org.axonframework:axon-spring-boot-starter:4.12.1 → 5.0.1
org.axonframework:axon-messaging:4.12.1 → 5.0.1
org.axonframework:axon-eventsourcing:4.12.1 → 5.0.1
org.axonframework:axon-modelling:4.12.1 → 5.0.1
org.axonframework:axon-spring:4.12.1 → 5.0.1
```

**New Modules (Potentially):**
- Axon 5 may split modules differently
- Check release notes for module restructuring

---

#### 5.2.3 Fix Conflicts (If Any)

**Example Conflict Resolution:**

```kotlin
// If BOM conflict occurs
dependencies {
    // Explicitly declare Axon BOM
    implementation(platform("org.axonframework:axon-bom:5.0.1"))

    // Then use modules without versions
    implementation("org.axonframework:axon-spring-boot-starter")
    implementation("org.axonframework:axon-messaging")
}
```

---

### 5.3 Compilation Verification

**Attempt compilation:**
```bash
./gradlew clean compileKotlin

# Expected: Compilation errors related to API changes
```

**DO NOT FIX YET** - Document errors for next phase.

**Create:** `docs/migration-compilation-errors.md`

Example:
```markdown
### Compilation Errors (Axon 5.0.1)

1. **AxonConfiguration.kt:45**
   - Error: `Unresolved reference: Configurer`
   - Cause: Configuration API changed
   - Fix: Phase 3 (Configuration Migration)

2. **TenantPropagationInterceptor.kt:23**
   - Error: `Type mismatch: UnitOfWork required, ProcessingContext found`
   - Cause: UnitOfWork → ProcessingContext
   - Fix: Phase 4 (Code Updates)
```

---

### 5.4 Deliverables - Phase 2

- [x] Axon Framework updated to 5.0.x
- [x] Dependency tree verified
- [x] Conflicts resolved (if any)
- [x] Compilation errors documented
- [x] Not yet compiling (expected)

**Duration:** 5 working days
**Effort:** 1.0 FTE (Senior Developer)

---

## 6. Phase 3: Configuration Migration (2 weeks)

**Most critical phase** - Configuration API significantly changed in Axon 5

### 6.1 OpenRewrite Migration (Week 1)

#### 6.1.1 Install OpenRewrite Gradle Plugin

**Update:** `build.gradle.kts` (root)

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "6.25.0"  // Latest version
}

repositories {
    mavenCentral()
}

rewrite {
    activeRecipe("org.axonframework.migration.UpgradeAxonFramework_5")
}
```

---

#### 6.1.2 Run OpenRewrite Recipes

```bash
# Dry run (preview changes)
./gradlew rewriteDryRun

# Review changes in build/reports/rewrite/

# Apply changes
./gradlew rewriteRun
```

**OpenRewrite will handle:**
- Package name changes (if any)
- Import statement updates
- Deprecated API replacements
- Configuration API updates (partial)

---

#### 6.1.3 Review OpenRewrite Changes

```bash
# Show changes
git diff

# Review each file carefully
# Commit in logical chunks
git add <file>
git commit -m "refactor(axon5): apply OpenRewrite migration for <area>"
```

---

### 6.2 AxonConfiguration.kt Migration (Week 1)

**Current File:** `framework/cqrs/src/main/kotlin/.../AxonConfiguration.kt`

#### 6.2.1 Current Configuration (Axon 4.12.1)

```kotlin
@Configuration
class AxonConfiguration {

    @Bean
    fun commandGateway(configurer: Configurer): CommandGateway {
        return configurer.commandGateway()
    }

    @Bean
    fun queryGateway(configurer: Configurer): QueryGateway {
        return configurer.queryGateway()
    }

    @Bean
    fun snapshotter(
        eventStore: EventStore,
        transactionManager: TransactionManager
    ): Snapshotter {
        return AggregateSnapshotter.builder()
            .eventStore(eventStore)
            .transactionManager(transactionManager)
            .build()
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}
```

---

#### 6.2.2 Migrated Configuration (Axon 5.0)

**Key Changes:**
1. Configurer API simplified
2. More explicit bean registration
3. Different method names (potentially)

**Updated Configuration:**

```kotlin
@Configuration
class AxonConfiguration {

    // CommandGateway - likely auto-configured in Axon 5
    // Remove if auto-configuration works
    @Bean
    @ConditionalOnMissingBean
    fun commandGateway(configurer: org.axonframework.config.Configuration): CommandGateway {
        return configurer.commandGateway()
    }

    // QueryGateway - likely auto-configured
    @Bean
    @ConditionalOnMissingBean
    fun queryGateway(configurer: org.axonframework.config.Configuration): QueryGateway {
        return configurer.queryGateway()
    }

    // Snapshotter - verify API compatibility
    @Bean
    fun snapshotter(
        eventStore: EventStore,
        transactionManager: TransactionManager
    ): Snapshotter {
        return AggregateSnapshotter.builder()
            .eventStore(eventStore)
            .transactionManager(transactionManager)
            .build()
    }

    // Aggregate cache - should remain the same
    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}
```

**Note:** Actual API may differ - consult Axon 5 documentation.

---

#### 6.2.3 Test Configuration Wiring

```bash
# Verify beans are wired correctly
./gradlew :framework:cqrs:test --tests "*AxonConfigurationTest"

# Expected: Tests pass ✅
```

**If beans missing:**
- Check Spring Boot auto-configuration
- Verify `@ConditionalOnMissingBean` logic
- Review Axon 5 starter configuration

---

### 6.3 Event Processor Configuration (Week 2)

#### 6.3.1 Current Configuration (Axon 4.x)

**File:** `products/widget-demo/src/main/resources/application.yml`

```yaml
axon:
  eventhandling:
    processors:
      # Axon 4.x default
      widget-projection:
        mode: tracking
        source: eventBus
        thread-count: 2
```

---

#### 6.3.2 Migrated Configuration (Axon 5.0)

**Key Change:** PooledStreamingEventProcessor is now default

```yaml
axon:
  eventhandling:
    processors:
      # Axon 5.0 default: pooled-streaming
      widget-projection:
        mode: pooled-streaming
        pool-size: 2
        max-segment-size: 100
        claim-extend-threshold: 5000
```

**Configuration Options:**

| Property | Description | Default | Recommended |
|----------|-------------|---------|-------------|
| `mode` | Processor type | `pooled-streaming` | `pooled-streaming` |
| `pool-size` | Number of threads | 16 | 2-4 (based on CPU) |
| `max-segment-size` | Max events per segment | 100 | 100 |
| `claim-extend-threshold` | Claim extension time | 5000ms | 5000ms |

**Why pooled-streaming?**
- Better performance than TrackingEventProcessor
- Parallel processing within segments
- Lower latency for event handling

---

#### 6.3.3 Test Event Processing

**Integration Test:**

```kotlin
@SpringBootTest
@ActiveProfiles("test", "axon5-test")
class EventProcessorMigrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var dsl: DSLContext

    init {
        extension(SpringExtension())

        test("should process events with PooledStreamingEventProcessor") {
            val widgetId = UUID.randomUUID()

            // Dispatch command
            commandGateway.sendAndWait<Unit>(
                CreateWidgetCommand(widgetId, "Test Widget", "tenant-a")
            )

            // Verify projection updated (eventually)
            eventually(Duration.ofSeconds(5)) {
                val projection = dsl.selectFrom(table("widget_projection"))
                    .where(field("id").eq(widgetId))
                    .fetchOne()

                projection.shouldNotBeNull()
                projection[field("name", String::class.java)] shouldBe "Test Widget"
            }
        }
    }
}
```

**Run test:**
```bash
./gradlew :products:widget-demo:integrationTest --tests "*EventProcessorMigrationTest"

# Expected: Test passes ✅
# Event processed within 5 seconds
```

---

### 6.4 Serializer Configuration (Week 2)

#### 6.4.1 Current Serializer (Axon 4.x)

**File:** `framework/persistence/.../PostgresEventStoreConfiguration.kt`

```kotlin
@Bean
@Primary
fun jacksonSerializer(): Serializer {
    return JacksonSerializer.builder()
        .objectMapper(
            ObjectMapper().apply {
                registerModule(JavaTimeModule())
                registerModule(KotlinModule.Builder().build())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        )
        .build()
}
```

---

#### 6.4.2 Migrated Serializer (Axon 5.0)

**Note:** Axon 5 introduces **Converters** alongside Serializers

**Check if API changed:**
```kotlin
// Axon 5 may still support JacksonSerializer
// OR may require new Converter API

@Bean
@Primary
fun jacksonSerializer(): Serializer {
    // Verify this API still works in Axon 5
    return JacksonSerializer.builder()
        .objectMapper(
            ObjectMapper().apply {
                registerModule(JavaTimeModule())
                registerModule(KotlinModule.Builder().build())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        )
        .build()
}
```

**If Converter API required:**
```kotlin
// NEW Axon 5 Converter API (if applicable)
@Bean
fun jacksonConverter(): Converter {
    // Consult Axon 5 docs for exact API
    // May need to implement Converter interface
}
```

---

#### 6.4.3 Test Serialization

**Event Replay Test:**

```kotlin
test("should deserialize Axon 4 events with Axon 5 serializer") {
    // Load events from Axon 4 event store
    val events = eventStore.readEvents("widget-123").asStream()
        .collect(Collectors.toList())

    // Should deserialize without errors
    events.shouldNotBeEmpty()
    events.forEach { event ->
        event.payload shouldBeInstanceOf DomainEvent::class
    }
}
```

---

### 6.5 Multi-Tenancy Context Propagation (Week 2)

#### 6.5.1 Current Implementation (Axon 4.x)

**File:** `framework/security/.../TenantPropagationInterceptor.kt`

```kotlin
@Component
class TenantPropagationInterceptor : MessageHandlerInterceptor<EventMessage<*>> {

    override fun handle(
        unitOfWork: UnitOfWork<out EventMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any {
        // Extract tenant from event metadata
        val tenantId = unitOfWork.message.metaData["tenantId"] as? String

        if (tenantId != null) {
            TenantContext.setCurrentTenantId(tenantId)
        }

        return try {
            interceptorChain.proceed()
        } finally {
            TenantContext.clearCurrentTenant()
        }
    }
}
```

---

#### 6.5.2 Migrated Implementation (Axon 5.0)

**Key Change:** UnitOfWork → ProcessingContext

**Note:** Exact API to be confirmed from Axon 5 docs

**Expected Migration:**

```kotlin
@Component
class TenantPropagationInterceptor : MessageHandlerInterceptor<EventMessage<*>> {

    override fun handle(
        message: EventMessage<*>,
        context: ProcessingContext,  // Changed from UnitOfWork
        interceptorChain: InterceptorChain
    ): Any {
        // Extract tenant from event metadata
        val tenantId = message.metaData["tenantId"] as? String

        if (tenantId != null) {
            TenantContext.setCurrentTenantId(tenantId)
        }

        return try {
            interceptorChain.proceed()
        } finally {
            TenantContext.clearCurrentTenant()
        }
    }
}
```

**Alternative (if API different):**
- Consult Axon 5 migration guide
- Check ProcessingContext API reference
- May use different interceptor interface

---

#### 6.5.3 Test Multi-Tenancy

**Critical Test:**

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class MultiTenancyMigrationTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    init {
        extension(SpringExtension())

        test("should propagate tenant context to async event processor") {
            // Set tenant context
            TenantContext.setCurrentTenantId("tenant-a")

            try {
                // Dispatch command
                val widgetId = UUID.randomUUID()
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetId, "Test", "tenant-a")
                )

                // Event handler should have tenant context
                eventually(Duration.ofSeconds(5)) {
                    // Verify projection created with correct tenant
                    val projection = queryTenantAwareProjection(widgetId)
                    projection.tenantId shouldBe "tenant-a"
                }
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }

        test("should enforce tenant isolation in command handler") {
            TenantContext.setCurrentTenantId("tenant-a")

            try {
                // Attempt to create widget for different tenant
                val exception = shouldThrow<IllegalArgumentException> {
                    commandGateway.sendAndWait<Unit>(
                        CreateWidgetCommand(UUID.randomUUID(), "Test", "tenant-b")
                    )
                }

                exception.message shouldContain "Access denied"
            } finally {
                TenantContext.clearCurrentTenant()
            }
        }
    }
}
```

**Run:**
```bash
./gradlew :products:widget-demo:integrationTest --tests "*MultiTenancyMigrationTest"

# Expected: Both tests pass ✅
```

---

### 6.6 Deliverables - Phase 3

- [x] OpenRewrite migrations applied
- [x] AxonConfiguration.kt updated
- [x] Event processor configuration migrated
- [x] Serializer compatibility verified
- [x] Multi-tenancy context propagation working
- [x] All configuration tests passing

**Duration:** 10 working days
**Effort:** 1.5 FTE (Senior Developer + Migration Lead)

---

## 7. Phase 4: Code Updates (1 week)

### 7.1 Import Statement Updates

**Run automated import fix:**

```bash
# IntelliJ IDEA
# Code → Optimize Imports (Ctrl+Alt+O on all files)

# Or use ktlint
./gradlew ktlintFormat
```

**Common Import Changes:**

```kotlin
// BEFORE (Axon 4.x)
import org.axonframework.config.Configurer

// AFTER (Axon 5.0)
import org.axonframework.config.Configuration
```

---

### 7.2 Aggregate Code Verification

**Good News:** Annotation-based aggregates should work unchanged!

**Verify each aggregate compiles:**

```bash
./gradlew :products:widget-demo:compileKotlin

# Expected: SUCCESS ✅
```

**If compilation errors in aggregates:**
- Check for removed annotations
- Verify @AggregateIdentifier still works
- Consult Axon 5 migration guide

**Example Aggregate (Should Work As-Is):**

```kotlin
@Aggregate
class WidgetAggregate {
    @AggregateIdentifier
    private lateinit var widgetId: UUID
    private lateinit var name: String
    private lateinit var tenantId: String
    private var status: WidgetStatus = WidgetStatus.DRAFT

    constructor() // Required for Axon

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        // Tenant validation (Story 4.2)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        apply(WidgetCreatedEvent(
            widgetId = command.widgetId,
            name = command.name,
            tenantId = command.tenantId
        ))
    }

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.tenantId = event.tenantId
        this.status = WidgetStatus.DRAFT
    }

    // Additional command/event handlers...
}
```

**No changes needed!** ✅

---

### 7.3 Query Handler Verification

**Check query handlers compile:**

```kotlin
@Component
class WidgetProjectionQueryHandler(
    private val dsl: DSLContext
) {
    @QueryHandler
    fun handle(query: FindWidgetQuery): WidgetView? {
        // Should work unchanged
        return dsl.selectFrom(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.ID.eq(query.widgetId))
            .fetchOne()
            ?.map { /* mapping logic */ }
    }

    @QueryHandler
    fun handle(query: FindAllWidgetsQuery): List<WidgetView> {
        // Should work unchanged
        return dsl.selectFrom(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.TENANT_ID.eq(query.tenantId))
            .fetch()
            .map { /* mapping logic */ }
    }
}
```

**Expected:** No changes needed ✅

---

### 7.4 Event Handler Verification

**Check event handlers (projections):**

```kotlin
@Component
@ProcessingGroup("widget-projection")
class WidgetProjectionEventHandler(
    private val dsl: DSLContext
) {
    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        dsl.insertInto(WIDGET_PROJECTION)
            .set(WIDGET_PROJECTION.ID, event.widgetId)
            .set(WIDGET_PROJECTION.NAME, event.name)
            .set(WIDGET_PROJECTION.TENANT_ID, event.tenantId)
            .set(WIDGET_PROJECTION.STATUS, WidgetStatus.DRAFT.name)
            .execute()
    }

    @EventHandler
    fun on(event: WidgetUpdatedEvent) {
        dsl.update(WIDGET_PROJECTION)
            .set(WIDGET_PROJECTION.NAME, event.name)
            .where(WIDGET_PROJECTION.ID.eq(event.widgetId))
            .execute()
    }
}
```

**Expected:** No changes needed ✅

---

### 7.5 REST Controller Verification

**Check controllers compile:**

```kotlin
@RestController
@RequestMapping("/api/widgets")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {
    @PostMapping
    @PreAuthorize("hasRole('WIDGET_ADMIN')")
    fun createWidget(
        @Valid @RequestBody request: CreateWidgetRequest
    ): ResponseEntity<WidgetResponse> {
        val tenantId = TenantContext.getCurrentTenantId()

        // Blocking command (unchanged)
        val widgetId = commandGateway.sendAndWait<UUID>(
            CreateWidgetCommand(
                widgetId = UUID.randomUUID(),
                name = request.name,
                tenantId = tenantId
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(WidgetResponse(widgetId))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WIDGET_ADMIN', 'WIDGET_VIEWER')")
    fun getWidget(@PathVariable id: UUID): ResponseEntity<WidgetView> {
        val tenantId = TenantContext.getCurrentTenantId()

        // Blocking query (unchanged)
        val widget = queryGateway.query(
            FindWidgetQuery(id, tenantId),
            ResponseTypes.instanceOf(WidgetView::class.java)
        ).join()

        return ResponseEntity.ok(widget)
    }
}
```

**Expected:** No changes needed ✅

---

### 7.6 Fix Remaining Compilation Errors

**Check compilation:**

```bash
./gradlew clean compileKotlin

# Review errors
```

**Common Issues:**

**1. Import changes:**
```kotlin
// Update imports as needed
import org.axonframework.config.Configuration  // Not Configurer
```

**2. Deprecated methods:**
```kotlin
// If method deprecated, check Axon 5 docs for replacement
// Example (hypothetical):
// BEFORE: configurer.commandGateway()
// AFTER:  configuration.commandGateway()
```

**3. API signature changes:**
- Consult migration guide
- Update method signatures

---

### 7.7 Code Review

**Create Pull Request:**

```bash
git add .
git commit -m "refactor(axon5): complete code migration to Axon Framework 5.0"
git push origin axon5-migration-path-a
```

**PR Checklist:**
- [ ] All compilation errors fixed
- [ ] No deprecated API warnings
- [ ] Import statements cleaned up
- [ ] Code follows EAF coding standards
- [ ] Multi-tenancy patterns preserved
- [ ] Blocking I/O patterns maintained

**Reviewers:**
- Lead Architect
- Senior Developer
- Migration Lead

---

### 7.8 Deliverables - Phase 4

- [x] All code compiles successfully
- [x] No deprecated API usage
- [x] Import statements updated
- [x] Code review complete
- [x] Ready for testing

**Duration:** 5 working days
**Effort:** 1.0 FTE (Senior Developer)

---

## 8. Phase 5: Testing & Validation (2-3 weeks)

**Most critical phase** - Comprehensive validation before production

### 8.1 Unit Test Execution (Week 1)

#### 8.1.1 Run Full Unit Test Suite

```bash
# Clean build
./gradlew clean

# Run all unit tests
./gradlew test

# Expected: All tests pass ✅
```

**If failures:**
- Review test logs
- Identify Axon 5-specific issues
- Fix and re-run

---

#### 8.1.2 Aggregate Test Fixture Validation

**Verify Axon Test Fixtures work:**

```kotlin
class WidgetAggregateTest : FunSpec({
    val fixture = AggregateTestFixture(WidgetAggregate::class.java)

    beforeTest {
        // Set tenant context for tests
        TenantContext.setCurrentTenantId("tenant-a")
    }

    afterTest {
        TenantContext.clearCurrentTenant()
    }

    test("should create widget with valid tenant") {
        fixture.givenNoPriorActivity()
            .`when`(CreateWidgetCommand(
                widgetId = UUID.randomUUID(),
                name = "Test Widget",
                tenantId = "tenant-a"
            ))
            .expectEvents(WidgetCreatedEvent(
                widgetId = /* same as command */,
                name = "Test Widget",
                tenantId = "tenant-a"
            ))
    }

    test("should reject command with mismatched tenant") {
        fixture.givenNoPriorActivity()
            .`when`(CreateWidgetCommand(
                widgetId = UUID.randomUUID(),
                name = "Test Widget",
                tenantId = "tenant-b"  // Different tenant!
            ))
            .expectException(IllegalArgumentException::class.java)
            .expectExceptionMessage("Access denied")
    }
})
```

**Run:**
```bash
./gradlew :products:widget-demo:test --tests "*WidgetAggregateTest"
```

---

#### 8.1.3 Coverage Verification

```bash
# Generate coverage report
./gradlew test koverHtmlReport

# Open report
open build/reports/kover/html/index.html
```

**Target:** ≥85% line coverage (maintain current level)

**If coverage dropped:**
- Identify uncovered lines
- Add tests for new Axon 5 code paths

---

### 8.2 Integration Test Execution (Week 1-2)

#### 8.2.1 CQRS Flow Testing

**Test complete command-query flow:**

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class WidgetCQRSFlowTest : FunSpec() {
    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

    @Autowired
    private lateinit var dsl: DSLContext

    init {
        extension(SpringExtension())

        test("should complete full CQRS flow (command → event → projection → query)") {
            // Set tenant context
            TenantContext.setCurrentTenantId("tenant-a")

            try {
                val widgetId = UUID.randomUUID()

                // 1. Send command
                commandGateway.sendAndWait<Unit>(
                    CreateWidgetCommand(widgetId, "Integration Test", "tenant-a")
                )

                // 2. Wait for projection update (async event processing)
                eventually(Duration.ofSeconds(5)) {
                    val projection = dsl.selectFrom(WIDGET_PROJECTION)
                        .where(WIDGET_PROJECTION.ID.eq(widgetId))
                        .fetchOne()

                    projection.shouldNotBeNull()
                }

                // 3. Query via QueryGateway
                val result = queryGateway.query(
                    FindWidgetQuery(widgetId, "tenant-a"),
                    ResponseTypes.instanceOf(WidgetView::class.java)
                ).join()

                result.shouldNotBeNull()
                result.name shouldBe "Integration Test"
                result.tenantId shouldBe "tenant-a"

            } finally {
                TenantContext.clearCurrentTenant()
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))

        @Container
        val keycloak = KeycloakContainer(DockerImageName.parse("quay.io/keycloak/keycloak:26.4.2"))

        @Container
        val redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
    }
}
```

**Run:**
```bash
./gradlew :products:widget-demo:integrationTest
```

**Expected:** All integration tests pass ✅

---

#### 8.2.2 Multi-Tenancy Validation (3 Layers)

**Test each layer:**

**Layer 1: Request Filter**
```kotlin
test("Layer 1: Should extract tenant from JWT and set context") {
    // Mock JWT with tenant_id claim
    val jwt = createMockJwt(tenantId = "tenant-a")

    // Make request with JWT
    mockMvc.get("/api/widgets") {
        header("Authorization", "Bearer $jwt")
    }.andExpect {
        status { isOk() }
    }

    // TenantContext should have been set by filter
    // (verify via logs or internal state)
}
```

**Layer 2: Command Handler Validation**
```kotlin
test("Layer 2: Should reject command with mismatched tenant") {
    TenantContext.setCurrentTenantId("tenant-a")

    try {
        val exception = shouldThrow<IllegalArgumentException> {
            commandGateway.sendAndWait<Unit>(
                CreateWidgetCommand(
                    widgetId = UUID.randomUUID(),
                    name = "Test",
                    tenantId = "tenant-b"  // Mismatch!
                )
            )
        }

        exception.message shouldContain "Access denied"
    } finally {
        TenantContext.clearCurrentTenant()
    }
}
```

**Layer 3: PostgreSQL RLS**
```kotlin
test("Layer 3: PostgreSQL RLS should prevent cross-tenant access") {
    // Create widget for tenant-a
    TenantContext.setCurrentTenantId("tenant-a")
    val widgetId = UUID.randomUUID()
    commandGateway.sendAndWait<Unit>(
        CreateWidgetCommand(widgetId, "Tenant A Widget", "tenant-a")
    )
    TenantContext.clearCurrentTenant()

    // Attempt to query as tenant-b
    TenantContext.setCurrentTenantId("tenant-b")
    try {
        val result = dsl.selectFrom(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.ID.eq(widgetId))
            .fetchOne()

        result.shouldBeNull()  // RLS blocks access
    } finally {
        TenantContext.clearCurrentTenant()
    }
}
```

**Run:**
```bash
./gradlew :products:widget-demo:integrationTest --tests "*MultiTenancy*"
```

**Critical:** All 3 layers must pass ✅

---

### 8.3 Event Replay Testing (Week 2)

**Critical:** Ensure Axon 5 can replay Axon 4 events

#### 8.3.1 Backup Production Event Store

```bash
# Backup PostgreSQL database
pg_dump -h localhost -U eaf_user -d eaf_db \
    -t domain_event_entry \
    -t snapshot_event_entry \
    -t token_entry \
    --data-only \
    -f axon4-events-backup.sql
```

---

#### 8.3.2 Load Events into Test Environment

```bash
# Load into Axon 5 test database
psql -h localhost -U eaf_test -d eaf_axon5_test < axon4-events-backup.sql
```

---

#### 8.3.3 Replay Events

**Test event replay:**

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class EventReplayTest : FunSpec() {
    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var dsl: DSLContext

    init {
        test("should replay Axon 4 events with Axon 5") {
            // Clear projections
            dsl.truncate(WIDGET_PROJECTION).execute()

            // Replay all events from event store
            val eventProcessingContext = /* get context */
            eventProcessingContext.resetTokens()

            // Wait for replay to complete
            eventually(Duration.ofMinutes(5)) {
                val projectionCount = dsl.selectCount()
                    .from(WIDGET_PROJECTION)
                    .fetchOne(0, Int::class.java)

                projectionCount shouldBeGreaterThan 0
            }

            // Verify projection integrity
            val widgets = dsl.selectFrom(WIDGET_PROJECTION).fetch()

            widgets.forEach { widget ->
                widget[WIDGET_PROJECTION.ID].shouldNotBeNull()
                widget[WIDGET_PROJECTION.NAME].shouldNotBeBlank()
                widget[WIDGET_PROJECTION.TENANT_ID].shouldNotBeBlank()
            }
        }
    }
}
```

**Expected:** All events replay successfully ✅

---

### 8.4 Performance Benchmarking (Week 2)

#### 8.4.1 Command Latency Testing

**Load Test Script:**

```bash
#!/bin/bash
# scripts/load-test-axon5.sh

# Create 1000 widgets
for i in {1..1000}; do
    curl -X POST http://localhost:8080/api/widgets \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"LoadTest-$i\"}" &
done

wait
```

**Measure latency:**
```bash
# Run load test
./scripts/load-test-axon5.sh

# Analyze metrics from Prometheus
# Query: histogram_quantile(0.95, rate(command_latency_seconds_bucket[5m]))
```

**Target:** p95 <200ms (same as Axon 4 baseline)

**Compare to baseline:**
```markdown
### Command Latency Comparison

| Command | Axon 4.12.1 (p95) | Axon 5.0.1 (p95) | Change |
|---------|------------------|------------------|--------|
| CreateWidget | 145ms | 138ms | ✅ -7ms (faster) |
| UpdateWidget | 132ms | 135ms | ⚠️ +3ms (acceptable) |
| ActivateWidget | 98ms | 95ms | ✅ -3ms (faster) |
```

**If regression >10%:**
- Investigate root cause
- Check event processor configuration
- Review aggregate caching
- Consult Axon community

---

#### 8.4.2 Event Processor Lag Testing

**Generate event burst:**

```kotlin
test("should process 1000 events within lag target") {
    val startTime = Instant.now()

    // Generate 1000 events rapidly
    repeat(1000) { i ->
        commandGateway.sendAndWait<Unit>(
            CreateWidgetCommand(UUID.randomUUID(), "Event-$i", "tenant-a")
        )
    }

    // Wait for all projections to update
    eventually(Duration.ofSeconds(15)) {
        val count = dsl.selectCount()
            .from(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.NAME.like("Event-%"))
            .fetchOne(0, Int::class.java)

        count shouldBe 1000
    }

    val endTime = Instant.now()
    val totalLag = Duration.between(startTime, endTime)

    // Target: <10 seconds for 1000 events
    totalLag.seconds shouldBeLessThan 10
}
```

**Expected:** Event lag <10 seconds ✅

---

#### 8.4.3 Memory and Thread Usage

**Monitor during load test:**

```bash
# JVM metrics
jcmd <pid> GC.heap_info

# Thread count
jcmd <pid> Thread.print | grep "Thread" | wc -l
```

**Compare to Axon 4 baseline:**

| Metric | Axon 4.12.1 | Axon 5.0.1 | Change |
|--------|-------------|------------|--------|
| Heap Usage | 450MB | 440MB | ✅ -10MB |
| Thread Count | 45 | 42 | ✅ -3 threads |
| CPU Usage | 35% | 33% | ✅ -2% |

**If significant increase:**
- Investigate memory leaks
- Check thread pool configuration
- Review ProcessingContext overhead

---

### 8.5 Security Testing (Week 2-3)

#### 8.5.1 JWT Validation (10 Layers)

**Test all 10 layers still work:**

```kotlin
test("Layer 1: Format validation") {
    val malformedJwt = "not.a.jwt"

    mockMvc.get("/api/widgets") {
        header("Authorization", "Bearer $malformedJwt")
    }.andExpect {
        status { isUnauthorized() }
    }
}

test("Layer 2: Signature validation") {
    val invalidSignatureJwt = createJwtWithInvalidSignature()

    mockMvc.get("/api/widgets") {
        header("Authorization", "Bearer $invalidSignatureJwt")
    }.andExpect {
        status { isUnauthorized() }
    }
}

// ... test all 10 layers ...

test("Layer 7: Token revocation check") {
    val validJwt = createValidJwt()

    // Revoke token
    redisTemplate.opsForSet().add("revoked-tokens", extractJti(validJwt))

    // Should be rejected
    mockMvc.get("/api/widgets") {
        header("Authorization", "Bearer $validJwt")
    }.andExpect {
        status { isUnauthorized() }
    }
}
```

**Run:**
```bash
./gradlew :framework:security:integrationTest --tests "*JwtValidationTest"
```

**Expected:** All 10 layers pass ✅

---

#### 8.5.2 OWASP Dependency Check

```bash
# Run dependency vulnerability scan
./gradlew dependencyCheckAnalyze

# Review report
open build/reports/dependency-check-report.html
```

**Action on CVEs:**
- Review all HIGH/CRITICAL vulnerabilities
- Update dependencies if patches available
- Document accepted risks

---

### 8.6 Architecture Compliance (Week 3)

#### 8.6.1 Konsist Architecture Tests

```bash
# Run Konsist rules
./gradlew :konsist-tests:test

# Expected: Zero violations
```

**If violations:**
- Review violations
- Fix architectural issues
- Update Konsist rules if needed (for Axon 5 patterns)

---

#### 8.6.2 Spring Modulith Verification

```bash
# Verify module boundaries
./gradlew :products:widget-demo:modulithTest

# Expected: No boundary violations
```

---

### 8.7 Quality Gates (Week 3)

**Run all quality checks:**

```bash
# ktlint
./gradlew ktlintCheck

# Detekt
./gradlew detekt

# Kover coverage
./gradlew koverVerify

# Expected: All pass with zero violations
```

---

### 8.8 Deliverables - Phase 5

- [x] Unit tests: 100% pass rate
- [x] Integration tests: 100% pass rate
- [x] Event replay: Successful with Axon 4 events
- [x] Performance: No regression (p95 <200ms, lag <10s)
- [x] Security: All 10 JWT layers validated
- [x] Multi-tenancy: All 3 layers enforced
- [x] Architecture: Konsist + Modulith compliance
- [x] Quality: ktlint, Detekt, Kover all pass
- [x] Coverage: ≥85% maintained

**Duration:** 10-15 working days
**Effort:** 2.0 FTE (QA Engineer + Senior Developer)

---

## 9. Phase 6: Deployment (1 week)

### 9.1 Staging Deployment (Day 1-2)

#### 9.1.1 Pre-Deployment Checklist

**Verify before deploying:**

- [x] All tests passing
- [x] Performance benchmarks acceptable
- [x] No critical vulnerabilities (OWASP)
- [x] Rollback procedure tested
- [x] Team briefed on deployment

---

#### 9.1.2 Deploy to Staging

```bash
# Build production artifacts
./gradlew clean build

# Build Docker image
./gradlew :products:widget-demo:bootBuildImage

# Tag image
docker tag eaf-widget-demo:latest eaf-widget-demo:axon5-staging

# Push to registry
docker push eaf-widget-demo:axon5-staging

# Deploy to staging (Docker Compose)
docker-compose -f docker-compose.staging.yml up -d
```

---

#### 9.1.3 Smoke Tests on Staging

**Manual Tests:**

1. **Health Check**
   ```bash
   curl http://staging.eaf.example.com/actuator/health
   # Expected: {"status":"UP"}
   ```

2. **Create Widget**
   ```bash
   curl -X POST http://staging.eaf.example.com/api/widgets \
       -H "Authorization: Bearer $JWT" \
       -H "Content-Type: application/json" \
       -d '{"name":"Staging Test"}'
   # Expected: 201 Created
   ```

3. **Query Widget**
   ```bash
   curl http://staging.eaf.example.com/api/widgets/{id} \
       -H "Authorization: Bearer $JWT"
   # Expected: 200 OK with widget data
   ```

4. **Multi-Tenancy Check**
   ```bash
   # Create widget as tenant-a
   # Attempt to access as tenant-b
   # Expected: 403 Forbidden
   ```

**Automated Smoke Tests:**
```bash
# Run smoke test suite
./gradlew :products:widget-demo:smokeTest \
    -Dspring.profiles.active=staging
```

---

#### 9.1.4 Monitor Staging (24 hours)

**Metrics to watch:**

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Error Rate | <0.1% | >0.5% |
| Response Time (p95) | <200ms | >300ms |
| Event Lag | <10s | >15s |
| Memory Usage | <1GB | >1.5GB |
| CPU Usage | <40% | >60% |

**Grafana Dashboard:** `http://grafana.eaf.example.com/d/axon5-staging`

---

### 9.2 Production Deployment (Day 3-5)

#### 9.2.1 Pre-Production Checklist

- [x] Staging validated (24+ hours)
- [x] No critical issues in staging
- [x] Stakeholders notified
- [x] Maintenance window scheduled (if needed)
- [x] Rollback plan ready
- [x] On-call team briefed

---

#### 9.2.2 Production Deployment Strategy

**Use Blue-Green Deployment:**

```
┌─────────────────────────────────────────┐
│          Load Balancer                  │
└─────────────┬───────────────────────────┘
              │
    ┌─────────┴──────────┐
    │                    │
┌───▼────┐         ┌─────▼───┐
│  BLUE  │         │  GREEN  │
│ (old)  │         │  (new)  │
│ Axon 4 │         │ Axon 5  │
└────────┘         └─────────┘
  100%                 0%
   traffic          traffic

Step 1: Deploy Axon 5 to GREEN (0% traffic)
Step 2: Route 10% traffic to GREEN (canary)
Step 3: Monitor for 1 hour
Step 4: Route 100% traffic to GREEN
Step 5: Keep BLUE running for quick rollback
```

---

#### 9.2.3 Deployment Steps

**Step 1: Deploy to GREEN environment**

```bash
# Build production image
./gradlew :products:widget-demo:bootBuildImage \
    -Pprofile=production

# Tag for production
docker tag eaf-widget-demo:latest eaf-widget-demo:5.0-prod

# Deploy to GREEN servers
ansible-playbook deploy-green.yml \
    -e image_tag=5.0-prod
```

---

**Step 2: Health Check GREEN**

```bash
# Wait for application to start
sleep 60

# Health check
curl http://green.eaf.example.com/actuator/health

# Expected: {"status":"UP"}
```

---

**Step 3: Canary Traffic (10%)**

```bash
# Update load balancer
# Route 10% of traffic to GREEN

# HAProxy example:
# backend eaf-backend
#     server blue blue.eaf.example.com:8080 weight 90
#     server green green.eaf.example.com:8080 weight 10

# Apply configuration
sudo systemctl reload haproxy
```

---

**Step 4: Monitor Canary (1 hour)**

**Watch for:**
- Error rate increase
- Latency spikes
- Event processing delays
- Memory/CPU issues

**Grafana Dashboard:** Live monitoring

**If issues detected:**
```bash
# Immediate rollback
# Route 100% traffic back to BLUE
sudo systemctl reload haproxy  # Revert config
```

---

**Step 5: Full Traffic Switch**

**If canary successful:**

```bash
# Route 100% traffic to GREEN
# HAProxy: weight blue=0, weight green=100

sudo systemctl reload haproxy
```

**Monitor for 4 hours post-switch**

---

**Step 6: Decommission BLUE (Day 5)**

**After 48 hours stability:**

```bash
# Stop BLUE environment
ansible-playbook stop-blue.yml

# Keep BLUE image for emergency rollback (7 days)
```

---

### 9.3 Post-Deployment Validation (Day 4-5)

#### 9.3.1 Production Smoke Tests

**Run automated tests against production:**

```bash
./gradlew :products:widget-demo:productionSmokeTest \
    -Dbase.url=https://eaf.example.com \
    -Djwt.token=$PROD_JWT
```

**Manual Validation:**
- Create widget via UI
- Query widgets via API
- Verify multi-tenancy isolation
- Check metrics in Grafana

---

#### 9.3.2 Performance Validation

**Compare production metrics:**

| Metric | Axon 4 Baseline | Axon 5 Production | Status |
|--------|----------------|-------------------|--------|
| Command Latency (p95) | 145ms | 142ms | ✅ Improved |
| Event Lag | 8.5s | 8.2s | ✅ Improved |
| Memory Usage | 450MB | 445MB | ✅ Stable |
| Error Rate | 0.05% | 0.04% | ✅ Improved |

---

#### 9.3.3 Monitor for 48 Hours

**Extended Monitoring:**

**Day 1-2 Post-Deployment:**
- Monitor every 2 hours
- On-call engineer available
- Quick rollback ready

**Metrics Dashboard:** 24/7 display in team room

**Alert Thresholds:**
- 🔴 **Critical:** Error rate >0.5%, latency >500ms → Immediate rollback
- 🟡 **Warning:** Error rate >0.2%, latency >300ms → Investigate

---

### 9.4 Deliverables - Phase 6

- [x] Staging deployment successful
- [x] Staging validated (24 hours)
- [x] Production blue-green deployment complete
- [x] Canary testing successful (10% traffic)
- [x] Full traffic switch successful (100%)
- [x] Production monitored (48 hours stable)
- [x] Performance metrics acceptable
- [x] BLUE environment decommissioned

**Duration:** 5 working days
**Effort:** 1.5 FTE (DevOps + Migration Lead)

---

## 10. Rollback Procedures

### 10.1 Rollback Triggers

**Initiate rollback if:**

| Trigger | Severity | Action |
|---------|----------|--------|
| **Error rate >0.5%** | 🔴 Critical | Immediate rollback |
| **Latency p95 >500ms** | 🔴 Critical | Immediate rollback |
| **Event lag >30s** | 🔴 Critical | Immediate rollback |
| **Multi-tenancy breach** | 🔴 Critical | Immediate rollback |
| **Data corruption** | 🔴 Critical | Immediate rollback |
| **Error rate >0.2%** | 🟡 Warning | Investigate, rollback if worsens |
| **Latency p95 >300ms** | 🟡 Warning | Investigate, rollback if worsens |

---

### 10.2 Rollback Procedure

**Time Budget:** <15 minutes from decision to stable

#### Step 1: Announce Rollback (1 minute)

```bash
# Slack notification
/alert "🔴 ROLLBACK INITIATED - Axon 5 migration"
```

---

#### Step 2: Route Traffic to BLUE (2 minutes)

```bash
# Update load balancer
# HAProxy: weight green=0, weight blue=100

sudo systemctl reload haproxy

# Verify traffic shifted
curl -I http://eaf.example.com/actuator/health
# Check X-Server header for blue
```

---

#### Step 3: Stop GREEN Environment (1 minute)

```bash
# Stop Axon 5 application
ansible-playbook stop-green.yml
```

---

#### Step 4: Verify BLUE Stability (5 minutes)

**Check metrics:**
- Error rate normal
- Latency back to baseline
- Event processing resuming

---

#### Step 5: Investigate Root Cause (After Stabilization)

**Post-Rollback Analysis:**
- Extract logs from GREEN
- Review error patterns
- Identify Axon 5-specific issues
- Plan remediation

---

### 10.3 Database Rollback (If Needed)

**Event Store Compatibility:**

Axon 5 should be forward-compatible with Axon 4 event store.

**If event store corrupted:**

```bash
# Restore from backup
pg_restore -h localhost -U eaf_user -d eaf_db \
    axon4-events-backup.sql

# Restart BLUE environment
ansible-playbook restart-blue.yml
```

**Data Loss:** Minimal (events after backup)

---

### 10.4 Rollback Testing (Pre-Deployment)

**Practice rollback procedure:**

```bash
# In staging environment
# 1. Deploy Axon 5
# 2. Route traffic
# 3. SIMULATE FAILURE
# 4. Execute rollback procedure
# 5. Verify stable

# Time the procedure
# Target: <15 minutes
```

**Document:**
- Actual rollback time
- Issues encountered
- Procedure refinements

---

## 11. Success Criteria

### 11.1 Technical Success Criteria

**Migration is successful when:**

- [x] **All tests pass:** Unit, integration, E2E (100% pass rate)
- [x] **Performance maintained:** Command latency p95 <200ms, event lag <10s
- [x] **No regressions:** Event replay works, projections correct
- [x] **Multi-tenancy intact:** All 3 layers enforced
- [x] **Security validated:** 10-layer JWT validation working
- [x] **Quality gates pass:** ktlint, Detekt, Konsist, Kover all green
- [x] **Production stable:** 48 hours without critical issues

---

### 11.2 Business Success Criteria

**Migration delivers value when:**

- [x] **Zero downtime:** Users experience no service interruption
- [x] **No data loss:** All events preserved and accessible
- [x] **Feature parity:** All existing functionality works
- [x] **Team confidence:** Developers comfortable with Axon 5
- [x] **Documentation complete:** Migration lessons documented

---

### 11.3 Path B Readiness Criteria

**Positioned for async adoption when:**

- [x] **Async-native architecture:** ProcessingContext in place
- [x] **Configuration modularity:** Easy to adopt reactive processors
- [x] **Testing infrastructure:** Kotest compatible with async patterns
- [x] **Team knowledge:** Understands Axon 5 capabilities
- [x] **No architectural barriers:** Clean separation of concerns

---

## 12. Path B Preparation (Future Async Adoption)

**Note:** Path A migration positions EAF for future async adoption without forcing it.

### 12.1 Architectural Decisions (Path A) That Help Path B

**1. Keep Configuration Modular**

```kotlin
// Good: Modular configuration (easy to extend)
@Configuration
class EventProcessorConfiguration {

    @Bean
    fun widgetProcessorConfig(): EventProcessingConfigurer {
        return { config ->
            config.registerEventProcessor("widget-projection") {
                // Currently: Blocking
                // Future: Easy to switch to reactive
            }
        }
    }
}
```

**2. Avoid Deep ThreadLocal Dependencies**

- ProcessingContext already used (Axon 5)
- TenantContext could be refactored to reactive context later
- No new ThreadLocal usage

**3. Prefer Immutable Entities (Optional in Path A)**

```kotlin
// If adopting new aggregates in Path A, consider:
data class WidgetAggregate(
    val id: UUID,
    val name: String,
    val status: WidgetStatus
) {
    // Immutable - ready for reactive
    fun activate(): WidgetAggregate = copy(status = WidgetStatus.ACTIVE)
}
```

**4. Test with Async Patterns (Even If Blocking)**

```kotlin
// Use `eventually` pattern (prepares for async)
eventually(Duration.ofSeconds(5)) {
    val projection = queryProjection(widgetId)
    projection.shouldNotBeNull()
}
```

---

### 12.2 When to Consider Path B

**Evaluate Path B (Async Adoption) when:**

1. **Performance Requirements Change**
   - Need to handle >1000 concurrent requests
   - Current thread pool exhausting (200 threads not enough)

2. **New Features Require Async**
   - Long-running workflows (Epic 6)
   - Real-time updates (WebSockets, SSE)
   - Microservices communication

3. **Team Readiness**
   - Developers trained in reactive programming
   - Kotlin Coroutines expertise acquired
   - Time budget for migration (3-4 months)

4. **Business Justification**
   - Clear ROI for async adoption
   - Performance bottlenecks identified
   - Scalability becomes critical

---

### 12.3 Path B Trigger Points (Quantitative)

**Consider Path B if:**

| Metric | Current Target | Path B Trigger |
|--------|---------------|----------------|
| **Concurrent Requests** | <200 | >500 sustained |
| **Thread Pool Utilization** | <70% | >85% sustained |
| **Response Time (p95)** | <200ms | >250ms sustained |
| **Memory per Request** | <5MB | >10MB sustained |

---

### 12.4 Path A → Path B Migration Estimate

**If Path B needed later:**

**Duration:** 3-4 months
**Phases:**
1. Team training (Kotlin Coroutines, Spring WebFlux) - 4 weeks
2. Spring MVC → WebFlux migration - 6 weeks
3. Blocking I/O → Async I/O refactoring - 8 weeks
4. Testing and validation - 4 weeks

**Effort:** ~40 person-weeks

**Complexity:** 🔴 **HIGH** - Major architectural change

---

## 13. Appendices

### Appendix A: Axon Framework 5 Resources

**Official Documentation:**
- [Axon Framework 5 Getting Started](https://docs.axoniq.io/axon-framework-5-getting-started/)
- [Axon Framework 5 Reference Guide](https://docs.axoniq.io/reference-guide/)
- [GitHub Repository](https://github.com/AxonFramework/AxonFramework)

**Migration Resources:**
- [Axon 5 Configuration Blog](https://www.axoniq.io/blog/announcing-axon-framework-5-configuring-axon-made-easy)
- [Dynamic Consistency Boundary Overview](https://www.axoniq.io/blog/dcb-in-af-5)
- [OpenRewrite Recipes](https://github.com/AxonFramework/AxonFramework/tree/main/migration)

**Community:**
- [AxonIQ Discussion Forum](https://discuss.axoniq.io/)
- [Stack Overflow - axon tag](https://stackoverflow.com/questions/tagged/axon)

---

### Appendix B: Checklist Template

**Copy to:** `docs/axon5-migration-checklist.md`

```markdown
# Axon 5 Migration Checklist - Path A

## Pre-Migration
- [ ] Axon 5.0 GA released
- [ ] OpenRewrite recipes available
- [ ] Team training complete
- [ ] Codebase audit complete
- [ ] Test environment ready
- [ ] Baseline metrics collected

## Phase 1: Environment Setup
- [ ] Migration branch created
- [ ] Version catalog prepared
- [ ] Docker Compose configured
- [ ] Baseline tests passing

## Phase 2: Dependency Updates
- [ ] Axon Framework updated to 5.0.x
- [ ] Dependency conflicts resolved
- [ ] Compilation errors documented

## Phase 3: Configuration Migration
- [ ] OpenRewrite migrations applied
- [ ] AxonConfiguration.kt updated
- [ ] Event processor configuration migrated
- [ ] Serializer compatibility verified
- [ ] Multi-tenancy context propagation working

## Phase 4: Code Updates
- [ ] Import statements updated
- [ ] Compilation successful
- [ ] Code review complete

## Phase 5: Testing & Validation
- [ ] Unit tests (100% pass)
- [ ] Integration tests (100% pass)
- [ ] Event replay successful
- [ ] Performance benchmarks acceptable
- [ ] Security validation complete
- [ ] Multi-tenancy validated (3 layers)
- [ ] Architecture compliance (Konsist, Modulith)
- [ ] Quality gates pass (ktlint, Detekt, Kover)

## Phase 6: Deployment
- [ ] Staging deployment successful
- [ ] Staging validated (24 hours)
- [ ] Production blue-green deployment
- [ ] Canary testing successful
- [ ] Full traffic switch successful
- [ ] Production monitored (48 hours)

## Post-Migration
- [ ] BLUE environment decommissioned
- [ ] Documentation updated
- [ ] Lessons learned documented
- [ ] Team retrospective complete
```

---

### Appendix C: Communication Plan

**Stakeholder Updates:**

**Weekly (During Migration):**
- Migration status report
- Risks and blockers
- Timeline updates

**Daily (During Deployment Week):**
- Deployment progress
- Metrics dashboard
- Incident reports (if any)

**Post-Migration:**
- Success report
- Lessons learned
- Path B evaluation (6 months)

---

### Appendix D: Estimated Costs

**Team Effort:**
- Migration Lead: 8 weeks × 100% = 8 person-weeks
- Senior Developer: 8 weeks × 100% = 8 person-weeks
- QA Engineer: 4 weeks × 75% = 3 person-weeks
- DevOps Engineer: 2 weeks × 50% = 1 person-week
- Architect: 8 weeks × 25% = 2 person-weeks

**Total:** ~22 person-weeks

**Infrastructure Costs:**
- Test environment: ~$500/month × 2 months = $1,000
- Staging environment: ~$1,000/month × 1 month = $1,000
- Blue-green deployment: ~$2,000/month × 1 month = $2,000

**Total Infrastructure:** ~$4,000

**Contingency:** +20% = $4,800

**Grand Total:** ~$4,800 infrastructure + team time

---

### Appendix E: Glossary

| Term | Definition |
|------|------------|
| **Path A** | Conservative migration - Blocking I/O, minimal changes |
| **Path B** | Aggressive migration - Full async/reactive adoption |
| **ProcessingContext** | Axon 5 replacement for ThreadLocal-based UnitOfWork |
| **PooledStreamingEventProcessor** | Axon 5 default event processor (replaces Tracking) |
| **DCB** | Dynamic Consistency Boundary - Flexible event sourcing pattern |
| **Blue-Green Deployment** | Zero-downtime deployment with two identical environments |
| **Canary Testing** | Gradual rollout (10% traffic) before full deployment |
| **OpenRewrite** | Automated refactoring tool for code migrations |

---

## Document Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-17 | Architecture Team | Initial comprehensive migration plan (Path A) |

---

## Approval and Sign-off

**Status:** 🟡 **READY FOR EXECUTION** (Pending Axon 5.0 GA)

**Approvals Required:**
- [ ] **Product Owner** - Business impact and timeline
- [ ] **Lead Architect** - Technical approach
- [ ] **Development Lead** - Team capacity and effort
- [ ] **Operations Lead** - Deployment strategy
- [ ] **Security Lead** - Security validation plan

**Next Steps:**
1. **Now:** Review and approve migration plan
2. **Monitor:** Axon Framework 5.0 GA release (weekly)
3. **Trigger:** When GA + OpenRewrite available → Begin Phase 1
4. **Target Start:** January 2026

---

**Document End**

**Total Pages:** 47
**Total Duration:** 6-8 weeks
**Total Effort:** ~22 person-weeks
**Risk Level:** 🟡 **MEDIUM** (Conservative approach)
**Path B Ready:** ✅ **YES** (Future-proof for async adoption)
