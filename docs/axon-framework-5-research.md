# Axon Framework 5: Deep Research and Spring Boot 4 / Spring Framework 7 Compatibility Analysis

**Document Version:** 1.0
**Created:** 2025-11-17
**Status:** RESEARCH COMPLETE
**Author:** Architecture Team

---

## Executive Summary

This document provides comprehensive research on **Axon Framework 5.0** and its compatibility with **Spring Boot 4** and **Spring Framework 7**. The research is critical for the EAF v1.0 migration planning from Spring Boot 3.5.7 to Spring Boot 4.0.x.

### Critical Findings 🔴

1. **Axon Framework 5.0 is NOT production-ready** (RC3 as of 2025-11-06)
2. **NO confirmed Spring Boot 4 or Spring Framework 7 compatibility** in official documentation
3. **GA release expected late 2025**, but no specific date announced
4. **Migration STILL BLOCKED** - Spring Boot 4 migration cannot proceed without Axon support

### Key Research Insights ✅

1. **Axon 5.0 RC3 released November 6, 2025** - Latest pre-release version
2. **Java 21 baseline** - Compatible with EAF's JVM 21 requirement
3. **Jakarta EE namespace support** - Uses jakarta.* by default (compatible with Jakarta EE 11)
4. **Spring Boot integration confirmed** - Milestone 2 included Spring Boot integration
5. **Revolutionary new features** - DCB, immutable entities, new configuration API

---

## Table of Contents

1. [Axon Framework 5.0 Release Status](#1-axon-framework-50-release-status)
2. [Spring Boot and Spring Framework Compatibility](#2-spring-boot-and-spring-framework-compatibility)
3. [Baseline Requirements](#3-baseline-requirements)
4. [Major New Features](#4-major-new-features)
5. [Breaking Changes from 4.x](#5-breaking-changes-from-4x)
6. [Migration Guidance](#6-migration-guidance)
7. [Impact on EAF Migration Plan](#7-impact-on-eaf-migration-plan)
8. [Recommendations and Action Items](#8-recommendations-and-action-items)
9. [Appendices](#9-appendices)

---

## 1. Axon Framework 5.0 Release Status

### 1.1 Release Timeline

**Milestone Releases:**

| Version | Release Date | Key Features | Status |
|---------|--------------|--------------|--------|
| **5.0.0-M1** | April 2025 | Core infrastructure, async-native architecture | Milestone |
| **5.0.0-M2** | June 27, 2025 | DCB, Spring Boot integration, entity modeling | Milestone |
| **5.0.0-M2.1** | July 3, 2025 | Bug fixes | Milestone |
| **5.0.0-M3** | August 29, 2025 | Event processing updates, interceptors | Milestone |
| **5.0.0-preview** | September 29, 2025 | Feature preview | Preview |
| **5.0.0-RC1** | October 23, 2025 | Class-level annotations | Release Candidate |
| **5.0.0-RC2** | October 29, 2025 | Bug fixes | Release Candidate |
| **5.0.0-RC3** | November 6, 2025 | Latest improvements | **Current** |

**Expected GA:**
- **Target:** Late 2025 (official statement: "we intend to release it this year")
- **Specific Date:** NOT ANNOUNCED
- **Status:** Expected "very shortly" after RC3

### 1.2 Production Readiness

**Current Status (RC3):**
- ⚠️ **NOT production-ready or feature-complete**
- ✅ Release Candidate stage (final pre-release phase)
- ⚠️ May not contain all features from Axon Framework 4.x
- ⚠️ Organizations may choose to wait for complete feature parity

**Recommendation from AxonIQ:**
> "Organizations relying on particular Axon Framework 4 features might choose to wait until Axon Framework 5 gains complete feature parity."

### 1.3 Release Roadmap (4 Milestones + GA)

**Milestone 1:** Core Infrastructure
- Async-native architecture (ThreadLocal-free)
- UnitOfWork → ProcessingContext migration
- Java 21 baseline
- Renewed Configuration API
- Stateful command handling

**Milestone 2:** Axon Server + Modeling
- Dynamic Consistency Boundary (DCB) integration
- Entity/Aggregate modeling support
- **Spring Boot integration** ✅
- Update Checker feature

**Milestone 3:** Messaging Enhancements
- Event processing updates
- Interceptor support
- Serializers → Converters migration

**Milestone 4:** Cleanup and Stabilization
- Package restructuring
- PostgreSQL storage engine support
- Bug fixes and refinements

**GA Release:** Production-ready 5.0.0
- Complete feature parity with 4.x
- Full documentation
- Migration tooling (OpenRewrite recipes)

---

## 2. Spring Boot and Spring Framework Compatibility

### 2.1 Official Compatibility Information

**What We Know:**
- ✅ **Milestone 2** explicitly includes "Spring Boot Integration" as a major feature
- ✅ **Spring framework compatibility** mentioned in RC1 release notes
- ✅ **Spring Boot autoconfiguration** enhancements confirmed
- ❌ **Specific Spring Boot version numbers NOT mentioned** in official docs
- ❌ **Spring Framework version numbers NOT specified**
- ❌ **Spring Boot 4 compatibility NOT confirmed**
- ❌ **Spring Framework 7 compatibility NOT confirmed**

**What We Don't Know:**
- ❓ Which Spring Boot versions are compatible (3.x? 4.x?)
- ❓ Which Spring Framework versions are compatible (6.x? 7.x?)
- ❓ Whether Spring Boot 4 support is planned or in development
- ❓ Timeline for Spring Boot 4 compatibility (if planned)

### 2.2 Historical Context: Axon 4.7 + Spring Boot 3

**Axon Framework 4.7.0 (Released January 26, 2023):**
- ✅ Introduced Spring Boot 3 compatibility
- ✅ Supports both Spring Boot 2 and Spring Boot 3
- ✅ Supports both javax.* (Spring Boot 2) and jakarta.* (Spring Boot 3)
- ⏱️ Released **~3 months** after Spring Boot 3 GA (November 2022)

**Pattern Analysis:**
- Spring Boot 3 GA: November 2022
- Axon 4.7 with Boot 3 support: January 2023
- **Lag time: ~3 months**

**Applying to Spring Boot 4:**
- Spring Boot 4 GA: November 2025 (expected)
- **Potential Axon support: February 2026** (if same pattern)
- **This is purely speculative** - no official confirmation

### 2.3 Spring Boot Integration Features (Milestone 2)

**Confirmed Integration Capabilities:**
1. **Spring Boot autoconfiguration** - Automatic bean wiring
2. **Spring framework compatibility** - Core Spring support
3. **Configuration properties** - Spring Boot externalized config
4. **Component scanning** - Spring-managed beans
5. **Starter dependencies** - axon-spring-boot-starter

**Example from Axon 4.x (likely similar in 5.x):**
```kotlin
@SpringBootApplication
class MyAxonApplication

fun main(args: Array<String>) {
    runApplication<MyAxonApplication>(*args)
}
```

**Auto-configured Beans:**
- CommandBus
- EventBus
- QueryBus
- EventStore
- Snapshotter
- Serializer
- etc.

### 2.4 Spring Boot 4 / Framework 7 Compatibility Assessment

**Compatibility Likelihood: MEDIUM-HIGH 🟡**

**Evidence Supporting Compatibility:**
1. ✅ **Java 21 baseline** - Exceeds Spring Boot 4 minimum (Java 17)
2. ✅ **Jakarta namespace by default** - Compatible with Jakarta EE 11
3. ✅ **Spring Boot integration** is a core feature (Milestone 2)
4. ✅ **Modern Spring patterns** - Likely designed for current Spring versions
5. ✅ **Release timing** - Axon 5 GA expected late 2025, same as Spring Boot 4

**Evidence Against Immediate Compatibility:**
1. ⚠️ **No explicit Spring Boot 4 mention** in any official documentation
2. ⚠️ **No Spring Framework 7 mention** in release notes
3. ⚠️ **RC stage** - May not have tested with Spring Boot 4 yet (RC2 released in Oct)
4. ⚠️ **Spring Boot 4 is very new** - GA November 2025, may not be tested yet

**Most Likely Scenario:**
- Axon 5.0 GA will likely support **Spring Boot 3.x** initially
- Spring Boot 4 support may come in:
  - **Axon 5.0.x** patch release (Q1 2026)
  - **Axon 5.1** minor release (Q2 2026)
  - Similar to Axon 4.7 adding Spring Boot 3 support post-GA

---

## 3. Baseline Requirements

### 3.1 Axon Framework 5.0 Requirements

| Requirement | Version | EAF v1.0 Status | Compatibility |
|-------------|---------|-----------------|---------------|
| **Java** | 21+ | JVM 21 LTS | ✅ **COMPATIBLE** |
| **Jakarta EE** | 9+ (jakarta namespace) | Jakarta EE 10 | ✅ **COMPATIBLE** (11 also ok) |
| **Spring Boot** | TBD (likely 3.x) | 3.5.7 | ✅ **COMPATIBLE** (4.x unknown) |
| **Spring Framework** | TBD (likely 6.x) | 6.2.12 | ✅ **COMPATIBLE** (7.x unknown) |

### 3.2 Java 21 Baseline

**Why Java 21:**
- Take advantage of **Java records** for DCB feature
- Support for **pattern matching** and modern Java features
- Align with LTS release (Java 21)

**EAF Impact:**
- ✅ EAF already uses **JVM 21** - no upgrade needed
- ✅ No compatibility issues

### 3.3 Jakarta EE Namespace

**Axon Framework 5 Jakarta Support:**
- ✅ Uses **jakarta.*** namespace **by default**
- ✅ No longer provides `-jakarta` modules (4.6 feature)
- ✅ All javax.* code migrated to jakarta.*

**Affected APIs:**
- `jakarta.inject.Inject`
- `jakarta.persistence.*`
- `jakarta.validation.*`

**EAF Impact:**
- ✅ EAF uses **Spring Boot 3.x** - already on jakarta.*
- ✅ Compatible with **Jakarta EE 11** (Spring Boot 4 requirement)
- ✅ No migration needed (already done in Boot 3)

---

## 4. Major New Features

### 4.1 Dynamic Consistency Boundary (DCB)

**What is DCB:**
> "A revolutionary concept that allows more flexible event sourcing patterns, overcoming traditional aggregate limitations and enabling Vertical Slice Architecture."

**Key Capabilities:**

1. **Multi-Tag Events** - Events can have multiple labels instead of single aggregate ID
2. **Query-Based Consistency** - Replaces stream-based optimistic locking
3. **Dynamic Restructuring** - Change consistency boundaries without data migration
4. **Parallel Writes** - Unrelated operations can write simultaneously
5. **Aggregate-less Modeling** - Optional: Model without traditional aggregates

**Traditional Aggregate vs DCB:**

```kotlin
// TRADITIONAL (Axon 4.x)
@AggregateIdentifier
val widgetId: UUID

apply(WidgetCreatedEvent(widgetId, name, tenantId))
// Event stream: widget-{widgetId}
// Locked during command processing

// DCB (Axon 5.0)
@Tag("widget-${widgetId}")
@Tag("tenant-${tenantId}")
apply(WidgetCreatedEvent(widgetId, name, tenantId))
// Event can be queried by multiple tags
// Consistency checked by queries, not stream locks
```

**Benefits:**
- **Flexibility:** Change boundaries as business evolves
- **Performance:** Parallel processing for unrelated operations
- **Scalability:** No single aggregate bottleneck
- **Evolution:** Adapt without losing historical data

**Current Status:**
- ⚠️ **Experimental** in Axon 5.0 and Axon Server 2025.1
- ⚠️ **Limitations:** No retagging support yet, no automatic migration
- ⚠️ **Not backward compatible** with Axon Server 4.x
- ⚠️ **Cannot mix** DCB contexts with Axon Framework 4.x

**EAF Impact:**
- ✅ **Optional feature** - Can continue using traditional aggregates
- ⚠️ **Experimental** - Not recommended for production initially
- 📋 **Future opportunity** - Evaluate DCB for flexibility needs
- ⚠️ **Breaking change** - Would require significant architectural changes

**Recommendation:** **Defer DCB adoption** until production-ready and stable.

---

### 4.2 Revisited Configuration API

**Problem in Axon 4.x:**
- Configuration complexity with dozens of methods
- "Magic" hidden behind autoconfiguration
- Difficult to understand what's configured
- Monolithic configuration object

**Solution in Axon 5.0:**
- **Segmented configuration** - Focus on specific aspects
- **Explicit registration** - Less magic, more control
- **Modular compartments** - Messaging, automations, command models
- **Builder-based approach** - Fluent, intuitive API

**Configuration Patterns:**

**Annotation-Based (Backward Compatible):**
```kotlin
@Aggregate
class WidgetAggregate {
    @AggregateIdentifier
    lateinit var id: UUID

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        apply(WidgetCreatedEvent(...))
    }

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.id = event.widgetId
    }
}
```

**Builder-Based (New in 5.0):**
```kotlin
// Pure domain model (no annotations)
class WidgetAggregate(val id: UUID, val name: String) {
    companion object {
        fun create(command: CreateWidgetCommand): WidgetAggregate {
            // Validation
            // Return immutable instance
            return WidgetAggregate(command.widgetId, command.name)
        }
    }
}

// Configuration (separate from domain)
configurer.eventSourcing()
    .registerAggregate(WidgetAggregate::class.java)
    .registerCommandHandler(WidgetAggregate::create)
```

**Migration Impact:**
- ✅ **Backward compatible** - Annotations still work
- ✅ **Optional migration** - Can keep existing code
- 🔄 **Configuration changes** - May need to update wiring
- 📚 **Learning curve** - New patterns to understand

**EAF Impact:**
- ⚠️ **Configuration updates required** - Framework module configurations
- ✅ **Minimal code changes** - Can keep annotation-based aggregates
- 📋 **Testing required** - Verify autoconfiguration still works
- 🎯 **Optional improvements** - Consider builder pattern for new aggregates

---

### 4.3 Immutable Entities

**Traditional Approach (Axon 4.x):**
```kotlin
@Aggregate
class WidgetAggregate {
    @AggregateIdentifier
    lateinit var id: UUID
    lateinit var name: String
    var status: WidgetStatus = WidgetStatus.DRAFT

    @CommandHandler
    fun handle(command: ActivateWidgetCommand) {
        // Mutate in place
        apply(WidgetActivatedEvent(id))
    }

    @EventSourcingHandler
    fun on(event: WidgetActivatedEvent) {
        this.status = WidgetStatus.ACTIVE // Mutation
    }
}
```

**Immutable Approach (Axon 5.0):**
```kotlin
data class WidgetAggregate(
    val id: UUID,
    val name: String,
    val status: WidgetStatus
) {
    fun handle(command: ActivateWidgetCommand): WidgetAggregate {
        // Return new instance
        return this.copy(status = WidgetStatus.ACTIVE)
    }
}
```

**Using Java Records:**
```java
record WidgetAggregate(UUID id, String name, WidgetStatus status) {
    WidgetAggregate activate() {
        return new WidgetAggregate(id, name, WidgetStatus.ACTIVE);
    }
}
```

**Benefits:**
1. **Thread Safety** - No concurrent modification issues
2. **Predictability** - State changes are explicit
3. **Testability** - Easier to verify state transitions
4. **IDE Support** - Immutability violations caught at compile time
5. **Functional Programming** - Aligns with FP principles

**EAF Impact:**
- ✅ **Optional feature** - Can continue using mutable aggregates
- 🎯 **Best practice** - Consider for new aggregates
- ⚠️ **Breaking change** - Requires handler signature changes
- 🔄 **Migration effort** - Convert existing aggregates (optional)

**Recommendation:** **Adopt for new code**, keep existing aggregates as-is initially.

---

### 4.4 Async-Native Architecture

**Problem in Axon 4.x:**
- **ThreadLocal-based UnitOfWork** - Incompatible with async frameworks
- Doesn't work with **Project Reactor** (Spring WebFlux)
- Doesn't work with **Kotlin Coroutines**
- Doesn't work with **Java Virtual Threads** (Loom)

**Solution in Axon 5.0:**
- **ProcessingContext** replaces ThreadLocal-based UnitOfWork
- **Async-native** architecture from the ground up
- **Reactive support** - Works with Reactor, Coroutines, Loom
- **Non-blocking** - Improved scalability

**EAF Impact:**
- ✅ **Future-proof** - Ready for reactive if needed
- ✅ **No immediate impact** - EAF uses blocking I/O (by design)
- 📋 **Opportunity** - Could adopt reactive patterns later (Epic 6+)
- ⚠️ **Testing changes** - May affect test lifecycle

**Current EAF Stance (from architecture.md):**
> "Spring WebFlux: Blocking/imperative model (by design)"

**Recommendation:** **No immediate action**, but async-native is ready if needed.

---

### 4.5 Enhanced Testing Support

**New in Axon 5.0:**
- **Testing fixtures** match production configuration
- **Verify configuration** - Test app configured as in production
- **Improved assertions** - Better test failure messages
- **Builder-based tests** - More intuitive test setup

**EAF Impact:**
- 📋 **Test updates required** - Kotest tests may need changes
- ✅ **Better testing** - Enhanced fixtures improve test quality
- 🔍 **Validation needed** - Ensure Kotest + Axon 5 compatibility

---

### 4.6 Update Checker

**What it does:**
- Retrieves available updates for Axon modules
- Identifies known vulnerabilities (CVEs)
- Security-focused proactive monitoring
- Automatic checks during startup (configurable)

**EAF Impact:**
- ✅ **Security improvement** - Aligns with EAF security-first design
- 📋 **Configuration needed** - Enable/disable in production
- 🔍 **CI/CD integration** - Add to quality gates

---

## 5. Breaking Changes from 4.x

### 5.1 Configuration API Changes

**Impact:** 🟡 **MEDIUM**

**Changes:**
- Monolithic Configurer → Segmented compartments
- Some configuration methods renamed or removed
- Different autoconfiguration package structure

**Migration:**
- Review all custom `Configurer` usage
- Update explicit bean definitions
- Test autoconfiguration in integration tests

**EAF Affected Files:**
- `framework/cqrs/src/main/kotlin/.../AxonConfiguration.kt`
- `products/widget-demo/src/main/resources/application.yml`

---

### 5.2 UnitOfWork → ProcessingContext

**Impact:** 🟡 **MEDIUM**

**Changes:**
- ThreadLocal-based UnitOfWork deprecated
- ProcessingContext for async-native support
- Interceptor signatures may change

**Migration:**
- Review custom interceptors
- Update UnitOfWork references
- Test event processor lifecycle

**EAF Affected Files:**
- Custom interceptors (if any in future epics)
- Multi-tenancy context propagation (Story 4.2)

---

### 5.3 TrackingEventProcessor Removed

**Impact:** 🔴 **HIGH** (if used)

**Changes:**
- TrackingEventProcessor removed
- **PooledStreamingEventProcessor** is now default
- Different configuration API

**Migration:**
- Identify TrackingEventProcessor usage
- Migrate to PooledStreamingEventProcessor
- Update processor configuration

**EAF Impact:**
- 🔍 **Verification needed** - Check event processor type in use
- ⚠️ **Potential blocker** - If explicitly using TrackingEventProcessor

---

### 5.4 Serializers → Converters

**Impact:** 🟡 **MEDIUM**

**Changes:**
- Serializer API evolving to Converters
- May affect custom serializers
- Jackson serializer configuration changes

**Migration:**
- Review custom serializer implementations
- Test event serialization/deserialization
- Verify upcasters still work

**EAF Affected Files:**
- `framework/persistence/src/main/kotlin/.../PostgresEventStoreConfiguration.kt`
- Jackson serializer bean definition

---

### 5.5 Package Restructuring (Milestone 4)

**Impact:** 🟡 **MEDIUM**

**Changes:**
- Package names may change
- Import statements need updates
- Potential breaking changes in final cleanup phase

**Migration:**
- **OpenRewrite migration recipes** - Automated refactoring
- Update all Axon imports
- Run Detekt/ktlint after migration

---

### 5.6 Annotation Support Changes

**Impact:** 🟢 **LOW** (backward compatible)

**Changes:**
- Annotations **reintroduced** in Axon 5
- Backward compatible with Axon 4.x annotations
- New class-level annotations available (RC1)

**New Class-Level Annotations:**
```kotlin
@Command
class WidgetCommands {
    fun create(command: CreateWidgetCommand) { }
    fun update(command: UpdateWidgetCommand) { }
}
```

**Migration:**
- ✅ **No changes required** - Existing annotations work
- 🎯 **Optional** - Adopt new class-level annotations for cleaner code

---

## 6. Migration Guidance

### 6.1 Official Migration Status

**Current State:**
- ⚠️ **Full migration guide NOT yet available** (as of RC3)
- 📋 **Examples and test suites** in development
- 🔧 **OpenRewrite migration recipes** on roadmap
- 📚 **Getting Started Guide** available for new projects

**AxonIQ Statement:**
> "One of the biggest concerns for existing users is how to migrate from Axon Framework 4 to 5. The team has been working on examples and test suites that illustrate how to port existing applications, with code remaining the same in many cases if sticking to the annotation-based approach."

### 6.2 Migration Strategy Options

#### Option A: Annotation-Based (Low Risk)

**Approach:**
- Keep existing @Aggregate, @CommandHandler, @EventSourcingHandler
- Update Configuration API only
- Minimal code changes

**Effort:** 🟢 **LOW** (1-2 weeks)
**Risk:** 🟢 **LOW**
**Recommendation:** **Recommended for EAF** (initial migration)

**Steps:**
1. Update Axon Framework version to 5.0.x GA
2. Update Configuration API (AxonConfiguration.kt)
3. Test all CQRS/ES functionality
4. Verify event store compatibility
5. Validate multi-tenancy context propagation

---

#### Option B: Builder-Based (High Risk, High Reward)

**Approach:**
- Migrate to builder-based configuration
- Adopt immutable entities
- Remove framework annotations from domain

**Effort:** 🔴 **HIGH** (4-6 weeks)
**Risk:** 🔴 **HIGH**
**Recommendation:** **Defer** (post-migration evaluation)

**Steps:**
1. Refactor all aggregates to builder pattern
2. Implement immutable entities with data classes
3. Separate domain from framework configuration
4. Extensive testing and validation

---

### 6.3 OpenRewrite Migration Recipes

**What is OpenRewrite:**
- Automated refactoring tool
- Used by Axon Framework for migrations
- Handles breaking changes automatically

**Axon 4 → 5 Recipes:**
- ⚠️ **Not yet available** (in development)
- ✅ **Proven approach** - Used for Axon 4.7 migration
- 📋 **Expected before GA** or shortly after

**Example (Axon 4.6 → 4.7 recipe):**
```yaml
type: specs.openrewrite.org/v1beta/recipe
name: org.axonframework.migration.UpgradeAxonFramework_4_Javax
displayName: Upgrade to Axon Framework 4.x (javax)
description: Upgrade to Axon Framework 4.7 with javax
recipeList:
  - org.openrewrite.java.dependencies.UpgradeDependencyVersion:
      groupId: org.axonframework
      artifactId: *
      newVersion: 4.7.x
```

**EAF Usage:**
- ✅ **High priority** - Wait for OpenRewrite recipes before migration
- 🔧 **Automation** - Reduces manual refactoring effort
- ✅ **Reliability** - Tested migration paths

---

### 6.4 Testing Strategy for Migration

**Phase 1: Unit Tests**
- Run all unit tests with Axon 5
- Fix annotation/configuration issues
- Verify command/event handlers

**Phase 2: Integration Tests**
- Testcontainers with PostgreSQL event store
- Full CQRS/ES flow testing
- Multi-tenancy validation (3 layers)

**Phase 3: Event Store Compatibility**
- Verify existing events can be replayed
- Test snapshots compatibility
- Validate upcasters (if any)

**Phase 4: Performance Validation**
- Command latency benchmarks (target: <200ms)
- Event processor lag (target: <10s)
- No regression from Axon 4.12.1

---

## 7. Impact on EAF Migration Plan

### 7.1 Updated Migration Blocker Status

**Previous Status (Migration Plan v1.0):**
- 🔴 **BLOCKED** - Axon Framework 5 in milestone, no Spring Boot 4 support confirmed

**Updated Status (After Deep Research):**
- 🟡 **PARTIALLY UNBLOCKED** - Axon 5 RC3 available, nearing GA
- 🔴 **STILL BLOCKED** - Spring Boot 4 compatibility NOT confirmed
- ⏳ **Timeline improved** - GA expected late 2025 (weeks, not months)

**Blocker Analysis:**

| Blocker | Status | Impact | Timeline Estimate |
|---------|--------|--------|-------------------|
| **Axon 5 GA Release** | 🟡 **RC3 (weeks away)** | HIGH | **Late Nov - Dec 2025** |
| **Spring Boot 4 Compatibility** | 🔴 **Unknown** | CRITICAL | **Q1-Q2 2026?** |
| **Spring Framework 7 Compatibility** | 🔴 **Unknown** | CRITICAL | **Q1-Q2 2026?** |
| **OpenRewrite Migration Recipes** | 🔴 **Not available** | MEDIUM | **GA or shortly after** |
| **Production Readiness** | 🟡 **RC3 stage** | HIGH | **GA release** |

### 7.2 Revised Migration Timeline Scenarios

#### Scenario A: Axon 5 GA + Spring Boot 3 Only (Most Likely)

**Assumptions:**
- Axon 5.0 GA released December 2025
- Supports Spring Boot 3.x only (not 4.x)
- Spring Boot 4 support added in Axon 5.1 (Q2 2026)

**Timeline:**
1. **Dec 2025:** Axon 5.0 GA released
2. **Jan 2026:** OpenRewrite recipes available
3. **Feb-Mar 2026:** Migrate EAF to Axon 5 + Spring Boot 3.x
4. **Apr-Jun 2026:** Axon 5.1 with Spring Boot 4 support
5. **Jul-Sep 2026:** Migrate EAF to Spring Boot 4

**Total Duration:** 9 months (Dec 2025 - Sep 2026)

---

#### Scenario B: Axon 5 GA + Spring Boot 4 Support (Optimistic)

**Assumptions:**
- Axon 5.0 GA released December 2025
- Includes Spring Boot 4 support from day one
- OpenRewrite recipes available at GA

**Timeline:**
1. **Dec 2025:** Axon 5.0 GA with Spring Boot 4 support
2. **Jan 2026:** OpenRewrite recipes + testing
3. **Feb-Apr 2026:** Migrate EAF to Axon 5 + Spring Boot 4 simultaneously

**Total Duration:** 4 months (Dec 2025 - Apr 2026)

**Likelihood:** 🟢 **LOW** - No evidence of Spring Boot 4 support in current RC

---

#### Scenario C: Axon 5 GA Delayed (Pessimistic)

**Assumptions:**
- Axon 5.0 GA delayed to Q1 2026
- Spring Boot 4 support not included
- Axon 5.1 with Boot 4 support in Q3 2026

**Timeline:**
1. **Feb 2026:** Axon 5.0 GA released
2. **Mar-Apr 2026:** Migrate EAF to Axon 5 + Spring Boot 3.x
3. **Jul-Sep 2026:** Axon 5.1 with Spring Boot 4 support
4. **Oct-Dec 2026:** Migrate EAF to Spring Boot 4

**Total Duration:** 11 months (Feb 2026 - Dec 2026)

**Likelihood:** 🟡 **MEDIUM** - Conservative estimate

---

### 7.3 Recommended Migration Path

**Two-Phase Approach:**

**Phase 1: Axon 5 Migration (Spring Boot 3.x)**
- **Trigger:** Axon 5.0 GA released + OpenRewrite recipes available
- **Timeline:** 6-8 weeks
- **Scope:**
  - Upgrade Axon Framework 4.12.1 → 5.0.x
  - Keep Spring Boot 3.5.x (stable platform)
  - Update Configuration API
  - Validate all CQRS/ES functionality
  - Ensure multi-tenancy still works

**Phase 2: Spring Boot 4 Migration**
- **Trigger:** Axon Framework confirms Spring Boot 4 support
- **Timeline:** 10-12 weeks (from Migration Plan v1.0)
- **Scope:**
  - Spring Boot 3.5.x → 4.0.x
  - Spring Framework 6.2.x → 7.0.x
  - Spring Modulith 1.4.x → 2.0.x
  - All changes from original migration plan

**Benefits:**
- ✅ **Reduced risk** - Two smaller migrations instead of one large
- ✅ **Early Axon 5 adoption** - Get new features sooner
- ✅ **Parallel work** - Can prepare for Spring Boot 4 while on Axon 5
- ✅ **Faster ROI** - Benefit from Axon 5 improvements earlier

**Risks:**
- ⚠️ **Two migrations** - More total effort than combined migration
- ⚠️ **Compatibility** - Potential issues running Axon 5 + Boot 3 then Boot 4
- ⚠️ **Testing overhead** - Full test suite twice

---

### 7.4 Critical Dependencies Update

**Updated Dependency Compatibility Matrix:**

| Dependency | Current (EAF) | Axon 5 + Boot 3 | Axon 5 + Boot 4 | Risk Level |
|------------|---------------|-----------------|-----------------|------------|
| **Axon Framework** | 4.12.1 | 5.0.x GA | 5.0.x or 5.1.x | 🔴 **CRITICAL** |
| **Spring Boot** | 3.5.7 | 3.5.x | 4.0.x | 🔴 **CRITICAL** |
| **Spring Framework** | 6.2.12 | 6.2.x | 7.0.x | 🔴 **CRITICAL** |
| **Spring Modulith** | 1.4.4 | 1.4.x | 2.0.x | 🟡 Medium |
| **Java** | 21 LTS | 21+ | 21+ | ✅ Compatible |
| **Jakarta EE** | 10 | 9+ | 11 | ✅ Compatible |
| **Kotlin** | 2.2.21 | 2.2+ | 2.2+ | ✅ Compatible |
| **jOOQ** | 3.20.8 | 3.20+ | 3.21+ | 🟢 Low |
| **Kotest** | 6.0.4 | 6.0+ | 6.x+ | 🟡 Medium |

---

## 8. Recommendations and Action Items

### 8.1 Primary Recommendation: TWO-PHASE MIGRATION ✅

**Phase 1: Axon 5 Migration (Q1 2026)**
- **When:** Axon 5.0 GA + OpenRewrite recipes available
- **Duration:** 6-8 weeks
- **Scope:** Axon 4.12.1 → 5.0.x (Spring Boot 3.x stable)
- **Priority:** 🟡 **MEDIUM** (wait for GA, not urgent)

**Phase 2: Spring Boot 4 Migration (Q2-Q3 2026)**
- **When:** Axon Framework confirms Spring Boot 4 support
- **Duration:** 10-12 weeks
- **Scope:** Spring Boot 3 → 4, Framework 6 → 7, Modulith 1 → 2
- **Priority:** 🟢 **LOW** (deferred until dependencies ready)

**Rationale:**
1. **Risk reduction** - Smaller, focused migrations
2. **Early value** - Get Axon 5 benefits sooner (DCB, immutability)
3. **Flexibility** - Can adjust Phase 2 timeline based on Axon roadmap
4. **Testing** - More thorough validation at each phase

---

### 8.2 Immediate Action Items (November-December 2025)

#### 8.2.1 Monitoring and Research (Ongoing)

**Weekly Checks:**
- [ ] **AxonIQ Blog** - https://www.axoniq.io/blog/
- [ ] **GitHub Releases** - https://github.com/AxonFramework/AxonFramework/releases
- [ ] **Discuss Forum** - https://discuss.axoniq.io/c/announcements/

**Watch For:**
- ✅ Axon Framework 5.0 GA announcement
- ✅ OpenRewrite migration recipes release
- ✅ Spring Boot 4 compatibility confirmation
- ✅ Migration guide publication

---

#### 8.2.2 Team Preparation (4 weeks effort)

**Training:**
- [ ] Review Axon 5 Getting Started Guide: https://docs.axoniq.io/axon-framework-5-getting-started/
- [ ] Hands-on workshop: Build sample app with Axon 5 RC3
- [ ] Study DCB concept (understand, but don't adopt yet)
- [ ] Review new Configuration API patterns

**Documentation:**
- [ ] Create "Axon 5 Migration Checklist" based on this research
- [ ] Document EAF-specific Axon usage patterns (for migration)
- [ ] Identify all custom Axon configurations in codebase
- [ ] Catalog all command handlers, event handlers, query handlers

---

#### 8.2.3 Codebase Audit (2 weeks effort)

**Axon 4 Usage Inventory:**
- [ ] List all `@Aggregate` classes
- [ ] List all `@CommandHandler` methods
- [ ] List all `@EventSourcingHandler` methods
- [ ] List all `@QueryHandler` methods
- [ ] Identify custom interceptors (if any)
- [ ] Document current event processor type (Tracking vs Streaming)
- [ ] Catalog serializer configuration (Jackson)

**Breaking Change Detection:**
- [ ] Search for `UnitOfWork` usage (deprecated in Axon 5)
- [ ] Search for `TrackingEventProcessor` (removed in Axon 5)
- [ ] Identify custom Configurer usage
- [ ] Check for ThreadLocal usage with Axon

**Search Commands:**
```bash
# Aggregate inventory
grep -r "@Aggregate" --include="*.kt" .

# Command handler inventory
grep -r "@CommandHandler" --include="*.kt" .

# Event sourcing handler inventory
grep -r "@EventSourcingHandler" --include="*.kt" .

# UnitOfWork usage (potential breaking change)
grep -r "UnitOfWork" --include="*.kt" .

# TrackingEventProcessor usage (breaking change)
grep -r "TrackingEventProcessor" --include="*.kt" .
```

---

#### 8.2.4 Test Environment Setup (1 week effort)

**Isolated Axon 5 Environment:**
- [ ] Create `axon5-test` branch
- [ ] Set up Axon 5 RC3 in test environment
- [ ] Deploy sample CQRS application
- [ ] Test with PostgreSQL event store
- [ ] Verify Keycloak integration (JWT still works)
- [ ] Test multi-tenancy enforcement

**Compatibility Validation:**
- [ ] Verify event store schema compatibility (4.x → 5.x)
- [ ] Test event replay with Axon 5
- [ ] Validate snapshots still load
- [ ] Check token store compatibility

---

### 8.3 Phase 1 Planning: Axon 5 Migration (Future)

**Prerequisites:**
- ✅ Axon Framework 5.0 GA released
- ✅ OpenRewrite migration recipes available
- ✅ Team trained on Axon 5 features
- ✅ Codebase audit complete

**Migration Steps (6-8 weeks):**

**Week 1-2: Dependency Updates**
- Update `gradle/libs.versions.toml`: axon-framework = "5.0.x"
- Run OpenRewrite migration recipes
- Fix compilation errors
- Update imports

**Week 3-4: Configuration Migration**
- Migrate AxonConfiguration.kt to new API
- Update Spring Boot autoconfiguration
- Test bean wiring and dependency injection

**Week 5-6: Testing and Validation**
- Run full test suite (unit + integration)
- Validate CQRS/ES flows
- Test multi-tenancy enforcement (3 layers)
- Performance benchmarking

**Week 7-8: Stabilization and Deployment**
- Fix any test failures
- Deploy to staging environment
- Run smoke tests
- Production deployment (blue-green)

---

### 8.4 Phase 2 Planning: Spring Boot 4 Migration (Future)

**Prerequisites:**
- ✅ Axon Framework confirms Spring Boot 4 support
- ✅ Axon 5.x stable on Spring Boot 3 (Phase 1 complete)
- ✅ Spring Boot 4.0 GA released
- ✅ Spring Modulith 2.0 GA released

**Migration Steps:**
- Follow original Spring Boot 4 Migration Plan (docs/spring-boot-4-migration-plan.md)
- All 4 phases (Dependency Updates, Code Migration, Testing, Deployment)
- 10-12 weeks estimated duration

---

### 8.5 Alternative: Stay on Axon 4 + Spring Boot 3 Longer

**If Axon 5 + Spring Boot 4 Support is Delayed Beyond Q2 2026:**

**Option:** Continue with current stack
- Axon Framework 4.12.1 (maintained until Axon 5 mature)
- Spring Boot 3.5.x (OSS support until November 2026)
- Defer both migrations until Axon 5 + Spring Boot 4 confirmed

**Benefits:**
- ✅ **Stability** - Proven, production-ready stack
- ✅ **No migration risk** - Continue delivering features
- ✅ **Cost** - No migration effort required

**Risks:**
- ⚠️ **Tech debt** - Falling behind on latest versions
- ⚠️ **Security** - Missing security patches in newer versions
- ⚠️ **Commercial support** - May need to pay for extended support

**Decision Point:** **Q2 2026** - Re-evaluate if Axon 5 + Boot 4 not available

---

## 9. Appendices

### 9.1 Axon Framework 5 Resources

**Official Documentation:**
- [Axon Framework 5 Getting Started](https://docs.axoniq.io/axon-framework-5-getting-started/)
- [Axon Framework 5 Roadmap](https://discuss.axoniq.io/t/axon-framework-5-roadmap/6032)
- [GitHub Repository](https://github.com/AxonFramework/AxonFramework)
- [Release Notes](https://github.com/AxonFramework/AxonFramework/releases)

**Key Blog Posts:**
- [Announcing Axon Framework 5: Configuring Axon made easy](https://www.axoniq.io/blog/announcing-axon-framework-5-configuring-axon-made-easy)
- [The Release of Axon Framework 5.0](https://www.axoniq.io/blog/release-of-axon-framework-5-0)
- [Dynamic Consistency Boundary (DCB) in Axon Framework 5](https://www.axoniq.io/blog/dcb-in-af-5)
- [AxonIQ 2025 Outlook](https://www.axoniq.io/blog/2025-axoniq-forecast)

**Community:**
- [Discuss Forum](https://discuss.axoniq.io/)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/axon)

---

### 9.2 Axon Framework 5 Feature Summary

| Feature | Description | Status | EAF Impact |
|---------|-------------|--------|------------|
| **Dynamic Consistency Boundary** | Flexible event sourcing without aggregate limitations | Experimental | Optional |
| **Revisited Configuration** | Modular, explicit configuration API | RC3 | Required |
| **Immutable Entities** | Return new instances instead of mutation | RC3 | Optional |
| **Async-Native Architecture** | ProcessingContext replaces ThreadLocal UnitOfWork | RC3 | Low |
| **Enhanced Testing** | Testing fixtures match production config | RC3 | Medium |
| **Update Checker** | Automatic vulnerability and update detection | RC3 | Low |
| **Java 21 Baseline** | Modern Java features (records, pattern matching) | RC3 | None (already 21) |
| **Jakarta Namespace** | jakarta.* by default | RC3 | None (already migrated) |
| **Spring Boot Integration** | Autoconfiguration and starters | Milestone 2 | Critical |

---

### 9.3 Breaking Changes Checklist

**Configuration:**
- [ ] Configurer API usage
- [ ] Bean definitions (CommandGateway, QueryGateway, etc.)
- [ ] Autoconfiguration exclusions

**Processing:**
- [ ] UnitOfWork → ProcessingContext
- [ ] TrackingEventProcessor → PooledStreamingEventProcessor
- [ ] Custom interceptors

**Serialization:**
- [ ] Serializer → Converter migration
- [ ] Jackson configuration
- [ ] Upcasters

**Package Structure:**
- [ ] Import statements (Milestone 4 changes)
- [ ] OpenRewrite recipes

**Testing:**
- [ ] Test fixture setup
- [ ] Kotest integration
- [ ] @SpringBootTest compatibility

---

### 9.4 Axon Framework Version Compatibility Matrix

| Axon Version | Java | Spring Boot | Spring Framework | Jakarta EE | Release Date |
|--------------|------|-------------|------------------|------------|--------------|
| 4.6.x | 8+ | 2.x (javax) | 5.x | - | - |
| 4.7.x | 11+ | 2.x, 3.x | 5.x, 6.x | 9+ | Jan 2023 |
| 4.12.1 | 11+ | 3.x | 6.x | 10 | Jan 2025 |
| 5.0.0-RC3 | 21+ | 3.x (likely) | 6.x (likely) | 9+ | Nov 2025 |
| 5.0.0 GA | 21+ | 3.x? 4.x? | 6.x? 7.x? | 9+, 11? | Late 2025 |

**Legend:**
- ✅ **Confirmed** - Official documentation
- ⚠️ **Likely** - Strong evidence, not confirmed
- ❓ **Unknown** - No information available

---

### 9.5 Decision Matrix: When to Migrate

| Scenario | Axon 5 GA | Boot 4 Support | Action | Timeline |
|----------|-----------|----------------|--------|----------|
| **A** | ✅ Released | ✅ Confirmed | **Proceed** with 2-phase migration | Q1-Q3 2026 |
| **B** | ✅ Released | ❌ Not confirmed | **Phase 1 only** (Axon 5 + Boot 3) | Q1 2026 |
| **C** | ❌ Not released | N/A | **Wait** for GA | Monitor weekly |
| **D** | ✅ Released | ⏳ Delayed (Q3 2026+) | **Stay on current stack** | Re-evaluate Q2 2026 |

---

### 9.6 Contact Points for Axon Support

**Community Support:**
- [Discuss Forum](https://discuss.axoniq.io/) - Q&A, announcements
- [GitHub Issues](https://github.com/AxonFramework/AxonFramework/issues) - Bug reports

**Enterprise Support:**
- [AxonIQ](https://www.axoniq.io/products) - Commercial support plans
- Direct engagement for enterprise customers

**Recommendation:** Engage AxonIQ support for:
1. Spring Boot 4 compatibility roadmap
2. Migration assistance for Axon 5
3. Production support during migration

---

## Document Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-17 | Architecture Team | Initial deep research and compatibility analysis |

---

## Summary and Next Steps

### Key Takeaways

1. ✅ **Axon 5.0 RC3** is available and nearing GA (late 2025)
2. ❌ **Spring Boot 4 compatibility NOT confirmed** - still a blocker
3. ✅ **Java 21 + Jakarta EE** compatible - no issues for EAF
4. 🎯 **Two-phase migration recommended** - Axon 5 first, then Spring Boot 4
5. ⏳ **Timeline improved** - GA weeks away, not months

### Immediate Actions (This Week)

1. **Share this research** with Product Owner and stakeholders
2. **Monitor AxonIQ** for 5.0 GA announcement (weekly)
3. **Begin team training** on Axon 5 features
4. **Start codebase audit** - inventory Axon usage

### Next Milestone

**Trigger:** Axon Framework 5.0 GA released + OpenRewrite recipes available
**Action:** Kick off Phase 1 migration planning (Axon 5 + Spring Boot 3)
**Timeline:** Expected December 2025 - January 2026

**Spring Boot 4 migration remains deferred** until Axon Framework compatibility confirmed.

---

**Document End**
