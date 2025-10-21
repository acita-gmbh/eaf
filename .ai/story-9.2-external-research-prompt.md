# External Research Request: Axon Framework Query Handler Registration Issue

## Mission
Find a working solution for registering Axon Framework query handlers that avoids a timing race condition in Spring Boot initialization, where MessageHandlerConfigurer scans for handlers BEFORE the handler bean is created.

---

## System Context

### Technology Stack (IMMUTABLE)
- **Language**: Kotlin 2.2.20 (PINNED - critical constraint, cannot change)
- **Runtime**: JVM 21
- **Framework**: Spring Boot 3.5.6 (LOCKED for Spring Modulith 1.4.3 compatibility)
- **CQRS**: Axon Framework 4.9.4 (current stable)
  - ⚠️ **Constraint**: Cannot upgrade to Axon 5.x yet (migration planned for later)
- **Build**: Gradle 9.1.0
- **Database**: PostgreSQL 16.1+ with Row-Level Security (RLS)
- **Query Layer**: jOOQ for read projections

### Architecture Pattern
- **Hexagonal Architecture** with Spring Modulith
- **CQRS/Event Sourcing** via Axon Framework
- **Multi-Tenant System** with 3-layer tenant isolation:
  1. JWT extraction (TenantContextFilter)
  2. Service boundary validation (@CommandHandler/@QueryHandler)
  3. Database RLS enforcement (PostgreSQL session variables)

### Module Structure
```
framework/
  cqrs/  # Contains AxonConfiguration for interceptor registration
products/
  widget-demo/  # Contains WidgetQueryHandler (the problem component)
```

---

## The Problem

### Error Manifestation
GET `/widgets` endpoint returns **500 Internal Server Error**:

```json
{
  "type": "/problems/execution-error",
  "title": "Query Execution Error",
  "status": 500,
  "detail": "Query execution failed: No handler found for [com.axians.eaf.api.widget.queries.FindWidgetsQuery] with response type [InstanceResponseType{class com.axians.eaf.api.widget.dto.PagedResponse}]",
  "rootCauseType": "NoHandlerForQueryException"
}
```

### Root Cause: Timing Race Condition
Discovered via detailed Spring Boot startup log analysis:

```
21:54:25.671 - Creating MessageHandlerConfigurer$$Axon$$QUERY  ← Axon scans for query handlers
21:54:26.187 - Creating widgetQueryHandler bean               ← Handler created 516ms TOO LATE
```

**Problem**: Axon's `MessageHandlerConfigurer` scans for beans with `@QueryHandler` methods during its initialization (~25.67s), but the `WidgetQueryHandler` bean is created by Spring's component scanning ~516ms later (~26.19s). By the time the handler exists, query routing has already been configured without it.

---

## Current Code State

### 1. Query Handler (Simplified)

```kotlin
package com.axians.eaf.products.widgetdemo.query

import com.axians.eaf.api.widget.dto.PagedResponse
import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.api.widget.queries.FindWidgetByIdQuery
import com.axians.eaf.api.widget.queries.FindWidgetsQuery
import com.axians.eaf.products.widgetdemo.entities.WidgetProjection
import com.axians.eaf.products.widgetdemo.repositories.WidgetProjectionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.axonframework.queryhandling.QueryHandler
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Query handler for Widget-related queries.
 *
 * **Kotlin Note**: Class must be `open` (non-final) to allow Spring CGLIB proxying
 * for @Transactional AOP. This is a Kotlin-specific requirement.
 */
@Component
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
        // Validation, repository query, conversion to PagedResponse
        // (implementation details omitted for brevity)
    }

    private fun convertToResponse(projection: WidgetProjection): WidgetResponse {
        // Converts entity to DTO
    }
}
```

**Key Requirements**:
- Must be `open` class (Kotlin) for `@Transactional` CGLIB proxying
- Has two `@QueryHandler` methods with different signatures
- Dependencies: `WidgetProjectionRepository`, `ObjectMapper` (both Spring beans)

### 2. Controller (Query Dispatch)

```kotlin
@GetMapping
fun getWidgets(
    @RequestParam(defaultValue = "0") page: Int,
    @RequestParam(defaultValue = "20") size: Int,
    @RequestParam(required = false) category: String?,
    @RequestParam(required = false) search: String?,
    @RequestParam(defaultValue = "createdAt.desc") sort: Array<String>,
): ResponseEntity<PagedResponse<WidgetResponse>> {
    val tenantId = TenantContext().getCurrentTenantId()
    val query = FindWidgetsQuery(
        tenantId = tenantId,
        page = page,
        size = size,
        category = category,
        search = search,
        sort = sort.toList(),
    )

    // Workaround for Axon 4.9.4 + Kotlin type erasure issue
    @Suppress("UNCHECKED_CAST")
    val responseType = ResponseTypes.instanceOf(PagedResponse::class.java)
        as ResponseType<PagedResponse<WidgetResponse>>

    val response = queryGateway.query(query, responseType).get(5, TimeUnit.SECONDS)
    return ResponseEntity.ok(response)
}
```

### 3. Working Interceptor Pattern (For Reference)

This is how **interceptors** are successfully registered in our framework (from `framework/cqrs/AxonConfiguration.kt`):

```kotlin
@AutoConfiguration
@ConditionalOnClass(Configurer::class)
class AxonConfiguration {

    /**
     * ✅ THIS WORKS - Interceptor registration during Axon initialization
     */
    @Autowired(required = false)
    fun configureTenantQueryInterceptor(
        configurer: Configurer,
        tenantQueryInterceptor: TenantQueryHandlerInterceptor?,
    ) {
        tenantQueryInterceptor?.let { interceptor ->
            configurer.onInitialize { config ->
                config.queryBus().registerHandlerInterceptor(interceptor)
            }
        }
    }
}
```

**Why this works**: Interceptors implement an interface (`MessageHandlerInterceptor`), so they don't need annotation scanning. They're just registered with the QueryBus.

**Why handlers are different**: Query handlers need their `@QueryHandler` methods discovered and registered with query routing, which happens during `MessageHandlerConfigurer` initialization.

---

## All Attempted Solutions (Chronological)

### Attempt 1: @Component Annotation
```kotlin
@Component
open class WidgetQueryHandler(...)
```
**Result**: ❌ Handler created at ~600ms after MessageHandlerConfigurer scan
**Why it failed**: Spring component scanning happens too late in initialization order

---

### Attempt 2: @Lazy(false) for Early Initialization
```kotlin
@Component
@Lazy(false)
open class WidgetQueryHandler(...)
```
**Result**: ❌ Handler still created 567ms after scan
**Why it failed**: @Lazy(false) doesn't guarantee early enough initialization; depends on dependency tree

---

### Attempt 3: @Configuration with @Bean + @Lazy(false)
```kotlin
@Configuration
open class WidgetQueryHandlerConfiguration {
    @Bean
    @Lazy(false)
    fun widgetQueryHandler(
        repository: WidgetProjectionRepository,
        objectMapper: ObjectMapper,
    ): WidgetQueryHandler {
        return WidgetQueryHandler(repository, objectMapper)
    }
}
```
**Result**: ❌ Handler still created 573ms after scan
**Why it failed**: @Bean creation timing still depends on configuration class processing order

---

### Attempt 4: ApplicationListener for Post-Initialization Registration
```kotlin
@Component
class QueryHandlerRegistrationListener(
    private val configurer: Configurer,
    private val handler: WidgetQueryHandler,
) : ApplicationListener<ContextRefreshedEvent> {

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        configurer.onInitialize { config ->
            val adapter = AnnotationQueryHandlerAdapter(
                handler,
                config.parameterResolverFactory()
            )
            adapter.subscribe(config.queryBus())
        }
    }
}
```
**Result**: ❌ Handler subscribes but queries still fail with NoHandlerForQueryException
**Why it failed**: Query routing is configured during MessageHandlerConfigurer initialization, not dynamically updatable. Post-initialization subscription doesn't affect existing routing.

---

### Attempt 5: ConfigurerModule with registerMessageHandler()
```kotlin
@Component
class WidgetQueryHandlerModule(
    private val repository: WidgetProjectionRepository,
    private val objectMapper: ObjectMapper,
) : ConfigurerModule {

    override fun configureModule(configurer: Configurer) {
        configurer.registerMessageHandler { config ->
            WidgetQueryHandler(repository, objectMapper)
        }
    }
}
```
**Result**: ❌ Handler created but @QueryHandler methods not discovered
**Logs showed**:
```
★★★ WidgetQueryHandlerModule.configureModule() CALLED ★★★
★★★ Handler created: com.axians.eaf.products.widgetdemo.query.WidgetQueryHandler@6e886e25 ★★★
```
But query still failed.

**Why it failed**: `registerMessageHandler()` creates the handler instance but doesn't trigger annotation scanning for @QueryHandler methods.

---

### Attempt 6: ConfigurerModule + Explicit AnnotationQueryHandlerAdapter
```kotlin
@Component
class WidgetQueryHandlerModule(...) : ConfigurerModule {

    override fun configureModule(configurer: Configurer) {
        configurer.registerMessageHandler { config ->
            val handler = WidgetQueryHandler(repository, objectMapper)

            // Manually scan and subscribe @QueryHandler methods
            val queryBus = config.queryBus()  // ⚠️ TRIGGERS CIRCULAR DEPENDENCY
            val adapter = AnnotationQueryHandlerAdapter(
                handler,
                config.parameterResolverFactory()
            )
            adapter.subscribe(queryBus)

            handler
        }
    }
}
```
**Result**: ❌ **Circular dependency error** - application fails to start
**Error**:
```
Error creating bean with name 'springAxonConfigurer':
  Requested bean is currently in creation:
    Is there an unresolvable circular reference?
```

**Why it failed**:
1. `ConfigurerModule.configureModule()` executes during `springAxonConfigurer` bean creation
2. Calling `config.queryBus()` triggers Spring to create the `queryBus` bean
3. `queryBus` depends on `springAxonConfiguration`
4. `springAxonConfiguration` depends on `springAxonConfigurer` (currently being created)
5. **Circular dependency**: `springAxonConfigurer` → `ConfigurerModule.lambda` → `queryBus` → `springAxonConfiguration` → `springAxonConfigurer`

---

## Additional Context

### Multi-Tenant Query Flow (Important for Solution)
1. Controller extracts `tenantId` from JWT via `TenantContext`
2. Query payload includes `tenantId` field (all queries MUST have this)
3. `TenantQueryHandlerInterceptor` (registered successfully) extracts `tenantId` from query
4. Sets PostgreSQL session variable: `SET LOCAL app.current_tenant = 'tenant-id'`
5. **@QueryHandler method executes** (THIS IS WHERE WE'RE STUCK)
6. Repository queries use RLS with session variable for tenant isolation

### Query Types
```kotlin
// Shared API module
data class FindWidgetsQuery(
    val tenantId: String,  // Required for tenant isolation
    val page: Int,
    val size: Int,
    val category: String?,
    val search: String?,
    val sort: List<String>,
)

data class FindWidgetByIdQuery(
    val widgetId: String,
    val tenantId: String,  // Required for tenant isolation
)
```

### Return Type Considerations
- Axon 4.9.4 has issues with Kotlin generic types (type erasure)
- Our workaround: Use raw `PagedResponse` type with `@Suppress("UNCHECKED_CAST")`
- `PagedResponse<T>` is a custom DTO (not Spring Data Page) to avoid Axon's parameterized interface limitations

---

## Constraints & Requirements

### MUST Preserve
1. ✅ **@Transactional support** - Repository queries must run in read-only transactions
2. ✅ **Multi-tenant isolation** - TenantQueryHandlerInterceptor must execute before handler
3. ✅ **Kotlin open class** - Required for CGLIB proxying with @Transactional
4. ✅ **Dependency injection** - Handler needs repository and objectMapper from Spring
5. ✅ **Both @QueryHandler methods** - Support for single-item and paginated queries

### CANNOT Change
1. ❌ **Axon Framework version** - Stuck on 4.9.4 (v5 migration planned but not now)
2. ❌ **Kotlin version** - 2.2.20 pinned for tool compatibility
3. ❌ **Spring Boot version** - 3.5.6 locked for Spring Modulith
4. ❌ **Architecture pattern** - CQRS/ES with Axon is fundamental

### Acceptable Trade-offs
- ✅ Manual configuration if necessary (e.g., explicit bean registration)
- ✅ Framework-level workarounds (e.g., custom auto-configuration)
- ✅ Slightly increased startup time (if it solves the timing issue)
- ✅ Additional configuration classes (if they work)

---

## Success Criteria

### Primary Goal
✅ GET `/widgets` endpoint returns **200 OK** with query results instead of `NoHandlerForQueryException`

### Validation Test
```bash
# 1. Start application
./gradlew :products:widget-demo:bootRun

# 2. Get JWT token
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/eaf-test/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=eaf-admin" \
  -d "username=testuser" \
  -d "password=testuser" | jq -r '.access_token')

# 3. Test query
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/widgets
```

**Expected Response** (200 OK):
```json
{
  "content": [...],
  "totalElements": 0,
  "page": 0,
  "size": 20,
  "totalPages": 0
}
```

**Current Response** (500 Error):
```json
{
  "type": "/problems/execution-error",
  "detail": "Query execution failed: No handler found for [com.axians.eaf.api.widget.queries.FindWidgetsQuery]..."
}
```

---

## Research Questions

### Primary Questions
1. **Axon 4.9.4 Query Handler Registration**: What is the CORRECT way to register query handlers in Axon Framework 4.9.4 with Spring Boot 3.x to ensure they're discovered BEFORE MessageHandlerConfigurer initialization?

2. **Timing Control**: How can we force a Spring bean to be created BEFORE Axon's MessageHandlerConfigurer scans for handlers (~25-30s into startup)? Are there annotations, bean definitions, or configuration patterns that guarantee early initialization?

3. **ConfigurerModule Pattern**: Is there a correct way to use `ConfigurerModule` for registering query handlers (not just interceptors) that triggers annotation scanning WITHOUT causing circular dependencies?

### Secondary Questions
4. **Spring Modulith Compatibility**: Does Spring Modulith's module boundary enforcement or initialization order affect Axon handler discovery? Are there known issues?

5. **Kotlin-Specific Considerations**: Are there Kotlin-specific patterns or annotations that affect Spring bean initialization timing differently than Java?

6. **Axon Auto-Configuration**: Does Axon's Spring Boot auto-configuration provide any properties or hooks to control handler discovery timing? (e.g., `axon.axonserver.enabled`, `axon.eventhandling.processors.*`)

7. **Alternative Registration APIs**: Besides `registerMessageHandler()`, are there other Axon Configurer methods that properly register query handlers with annotation scanning? (e.g., `registerQueryHandler()`, `registerHandlerDefinition()`, etc.)

### Diagnostic Questions
8. **MessageHandlerConfigurer Lifecycle**: Can we delay MessageHandlerConfigurer initialization until after all @Component beans are created? Is there a Spring Boot property or configuration for this?

9. **Manual Query Routing**: If automatic discovery is impossible, can we manually register query-to-handler mappings AFTER initialization without breaking tenant interceptor execution order?

10. **Axon 5.x Comparison**: How does Axon Framework 5.x handle this differently? (Context for future migration planning)

---

## Recommendations from Investigation

Based on extensive troubleshooting, three potential paths forward:

### Option A: Framework-Level Solution (Preferred)
Find the correct Axon/Spring configuration pattern that ensures handler beans are created before MessageHandlerConfigurer scans. This might involve:
- Undiscovered Axon configuration properties
- Spring Boot bean initialization ordering mechanisms
- Spring Modulith integration hooks

### Option B: Axon 5.x Migration (Future)
Upgrade to Axon Framework 5.x, which may have resolved these initialization timing issues. However, this is a LARGE change affecting the entire framework and is NOT feasible for immediate resolution.

### Option C: Community/Expert Consultation
This appears to be the first query handler in this codebase (interceptors work fine), and there's no working example to follow. Consulting:
- Axon Framework community forums
- AxonIQ support channels
- Spring Framework experts familiar with bean initialization ordering

---

## Deliverables Requested

1. **Working Solution Code**: Kotlin code showing the correct registration pattern
2. **Explanation**: Why this approach succeeds where others failed
3. **Timing Analysis**: Evidence that handler is now registered before MessageHandlerConfigurer scan
4. **Trade-offs**: Any limitations or side effects of the solution
5. **Testing Guidance**: How to verify the solution works
6. **Documentation**: Any Axon/Spring configuration properties or patterns used

---

## Reference Materials

### Axon Framework 4.9.4 Documentation
- Query Handling: https://docs.axoniq.io/reference-guide/axon-framework/queries
- Spring Boot Integration: https://docs.axoniq.io/reference-guide/axon-framework/spring-boot-integration
- Configuration API: https://docs.axoniq.io/reference-guide/axon-framework/spring-boot-integration/spring-configuration-api

### Known Working Patterns in This Codebase
- **Interceptor Registration**: See AxonConfiguration (framework/cqrs) - all interceptors register successfully using `configurer.onInitialize { config -> config.queryBus().registerHandlerInterceptor(interceptor) }`
- **Command Handlers**: Work fine with simple @Component annotation (no timing issues observed)
- **Event Handlers**: Work fine with @Component + @EventHandler annotations

### Environment
- **Build Tool**: Gradle 9.1.0 with Kotlin DSL
- **Test Framework**: Kotest 6.0.3 (not JUnit)
- **IDE**: Assumed IntelliJ IDEA (Kotlin project)

---

## Contact & Collaboration

This research request is part of **Story 9.2** in our Epic 9 (Widget Demo CRUD Operations). The goal is to complete the query side of CQRS implementation for our multi-tenant widget management system.

**Timeline**: Seeking solution as soon as possible - this blocks all read operations in our widget-demo product module.

**Success Indicator**: When a developer can run the test script above and receive a 200 OK response with empty or populated widget list.

---

## Appendix: Timing Evidence

Extracted from Spring Boot startup logs (`/tmp/backend-component.log`):

```json
{
  "@timestamp": "2025-10-19T21:54:22.201+02:00",
  "message": "Identified candidate component class: .../WidgetQueryHandler.class"
}
{
  "@timestamp": "2025-10-19T21:54:25.487+02:00",
  "message": "Creating shared instance of singleton bean 'widgetQueryHandlerConfiguration'"
}
{
  "@timestamp": "2025-10-19T21:54:25.670+02:00",
  "message": "Creating shared instance of singleton bean 'MessageHandlerConfigurer$$Axon$$EVENT'"
}
{
  "@timestamp": "2025-10-19T21:54:25.671+02:00",
  "message": "Creating shared instance of singleton bean 'MessageHandlerConfigurer$$Axon$$QUERY'"
}
{
  "@timestamp": "2025-10-19T21:54:25.674+02:00",
  "message": "Autowiring by type from bean name 'springAxonConfigurer' via factory method to bean named 'MessageHandlerConfigurer$$Axon$$QUERY'"
}
{
  "@timestamp": "2025-10-19T21:54:26.187+02:00",
  "message": "Creating shared instance of singleton bean 'widgetQueryHandler'"
}
```

**Analysis**: 516ms gap between MessageHandlerConfigurer creation and handler bean creation is the root cause.

---

**END OF RESEARCH REQUEST**
