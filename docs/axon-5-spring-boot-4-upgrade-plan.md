# Axon Framework 5.0 + Spring Boot 4.0 + Spring Framework 7.0 Upgrade Plan

**Status**: EXPERIMENTAL - Major Breaking Changes
**Created**: 2025-11-22
**Target Versions**:
- Axon Framework 4.12.2 → **5.0.0** (MAJOR rewrite)
- Spring Boot 3.5.7 → **4.0.0**
- Spring Framework 6.2.12 → **7.0.1**
- Jackson 2.20.1 → **3.0.0**

**Risk Level**: 🔴 **CRITICAL - Complete API Rewrite**

This document provides a comprehensive upgrade plan for migrating EAF v1.0 to Axon Framework 5.0, Spring Boot 4.0, and Spring Framework 7.0.1. This represents the most significant upgrade in EAF's history.

---

## Executive Summary

### Critical Decision Point

**Axon Framework 5.0** is a complete architectural rewrite, not an incremental upgrade. Almost EVERY API has changed. This is comparable to moving from Axon 3 to Axon 4 in terms of breaking changes.

**Key Compatibility Requirements**:
- ✅ Jackson 3.0 support (CRITICAL for Spring Boot 4.0)
- ✅ Spring Boot 3+ support (we're upgrading to 4.0)
- ✅ JDK 21 (already our baseline)
- ✅ Jakarta instead of Javax (aligns with Spring Boot 4.0)

### Recommendation

**DEFER UNTIL POST-EPIC 10** unless you require immediate Spring Boot 4.0 / Spring Framework 7.0 adoption.

**Rationale**:
1. Axon 5.0 just released GA (2025-11-22) - zero production battle-testing
2. Migration tooling follows in **Axon 5.1.0** (not yet released)
3. 100% of our CQRS/Event Sourcing code requires rewrite
4. Estimated effort: **3-4 weeks** for complete migration
5. Spring Boot 4.0 works fine with Axon 4.x if you stay on Jackson 2.x

**Alternative Path**:
- Stay on Spring Boot 3.5.x + Axon 4.12.2 until Epic 10 complete
- Migrate to Axon 5.0 in Q1 2026 when tooling and patterns mature

---

## Table of Contents

1. [Version Verification](#1-version-verification)
2. [Breaking Changes Overview](#2-breaking-changes-overview)
3. [Jackson 3.0 Compatibility](#3-jackson-30-compatibility)
4. [Migration Strategy](#4-migration-strategy)
5. [Code Impact Assessment](#5-code-impact-assessment)
6. [Step-by-Step Migration Guide](#6-step-by-step-migration-guide)
7. [Testing Strategy](#7-testing-strategy)
8. [Rollback Plan](#8-rollback-plan)
9. [Timeline and Effort](#9-timeline-and-effort)
10. [References](#10-references)

---

## 1. Version Verification

### Dependency Versions (All Verified 2025-11-22)

| Dependency | Current | Target | Status | Notes |
|------------|---------|--------|--------|-------|
| Axon Framework | 4.12.2 | **5.0.0** | ⚠️ MAJOR | Complete API rewrite |
| Spring Boot | 3.5.7 | **4.0.0** | ✅ GA | Maven Central |
| Spring Framework | 6.2.12 | **7.0.1** | ✅ GA | Maven Central |
| Spring Modulith | 1.4.4 | **2.0.0** | ✅ GA | Aligns with Boot 4.0 |
| Jackson | 2.20.1 | **3.0.0** | ✅ REQUIRED | Spring Boot 4.0 baseline |
| JDK | 21 LTS | **21 LTS** | ✅ OK | No change needed |
| Kotlin | 2.2.21 | **2.2.21** | ✅ OK | Compatible |
| JUnit | 6.0.1 | **6.0.1** | ✅ OK | Already upgraded |

### Compatibility Matrix

```
Axon 5.0.0 Requirements:
- JDK 21+ ✅
- Spring Boot 3+ ✅ (we're using 4.0)
- Spring Framework 6+ ✅ (we're using 7.0.1)
- Jakarta Persistence ✅ (Spring Boot 4.0 provides)
- Jackson 3.0 ✅ (Spring Boot 4.0 requires)

EAF Stack Compatibility: ✅ ALL ALIGNED
```

---

## 2. Breaking Changes Overview

### 2.1 Axon Framework 5.0 - COMPLETE API REWRITE

**Impact**: 🔴 **CRITICAL** - Nearly 100% of Axon code requires changes

#### Category 1: Core Architecture Changes (MAJOR)

1. **Unit of Work - Complete Redesign**
   - `UnitOfWork` interface fundamentally changed
   - `ThreadLocal` usage completely eliminated
   - **BREAKING**: `start()`, `commit()`, `rollback()` removed
   - **BREAKING**: Nesting (`parent()`, `root()`) removed
   - **NEW**: `ProcessingContext` replaces direct UnitOfWork access
   - **IMPACT**: All message handlers must accept `ProcessingContext` parameter

2. **Async-Native Architecture**
   - All APIs now asynchronous by default
   - `CompletableFuture` everywhere instead of blocking calls
   - **BREAKING**: `AsynchronousCommandBus` removed (default now)
   - **BREAKING**: `DisruptorCommandBus` removed
   - **IMPACT**: All command/query dispatch code must use futures

3. **Message Type System - QualifiedName**
   - **BREAKING**: Messages no longer use Java FQCN for identification
   - **NEW**: `QualifiedName` + `MessageType` (name + version)
   - **BREAKING**: All subscriptions now use `QualifiedName` instead of `String`
   - **IMPACT**: Command/Query/Event handlers require annotation updates

4. **MessageStream Introduction**
   - **BREAKING**: All handlers return `MessageStream` (not direct values)
   - Supports 0, 1, or N responses uniformly
   - **IMPACT**: All `@CommandHandler`, `@QueryHandler`, `@EventHandler` return types change

5. **Dynamic Consistency Boundary (DCB)**
   - Events attach to **multiple tags** instead of single aggregate
   - **BREAKING**: `EventStore#readEvents(String aggregateIdentifier)` removed
   - **NEW**: `EventCriteria` + `EventStoreTransaction` API
   - **IMPACT**: All event sourcing code requires rewrite

#### Category 2: Aggregate → Entity Terminology (MAJOR)

1. **Terminology Change**
   - "Aggregate" renamed to "Entity" throughout
   - **BREAKING**: `@Aggregate` → `@EventSourced` (Spring detection)
   - **BREAKING**: `AggregateTestFixture` → `AxonTestFixture`
   - **BREAKING**: `AggregateEntityNotFoundException` → `ChildEntityNotFoundException`

2. **Immutable Entities**
   - **NEW**: Entities can be immutable (Java records, Kotlin data classes)
   - Event sourcing handlers return new instances instead of mutating
   - **IMPACT**: Can modernize aggregates to use Kotlin data classes

3. **Declarative Modeling**
   - **NEW**: `EntityMetamodel.forEntityType()` explicit configuration
   - Reflection-based still available via `AnnotatedEntityMetamodel`

#### Category 3: Configuration API Overhaul (MAJOR)

1. **Dependency Inversion**
   - Configuration API "turned upside down"
   - Modules provide own configurers instead of centralized dependency
   - **BREAKING**: All `Configurer` methods changed

2. **New Structure**
   ```kotlin
   ApplicationConfigurer    // Basic operations only
   ├─ MessagingConfigurer  // Messaging concerns
   ├─ ModellingConfigurer  // Modeling layer
   └─ EventSourcingConfigurer  // Event sourcing layer
   ```

3. **Registration Changes**
   - **BREAKING**: `@StartHandler` / `@ShutdownHandler` removed
   - **NEW**: `ComponentDefinition` with `onStart()` / `onShutdown()`
   - **BREAKING**: `ConfigurerModule` → `ConfigurationEnhancer`

#### Category 4: Serialization → Conversion (MAJOR)

1. **Serializer Removal**
   - **BREAKING**: `Serializer` interface completely removed
   - **BREAKING**: XStream serializer removed
   - **BREAKING**: Java serialization support removed
   - **NEW**: `Converter` interface (existing since AF3)
   - **NEW**: `JacksonConverter` as default (Jackson 3.0 compatible!)

2. **RevisionResolver Removal**
   - **BREAKING**: `RevisionResolver` and `@Revision` removed
   - **NEW**: `MessageTypeResolver` with `@Command`, `@Event`, `@Query` annotations
   - Annotations now have `version` field instead

3. **Metadata Changes**
   - **BREAKING**: Metadata changed from `Map<String, ?>` to `Map<String, String>`
   - All values must be strings now

#### Category 5: Event Processing Changes (MAJOR)

1. **TrackingEventProcessor Removal**
   - **BREAKING**: `TrackingEventProcessor` removed
   - **NEW**: `PooledStreamingEventProcessor` as default
   - Benefits: Lower IO, more parallelism, single thread pool

2. **ProcessingGroup Layer Removed**
   - **BREAKING**: Register handlers directly to event processors
   - No more implicit processing group discovery
   - **NEW**: Explicit registration via `EventProcessorModule.pooledStreaming()`

3. **SequencingPolicy Changes**
   - **BREAKING**: Default now `SequentialPolicy` (instead of aggregate-based)
   - **NEW**: `@SequencingPolicy` annotation for custom ordering

#### Category 6: Message API Changes (HIGH)

1. **Method Renames**
   - `getIdentifier()` → `identifier()`
   - `getPayload()` → `payload()`
   - `getPayloadType()` → `payloadType()`
   - `getMetaData()` → `metadata()`
   - `getTimestamp()` → `timestamp()`

2. **Factory Method Removal**
   - **BREAKING**: `GenericMessage#asMessage(Object)` removed
   - Use constructors directly

3. **Serialization Method Removal**
   - **BREAKING**: `Message#serializePayload()` removed
   - **BREAKING**: `Message#serializeMetaData()` removed
   - **NEW**: `Message#payloadAs(Type, Converter)`
   - **NEW**: `Message#withConvertedPayload(Type, Converter)`

#### Category 7: Command/Query Dispatch Changes (HIGH)

1. **CommandBus Changes**
   - **BREAKING**: `CommandCallback` completely removed
   - **BREAKING**: All commands return `CompletableFuture`
   - **BREAKING**: Subscription signature changed to use `QualifiedName`
   - **NEW**: `CommandDispatcher` for in-handler dispatches

2. **QueryBus Changes**
   - **BREAKING**: Scatter-Gather removed (limited use)
   - **BREAKING**: `ResponseType` eliminated
   - **BREAKING**: Only ONE handler per query/response name
   - **NEW**: `QueryDispatcher` for in-handler dispatches

3. **Gateway Changes**
   - **BREAKING**: Require `ProcessingContext` for in-handler dispatches
   - **BREAKING**: Timeout options removed from `sendAndWait`
   - **NEW**: `send`/`sendAndWait` methods expect `Class` parameter

#### Category 8: Event Store Changes (HIGH)

1. **JPA Event Store**
   - **BREAKING**: `JpaEventStorageEngine` → `AggregateBasedJpaEventStorageEngine`
   - **BREAKING**: `DomainEventEntry` → `AggregateBasedJpaEntry`
   - Constructor pattern changed

2. **Axon Server Event Store**
   - **BREAKING**: `AxonServerEventStore` removed
   - **NEW**: `AggregateBasedAxonServerEventStorageEngine` (aggregate-based)
   - **NEW**: `AxonServerEventStorageEngine` (DCB-based, default)

3. **Transaction Model**
   - **BREAKING**: All methods return `CompletableFuture`
   - **NEW**: `EventStoreTransaction` with active `ProcessingContext`

#### Category 9: Testing Changes (MEDIUM)

1. **Test Fixture Replacement**
   - **BREAKING**: `AggregateTestFixture` deprecated
   - **BREAKING**: `SagaTestFixture` deprecated
   - **NEW**: `AxonTestFixture` (unified, configuration-based)
   - Given-when-then still supported

2. **Configuration Consistency**
   - Tests now use same configuration as production
   - Better integration testing support

#### Category 10: Interceptor Changes (MEDIUM)

1. **Interface Changes**
   - **BREAKING**: Accept `ProcessingContext` instead of `UnitOfWork`
   - **BREAKING**: Return `MessageStream` instead of direct results
   - **NEW**: Can execute before AND after chain invocation

2. **Removed Classes**
   - `MessageDispatchInterceptorSupport`
   - `MessageHandlerInterceptorSupport`
   - `EventLoggingInterceptor`
   - `TransactionManagingInterceptor` → `TransactionalUnitOfWorkFactory`

3. **Registration Changes**
   - Interceptors now registered via `ApplicationConfigurer`
   - Spring Boot: provide as beans for auto-configuration

#### Category 11: Removed Features

- **Conflict Resolution**: Removed entirely
- **Target Aggregate Versioning**: Removed entirely
- **Dynamic Proxies**: Command and Query handler proxies removed
- **Lifecycle Interface**: Removed (replaced by component lifecycle)

### 2.2 Spring Boot 4.0 Breaking Changes

(See `docs/spring-boot-4-upgrade-plan.md` for full details)

**Key Changes**:
- Jackson 3.0 mandatory (package rename: `com.fasterxml.jackson` → `tools.jackson`)
- Jakarta EE 11 baseline (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- JSpecify null safety (replaces Spring's JSR-305)
- Property changes (tracing, persistence, actuator)
- @Nested test class behavior changes

### 2.3 Spring Framework 7.0 Breaking Changes

(See `docs/spring-boot-4-upgrade-plan.md` for full details)

**Key Changes**:
- Method validation on interfaces enabled by default
- Parameter name discovery changes
- RestClient as successor to RestTemplate
- Virtual threads support (JDK 21)
- JMX disabled by default

---

## 3. Jackson 3.0 Compatibility

### 3.1 Why Jackson 3.0 is Critical

**Dependency Chain**:
```
Spring Boot 4.0 REQUIRES Jackson 3.0
     ↓
Axon Framework 4.x uses XStream/Jackson 2.x serialization
     ↓
INCOMPATIBLE ❌
     ↓
Axon Framework 5.0 uses JacksonConverter with Jackson 3.0
     ↓
COMPATIBLE ✅
```

**Conclusion**: Cannot use Spring Boot 4.0 with Axon Framework 4.x due to Jackson version conflict.

### 3.2 Axon 5.0 Jackson 3.0 Support

**Confirmed**:
- ✅ `JacksonConverter` is new default serializer (replacing XStream)
- ✅ Supports Jackson 3.0 package namespace (`tools.jackson`)
- ✅ `Serializer` interface removed (no legacy Jackson 2.x dependencies)
- ✅ Converter-based architecture (clean separation)

**EAF Impact**:
- ✅ All 10 Jackson package migrations already completed (see `docs/spring-boot-4-migration-status.md`)
- ✅ Event serialization will use Jackson 3.0 automatically
- ✅ No XStream migration needed (we never used it)

---

## 4. Migration Strategy

### 4.1 Recommended Approach: DEFER

**Option 1: Stay on Current Stack (RECOMMENDED)**
```
Spring Boot 3.5.x + Axon 4.12.2 + Jackson 2.x
- Stable, proven, production-ready
- Complete Epic 10 without disruption
- Migrate to Axon 5.0 in Q1 2026 when tooling matures
```

**Option 2: Upgrade to Spring Boot 4.0 Only**
```
Spring Boot 4.0 + Axon 4.12.2 + Jackson 2.x
- BLOCKED: Jackson version conflict
- Spring Boot 4.0 requires Jackson 3.0
- Axon 4.12.2 requires Jackson 2.x
- NOT VIABLE ❌
```

**Option 3: Full Upgrade (EXPERIMENTAL)**
```
Spring Boot 4.0 + Axon 5.0.0 + Jackson 3.0
- CRITICAL: Complete API rewrite
- Estimated 3-4 weeks migration effort
- Zero production battle-testing (released 2025-11-22)
- Migration tooling follows in Axon 5.1.0
- HIGH RISK for Epic 10 timeline
```

### 4.2 If You Choose Option 3 (Full Upgrade)

#### Prerequisites

1. ✅ Complete all Epic 10 stories FIRST
2. ✅ Establish comprehensive test coverage baseline
3. ✅ Create feature branch isolation
4. ✅ Allocate 3-4 week dedicated effort
5. ✅ Wait for Axon 5.1.0 migration tooling (Q1 2026)

#### Migration Phases

**Phase 1: Preparation (Week 1)**
- Upgrade to latest Axon 4.x (4.12.2)
- Address ALL deprecation warnings
- Document current Axon usage patterns
- Establish test baseline (all tests passing)

**Phase 2: Dependency Upgrade (Week 1)**
- Update version catalog (Axon 5.0.0, Spring Boot 4.0.0, Spring Framework 7.0.1)
- Update Jackson 3.0 (already completed)
- Update Jakarta dependencies
- Resolve compilation errors

**Phase 3: Core API Migration (Week 2)**
- Migrate configuration to `ApplicationConfigurer` API
- Update all `@Aggregate` → `@EventSourced`
- Migrate `UnitOfWork` usage to `ProcessingContext`
- Update message handler signatures (return `MessageStream`)
- Update command/query dispatch to async APIs

**Phase 4: Event Store Migration (Week 2)**
- Migrate JPA event store to `AggregateBasedJpaEventStorageEngine`
- Update event reading to use `EventCriteria`
- Test event sourcing with new transaction model

**Phase 5: Event Processing Migration (Week 3)**
- Migrate `TrackingEventProcessor` → `PooledStreamingEventProcessor`
- Update event handler registration
- Configure sequencing policies

**Phase 6: Testing Migration (Week 3)**
- Migrate `AggregateTestFixture` → `AxonTestFixture`
- Update all given-when-then tests
- Verify test coverage maintained

**Phase 7: Validation & Stabilization (Week 4)**
- Full regression testing
- Performance benchmarking
- Axon-specific integration tests
- Production readiness review

---

## 5. Code Impact Assessment

### 5.1 EAF Modules Affected

| Module | Axon Usage | Impact Level | Estimated Effort |
|--------|------------|--------------|------------------|
| `framework/cqrs` | HIGH | 🔴 CRITICAL | 5-7 days |
| `framework/persistence` | HIGH | 🔴 CRITICAL | 3-4 days |
| `products/widget-demo` | HIGH | 🔴 CRITICAL | 4-5 days |
| `shared/shared-api` | MEDIUM | 🟡 HIGH | 2-3 days |
| `shared/testing` | MEDIUM | 🟡 HIGH | 2-3 days |
| `framework/web` | LOW | 🟢 MEDIUM | 1 day |

**Total Estimated Effort**: **17-27 days** (3-4 weeks with 1 developer)

### 5.2 File-Level Impact Analysis

#### Aggregates (→ Entities)

**Files Requiring Changes**: ~15 files
```
products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/domain/Widget.kt
- @Aggregate → @EventSourced
- @AggregateIdentifier unchanged
- @CommandHandler → return MessageStream
- @EventSourcingHandler → may return new instance (immutable pattern)
- apply() → unchanged (but async under the hood)
```

#### Command Handlers

**Files Requiring Changes**: ~20 files
```kotlin
// BEFORE (Axon 4.x)
@CommandHandler
fun handle(command: CreateWidgetCommand): String {
    return widgetId.toString()
}

// AFTER (Axon 5.0)
@CommandHandler
fun handle(command: CreateWidgetCommand, context: ProcessingContext): MessageStream<String> {
    return MessageStream.just(widgetId.toString())
}
```

#### Event Handlers

**Files Requiring Changes**: ~30 files
```kotlin
// BEFORE (Axon 4.x)
@EventHandler
fun on(event: WidgetCreatedEvent) {
    // Update projection
}

// AFTER (Axon 5.0)
@EventHandler
fun on(event: WidgetCreatedEvent, context: ProcessingContext): MessageStream<Void> {
    // Update projection
    return MessageStream.empty()
}
```

#### Query Handlers

**Files Requiring Changes**: ~10 files
```kotlin
// BEFORE (Axon 4.x)
@QueryHandler
fun handle(query: FindWidgetQuery): WidgetSummary? {
    return widgetRepository.findById(query.widgetId)
}

// AFTER (Axon 5.0)
@QueryHandler
fun handle(query: FindWidgetQuery, context: ProcessingContext): MessageStream<WidgetSummary> {
    val result = widgetRepository.findById(query.widgetId)
    return result?.let { MessageStream.just(it) } ?: MessageStream.empty()
}
```

#### Configuration

**Files Requiring Changes**: ~5 files
```
framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/CqrsAutoConfiguration.kt
- Complete rewrite to use ApplicationConfigurer
- Update component registration
- Migrate interceptor registration
```

#### Event Store Configuration

**Files Requiring Changes**: ~3 files
```
framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/eventstore/PostgresEventStoreConfiguration.kt
- JpaEventStorageEngine → AggregateBasedJpaEventStorageEngine
- Update serializer → converter
- Configure Jackson 3.0 converter
```

#### Test Fixtures

**Files Requiring Changes**: ~25 test files
```kotlin
// BEFORE (Axon 4.x)
private val fixture = AggregateTestFixture(Widget::class.java)

fixture.givenNoPriorActivity()
    .when(CreateWidgetCommand(...))
    .expectEvents(WidgetCreatedEvent(...))

// AFTER (Axon 5.0)
private val fixture = AxonTestFixture(
    ApplicationConfigurer.create()
        .eventSourcing { config ->
            config.registerEntity(Widget::class.java)
        }
)

fixture.givenNoPriorActivity()
    .when(CreateWidgetCommand(...))
    .expectEvents(WidgetCreatedEvent(...))
```

### 5.3 Breaking Changes by Category

| Category | Files Affected | Complexity | Risk |
|----------|----------------|------------|------|
| Aggregate annotations | 15 | LOW | 🟢 |
| Command handlers | 20 | MEDIUM | 🟡 |
| Event handlers | 30 | MEDIUM | 🟡 |
| Query handlers | 10 | MEDIUM | 🟡 |
| Configuration | 5 | HIGH | 🔴 |
| Event store | 3 | HIGH | 🔴 |
| Test fixtures | 25 | HIGH | 🔴 |
| Interceptors | 5 | MEDIUM | 🟡 |
| Event processing | 10 | HIGH | 🔴 |

---

## 6. Step-by-Step Migration Guide

### Step 1: Update Version Catalog

```toml
# gradle/libs.versions.toml
axon = "5.0.0"            # MAJOR upgrade from 4.12.2
spring-boot = "4.0.0"     # GA (released 2025-11-20)
spring-framework = "7.0.1" # Latest patch (released 2025-11-21)
spring-modulith = "2.0.0" # GA (released 2025-11-21)
jackson = "3.0.0"         # MAJOR upgrade from 2.x
```

### Step 2: Update Dependency Declarations

```kotlin
// No changes needed - version catalog handles it
dependencies {
    implementation(libs.bundles.axon.framework)  // Now 5.0.0
    implementation(libs.bundles.spring.boot.web) // Now 4.0.0
}
```

### Step 3: Update Aggregate Annotations

```kotlin
// File: products/widget-demo/src/main/kotlin/com/axians/eaf/products/widget/domain/Widget.kt

// BEFORE
@Aggregate
class Widget {
    @AggregateIdentifier
    private lateinit var widgetId: WidgetId

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        apply(WidgetCreatedEvent(...))
    }
}

// AFTER
@EventSourced  // Changed from @Aggregate
class Widget {
    @AggregateIdentifier  // Unchanged
    private lateinit var widgetId: WidgetId

    @CommandHandler
    @EntityCreator  // NEW: Mark creational handler
    constructor(command: CreateWidgetCommand, context: ProcessingContext) {  // Add ProcessingContext
        apply(WidgetCreatedEvent(...))
    }
}
```

### Step 4: Update Command Handlers

```kotlin
// BEFORE (Axon 4.x)
@CommandHandler
fun handle(command: UpdateWidgetCommand) {
    apply(WidgetUpdatedEvent(...))
}

// AFTER (Axon 5.0)
@CommandHandler
fun handle(command: UpdateWidgetCommand, context: ProcessingContext): MessageStream<Void> {
    apply(WidgetUpdatedEvent(...))
    return MessageStream.empty()
}
```

### Step 5: Update Event Sourcing Handlers

```kotlin
// Option 1: Mutable (traditional)
@EventSourcingHandler
fun on(event: WidgetCreatedEvent) {
    this.widgetId = event.widgetId
    this.name = event.name
}

// Option 2: Immutable (Kotlin data class)
@EventSourcingHandler
fun on(event: WidgetCreatedEvent): Widget {
    return copy(
        widgetId = event.widgetId,
        name = event.name
    )
}
```

### Step 6: Update Event Handlers (Projections)

```kotlin
// BEFORE (Axon 4.x)
@EventHandler
fun on(event: WidgetCreatedEvent) {
    val summary = WidgetSummary(event.widgetId, event.name)
    widgetSummaryRepository.save(summary)
}

// AFTER (Axon 5.0)
@EventHandler
fun on(event: WidgetCreatedEvent, context: ProcessingContext): MessageStream<Void> {
    val summary = WidgetSummary(event.widgetId, event.name)
    widgetSummaryRepository.save(summary)
    return MessageStream.empty()
}
```

### Step 7: Update Query Handlers

```kotlin
// BEFORE (Axon 4.x)
@QueryHandler
fun handle(query: FindWidgetQuery): WidgetSummary? {
    return widgetSummaryRepository.findById(query.widgetId)
}

// AFTER (Axon 5.0)
@QueryHandler
fun handle(query: FindWidgetQuery, context: ProcessingContext): MessageStream<WidgetSummary> {
    val result = widgetSummaryRepository.findById(query.widgetId)
    return result?.let { MessageStream.just(it) } ?: MessageStream.empty()
}
```

### Step 8: Update Command Dispatch

```kotlin
// BEFORE (Axon 4.x)
val result: CompletableFuture<String> = commandGateway.send(CreateWidgetCommand(...))

// AFTER (Axon 5.0) - Inside handler
val result: CompletableFuture<String> = commandDispatcher.dispatch(
    CreateWidgetCommand(...),
    context
)

// AFTER (Axon 5.0) - Outside handler
val result: CompletableFuture<String> = commandGateway.send(CreateWidgetCommand(...))
```

### Step 9: Update Query Dispatch

```kotlin
// BEFORE (Axon 4.x)
val result: CompletableFuture<WidgetSummary> = queryGateway.query(
    FindWidgetQuery(widgetId),
    ResponseTypes.instanceOf(WidgetSummary::class.java)
)

// AFTER (Axon 5.0) - Inside handler
val stream: MessageStream<WidgetSummary> = queryDispatcher.dispatch(
    FindWidgetQuery(widgetId),
    context
)
val result: CompletableFuture<WidgetSummary> = stream.asCompletableFuture()

// AFTER (Axon 5.0) - Outside handler
val stream: MessageStream<WidgetSummary> = queryGateway.query(
    FindWidgetQuery(widgetId),
    WidgetSummary::class.java
)
val result: CompletableFuture<WidgetSummary> = stream.asCompletableFuture()
```

### Step 10: Update Configuration

```kotlin
// BEFORE (Axon 4.x)
@Configuration
class AxonConfiguration {
    @Bean
    fun configurer(): Configurer {
        return DefaultConfigurer.defaultConfiguration()
            .eventProcessing { config ->
                config.usingTrackingEventProcessors()
            }
    }
}

// AFTER (Axon 5.0)
@Configuration
class AxonConfiguration {
    @Bean
    fun configurer(): ApplicationConfigurer {
        return MessagingConfigurer.create()
            .eventProcessing { processors ->
                processors.pooledStreaming("widget-projection")
                    .eventHandlingComponents { components ->
                        components.autodetected(WidgetProjection::class.java)
                    }
            }
    }
}
```

### Step 11: Update Event Store Configuration

```kotlin
// BEFORE (Axon 4.x)
@Bean
fun eventStorageEngine(
    entityManagerProvider: EntityManagerProvider,
    transactionManager: TransactionManager
): EventStorageEngine {
    return JpaEventStorageEngine.builder()
        .snapshotSerializer(jacksonSerializer())
        .eventSerializer(jacksonSerializer())
        .entityManagerProvider(entityManagerProvider)
        .transactionManager(transactionManager)
        .build()
}

// AFTER (Axon 5.0)
@Bean
fun eventStorageEngine(
    entityManagerProvider: EntityManagerProvider,
    transactionManager: TransactionManager,
    converter: Converter
): EventStorageEngine {
    val config = AggregateBasedJpaEventStorageEngineConfiguration.builder()
        .converter(converter)  // JacksonConverter with Jackson 3.0
        .entityManagerProvider(entityManagerProvider)
        .transactionManager(transactionManager)
        .build()

    return AggregateBasedJpaEventStorageEngine(config)
}

@Bean
fun jacksonConverter(objectMapper: ObjectMapper): JacksonConverter {
    return JacksonConverter(objectMapper)
}
```

### Step 12: Update Test Fixtures

```kotlin
// BEFORE (Axon 4.x)
class WidgetTest {
    private val fixture = AggregateTestFixture(Widget::class.java)

    @Test
    fun `should create widget`() {
        fixture.givenNoPriorActivity()
            .when(CreateWidgetCommand(widgetId, "Test Widget"))
            .expectEvents(WidgetCreatedEvent(widgetId, "Test Widget"))
    }
}

// AFTER (Axon 5.0)
class WidgetTest {
    private val fixture = AxonTestFixture(
        EventSourcingConfigurer.create()
            .configureEntity { config ->
                config.registerEntity(Widget::class.java)
            }
    )

    @Test
    fun `should create widget`() {
        fixture.givenNoPriorActivity()
            .when(CreateWidgetCommand(widgetId, "Test Widget"))
            .expectEvents(WidgetCreatedEvent(widgetId, "Test Widget"))
    }
}
```

### Step 13: Update Interceptors

```kotlin
// BEFORE (Axon 4.x)
class TenantValidationInterceptor : MessageHandlerInterceptor<CommandMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any {
        val tenant = TenantContext.getCurrentTenantId()
        // Validation logic
        return interceptorChain.proceed()
    }
}

// AFTER (Axon 5.0)
class TenantValidationInterceptor : MessageHandlerInterceptor<CommandMessage<*>> {
    override fun handle(
        message: CommandMessage<*>,
        context: ProcessingContext,
        chain: MessageHandlerInterceptorChain
    ): MessageStream<*> {
        val tenant = TenantContext.getCurrentTenantId()
        // Validation logic
        return chain.proceed(message, context)
    }
}
```

### Step 14: Update Message Annotations

```kotlin
// BEFORE (Axon 4.x)
@Revision("1.0")
data class WidgetCreatedEvent(...)

// AFTER (Axon 5.0)
@Event(version = "1.0")  // Changed from @Revision
data class WidgetCreatedEvent(...)
```

```kotlin
// Commands
@Command(version = "1.0")
data class CreateWidgetCommand(...)

// Queries
@Query(version = "1.0")
data class FindWidgetQuery(...)
```

### Step 15: Update Event Processing Configuration

```kotlin
// BEFORE (Axon 4.x)
@ProcessingGroup("widget-projection")
@Component
class WidgetProjection {
    @EventHandler
    fun on(event: WidgetCreatedEvent) { ... }
}

// AFTER (Axon 5.0) - Direct registration
@Configuration
class EventProcessingConfiguration {
    @Bean
    fun eventProcessing(): EventProcessorModule {
        return EventProcessorModule.pooledStreaming("widget-projection")
            .eventHandlingComponents { components ->
                components.declarative(WidgetProjection::class.java)
            }
    }
}

@Component  // No @ProcessingGroup annotation
class WidgetProjection {
    @EventHandler
    fun on(event: WidgetCreatedEvent, context: ProcessingContext): MessageStream<Void> {
        ...
        return MessageStream.empty()
    }
}
```

---

## 7. Testing Strategy

### 7.1 Pre-Migration Test Baseline

**Objective**: Establish comprehensive passing test baseline before migration

1. **Run full test suite** (all tests must pass)
   ```bash
   ./gradlew clean test integrationTest
   ```

2. **Document test counts**
   - Unit tests: ~XXX
   - Integration tests: ~XXX
   - Axon-specific tests: ~XXX

3. **Capture coverage baseline**
   ```bash
   ./gradlew koverHtmlReport
   ```

4. **Performance baseline**
   - Command processing p95 latency
   - Event processor lag
   - Projection consistency time

### 7.2 Migration Testing Phases

#### Phase 1: Compilation Testing
- Fix all compilation errors
- Resolve all type errors
- Update all imports

#### Phase 2: Unit Testing
- Migrate all `AggregateTestFixture` tests
- Update all given-when-then scenarios
- Verify business logic unchanged

#### Phase 3: Integration Testing
- Test event store read/write
- Test command dispatch
- Test query dispatch
- Test event processing
- Test projections

#### Phase 4: Axon-Specific Testing
- Test aggregate lifecycle
- Test saga lifecycle (if used)
- Test snapshot functionality
- Test event upcasting (if used)
- Test deadline management (if used)

#### Phase 5: Multi-Tenancy Testing
- Test tenant context propagation
- Test tenant isolation in event store
- Test tenant validation interceptors

#### Phase 6: Performance Testing
- Compare command latency (p50, p95, p99)
- Compare event processor lag
- Compare projection consistency
- Verify no regressions

### 7.3 Acceptance Criteria

- ✅ All tests passing (100% parity with baseline)
- ✅ Code coverage ≥ baseline (85%+ line coverage)
- ✅ Mutation coverage ≥ baseline (60-70%)
- ✅ Performance within ±10% of baseline
- ✅ Zero deprecation warnings
- ✅ All Konsist architecture tests passing

---

## 8. Rollback Plan

### 8.1 Git Strategy

**Feature Branch Isolation**:
```bash
# Create feature branch from main
git checkout -b experimental/axon-5-spring-boot-4-upgrade main

# Work in isolation
# ...

# If migration fails, abandon branch
git checkout main
git branch -D experimental/axon-5-spring-boot-4-upgrade
```

### 8.2 Rollback Triggers

Abort migration if:
1. ❌ Migration effort exceeds 4 weeks
2. ❌ Critical bugs discovered in Axon 5.0.0
3. ❌ Performance regressions >20%
4. ❌ Cannot achieve test parity
5. ❌ Epic 10 timeline at risk

### 8.3 Rollback Procedure

1. **Abandon feature branch**
2. **Revert to main branch**
3. **No production impact** (isolated work)
4. **Document lessons learned**
5. **Re-evaluate in Q1 2026** (when Axon 5.1.0 available)

---

## 9. Timeline and Effort

### 9.1 Estimated Effort (Option 3: Full Upgrade)

| Phase | Duration | Resources | Risk |
|-------|----------|-----------|------|
| Preparation | 3 days | 1 dev | 🟢 LOW |
| Dependency Upgrade | 2 days | 1 dev | 🟡 MEDIUM |
| Core API Migration | 5 days | 1 dev | 🔴 HIGH |
| Event Store Migration | 3 days | 1 dev | 🔴 HIGH |
| Event Processing Migration | 3 days | 1 dev | 🔴 HIGH |
| Testing Migration | 4 days | 1 dev | 🔴 HIGH |
| Validation & Stabilization | 5 days | 1 dev | 🟡 MEDIUM |
| **Total** | **25 days** | **1 dev** | 🔴 **HIGH** |

**Calendar Time**: 5 weeks (with buffer)

### 9.2 Effort Comparison

| Upgrade Scope | Effort | Risk | Benefit |
|---------------|--------|------|---------|
| Spring Boot 4.0 only | BLOCKED | N/A | Modern stack |
| Axon 5.0 only | 3 weeks | 🔴 HIGH | Jackson 3.0 |
| Full stack upgrade | **4 weeks** | 🔴 **CRITICAL** | Complete modernization |
| Defer to Q1 2026 | 0 weeks | 🟢 LOW | Epic 10 focus |

### 9.3 Recommended Timeline

**If choosing Option 1 (DEFER)**:
- Q4 2025: Complete Epic 10 on stable stack
- Q1 2026: Wait for Axon 5.1.0 with migration tooling
- Q2 2026: Execute migration with mature ecosystem

**If choosing Option 3 (Full Upgrade)**:
- Week 1: Preparation + dependency upgrade
- Week 2-3: Core API + event store migration
- Week 4: Testing migration
- Week 5: Validation + stabilization

---

## 10. References

### Official Documentation

- [Axon Framework 5.0.0 Release](https://github.com/AxonFramework/AxonFramework/releases/tag/axon-5.0.0)
- [Axon Framework 5 API Changes](https://github.com/AxonFramework/AxonFramework/blob/axon-5.0.0/axon-5/api-changes.md)
- [Axon Framework 5 Getting Started](https://docs.axoniq.io/axon-framework-5-getting-started/)
- [Announcing Axon Framework 5](https://www.axoniq.io/blog/announcing-axon-framework-5-configuring-axon-made-easy)
- [Release of Axon Framework 5.0](https://www.axoniq.io/blog/release-of-axon-framework-5-0)
- [Spring Boot 4.0.0 Release Notes](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/What's-New-in-Spring-Framework-7.x)

### Migration Tools

- [OpenRewrite Recipes for Axon Framework](https://www.moderne.ai/blog/how-axon-framework-handles-breaking-changes-through-openrewrite)
- [OpenRewrite Spring Boot 4.0 Recipe](https://docs.openrewrite.org/recipes/java/spring/boot4/upgradespringboot_4_0)

### EAF Documentation

- [Spring Boot 4.0 Upgrade Plan](docs/spring-boot-4-upgrade-plan.md)
- [Spring Boot 4.0 Migration Status](docs/spring-boot-4-migration-status.md)
- [Architecture Decision Record](docs/architecture.md)

---

## Appendix A: Version Compatibility Matrix

```
┌─────────────────────────────────────────────────────────────┐
│                  COMPATIBILITY MATRIX                        │
├─────────────────┬──────────┬──────────┬────────────────────┤
│ Component       │ Current  │ Target   │ Compatibility      │
├─────────────────┼──────────┼──────────┼────────────────────┤
│ JDK             │ 21 LTS   │ 21 LTS   │ ✅ OK              │
│ Kotlin          │ 2.2.21   │ 2.2.21   │ ✅ OK              │
│ Axon Framework  │ 4.12.2   │ 5.0.0    │ ⚠️ MAJOR REWRITE  │
│ Spring Boot     │ 3.5.7    │ 4.0.0    │ ✅ Compatible     │
│ Spring Fwk      │ 6.2.12   │ 7.0.1    │ ✅ Compatible     │
│ Spring Modulith │ 1.4.4    │ 2.0.0    │ ✅ Compatible     │
│ Jackson         │ 2.20.1   │ 3.0.0    │ ✅ Compatible     │
│ Jakarta EE      │ 10       │ 11       │ ✅ Compatible     │
│ JPA             │ 3.1      │ 3.2      │ ✅ Compatible     │
│ Servlet         │ 6.0      │ 6.1      │ ✅ Compatible     │
│ Bean Validation │ 3.0      │ 3.1      │ ✅ Compatible     │
└─────────────────┴──────────┴──────────┴────────────────────┘

Axon 5.0 Requirements Check:
├─ JDK 21+              ✅ SATISFIED
├─ Spring Boot 3+       ✅ SATISFIED (using 4.0)
├─ Spring Framework 6+  ✅ SATISFIED (using 7.0.1)
├─ Jakarta Persistence  ✅ SATISFIED (Spring Boot 4.0)
├─ Jackson 3.0          ✅ SATISFIED
└─ RESULT: ✅ ALL REQUIREMENTS MET
```

---

## Appendix B: Breaking Change Severity Matrix

| Change Category | Severity | Files Affected | Auto-Fixable | Risk |
|----------------|----------|----------------|--------------|------|
| @Aggregate → @EventSourced | 🟢 LOW | 15 | ✅ Yes (OpenRewrite) | 🟢 |
| Handler signatures | 🟡 MEDIUM | 60 | ⚠️ Partial | 🟡 |
| UnitOfWork → ProcessingContext | 🔴 HIGH | 40 | ❌ No | 🔴 |
| Configuration API | 🔴 HIGH | 5 | ❌ No | 🔴 |
| Event Store API | 🔴 HIGH | 3 | ❌ No | 🔴 |
| Test fixtures | 🔴 HIGH | 25 | ⚠️ Partial | 🔴 |
| Serializer → Converter | 🟡 MEDIUM | 3 | ⚠️ Partial | 🟡 |
| Message methods | 🟢 LOW | 30 | ✅ Yes (OpenRewrite) | 🟢 |
| Event processors | 🔴 HIGH | 10 | ❌ No | 🔴 |
| Interceptors | 🟡 MEDIUM | 5 | ⚠️ Partial | 🟡 |

**Legend**:
- 🟢 LOW: Simple find-replace or annotation change
- 🟡 MEDIUM: Signature changes, requires logic review
- 🔴 HIGH: Architecture changes, requires redesign

---

## Appendix C: Decision Tree

```
┌─────────────────────────────────────────────────────────────┐
│          SHOULD YOU UPGRADE TO AXON 5.0 NOW?                │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────────┐
        │ Is Epic 10 complete?                │
        └─────────────────────────────────────┘
                 │                     │
                NO                    YES
                 │                     │
                 ▼                     ▼
        ┌────────────────┐    ┌────────────────┐
        │ DEFER          │    │ Consider timing│
        │ Complete Epic  │    └────────────────┘
        │ 10 first       │             │
        └────────────────┘             ▼
                          ┌────────────────────────────┐
                          │ Can you allocate 4 weeks?  │
                          └────────────────────────────┘
                                 │              │
                                NO             YES
                                 │              │
                                 ▼              ▼
                        ┌────────────┐  ┌──────────────────┐
                        │ DEFER      │  │ Is Axon 5.1.0    │
                        │ Wait for   │  │ available with   │
                        │ Q1 2026    │  │ migration tools? │
                        └────────────┘  └──────────────────┘
                                               │        │
                                              YES      NO
                                               │        │
                                               ▼        ▼
                                        ┌──────────┐ ┌──────────┐
                                        │ PROCEED  │ │ DEFER    │
                                        │ Full     │ │ Wait for │
                                        │ upgrade  │ │ tooling  │
                                        └──────────┘ └──────────┘
```

---

## Conclusion

The Axon Framework 5.0 + Spring Boot 4.0 + Spring Framework 7.0 upgrade represents a **complete architectural modernization** of EAF's CQRS/Event Sourcing infrastructure. While Jackson 3.0 compatibility is confirmed and all technical requirements are met, the **magnitude of breaking changes** makes this a high-risk, high-effort migration.

**Final Recommendation**: **DEFER to Q1 2026** unless you have a critical business requirement for Spring Boot 4.0 / Spring Framework 7.0 immediately. Complete Epic 10 on the stable Axon 4.12.2 + Spring Boot 3.5.x stack, then execute the migration when Axon 5.1.0 is available with comprehensive migration tooling.

**If you proceed with the experimental upgrade**:
- Allocate **4 full weeks** of dedicated effort
- Expect to rewrite **100% of Axon-related code**
- Establish comprehensive test coverage before starting
- Use feature branch isolation for safe rollback
- Monitor Axon Framework GitHub for critical issues

The good news: Once complete, you'll have a **modern, async-native CQRS/Event Sourcing stack** fully compatible with Jackson 3.0, Spring Boot 4.0, and Spring Framework 7.0, positioning EAF for the next 5+ years of evolution.
