# Coding Standards

**Enterprise Application Framework (EAF) v1.0**
**Last Updated:** 2025-11-01
**Enforcement:** Automated via ktlint 1.7.1, Detekt 1.23.8, Konsist 0.17.3

---

## Overview

This document establishes comprehensive coding standards for EAF v1.0 to ensure consistency, maintainability, and quality across the entire codebase. These standards are enforced through automated tools and are mandatory for all contributors.

**Related Documents:**
- [Architecture Document](../architecture.md) - 89 architectural decisions
- [Test Strategy](test-strategy.md) - Constitutional TDD and testing patterns
- [Tech Spec](../tech-spec.md) - Technical implementation details

---

## Critical Requirements (MUST Follow)

### ⚠️ Zero-Tolerance Policies

These requirements are **MANDATORY** and violations will cause build failures:

1. **NO wildcard imports** - Every import must be explicit
2. **NO generic exceptions** - Always use specific exception types *except* in infrastructure interceptors (see Infrastructure Interceptor Exception Pattern)
3. **Kotest ONLY** - JUnit is explicitly forbidden
4. **Version Catalog REQUIRED** - All versions in `gradle/libs.versions.toml`
5. **Zero violations** - ktlint, Detekt, and Konsist must pass without warnings

**Exception to Policy #2**: Infrastructure interceptors (metrics, logging, tracing) may catch generic `Exception` **only when**:
- Purpose is pure observability (record metrics/logs then re-throw)
- Exception is immediately re-thrown unchanged
- Pattern is documented with `@Suppress("TooGenericExceptionCaught")` and justification comment

---

## Kotlin Standards

### Import Management

**Rule**: Every import must be explicit. Wildcard imports are forbidden.

```kotlin
// ✅ CORRECT - Explicit imports
import com.axians.eaf.framework.core.domain.AggregateRoot
import com.axians.eaf.framework.security.TenantContext
import org.springframework.stereotype.Service
import arrow.core.Either
import arrow.core.left
import arrow.core.right

// ❌ FORBIDDEN - Wildcard imports
import com.axians.eaf.framework.core.domain.*
import org.springframework.stereotype.*
import arrow.core.*
```

**Enforcement**: Configure ktlint in `.editorconfig`:

```
[*.{kt,kts}]
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647
```

---

### Exception Handling

#### Domain Layer: Arrow Either Pattern

**Rule**: Domain services return `Either<DomainError, Success>` for business operations.

```kotlin
// ✅ CORRECT - Specific exception types with Arrow Either
class WidgetService {
    fun createWidget(command: CreateWidgetCommand): Either<DomainError, Widget> = either {
        // Domain validation
        ensure(command.name.isNotBlank()) {
            DomainError.ValidationError(
                field = "name",
                constraint = "required",
                invalidValue = command.name
            )
        }

        // Repository interaction
        val existing = repository.findBySku(command.sku, command.tenantId).bind()
        ensure(existing == null) {
            DomainError.BusinessRuleViolation(
                rule = "widget.sku.unique",
                reason = "Widget with SKU already exists for this tenant"
            )
        }

        Widget.create(command).bind()
    }
}

// ❌ FORBIDDEN - Generic exceptions
fun badExample() {
    throw Exception("Something went wrong")           // Generic exception
    throw RuntimeException("Error occurred")         // Generic runtime exception
    throw IllegalArgumentException("Bad input")      // Too generic for domain logic
}

// ✅ CORRECT - Domain-specific error hierarchy
sealed class DomainError {
    data class ValidationError(
        val field: String,
        val constraint: String,
        val invalidValue: Any?
    ) : DomainError()

    data class BusinessRuleViolation(
        val rule: String,
        val reason: String
    ) : DomainError()

    data class TenantIsolationViolation(
        val requestedTenant: String,
        val actualTenant: String
    ) : DomainError()

    data class AggregateNotFound(
        val aggregateType: String,
        val aggregateId: String
    ) : DomainError()
}
```

#### Infrastructure Interceptor Exception Pattern

**Rule**: Infrastructure concerns (metrics, logging, tracing) may catch generic `Exception` ONLY for observability with immediate re-throw.

```kotlin
// ✅ CORRECT - Infrastructure interceptor pattern
@Component
class CommandMetricsInterceptor(
    private val meterRegistry: MeterRegistry
) : MessageHandlerInterceptor<CommandMessage<*>> {
    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        interceptorChain: InterceptorChain
    ): Any {
        val command = unitOfWork.message
        val start = Instant.now()

        return try {
            val result = interceptorChain.proceed()
            recordSuccess(command, start)
            result
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception  // LEGITIMATE: Observability only
        ) {
            // Legitimate use of generic exception in infrastructure interceptor:
            // We record metrics for ANY exception type then re-throw immediately
            recordFailure(command, start, ex)
            throw ex  // CRITICAL: Exception propagates unchanged
        }
    }

    private fun recordSuccess(command: CommandMessage<*>, start: Instant) {
        val duration = Duration.between(start, Instant.now())
        meterRegistry.counter(
            "eaf.commands.total",
            "type", command.payloadType.simpleName,
            "status", "success"
        ).increment()
        meterRegistry.timer("eaf.commands.duration", "type", command.payloadType.simpleName)
            .record(duration)
    }

    private fun recordFailure(command: CommandMessage<*>, start: Instant, ex: Exception) {
        val duration = Duration.between(start, Instant.now())
        meterRegistry.counter(
            "eaf.commands.total",
            "type", command.payloadType.simpleName,
            "status", "error",
            "exception", ex.javaClass.simpleName
        ).increment()
    }
}
```

**Requirements for legitimate generic catch:**
1. ✅ Purpose is observability/telemetry only (record metrics/logs then re-throw)
2. ✅ No exception handling logic - just instrumentation
3. ✅ Exception is immediately re-thrown unchanged
4. ✅ Pattern is clearly documented with `@Suppress("TooGenericExceptionCaught")` and justification comment

```kotlin
// ❌ INCORRECT - Business logic using generic catch
fun processWidget(widget: Widget) {
    try {
        widgetService.process(widget)
    } catch (ex: Exception) {  // BAD: Swallows specific exception context
        logger.error("Failed to process widget")
        return  // BAD: Exception handling logic, doesn't re-throw
    }
}
```

**Reference**: Story 5.2 (Context Injection Interceptors), Story 5.4 (Prometheus Metrics)

---

### Code Formatting

**Indentation**: 4 spaces (no tabs)
**Line Length**: 120 characters maximum
**Trailing Commas**: Required for multi-line declarations
**Final Newline**: Required in all files

```kotlin
// ✅ CORRECT - Proper formatting
data class CreateWidgetCommand(
    val widgetId: String,
    val tenantId: String,
    val name: String,
    val description: String?,
    val category: WidgetCategory,
    val metadata: Map<String, String> = emptyMap(), // Trailing comma
)

// ❌ INCORRECT - Missing trailing comma
data class BadCommand(
    val field1: String,
    val field2: String  // Missing trailing comma
)
```

---

## Architecture Standards

### Spring Modulith Module Configuration

**Rule**: Each module requires `@ApplicationModule` configuration for Spring Modulith boundary enforcement.

```kotlin
// ✅ CORRECT - Module metadata at package level
@file:ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core", "shared.api", "shared.testing"]
)

package com.axians.eaf.framework.security

import org.springframework.modulith.ApplicationModule

// OR as a class:
@ApplicationModule(
    displayName = "EAF CQRS Module",
    allowedDependencies = ["core", "security", "shared.api"]
)
class CqrsModule

// ❌ INCORRECT - Missing module configuration
package com.axians.eaf.framework.workflow  // No @ApplicationModule
```

**Validation**: Konsist 0.17.3 tests verify module dependencies match `allowedDependencies`.

**Reference**: Architecture Decision (Spring Modulith 1.4.4), Story 1.8 (Spring Modulith Enforcement)

---

### CQRS Pattern Implementation

#### Aggregate Structure

```kotlin
// ✅ CORRECT - Proper Axon aggregate
@Aggregate
class Widget {
    @AggregateIdentifier
    private lateinit var widgetId: String

    private lateinit var tenantId: String
    private lateinit var name: String
    private var status: WidgetStatus = WidgetStatus.DRAFT

    // Constructor command handler
    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        // Tenant validation
        val currentTenant = TenantContext().getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        apply(WidgetCreatedEvent(
            widgetId = command.widgetId,
            tenantId = command.tenantId,
            name = command.name,
            category = command.category
        ))
    }

    // Update command handler with Either
    @CommandHandler
    fun handle(command: UpdateWidgetCommand): Either<WidgetError, Unit> = either {
        // Dual-layer tenant validation
        val currentTenant = TenantContext().getCurrentTenantId()
        ensure(command.tenantId == currentTenant) {
            WidgetError.TenantIsolationViolation(command.tenantId, currentTenant)
        }
        ensure(this.tenantId == currentTenant) {
            WidgetError.TenantIsolationViolation(this.tenantId, currentTenant)
        }

        // Business validation
        ensure(status == WidgetStatus.ACTIVE) {
            WidgetError.BusinessRuleViolation(
                rule = "widget.must.be.active",
                reason = "Only active widgets can be updated"
            )
        }

        apply(WidgetUpdatedEvent(widgetId, command.name, command.description))
    }

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.tenantId = event.tenantId
        this.name = event.name
        this.status = WidgetStatus.ACTIVE
    }

    @EventSourcingHandler
    fun on(event: WidgetUpdatedEvent) {
        this.name = event.name
    }
}

// ❌ INCORRECT - Missing annotations or tenant validation
class BadWidget {
    // Missing @Aggregate
    private var id: String = ""  // Missing @AggregateIdentifier

    fun handleCommand(command: Any) {  // Missing @CommandHandler
        // No tenant validation
        // No event sourcing
    }
}
```

**Reference**: Story 2.5 (Widget Aggregate), Story 4.2 (Tenant Validation in Commands)

---

#### Query Handlers with Pagination

**⚠️ Axon Framework 4.12.1 Limitation**: Generic wrapper types like `PagedResponse<T>` require custom `ResponseType` implementation due to Java type erasure.

**Solution Options:**

**Option 1: Custom ResponseType (Reusable)**

```kotlin
// ✅ CORRECT - Custom ResponseType for PagedResponse<T>
// Location: shared/shared-api/src/main/kotlin/com/axians/eaf/shared/api/query/PagedResponseType.kt
class PagedResponseType<T> private constructor(
    private val elementType: Class<T>,
) : ResponseType<PagedResponse<T>> {
    override fun matches(responseType: Type): Boolean =
        when (responseType) {
            is ParameterizedType -> matchesParameterizedType(responseType)
            is Class<*> -> PagedResponse::class.java.isAssignableFrom(responseType)
            else -> false
        }

    private fun matchesParameterizedType(responseType: ParameterizedType): Boolean {
        val rawType = responseType.rawType
        val isRawTypeValid =
            rawType == PagedResponse::class.java ||
                (rawType is Class<*> && PagedResponse::class.java.isAssignableFrom(rawType))

        val typeArguments = responseType.actualTypeArguments
        return isRawTypeValid && typeArguments.isNotEmpty() && matchesElementType(typeArguments[0])
    }

    private fun matchesElementType(argType: Type): Boolean =
        when (argType) {
            is Class<*> -> elementType.isAssignableFrom(argType)
            is ParameterizedType -> elementType.isAssignableFrom(argType.rawType as Class<*>)
            else -> false
        }

    override fun responseMessagePayloadType(): Class<PagedResponse<T>> {
        @Suppress("UNCHECKED_CAST")
        return PagedResponse::class.java as Class<PagedResponse<T>>
    }

    override fun getExpectedResponseType(): Class<*> = PagedResponse::class.java

    @Suppress("UNCHECKED_CAST")
    override fun convert(response: Any?): PagedResponse<T>? = response as PagedResponse<T>?

    companion object {
        @JvmStatic
        fun <T> pagedInstanceOf(elementType: Class<T>): PagedResponseType<T> =
            PagedResponseType(elementType)
    }
}

// Query handler
@Component
class WidgetQueryHandler(
    private val repository: WidgetProjectionRepository,
) {
    @QueryHandler
    @Transactional(readOnly = true)
    fun handle(query: FindWidgetsQuery): PagedResponse<WidgetResponse> {
        // Implementation
    }
}

// Controller usage
@RestController
class WidgetController(private val queryGateway: QueryGateway) {
    @GetMapping("/api/v1/widgets")
    fun getWidgets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedResponse<WidgetResponse>> {
        val query = FindWidgetsQuery(page, size)
        val responseType = PagedResponseType.pagedInstanceOf(WidgetResponse::class.java)
        val response = queryGateway.query(query, responseType).get()
        return ResponseEntity.ok(response)
    }
}
```

**Option 2: Separate List + Count Queries (Simpler)**

```kotlin
// ✅ CORRECT - Separate queries (no custom ResponseType needed)
@QueryHandler
fun handle(query: FindWidgetsQuery): List<WidgetResponse> {
    return repository.findAll(query.page, query.size)
}

@QueryHandler
fun handle(query: CountWidgetsQuery): Long {
    return repository.count()
}

// Controller combines results
@GetMapping("/api/v1/widgets")
fun getWidgets(pageable: Pageable): ResponseEntity<PagedResponse<WidgetResponse>> {
    val items = queryGateway.query(
        FindWidgetsQuery(pageable.pageNumber, pageable.pageSize),
        ResponseTypes.multipleInstancesOf(WidgetResponse::class.java)
    ).get()

    val total = queryGateway.query(
        CountWidgetsQuery(),
        ResponseTypes.instanceOf(Long::class.java)
    ).get()

    val response = PagedResponse(
        content = items,
        totalElements = total,
        page = pageable.pageNumber,
        size = pageable.pageSize,
        totalPages = (total + pageable.pageSize - 1) / pageable.pageSize
    )
    return ResponseEntity.ok(response)
}
```

**Option 3: Domain-Specific Wrapper (Quick Fix)**

```kotlin
// ✅ CORRECT - Non-generic wrapper (no custom ResponseType needed)
data class WidgetPagedResponse(  // No generic parameter!
    val widgets: List<WidgetResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
)

@QueryHandler
fun handle(query: FindWidgetsQuery): WidgetPagedResponse {
    val widgets = repository.findAll(query.page, query.size)
    val total = repository.count()
    return WidgetPagedResponse(
        widgets = widgets,
        totalElements = total,
        page = query.page,
        size = query.size,
        totalPages = (total + query.size - 1) / query.size
    )
}

// Works with standard ResponseTypes
val response = queryGateway.query(
    query,
    ResponseTypes.instanceOf(WidgetPagedResponse::class.java)
).get()
```

**When to Use Each Approach**:
- **Custom ResponseType**: Reusable solution for multiple paginated queries across domains
- **Separate Queries**: Simplest for one-off pagination needs, avoids custom framework code
- **Domain-Specific Wrapper**: Quick fix for single domain, simpler but less reusable

**Reference**: Epic 2 (Story 2.8 Query Handlers), Architecture Decision #3 (Pagination Strategy)

---

### Multi-Tenancy Patterns

#### Tenant Context Usage

**Rule**: All business operations must validate tenant context.

```kotlin
// ✅ CORRECT - Tenant-aware command handler
@CommandHandler
constructor(command: CreateWidgetCommand) {
    val currentTenant = TenantContext().getCurrentTenantId()  // Throws if missing
    require(command.tenantId == currentTenant) {
        "Access denied: tenant context mismatch"  // Generic message (CWE-209 protection)
    }
    apply(WidgetCreatedEvent(command.widgetId, command.tenantId, command.name))
}

// ✅ CORRECT - Tenant-aware service with Either
@Service
class WidgetService(
    private val repository: WidgetRepository,
    private val tenantContext: TenantContext
) {
    fun createWidget(command: CreateWidgetCommand): Either<WidgetError, Widget> = either {
        val currentTenant = tenantContext.current()
            ?: return WidgetError.TenantIsolationViolation("unknown", "none").left()

        ensure(command.tenantId == currentTenant) {
            WidgetError.TenantIsolationViolation(command.tenantId, currentTenant)
        }

        val widget = Widget.create(command).bind()
        repository.save(widget).bind()
    }
}

// ❌ INCORRECT - Missing tenant validation
@CommandHandler
constructor(command: CreateWidgetCommand) {
    // No tenant validation - SECURITY RISK
    apply(WidgetCreatedEvent(command.widgetId, command.tenantId, command.name))
}
```

#### Tenant-Aware Entities

```kotlin
// ✅ CORRECT - Projection with TenantAware interface
@Entity
@Table(
    name = "widget_projection",
    indexes = [
        Index(name = "idx_widget_tenant_status", columnList = "tenant_id, status"),
        Index(name = "idx_widget_tenant_created", columnList = "tenant_id, created_at")
    ]
)
data class WidgetProjection(
    @Id
    val widgetId: String,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,  // Mandatory tenant ID

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: WidgetStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
) : TenantAware {
    override fun getTenantId(): String = tenantId
}

// ❌ INCORRECT - Missing tenant_id or TenantAware
@Entity
data class BadProjection(
    @Id
    val id: String,
    val name: String
    // Missing tenantId field
    // Missing TenantAware interface
)
```

#### Tenant Context Fallback for Observability

**Rule**: Infrastructure/observability code may use graceful fallback; business logic must fail-closed.

```kotlin
// ✅ CORRECT - Graceful fallback for observability infrastructure
class CustomMetrics(
    private val meterRegistry: MeterRegistry,
    private val tenantContext: TenantContext
) {
    fun recordCommandExecution(commandType: String, duration: Duration, success: Boolean) {
        val tenantTag = tenantContext.current() ?: "system"  // Fallback for infrastructure

        meterRegistry.counter(
            "eaf.commands.total",
            "type", commandType,
            "status", if (success) "success" else "error",
            "tenant_id", tenantTag  // Never null - always has value
        ).increment()

        meterRegistry.timer(
            "eaf.commands.duration",
            "type", commandType,
            "tenant_id", tenantTag
        ).record(duration)
    }
}

// ✅ CORRECT - Fail-closed for business logic
@CommandHandler
fun handle(command: UpdateWidgetCommand): Either<WidgetError, Unit> = either {
    val currentTenant = TenantContext().getCurrentTenantId()  // Throws if missing - NO FALLBACK

    ensure(command.tenantId == currentTenant) {
        WidgetError.TenantIsolationViolation(command.tenantId, currentTenant)
    }

    apply(WidgetUpdatedEvent(command.widgetId, command.name))
}

// ❌ INCORRECT - Fallback in business logic (SECURITY RISK)
@CommandHandler
constructor(command: CreateWidgetCommand) {
    val tenantId = TenantContext().current() ?: "default-tenant"  // DANGEROUS FALLBACK
    apply(WidgetCreatedEvent(tenantId = tenantId, ...))  // Could leak data across tenants
}
```

**When to use fallback pattern:**
- ✅ Metrics collection (observability infrastructure)
- ✅ Background system jobs (no user/tenant context)
- ✅ Health checks and actuator endpoints
- ✅ Cross-cutting logging/tracing infrastructure

**When to fail-closed (NO fallback):**
- ❌ Command handlers and aggregates
- ❌ API endpoints serving user data
- ❌ Query handlers returning tenant-specific data
- ❌ Repository operations on tenant-scoped entities

**Reference**: Story 4.1 (TenantContext ThreadLocal), Story 4.2 (Tenant Validation), Story 5.2 (Metrics with Fallback)

---

## Version Management

### Version Catalog Usage (MANDATORY)

**Rule**: All dependency versions MUST be centralized in `gradle/libs.versions.toml`.

```kotlin
// gradle/libs.versions.toml
[versions]
kotlin = "2.2.21"
spring-boot = "3.5.7"
spring-modulith = "1.4.4"
axon = "4.12.1"
arrow = "2.1.2"
kotest = "6.0.4"
postgresql = "42.7.8"
jooq = "3.20.8"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }
axon-spring-boot-starter = { module = "org.axonframework:axon-spring-boot-starter", version.ref = "axon" }
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }

[bundles]
axon-framework = ["axon-spring-boot-starter"]
kotest = ["kotest-framework-engine-jvm", "kotest-assertions-core-jvm", "kotest-property-jvm"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotest = { id = "io.kotest", version.ref = "kotest" }
```

```kotlin
// ✅ CORRECT - Use version catalog references
// framework/cqrs/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.bundles.axon.framework)
    implementation(libs.arrow.core)
    testImplementation(libs.bundles.kotest)
}

// ❌ FORBIDDEN - Hardcoded versions
plugins {
    kotlin("jvm") version "2.2.21"  // Forbidden - use version catalog
    id("org.springframework.boot") version "3.5.7"  // Forbidden
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.7")  // Forbidden
    implementation("org.axonframework:axon-spring-boot-starter:4.12.1")  // Forbidden
}
```

**Reference**: Story 1.4 (Create Version Catalog), Architecture Section 2 (Version Verification)

---

## Data Access Standards

### jOOQ Usage for Projections

**Rule**: Use jOOQ 3.20.8 for type-safe SQL in projection repositories.

```kotlin
// ✅ CORRECT - Type-safe jOOQ queries with tenant filtering
@Repository
class WidgetProjectionRepository(
    private val dsl: DSLContext
) {
    fun findByTenantId(tenantId: String, pageable: Pageable): List<WidgetProjection> {
        return dsl.select()
            .from(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.TENANT_ID.eq(tenantId))
            .and(WIDGET_PROJECTION.STATUS.eq(WidgetStatus.ACTIVE.name))
            .orderBy(WIDGET_PROJECTION.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetchInto(WidgetProjection::class.java)
    }

    fun searchWidgets(
        searchTerm: String,
        tenantId: String,
        pageable: Pageable
    ): Page<WidgetProjection> {
        val baseQuery = dsl.select()
            .from(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.TENANT_ID.eq(tenantId))
            .and(
                WIDGET_PROJECTION.NAME.containsIgnoreCase(searchTerm)
                    .or(WIDGET_PROJECTION.DESCRIPTION.containsIgnoreCase(searchTerm))
            )

        val totalCount = dsl.selectCount()
            .from(baseQuery)
            .fetchOne(0, Int::class.java) ?: 0

        val results = baseQuery
            .orderBy(WIDGET_PROJECTION.NAME.asc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetchInto(WidgetProjection::class.java)

        return PageImpl(results, pageable, totalCount.toLong())
    }

    fun countByTenant(tenantId: String): Long {
        return dsl.selectCount()
            .from(WIDGET_PROJECTION)
            .where(WIDGET_PROJECTION.TENANT_ID.eq(tenantId))
            .fetchOne(0, Long::class.java) ?: 0L
    }
}

// ❌ INCORRECT - String-based SQL queries
class BadRepository {
    fun findWidgets(tenantId: String): List<Widget> {
        val sql = "SELECT * FROM widget_projection WHERE tenant_id = ?"  // String SQL - not type-safe
        return jdbcTemplate.query(sql, WidgetRowMapper(), tenantId)
    }
}
```

**Reference**: Story 2.6 (jOOQ Configuration), Story 2.7 (Widget Projection Handler)

---

### Database Schema Standards

```sql
-- ✅ CORRECT - Proper tenant isolation with indexes
CREATE TABLE widget_projection (
    widget_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- Tenant-aware unique constraints
    CONSTRAINT uk_widget_name_tenant UNIQUE (name, tenant_id),

    -- Tenant-aware indexes (tenant_id FIRST for RLS efficiency)
    INDEX idx_widget_tenant_status (tenant_id, status),
    INDEX idx_widget_tenant_category (tenant_id, category),
    INDEX idx_widget_tenant_created (tenant_id, created_at DESC)
);

-- Row-level security (Layer 3 isolation)
ALTER TABLE widget_projection ENABLE ROW LEVEL SECURITY;

CREATE POLICY widget_tenant_isolation ON widget_projection
    FOR ALL
    USING (tenant_id::text = current_setting('app.current_tenant'));

-- ❌ INCORRECT - Missing tenant isolation
CREATE TABLE bad_widget (
    widget_id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE,  -- Missing tenant_id in constraint
    status VARCHAR(20),
    -- Missing tenant_id column entirely
    -- Missing indexes
    -- No RLS policy
);
```

**Reference**: Story 2.2 (PostgreSQL Event Store), Story 4.4 (PostgreSQL RLS Policies)

---

## Security Standards

### Input Validation

```kotlin
// ✅ CORRECT - Comprehensive validation
data class CreateWidgetRequest(
    @field:NotBlank(message = "Widget name is required")
    @field:Size(min = 1, max = 255, message = "Widget name must be 1-255 characters")
    val name: String,

    @field:Size(max = 1000, message = "Description cannot exceed 1000 characters")
    val description: String? = null,

    @field:NotNull(message = "Category is required")
    val category: WidgetCategory,

    @field:Valid
    val metadata: Map<@NotBlank String, @NotBlank String> = emptyMap()
) {
    fun toCommand(tenantId: String, userId: String): CreateWidgetCommand {
        return CreateWidgetCommand(
            widgetId = UUID.randomUUID().toString(),
            tenantId = tenantId,
            userId = userId,
            name = name.trim(),
            description = description?.trim(),
            category = category,
            metadata = metadata
        )
    }
}

// ❌ INCORRECT - Missing validation
data class BadRequest(
    val name: String,  // No @NotBlank
    val anything: Any  // Unsafe type, no validation
)
```

### Security Annotations

```kotlin
// ✅ CORRECT - Proper security configuration
@RestController
@RequestMapping("/api/v1/widgets")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Widgets", description = "Widget management operations")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new widget")
    fun createWidget(
        @RequestBody @Valid request: CreateWidgetRequest,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<WidgetResponse> {
        val tenantId = authentication.token.getClaimAsString("tenant_id")
        val command = request.toCommand(tenantId, authentication.name)

        return commandGateway.send<String>(command).get().let { widgetId ->
            ResponseEntity
                .created(URI.create("/api/v1/widgets/$widgetId"))
                .body(WidgetResponse(widgetId, request.name, request.category))
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    @RequiresTenant
    @Operation(summary = "List widgets for current tenant")
    fun listWidgets(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<PagedResponse<WidgetResponse>> {
        val tenantId = authentication.token.getClaimAsString("tenant_id")
        val query = FindWidgetsQuery(tenantId, page, size)
        // Implementation
    }
}

// ❌ INCORRECT - Missing security annotations
@RestController
class BadController {
    @PostMapping("/api/widgets")
    fun createWidget(@RequestBody request: Any): Any {
        // No @PreAuthorize
        // No @SecurityRequirement
        // No tenant extraction
        // No validation
    }
}
```

**Reference**: Story 3.9 (RBAC API Endpoints), Architecture (10-Layer JWT Validation)

---

## Testing Standards

### Kotest Framework (MANDATORY)

**Rule**: All tests MUST use Kotest 6.0.4. JUnit is explicitly forbidden.

```kotlin
// ✅ CORRECT - Kotest BehaviorSpec with Nullable Pattern
class WidgetServiceTest : BehaviorSpec({
    Given("a widget service with nullable dependencies") {
        val repository = NullableWidgetRepository()
        val eventBus = NullableEventBus()
        val service = WidgetService(repository, eventBus)

        When("creating a valid widget") {
            val command = CreateWidgetCommand(
                widgetId = "test-widget-id",
                tenantId = "test-tenant",
                name = "Test Widget",
                category = WidgetCategory.STANDARD
            )

            val result = service.createWidget(command)

            Then("widget should be created successfully") {
                result.shouldBeRight()
                repository.findById("test-widget-id").shouldBeRight {
                    it.shouldNotBeNull()
                    it.name shouldBe "Test Widget"
                }
            }
        }

        When("creating a widget with duplicate name") {
            // Setup: Create first widget
            repository.save(Widget.create(CreateWidgetCommand(...)))

            val duplicateCommand = CreateWidgetCommand(
                widgetId = "test-widget-2",
                tenantId = "test-tenant",
                name = "Test Widget",  // Duplicate name
                category = WidgetCategory.STANDARD
            )

            val result = service.createWidget(duplicateCommand)

            Then("should return business rule violation") {
                result.shouldBeLeft()
                result.leftValue.shouldBeInstanceOf<DomainError.BusinessRuleViolation>()
                result.leftValue.should {
                    (it as DomainError.BusinessRuleViolation).rule shouldBe "widget.name.unique"
                }
            }
        }
    }
})

// ❌ FORBIDDEN - JUnit (annotations ignored by Kotest)
class BadTest : FunSpec({
    @Test  // ← This annotation is COMPLETELY IGNORED by Kotest
    fun badTest() {
        // This test will NEVER execute
    }

    @Disabled  // ← This has NO EFFECT in Kotest
    test("disabled test") {
        // This will still run regardless of @Disabled annotation
    }

    @BeforeEach  // ← Ignored - use beforeTest {} instead
    fun setup() {
        // Never executed
    }
})
```

**CRITICAL**: Never mix JUnit and Kotest annotations - JUnit annotations are completely ignored by Kotest test runner.

**Reference**: Test Strategy (Constitutional TDD), Story 8.8 (TDD Compliance Validation)

---

### Spring Boot Integration Test Pattern (MANDATORY)

**Rule**: Use `@Autowired` field injection + `init` block for @SpringBootTest with Kotest.

**Story 4.6 Lessons - Plugin Order CRITICAL**:

```kotlin
// Product module build.gradle.kts
plugins {
    id("eaf.testing")     // FIRST - Establishes Kotest DSL
    id("eaf.spring-boot") // SECOND - After Kotest setup
    id("eaf.quality-gates")
}

dependencies {
    // Explicit Kotest dependencies to override Spring Boot BOM
    integrationTestImplementation("io.kotest:kotest-runner-junit5:6.0.4")
    integrationTestImplementation("io.kotest:kotest-assertions-core:6.0.4")
    integrationTestImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
}
```

**Integration Test Pattern:**

```kotlin
// ✅ CORRECT - @Autowired field injection + init block
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WidgetIntegrationTest : FunSpec() {

    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        test("should create widget via command gateway") {
            // Given
            val command = CreateWidgetCommand(
                widgetId = UUID.randomUUID().toString(),
                tenantId = "test-tenant",
                name = "Integration Test Widget",
                category = WidgetCategory.PREMIUM
            )

            // When
            val result = commandGateway.sendAndWait<String>(command, Duration.ofSeconds(5))

            // Then
            result shouldBe command.widgetId

            // And projection should be updated
            eventually(Duration.ofSeconds(10)) {
                val query = FindWidgetByIdQuery(command.widgetId, command.tenantId)
                val widget = queryGateway.query(query, WidgetProjection::class.java).join()

                widget.shouldNotBeNull()
                widget.name shouldBe command.name
                widget.category shouldBe command.category
            }
        }

        test("should expose widget via REST API") {
            // Test using mockMvc
            mockMvc.perform(
                post("/api/v1/widgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"API Test Widget","category":"STANDARD"}""")
                    .header("Authorization", "Bearer ${createTestJwt()}")
            )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("API Test Widget"))
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // Start Testcontainers
            PostgresTestContainer.start()
            KeycloakTestContainer.start()
            RedisTestContainer.start()

            // Configure Spring properties
            registry.add("spring.datasource.url") { PostgresTestContainer.jdbcUrl }
            registry.add("spring.datasource.username") { PostgresTestContainer.username }
            registry.add("spring.datasource.password") { PostgresTestContainer.password }
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") {
                KeycloakTestContainer.issuerUri
            }
            registry.add("spring.data.redis.host") { RedisTestContainer.host }
            registry.add("spring.data.redis.port") { RedisTestContainer.port }
        }
    }
}

// ❌ FORBIDDEN - Constructor injection (causes lifecycle timing conflict → 150+ compilation errors)
@SpringBootTest
class BadIntegrationTest(
    private val commandGateway: CommandGateway,  // ← FAILS: Required before Spring context ready
    private val mockMvc: MockMvc
) : FunSpec({
    test("will not compile") {
        // Compilation error: Unresolved reference
    }
})
```

**Root Cause**: Constructor parameters required before Spring context initialization → circular dependency.

**Reference**: Story 4.6 (Multi-Tenant Widget Demo), Test Strategy (Integration Testing Patterns)

---

## Code Quality Enforcement

### ktlint Configuration

**File**: `.editorconfig`

```
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.{kt,kts}]
indent_style = space
indent_size = 4
max_line_length = 120

# Kotlin-specific rules
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

# Import organization (NO wildcard imports)
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647

# Code style
ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL
```

**Enforcement**: Story 1.10 (Git Hooks), CI/CD pipeline

---

### Detekt Configuration

**File**: `config/detekt/detekt.yml`

```yaml
build:
  maxIssues: 0  # Zero violations policy

complexity:
  ComplexMethod:
    threshold: 15
    active: true
  LongMethod:
    threshold: 60
    active: true
  TooManyFunctions:
    thresholdInFiles: 20
    thresholdInClasses: 15

style:
  MagicNumber:
    ignoreNumbers: ['-1', '0', '1', '2', '100', '1000']
  WildcardImport:
    excludeImports: []  # No wildcard imports allowed
  UnusedImports:
    active: true
  MaxLineLength:
    maxLineLength: 120

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
  ClassNaming:
    classPattern: '[A-Z][a-zA-Z0-9]*'
  PackageNaming:
    packagePattern: '[a-z]+(\.[a-z][A-Za-z0-9]*)*'

exceptions:
  TooGenericExceptionCaught:
    active: true
    allowedExceptionNameRegex: '_|ignore|expected'
    # Suppression required for infrastructure interceptors
```

**Enforcement**: Story 1.10 (Git Hooks), CI/CD pipeline

---

### Konsist Architecture Tests

**File**: `shared/testing/src/konsistTest/kotlin/com/axians/eaf/testing/ArchitectureTest.kt`

```kotlin
class ArchitectureTest : FunSpec({

    context("Module Dependencies") {
        test("modules should not have circular dependencies") {
            Konsist.scopeFromProject()
                .modules()
                .assertDoesNotHaveCircularDependencies()
        }

        test("framework modules should not depend on products") {
            Konsist.scopeFromProject()
                .modules()
                .filter { it.name.startsWith("framework") }
                .assertDoesNotDependOnModules { it.name.startsWith("products") }
        }

        test("products may depend on framework modules") {
            Konsist.scopeFromProject()
                .modules()
                .filter { it.name.startsWith("products") }
                .assertCanDependOnModules { it.name.startsWith("framework") }
        }
    }

    context("Coding Standards") {
        test("no wildcard imports allowed") {
            Konsist.scopeFromProject()
                .imports
                .assertNone { it.isWildcard }
        }

        test("all aggregates must be annotated with @Aggregate") {
            Konsist.scopeFromProject()
                .classes()
                .withNameEndingWith("Aggregate")
                .filter { !it.name.startsWith("Abstract") }
                .assertTrue { it.hasAnnotation("org.axonframework.modelling.command.Aggregate") }
        }

        test("all test classes must use Kotest") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.name.endsWith("Test") || it.name.endsWith("Spec") }
                .assertTrue {
                    it.parents().any { parent ->
                        parent.name in listOf("FunSpec", "BehaviorSpec", "DescribeSpec", "StringSpec")
                    }
                }
        }

        test("no JUnit annotations in Kotest tests") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.parents().any { p -> p.name.endsWith("Spec") } }
                .assertNone {
                    it.annotations.any { annotation ->
                        annotation.name in listOf("Test", "BeforeEach", "AfterEach", "Disabled")
                    }
                }
        }
    }

    context("Security Standards") {
        test("REST controllers must have security annotations") {
            Konsist.scopeFromProject()
                .classes()
                .withAnnotationOf<RestController>()
                .assertTrue {
                    it.hasAnnotation("PreAuthorize") ||
                    it.hasAnnotation("SecurityRequirement") ||
                    it.functions().any { func -> func.hasAnnotation("PreAuthorize") }
                }
        }

        test("tenant-aware entities must have tenant_id field") {
            Konsist.scopeFromProject()
                .classes()
                .withAnnotationOf<Entity>()
                .assertTrue {
                    it.properties().any { prop -> prop.name == "tenantId" }
                }
        }

        test("tenant-aware entities must implement TenantAware") {
            Konsist.scopeFromProject()
                .classes()
                .withAnnotationOf<Entity>()
                .filter { it.properties().any { prop -> prop.name == "tenantId" } }
                .assertTrue { it.hasParent { parent -> parent.name == "TenantAware" } }
        }
    }

    context("Multi-Tenancy Standards") {
        test("command handlers must validate tenant context") {
            Konsist.scopeFromProject()
                .functions()
                .withAnnotationOf<CommandHandler>()
                .assertTrue {
                    val body = it.text
                    body.contains("TenantContext") &&
                    (body.contains("getCurrentTenantId()") || body.contains("current()"))
                }
        }
    }
})
```

**Reference**: Story 1.8 (Spring Modulith Enforcement), Story 8.1 (Architecture Deviation Audit)

---

## Gradle Convention Plugins

### Plugin Structure (Story 1.3)

**Location**: `build-logic/src/main/kotlin/`

**Convention Plugins:**
- `eaf.kotlin-common.gradle.kts` - Common Kotlin configuration
- `eaf.spring-boot.gradle.kts` - Spring Boot configuration
- `eaf.testing.gradle.kts` - Kotest and Testcontainers setup
- `eaf.quality-gates.gradle.kts` - ktlint, Detekt, Konsist enforcement

**Example: eaf.kotlin-common.gradle.kts**

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-Xcontext-receivers"
        )
    }
}
```

**Reference**: Story 1.3 (Implement Convention Plugins)

---

## Related Documentation

- **[Architecture Document](../architecture.md)** - 89 architectural decisions, version verification
- **[Test Strategy](test-strategy.md)** - Constitutional TDD, 7-layer testing defense
- **[Tech Spec](../tech-spec.md)** - FR-to-Epic mapping, implementation patterns
- **[PRD](../PRD.md)** - Product requirements, success criteria
- **[Stories](../sprint-artifacts/stories/)** - 112 implementation-ready stories with code examples

---

**Next Steps**: Review [Test Strategy](test-strategy.md) for testing implementation details, then proceed to story implementation following these standards.
