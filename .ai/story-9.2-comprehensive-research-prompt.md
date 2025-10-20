# Axon Framework 4.12 Query Handler Registration - Comprehensive Expert Research Request

**Investigation Duration**: 350,000+ tokens over 6+ hours
**Attempts Made**: 12+ different approaches
**Status**: BLOCKED - Unable to resolve despite extensive investigation
**Date**: 2025-10-20

---

## Executive Summary

We are experiencing a persistent `NoHandlerForQueryException` when using standalone `@QueryHandler` classes in Axon Framework 4.12.1 with Kotlin and Spring Boot 3.5.6. After exhaustive testing including multiple registration strategies, serializer configurations, and deep web research, the issue remains unresolved.

**Critical Discovery**: Auto-discovery appears to NOT work for standalone query handler components, and manual registration attempts either create duplicates or fail silently despite "success" logs.

---

## Technical Stack (Immutable Constraints)

### Core Technologies
- **Language**: Kotlin 2.2.20 (PINNED - cannot change due to tool compatibility)
- **Runtime**: JVM 21
- **Framework**: Spring Boot 3.5.6 (LOCKED for Spring Modulith 1.4.3 compatibility)
- **CQRS**: Axon Framework 4.12.1 (via axon-spring-boot-starter)
  - Dependency: `org.axonframework:axon-spring-boot-starter:4.12.1`
  - ⚠️ **Constraint**: Cannot upgrade to Axon 5.x yet (still in milestones, migration planned for later)
- **Build**: Gradle 9.1.0 monorepo with Kotlin DSL
- **Database**: PostgreSQL 16.1+ with Row-Level Security (RLS)
- **Query Layer**: jOOQ 3.19 for read projections
- **Testing**: Kotest 6.0.3 (not JUnit)

### Serialization Configuration
```yaml
# application.yml
axon:
  axonserver:
    enabled: false  # Using SimpleQueryBus, not Axon Server
  serializer:
    events: jackson
    messages: jackson  # Tried XStream too - didn't help
```

### Architecture Pattern
- **Hexagonal Architecture** with Spring Modulith
- **CQRS/Event Sourcing** via Axon Framework
- **Multi-Tenant System** with 3-layer isolation:
  1. JWT extraction (TenantContextFilter)
  2. Service boundary validation (@CommandHandler/@QueryHandler)
  3. Database RLS (PostgreSQL session variables via TenantQueryHandlerInterceptor)

---

## The Problem in Detail

### Error Message
```
org.axonframework.queryhandling.NoHandlerForQueryException:
No handler found for [com.axians.eaf.api.widget.queries.FindWidgetsQuery]
with response type [InstanceResponseType{class com.axians.eaf.api.widget.dto.PagedResponse}]
```

### Current Code

**Query Handler** (`products/widget-demo/src/main/kotlin/.../query/WidgetQueryHandler.kt`):
```kotlin
package com.axians.eaf.products.widgetdemo.query

import com.axians.eaf.api.widget.dto.PagedResponse
import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import org.axonframework.queryhandling.QueryHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component  // Tried: @Service, @Bean, no annotation - all fail
open class WidgetQueryHandler(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) {
    @QueryHandler
    @Transactional(readOnly = true)
    fun handle(query: FindWidgetByIdQuery): WidgetResponse? {
        val projection = repository.findByWidgetIdAndTenantId(query.widgetId, query.tenantId)
        return projection?.let { convertToResponse(it) }
    }

    @QueryHandler
    @Transactional(readOnly = true)
    fun handle(query: FindWidgetsQuery): PagedResponse<WidgetResponse> {
        val criteria = WidgetSearchCriteria(query.tenantId, query.category, query.search, ...)
        val result = repository.search(criteria)  // Returns SearchResult with items + total count
        val responses = result.items.map { convertToResponse(it) }

        return PagedResponse(
            content = responses,
            totalElements = result.total,
            page = query.page,
            size = query.size,
            totalPages = ceil(result.total.toDouble() / query.size).toInt()
        )
    }

    private fun convertToResponse(projection: WidgetProjection): WidgetResponse { /*...*/ }
}
```

**PagedResponse** (Concrete Data Class - not interface):
```kotlin
// shared/shared-api/src/main/kotlin/.../dto/PagedResponse.kt
data class PagedResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)
```

**Controller**:
```kotlin
@RestController
@RequestMapping("/widgets")
class WidgetController(
    private val queryGateway: QueryGateway,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('widget:read')")
    fun getWidgets(@RequestParam params: Map<String, String>): ResponseEntity<PagedResponse<WidgetResponse>> {
        val query = FindWidgetsQuery(
            tenantId = tenantContext.getCurrentTenantId(),
            page = params["page"]?.toIntOrNull() ?: 0,
            size = params["size"]?.toIntOrNull() ?: 20,
            sort = params["sort"]?.split(",") ?: emptyList(),
            category = params["category"],
            search = params["search"]
        )

        @Suppress("UNCHECKED_CAST")
        val responseType = ResponseTypes.instanceOf(PagedResponse::class.java)
            as ResponseType<PagedResponse<WidgetResponse>>

        val response = queryGateway.query(query, responseType).get(5, TimeUnit.SECONDS)
        return ResponseEntity.ok(response)
    }
}
```

**Application Configuration**:
```kotlin
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.products.widgetdemo",
        "com.axians.eaf.framework",
    ],
)
@EntityScan(basePackages = ["com.axians.eaf.products.widgetdemo.entities", "org.axonframework..."])
@EnableAspectJAutoProxy
class WidgetDemoApplication
```

---

## Complete Chronology of Attempts

### Attempt #1: Pure @Component Auto-Discovery
**Code**:
```kotlin
@Component
open class WidgetQueryHandler(...)
```
**Result**: ❌ NoHandlerForQueryException
**Observation**: Handler bean created successfully, appears in Spring context, but never registered with Axon QueryBus
**Logs**: No subscription or registration logs for query handlers (unlike command handlers which work)

---

### Attempt #2: @Component + @Lazy(false)
**Code**:
```kotlin
@Component
@Lazy(false)
open class WidgetQueryHandler(...)
```
**Result**: ❌ NoHandlerForQueryException
**Observation**: Forced eager initialization didn't change timing enough

---

### Attempt #3: @Configuration with @Bean
**Code**:
```kotlin
@Configuration
class WidgetQueryHandlerConfiguration {
    @Bean
    fun widgetQueryHandler(repository: WidgetProjectionRepository, objectMapper: ObjectMapper): WidgetQueryHandler {
        return WidgetQueryHandler(repository, objectMapper)
    }
}
```
**Result**: ❌ NoHandlerForQueryException
**Observation**: @Bean method still creates handler too late

---

### Attempt #4: @DependsOn Annotation
**Code**:
```kotlin
@Component
@DependsOn("springAxonConfiguration")
open class WidgetQueryHandler(...)
```
**Result**: ❌ NoHandlerForQueryException
**Observation**: Anti-pattern that doesn't solve discovery issue

---

### Attempt #5: ApplicationListener<ContextRefreshedEvent>
**Code**:
```kotlin
@Component
class QueryHandlerRegistrationListener(
    private val configurer: Configurer,
    private val handler: WidgetQueryHandler,
) : ApplicationListener<ContextRefreshedEvent> {
    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        val adapter = AnnotationQueryHandlerAdapter(handler, parameterResolverFactory)
        adapter.subscribe(configurer.queryBus())
    }
}
```
**Result**: ❌ NoHandlerForQueryException
**Observation**: Handler subscribes (no errors) but queries still fail - routing already configured

---

### Attempt #6: BeanDefinitionRegistryPostProcessor
**Code**:
```kotlin
@Component
class QueryHandlerDependencyPostProcessor : BeanDefinitionRegistryPostProcessor {
    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        // Attempt to modify bean creation order
    }
}
```
**Result**: ❌ Wrong lifecycle phase - runs before beans exist

---

### Attempt #7: SmartInitializingSingleton
**Code**:
```kotlin
@Configuration
class WidgetQueryHandlerConfiguration(...) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        configurer.registerQueryHandler { _ -> queryHandler }  // API doesn't exist/work
    }
}
```
**Result**: ❌ Compilation error - `registerQueryHandler` API signature mismatch
**Observation**: Axon configuration already complete at this phase

---

### Attempt #8: ConfigurerModule + registerMessageHandler() with @Component
**Code**:
```kotlin
@Component
open class WidgetQueryHandler(...)  // Handler as @Component

@Configuration
class WidgetQueryHandlerConfiguration {
    @Bean
    fun widgetQueryHandlerModule(queryHandler: WidgetQueryHandler) =
        ConfigurerModule { configurer ->
            configurer.registerMessageHandler { _ -> queryHandler }
        }
}
```
**Result**: ⚠️ **DUPLICATE REGISTRATIONS** (closest to working!)
**Logs**:
```
WARN: A duplicate query handler was found for query [com.axians.eaf.api.widget.queries.FindWidgetsQuery]
and response type [com.axians.eaf.api.widget.dto.PagedResponse<com.axians.eaf.api.widget.dto.WidgetResponse>]
```
**Observation**:
- Handler WAS registered TWICE (auto-discovery + ConfigurerModule)
- Axon logged: "This is only valid for ScatterGather queries. Normal queries will only use one of these handlers."
- But queries STILL failed with NoHandlerForQueryException!
- This proves auto-discovery CAN find the handler, but something breaks when duplicates exist

---

### Attempt #9: ConfigurerModule with Private Handler Instance (No @Component)
**Code**:
```kotlin
@Configuration
class WidgetQueryHandlerConfiguration(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun widgetQueryHandlerModule() =
        ConfigurerModule { configurer ->
            // Create handler privately to avoid auto-discovery
            val queryHandler = WidgetQueryHandler(repository, objectMapper)
            configurer.registerMessageHandler { _ -> queryHandler }
        }
}
```
**Result**: ❌ NoHandlerForQueryException
**Logs**: No duplicate warnings (good), but handler not found
**Observation**: `registerMessageHandler()` doesn't actually register query handlers!

---

### Attempt #10: ApplicationReadyEvent + AnnotationQueryHandlerAdapter
**Code**:
```kotlin
@Component
class WidgetQueryHandlerConfiguration(...) : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val queryHandler = WidgetQueryHandler(repository, objectMapper)
        val parameterResolverFactory = MultiParameterResolverFactory.ordered(
            ClasspathParameterResolverFactory.forClass(queryHandler::class.java),
            SimpleResourceParameterResolverFactory(listOf(axonConfiguration.getComponent(Serializer::class.java)))
        )
        val adapter = AnnotationQueryHandlerAdapter(queryHandler, parameterResolverFactory)
        adapter.subscribe(axonConfiguration.queryBus())
        logger.info("WidgetQueryHandler successfully registered with QueryBus")  // THIS LOGS!
    }
}
```
**Result**: ❌ NoHandlerForQueryException despite success logs!
**Mystery**: Logs say "successfully registered with QueryBus" but queries still don't route
**Observation**: ApplicationReadyEvent fires AFTER "Started WidgetDemoApplicationKt" - QueryBus might be frozen/locked by then

---

### Attempt #11: XStream Serializer (Generic Type Fix)
**Rationale**: Web research showed Jackson has issues with generic types, XStream recommended
**Code**:
```kotlin
@Configuration
class AxonSerializerConfiguration {
    @Bean
    @Qualifier("messageSerializer")
    fun messageSerializer(): Serializer {
        val xStream = XStream()
        xStream.allowTypesByWildcard(arrayOf(
            "com.axians.eaf.**",
            "org.axonframework.**",
            "org.springframework.data.**",  // For Spring Data Page support
            "java.**"
        ))
        return XStreamSerializer.builder().xStream(xStream).build()
    }
}
```
**Result**: ❌ NoHandlerForQueryException persists
**Observation**: XStream configuration applied successfully, but handler still not registered

---

### Attempt #12: Spring Data Page<T> (Original Code)
**Code**: Reverted to original implementation using `Page<WidgetResponse>`
**Result**: ❌ Same NoHandlerForQueryException
**Observation**: Even the original code that was presumably working has the same issue
**Error**: `No handler for [FindWidgetsQuery] with response type [InstanceResponseType{interface Page}]`

---

## Critical Research Findings

### 1. Generic Type Erasure Problem (Confirmed by Multiple Sources)

**From Stack Overflow**: "Axon no Handler Found for query when returning Response with Generic"
- Axon developers confirmed: **"What you're trying to do isn't possible at the moment"**
- Generic return types like `Page<T>` or `PagedResponse<T>` require custom ResponseType implementation
- Root cause: Java type erasure prevents Axon from matching parameterized types

**Evidence from Our Logs**:
- **Handler registers with**: `PagedResponse<WidgetResponse>` (full generic signature)
- **Query searches for**: `InstanceResponseType{class PagedResponse}` (raw type)
- **Result**: Types don't match due to erasure!

**Duplicate Registration Logs Prove This**:
```
WARN: duplicate query handler for [FindWidgetsQuery]
and response type [PagedResponse<WidgetResponse>]  ← Full generic type
```
vs.
```
ERROR: No handler for [FindWidgetsQuery]
with response type [InstanceResponseType{class PagedResponse}]  ← Raw type
```

### 2. Spring Data Page Not Supported (GitHub Issue #486)

**From Axon GitHub**: Issue #486 "Implement Page and PageResponseType for QueryBus"
- Spring Data `Page<T>` return type not supported out-of-the-box in Axon
- Community workarounds: Custom PageResponseType implementation or use `List<T>`
- Status: Open issue since 2018, not resolved in Axon 4.x

### 3. Jackson Serializer vs XStream

**From Multiple Sources**:
- Jackson serializer has known issues with generic types in Axon
- Quote: "If using JacksonSerializer for messages, changing to XStreamSerializer makes everything work"
- XStream recommended by Axon community for complex return types
- **Our Test**: Switching to XStream didn't fix the issue (handler still not registered)

### 4. Auto-Discovery Limitations

**From Axon Documentation Analysis**:
- ALL official examples use simple types: `List<T>`, `Optional<T>`, single objects
- NO examples with `Page<T>`, `PagedResponse<T>`, or other generic wrapper types
- Working examples show query handlers in:
  - Aggregates (`@Aggregate` class with @QueryHandler methods)
  - Sagas (`@Saga` class with @QueryHandler methods)
  - Simple @Component classes returning `List<T>` or single objects

**Our Observation**:
- Command handlers in Aggregates: ✅ Auto-discovered and working
- Event handlers: ✅ Auto-discovered and working
- Standalone @Component query handlers: ❌ NOT auto-discovered (no subscription logs)

### 5. Duplicate Handler Behavior

**When Both @Component AND ConfigurerModule Present**:
```
WARN org.axonframework.queryhandling.registration.LoggingDuplicateQueryHandlerResolver:
A duplicate query handler was found for query [com.axians.eaf.api.widget.queries.FindWidgetsQuery]
and response type [com.axians.eaf.api.widget.dto.PagedResponse<com.axians.eaf.api.widget.dto.WidgetResponse>].
It has also been registered to the query bus.
This is only valid for ScatterGather queries.
Normal queries will only use one of these handlers.
```

**Expectation**: Axon says "one handler will be used" - queries should work!
**Reality**: Queries still fail with NoHandlerForQueryException
**Hypothesis**: Duplicate resolution might be broken, or only works for scatter-gather

---

## Key Questions for Expert Analysis

### Primary Investigation Questions

**Q1: Why doesn't Axon auto-discover standalone @QueryHandler classes?**
- We've tried: @Component, @Service, @Bean - all create the Spring bean successfully
- Component scanning includes the handler package
- axon-spring-boot-starter is on classpath
- But handler never registers with QueryBus (no subscription logs unlike command/event handlers)
- **Is there a specific Axon configuration or Spring Boot property we're missing?**

**Q2: Why do duplicate registrations fail?**
- ConfigurerModule + @Component created duplicates (confirmed in WARN logs)
- Axon documentation says "Normal queries will only use one of these handlers"
- But our queries STILL throw NoHandlerForQueryException
- **Does duplicate resolution actually work? Or is there a bug?**

**Q3: How to handle generic return types with type erasure?**
- Handler returns: `PagedResponse<WidgetResponse>`
- Controller queries for: `ResponseTypes.instanceOf(PagedResponse::class.java)` (raw type)
- **How do we make these match given Java's type erasure?**
- Custom ResponseType implementation? Different API?

**Q4: Is registerMessageHandler() the wrong API?**
We tried:
```kotlin
configurer.registerMessageHandler { _ -> queryHandler }  // Logs "success" but doesn't work
```
- Does this API even support query handlers, or just command/event handlers?
- Should it be `registerQueryHandler()`? (Tried - expects function not annotated object)
- **What's the correct manual registration API for query handlers with annotation scanning?**

### Framework Integration Questions

**Q5: Does our custom TenantQueryHandlerInterceptor interfere?**
```kotlin
// framework/cqrs/AxonConfiguration.kt
@Autowired(required = false)
fun configureTenantQueryInterceptor(configurer: Configurer, tenantQueryInterceptor: TenantQueryHandlerInterceptor?) {
    tenantQueryInterceptor?.let { interceptor ->
        configurer.onInitialize { config ->
            config.queryBus().registerHandlerInterceptor(interceptor)
        }
    }
}
```
- Could registering interceptors prevent handler auto-discovery?
- Should handlers be registered BEFORE interceptors?

**Q6: Spring Boot 3.5.6 + Axon 4.12.1 compatibility?**
- Are there known issues with this combination?
- Spring Boot 3.x changed bean initialization order - could this affect Axon?

**Q7: Kotlin-specific considerations?**
- `open` class required for @Transactional proxying
- Type inference differences between Kotlin and Java
- Are there Kotlin-specific Axon patterns we should follow?

### Diagnostic Questions

**Q8: How do we verify what Axon actually registered?**
- Is there a way to list all registered query handlers at runtime?
- Debug logs we should enable to see handler discovery process?
- Actuator endpoint or diagnostic tool?

**Q9: Why do success logs lie?**
```
INFO: WidgetQueryHandler successfully registered with QueryBus
```
But then:
```
ERROR: No handler found for [FindWidgetsQuery]
```
- Is `adapter.subscribe(queryBus)` synchronous or async?
- Could subscription succeed but routing fail?
- Is there validation we can add to confirm actual registration?

---

## Architectural Alternatives (Option 3 from Summary)

If standalone query handlers are fundamentally broken in our setup, we need to evaluate:

### Alternative A: Embed Query Handlers in Aggregates
```kotlin
@Aggregate
class Widget {
    @AggregateIdentifier
    private lateinit var widgetId: String

    @CommandHandler
    constructor(command: CreateWidgetCommand) { /* ... */ }

    @QueryHandler  // ← Would THIS auto-discover?
    fun handle(query: FindWidgetsQuery): PagedResponse<WidgetResponse> {
        // How to access WidgetProjectionRepository here?
        // Aggregates shouldn't have repository dependencies...
    }
}
```

**Questions**:
- Do @QueryHandler methods in Aggregates auto-discover reliably?
- How to access projection repositories from within aggregates (anti-pattern)?
- Performance implications of loading aggregates for queries?

### Alternative B: Bypass Axon QueryBus for Reads
```kotlin
@RestController
@RequestMapping("/widgets")
class WidgetController(
    private val repository: WidgetProjectionRepository,  // Direct injection
) {
    @GetMapping
    fun getWidgets(...): ResponseEntity<PagedResponse<WidgetResponse>> {
        // Query projections directly, bypass Axon QueryBus
        val result = repository.search(criteria)
        return ResponseEntity.ok(PagedResponse(...))
    }
}
```

**Trade-offs**:
- ✅ Simple, guaranteed to work
- ✅ No Axon configuration complexity
- ❌ Loses CQRS separation (controller directly depends on repository)
- ❌ Can't use Axon query features (scatter-gather, subscription queries, etc.)
- ❌ Inconsistent with command side (uses Axon)

**Question**: Is this acceptable for production, or does it violate CQRS principles too much?

### Alternative C: Axon 5.x Migration
**Status Check**:
- Axon 5.0 released in milestones (not GA yet)
- Main focus: New Configuration API, less reliance on Spring annotations
- Migration guides available with OpenRewrite recipes

**Questions**:
- Does Axon 5.x fix query handler auto-discovery issues?
- Migration effort estimation for our stack?
- Is 5.x stable enough for production use?
- Breaking changes we need to be aware of?

---

## Web Research Summary

### Stack Overflow Findings

**1. "Axon no Handler Found for query when returning Response with Generic"**
- **Problem**: Generic return types cause NoHandlerForQueryException
- **Axon Team Response**: "What you're trying to do isn't possible at the moment"
- **Workaround**: Avoid generics or implement custom ResponseType
- **Our Status**: Tried PagedResponse (concrete class) - still fails

**2. "Axon framework and Page response type"**
- **Problem**: Spring Data `Page<T>` not supported
- **Solution Suggested**:
  - Use XStream serializer: `xStream.allowTypesByWildcard(new String[]{"org.springframework.data.**"})`
  - Create custom PageResponseType
- **Our Status**: Tried XStream configuration - didn't help

**3. "Axon event handler and query handlers do not work together in kotlin"**
- Kotlin-specific issues with Axon handler discovery
- Solutions vary - need to investigate this thread deeper

### GitHub Issues

**1. AxonFramework/AxonFramework#486**: "Implement Page and PageResponseType for QueryBus"
- Open since 2018
- Community asking for built-in Page support
- Custom implementations exist but not in framework

**2. AxonFramework/AxonFramework#1537**: "Allow parameterized ResponseType"
- Type system limitations with generics
- Workarounds in issue comments

**3. AxonFramework/AxonFramework#2030**: "Jackson serializer multipleInstancesOf type loss"
- Jackson loses type information
- Recommendation: Use XStream for messages

### Axon Community Forums (discuss.axoniq.io)

Multiple threads about NoHandlerForQueryException with various causes:
- Missing axon-spring-boot-starter dependency (not our issue - we have it)
- Handler in wrong package (not our issue - component scan correct)
- Timing issues (possibly our issue!)
- Generic type mismatches (likely our issue!)

---

## Framework Configuration Context (May Be Relevant)

### Our Custom AxonConfiguration

Located in `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt`:

```kotlin
@AutoConfiguration
@ConditionalOnClass(Configurer::class)
@ConditionalOnProperty(
    prefix = "eaf.cqrs.tenant-propagation",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AxonConfiguration {

    // Successfully registers command interceptor
    @Autowired(required = false)
    fun configureCommandTracing(configurer: Configurer, tracingCommandInterceptor: TracingCommandInterceptor?) {
        tracingCommandInterceptor?.let { interceptor ->
            configurer.onInitialize { config ->
                config.commandBus().registerDispatchInterceptor(interceptor)
            }
        }
    }

    // Successfully registers query handler interceptor
    @Autowired(required = false)
    fun configureTenantQueryInterceptor(configurer: Configurer, tenantQueryInterceptor: TenantQueryHandlerInterceptor?) {
        tenantQueryInterceptor?.let { interceptor ->
            configurer.onInitialize { config ->
                config.queryBus().registerHandlerInterceptor(interceptor)
            }
        }
    }

    // Event processors, event interceptors, etc. - all work fine
}
```

**Question**: Could this custom configuration interfere with query handler auto-discovery?

---

## Specific Requests for External AI Research

### Request #1: Verify Auto-Discovery Hypothesis

**Statement to Verify**:
> "Axon Framework 4.12's auto-discovery of @QueryHandler annotations only works for handlers embedded in Aggregates or Sagas. Standalone query handler classes marked with @Component/@Service are NOT automatically registered with SimpleQueryBus, regardless of Spring Boot configuration."

**Evidence to Find**:
- Official Axon documentation confirming this
- Working examples of standalone query handler classes in Axon 4.12 + Spring Boot 3.x + Kotlin
- Known issues or limitations in Axon's Spring Boot integration

### Request #2: Explain Duplicate Registration Mystery

**Observed Behavior**:
1. ConfigurerModule + @Component creates duplicates (confirmed in logs)
2. Axon says: "Normal queries will only use one of these handlers"
3. But queries still fail with NoHandlerForQueryException

**Questions**:
- Why doesn't duplicate resolution work for point-to-point queries?
- Is there a bug in Axon's LoggingDuplicateQueryHandlerResolver?
- Are we triggering duplicate resolution incorrectly?
- Does it only work for scatter-gather, despite documentation saying otherwise?

### Request #3: Working Solution with Pagination

**Requirements**:
- Standalone query handler class (not in Aggregate)
- Kotlin + Spring Boot 3.x + Axon 4.12
- Returns paginated results with metadata (total count, page number, etc.)
- Uses @Component or @Service (Spring beans)
- Actually works in runtime

**Deliver**:
- Complete working code example
- Explanation of why it works
- Any required Axon/Spring configuration

### Request #4: Type Matching Solution

Given that we MUST return paginated results, how do we solve the type erasure problem?

**Options to Evaluate**:
1. **Custom ResponseType**: Implement `ResponseType<PagedResponse<WidgetResponse>>` interface
2. **Raw type workaround**: Make handler and controller both use raw PagedResponse (tried - didn't work)
3. **Different pagination approach**: Return `List<T>` + separate count query?
4. **Axon extensions**: Is there a Kotlin extension or library that solves this?

### Request #5: Debug Axon's Handler Discovery

**Help Us Understand**:
- How does Axon's MessageHandlerConfigurer actually find handlers?
- What Spring beans does it scan? (All? Only certain stereotypes?)
- Is there debug logging we can enable to see the discovery process?
- How to verify what handlers Axon has registered at runtime?

### Request #6: Manual Registration That Works

If auto-discovery is impossible, provide a manual registration approach that:
- Works with ConfigurerModule or similar pattern
- Doesn't create circular dependencies
- Properly triggers @QueryHandler annotation scanning
- Registers BEFORE query routing is configured
- Doesn't conflict with auto-discovered duplicates

**Code Pattern Needed**:
```kotlin
@Bean
fun queryHandlerRegistration(...) = ConfigurerModule { configurer ->
    // What goes here that actually works?
}
```

---

## Constraints & Success Criteria

### Must Preserve

1. ✅ **@Transactional support** - Queries must run in read-only transactions
2. ✅ **Multi-tenant isolation** - TenantQueryHandlerInterceptor executes before handler
3. ✅ **Dependency injection** - Handler needs Spring beans (repository, objectMapper)
4. ✅ **Pagination metadata** - Need total count, page numbers (not just List<T>)
5. ✅ **Both query types** - Single-item (`FindWidgetByIdQuery`) and paginated (`FindWidgetsQuery`)
6. ✅ **Kotlin compatibility** - Solution must work with Kotlin's type system

### Cannot Change

1. ❌ **Axon version** - Stuck on 4.12.1 (no Axon 5.x migration now)
2. ❌ **Kotlin version** - 2.2.20 pinned for ktlint/detekt compatibility
3. ❌ **Spring Boot version** - 3.5.6 locked for Spring Modulith
4. ❌ **CQRS pattern** - Axon QueryBus is architectural requirement
5. ❌ **Return type** - Must support pagination (can't just return List without metadata)

### Acceptable Trade-offs

- ✅ Manual configuration (explicit @Bean registration)
- ✅ Framework-level configuration (custom auto-configuration class)
- ✅ Increased startup time (if it solves the issue)
- ✅ Duplicate registration warnings (if queries actually work)
- ✅ Type safety suppression (`@Suppress("UNCHECKED_CAST")`) - already using

---

## Expected Deliverables

### 1. Root Cause Analysis
- Why auto-discovery fails in our specific setup
- Whether it's: configuration issue, framework limitation, or architectural problem

### 2. Working Solution
- Step-by-step implementation that resolves NoHandlerForQueryException
- Complete code examples (Kotlin)
- Configuration changes needed

### 3. Type Handling Strategy
- How to handle `PagedResponse<T>` with type erasure
- Whether to use custom ResponseType or different approach

### 4. Configuration Review
- Identify any misconfigurations in our Axon/Spring setup
- Recommendations for axon.* properties

### 5. Architectural Recommendation
- Should we: Fix standalone handlers? Embed in aggregates? Bypass QueryBus?
- Pros/cons of each approach for our multi-tenant CQRS system

### 6. Migration Guidance (if needed)
- If current approach is impossible, provide migration path
- Effort estimation and risks

---

## Additional Context

### What Currently Works

✅ **Command Handlers** (in Aggregates):
```kotlin
@Aggregate
class Widget {
    @CommandHandler
    constructor(command: CreateWidgetCommand) { apply(WidgetCreatedEvent(...)) }
}
```
Auto-discovered and working perfectly!

✅ **Event Handlers**:
```kotlin
@Component
class WidgetProjectionHandler {
    @EventHandler
    fun on(event: WidgetCreatedEvent) { /* save projection */ }
}
```
Auto-discovered and working!

✅ **Interceptors**:
```kotlin
@Component
class TenantQueryHandlerInterceptor : MessageHandlerInterceptor<QueryMessage<*>> {
    override fun handle(message: UnitOfWork<QueryMessage<*>>, chain: InterceptorChain): Any? {
        // Interceptor logic
    }
}
```
Registered via `configurer.onInitialize { config.queryBus().registerHandlerInterceptor(...) }` - works!

❌ **Query Handlers** (Standalone):
```kotlin
@Component  // or @Service, or @Bean - nothing works
open class WidgetQueryHandler {
    @QueryHandler fun handle(query: FindWidgetsQuery): PagedResponse<WidgetResponse> { }
}
```
**NOT auto-discovered, manual registration attempts fail!**

### Application Startup Behavior

- Application starts successfully (no errors in logs)
- All Spring beans created correctly
- Axon configuration completes
- Web server starts on port 8081
- Interceptors register successfully
- QueryBus and QueryGateway beans exist
- **But:** No query handler subscription logs
- **Result:** Runtime NoHandlerForQueryException when queries dispatched

---

## Investigation Tools Used

- ✅ Spring Boot DEBUG logging for bean creation timing
- ✅ Axon Framework reference documentation (4.10, 4.11, 4.12)
- ✅ Web search (Stack Overflow, GitHub, Axon forums)
- ✅ Context7 API documentation lookup
- ✅ Deep think analysis tool (thinkdeep with gemini-2.5-pro)
- ✅ Multiple serializer configurations tested
- ✅ E2E automated testing script

### Token Investment
- **Total tokens used**: 350,000+
- **Investigation duration**: 6+ hours
- **Attempts documented**: 12+ different approaches
- **Web searches**: 8+ targeted queries
- **Documentation reviewed**: Official Axon docs, GitHub issues, Stack Overflow, community forums

---

## Urgency & Impact

### Why This Blocks Us

**Story 9.2** (Fix widget-demo QueryHandler ExecutionException) is **BLOCKED**
- Cannot ship widget query functionality
- Blocks Epic 9 (Widget Management CRUD)
- All other CQRS components work (commands, events, projections)
- Only query handlers fail

### Risk Assessment

**Current Workaround**: None available
- Can't bypass QueryBus (architectural requirement)
- Can't embed in Aggregates (handlers need projection repositories)
- Can't upgrade Axon (version constraints)

**Impact if Unresolved**:
- Cannot implement read operations for any domain
- CQRS pattern incomplete (command side works, query side broken)
- Must reconsider entire Axon Framework usage

---

## Request for External AI Agent

**Primary Objective**: Find a working solution for registering standalone @QueryHandler classes in Axon Framework 4.12.1 with our specific technology stack.

**Investigation Scope**:
1. Deep dive into Axon 4.12 query handler registration internals
2. Search for working Kotlin + Spring Boot 3.x + Axon 4.12 examples
3. Analyze type erasure solutions for generic return types
4. Review Axon community discussions for similar issues
5. Evaluate architectural alternatives if direct fix is impossible

**Tools to Use**:
- Web search (GitHub code search, Stack Overflow, Axon forums)
- Official Axon Framework documentation (all versions)
- AI models with deep JVM framework knowledge
- Code example repositories (Axon samples, community projects)

**Deliverable Format**:
- Markdown document with working solution OR
- Clear explanation of why it's impossible + recommended alternative

---

**Prepared by**: Claude Code (Anthropic)
**Model**: claude-sonnet-4-5
**Investigation Date**: 2025-10-20
**Token Budget Consumed**: 350,000+ tokens
**Status**: Exhausted local investigation capacity - requires expert external research

---

## Appendix: Error Logs

### Startup Logs (Successful)
```
INFO: Started WidgetDemoApplicationKt in 5.96 seconds
INFO: Tomcat started on port 8081
DEBUG: Creating shared instance of singleton bean 'widgetQueryHandler'
DEBUG: Autowiring widgetQueryHandler with repository and objectMapper
```

### Runtime Error (When Query Dispatched)
```
ERROR: ExecutionException caught in controller.
Root cause: NoHandlerForQueryException -
No handler found for [com.axians.eaf.api.widget.queries.FindWidgetsQuery]
with response type [InstanceResponseType{class com.axians.eaf.api.widget.dto.PagedResponse}]

Stack trace:
  at org.axonframework.queryhandling.SimpleQueryBus.noHandlerException(SimpleQueryBus.java:385)
  at org.axonframework.queryhandling.SimpleQueryBus.doQuery(SimpleQueryBus.java:194)
  at org.axonframework.queryhandling.DefaultQueryGateway.query(DefaultQueryGateway.java:84)
  at com.axians.eaf.products.widgetdemo.controllers.WidgetController.getWidgets(WidgetController.kt:106)
```

### Duplicate Registration Warning (Attempt #8)
```
WARN org.axonframework.queryhandling.registration.LoggingDuplicateQueryHandlerResolver:
A duplicate query handler was found for query [com.axians.eaf.api.widget.queries.FindWidgetsQuery]
and response type [com.axians.eaf.api.widget.dto.PagedResponse<com.axians.eaf.api.widget.dto.WidgetResponse>].
It has also been registered to the query bus.
This is only valid for ScatterGather queries.
Normal queries will only use one of these handlers.
```

**Followed by**: NoHandlerForQueryException anyway!

---

END OF COMPREHENSIVE RESEARCH REQUEST
