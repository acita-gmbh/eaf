# Coding Standards (Revision 2)

## Overview

This document establishes comprehensive coding standards for the Enterprise Application Framework (v0.1) to ensure consistency, maintainability, and quality across the entire codebase. These standards are enforced through automated tools and are mandatory for all contributors.

## Critical Requirements (MUST Follow)

### ⚠️ Zero-Tolerance Policies

These requirements are **MANDATORY** and violations will cause build failures:

1. **NO wildcard imports** - Every import must be explicit
2. **NO generic exceptions** - Always use specific exception types
3. **Kotest ONLY** - JUnit is explicitly forbidden
4. **Version Catalog REQUIRED** - All versions in `gradle/libs.versions.toml`
5. **Zero violations** - ktlint, detekt, and konsist must pass without warnings

## Kotlin Standards

### Import Management

```kotlin
// ✅ CORRECT - Explicit imports
import com.axians.eaf.domain.Product
import com.axians.eaf.domain.ProductRepository
import com.axians.eaf.domain.ProductStatus
import org.springframework.stereotype.Service
import arrow.core.Either
import arrow.core.left
import arrow.core.right

// ❌ FORBIDDEN - Wildcard imports
import com.axians.eaf.domain.*
import org.springframework.stereotype.*
import arrow.core.*
```

**Enforcement**: Configure ktlint to prevent wildcard imports:

```kotlin
// .editorconfig
[*.kt]
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647
```

### Exception Handling

```kotlin
// ✅ CORRECT - Specific exception types
class ProductService {
    fun createProduct(command: CreateProductCommand): Either<DomainError, Product> = either {
        // Domain validation
        ensure(command.sku.matches(SKU_PATTERN)) {
            DomainError.ValidationError(
                field = "sku",
                constraint = "pattern",
                invalidValue = command.sku
            )
        }

        // Repository interaction
        val existingProduct = repository.findBySku(command.sku).bind()
        ensure(existingProduct == null) {
            DomainError.BusinessRuleViolation(
                rule = "product.sku.unique",
                reason = "Product with SKU already exists"
            )
        }

        Product.create(command).bind()
    }
}

// ❌ FORBIDDEN - Generic exceptions
fun badExample() {
    throw Exception("Something went wrong")           // Generic exception
    throw RuntimeException("Error occurred")         // Generic runtime exception
    throw IllegalArgumentException("Bad input")      // Too generic for domain logic
}

// ✅ CORRECT - Domain-specific exceptions
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
}
```

### Testing Standards

```kotlin
// ✅ CORRECT - Kotest BehaviorSpec
class ProductServiceTest : BehaviorSpec({
    Given("a product service with nullable dependencies") {
        val repository = nullable<ProductRepository>()
        val eventBus = nullable<EventBus>()
        val service = ProductService(repository, eventBus)

        When("creating a valid product") {
            val command = CreateProductCommand(
                productId = "test-id",
                tenantId = "test-tenant",
                name = "Test Product",
                sku = "TST-123456",
                price = BigDecimal("99.99")
            )

            val result = service.createProduct(command)

            Then("product should be created successfully") {
                result.shouldBeRight()
                repository.findById("test-id").shouldNotBeNull()
            }
        }

        When("creating a product with invalid SKU") {
            val command = CreateProductCommand(
                productId = "test-id",
                tenantId = "test-tenant",
                name = "Test Product",
                sku = "INVALID",
                price = BigDecimal("99.99")
            )

            val result = service.createProduct(command)

            Then("should return validation error") {
                result.shouldBeLeft()
                result.leftValue.shouldBeInstanceOf<DomainError.ValidationError>()
            }
        }
    }
})

// ❌ FORBIDDEN - JUnit (annotations completely ignored by Kotest)
class BadTest : FunSpec({
    @Test  // ← This annotation is IGNORED by Kotest
    fun badTest() {
        // This test will NEVER execute
    }

    @Disabled  // ← This has NO EFFECT in Kotest
    test("disabled test") {
        // This will still run regardless of @Disabled
    }
})
```

### Version Catalog Usage

```kotlin
// gradle/libs.versions.toml
[versions]
kotlin = "2.2.20"
spring-boot = "3.5.6"
axon = "4.12.1"
arrow = "1.2.4"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web", version.ref = "spring-boot" }
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }

[bundles]
spring-boot = ["spring-boot-starter-web", "spring-boot-starter-data-jpa"]

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
```

```kotlin
// ✅ CORRECT - Use version catalog
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.arrow.core)
    implementation(libs.bundles.spring.boot)
}

// ❌ FORBIDDEN - Hardcoded versions
plugins {
    kotlin("jvm") version "2.2.20"  // Forbidden
    id("org.springframework.boot") version "3.5.6"  // Forbidden
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.5.6")  // Forbidden
}
```

## Architecture Standards

### Spring Modulith Configuration

```kotlin
// ✅ CORRECT - Module metadata configuration
@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core", "shared.api", "shared.testing"]
)
class SecurityModule

// Package-level configuration for Kotlin
@file:ApplicationModule(
    displayName = "EAF CQRS Module",
    allowedDependencies = ["core", "shared.api"]
)

package com.axians.eaf.cqrs

// ❌ INCORRECT - Missing module configuration
class BadModule  // No @ApplicationModule annotation
```

### CQRS Pattern Implementation

```kotlin
// ✅ CORRECT - Aggregate with proper annotations
@Aggregate
class Product {
    @AggregateIdentifier
    private lateinit var productId: String
    private lateinit var tenantId: String
    private var status: ProductStatus = ProductStatus.DRAFT

    @CommandHandler
    constructor(command: CreateProductCommand) {
        // Validation and event application
        apply(ProductCreatedEvent(
            productId = command.productId,
            tenantId = command.tenantId,
            // ... other properties
        ))
    }

    @CommandHandler
    fun handle(command: UpdateProductCommand): Either<DomainError, Unit> = either {
        // Business logic with Arrow Either
        ensure(status == ProductStatus.ACTIVE) {
            DomainError.BusinessRuleViolation(
                rule = "product.must.be.active",
                reason = "Only active products can be updated"
            )
        }

        apply(ProductUpdatedEvent(productId, command.name))
    }

    @EventSourcingHandler
    fun on(event: ProductCreatedEvent) {
        this.productId = event.productId
        this.tenantId = event.tenantId
        this.status = ProductStatus.ACTIVE
    }
}

// ❌ INCORRECT - Missing annotations or improper structure
class BadAggregate {
    // Missing @Aggregate annotation
    private var id: String = ""  // Missing @AggregateIdentifier

    fun handleCommand(command: Any) {  // Missing @CommandHandler
        // No event sourcing
    }
}
```

### Multi-Tenancy Patterns

```kotlin
// ✅ CORRECT - Tenant-aware implementation
@RequiresTenant
@Service
class ProductService(
    private val productRepository: ProductRepository
) {

    fun createProduct(command: CreateProductCommand): Either<DomainError, Product> = either {
        val currentTenant = TenantContext.current()
            ?: return DomainError.TenantIsolationViolation(
                requestedTenant = "unknown",
                actualTenant = "none"
            ).left()

        // Ensure command tenant matches current context
        ensure(command.tenantId == currentTenant.tenantId) {
            DomainError.TenantIsolationViolation(
                requestedTenant = command.tenantId,
                actualTenant = currentTenant.tenantId
            )
        }

        // Repository call automatically filtered by tenant
        val product = Product.create(command).bind()
        productRepository.save(product).bind()
    }
}

// ✅ CORRECT - Tenant-aware entity
@Entity
@Table(name = "product_projection")
data class ProductProjection(
    @Id
    val productId: String,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: String,  // Mandatory tenant ID

    // ... other properties
) : TenantAware {
    override fun getTenantId(): String = tenantId
}

// ❌ INCORRECT - Missing tenant isolation
@Service
class BadService {
    fun createProduct(command: CreateProductCommand) {
        // No tenant context checking
        // No tenant validation
        repository.save(product)  // Could save to wrong tenant
    }
}
```

### Error Handling Patterns

```kotlin
// ✅ CORRECT - Arrow Either for domain operations
fun createProduct(command: CreateProductCommand): Either<DomainError, Product> = either {
    // Validation
    ensure(command.name.isNotBlank()) {
        DomainError.ValidationError("name", "not_blank", command.name)
    }

    // Business logic
    val product = Product.create(command).bind()
    repository.save(product).bind()
}

// ✅ CORRECT - Controller error handling
@PostMapping
fun createProduct(@RequestBody request: CreateProductRequest): ResponseEntity<ProductResponse> {
    val command = request.toCommand()

    return productService.createProduct(command).fold(
        ifLeft = { error -> throw error.toHttpException() },
        ifRight = { product ->
            ResponseEntity
                .created(URI.create("/api/v1/products/${product.productId}"))
                .body(product.toResponse())
        }
    )
}

// ❌ INCORRECT - Exception-based domain logic
fun badCreateProduct(command: CreateProductCommand): Product {
    if (command.name.isBlank()) {
        throw IllegalArgumentException("Name cannot be blank")  // Generic exception
    }

    return repository.save(Product.create(command))  // Exceptions propagate
}
```

## Data Access Standards

### jOOQ Usage

```kotlin
// ✅ CORRECT - Type-safe jOOQ queries
@Repository
class ProductProjectionRepository(
    private val dsl: DSLContext
) {

    fun findByTenantId(tenantId: String): List<ProductProjection> {
        return dsl.select()
            .from(PRODUCT_PROJECTION)
            .where(PRODUCT_PROJECTION.TENANT_ID.eq(tenantId))
            .and(PRODUCT_PROJECTION.STATUS.eq(ProductStatus.ACTIVE.name))
            .orderBy(PRODUCT_PROJECTION.NAME.asc())
            .fetchInto(ProductProjection::class.java)
    }

    fun searchProducts(
        searchTerm: String,
        tenantId: String,
        pageable: Pageable
    ): Page<ProductProjection> {
        val baseQuery = dsl.select()
            .from(PRODUCT_PROJECTION)
            .where(PRODUCT_PROJECTION.TENANT_ID.eq(tenantId))
            .and(
                PRODUCT_PROJECTION.NAME.containsIgnoreCase(searchTerm)
                    .or(PRODUCT_PROJECTION.SKU.containsIgnoreCase(searchTerm))
            )

        val totalCount = dsl.selectCount()
            .from(baseQuery)
            .fetchOne(0, Int::class.java) ?: 0

        val results = baseQuery
            .orderBy(PRODUCT_PROJECTION.NAME.asc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetchInto(ProductProjection::class.java)

        return PageImpl(results, pageable, totalCount.toLong())
    }
}

// ❌ INCORRECT - String-based queries
class BadRepository {
    fun findProducts(tenantId: String): List<Product> {
        val sql = "SELECT * FROM products WHERE tenant_id = ?"  // String SQL
        return jdbcTemplate.query(sql, ProductRowMapper(), tenantId)
    }
}
```

### Database Constraints

```sql
-- ✅ CORRECT - Proper tenant isolation with indexes
CREATE TABLE product_projection (
    product_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    -- Unique constraint includes tenant_id
    CONSTRAINT uk_product_sku_tenant UNIQUE (sku, tenant_id),

    -- Tenant-aware indexes
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_tenant_name (tenant_id, name),
    INDEX idx_tenant_created (tenant_id, created_at)
);

-- Row-level security
ALTER TABLE product_projection ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON product_projection
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

-- ❌ INCORRECT - Missing tenant isolation
CREATE TABLE bad_product (
    product_id UUID PRIMARY KEY,
    sku VARCHAR(50) UNIQUE,  -- Missing tenant_id in unique constraint
    name VARCHAR(255),
    -- Missing tenant_id column entirely
    -- No RLS policy
);
```

## Security Standards

### Authentication and Authorization

```kotlin
// ✅ CORRECT - Proper security annotations
@RestController
@RequestMapping("/api/v1/products")
@PreAuthorize("hasRole('USER')")
@SecurityRequirement(name = "BearerAuth")
class ProductController {

    @PostMapping
    @PreAuthorize("hasPermission(#request.tenantId, 'product', 'create')")
    fun createProduct(
        @RequestBody @Valid request: CreateProductRequest,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<ProductResponse> {
        // Implementation with tenant validation
    }

    @GetMapping("/{productId}")
    @RequiresTenant
    fun getProduct(
        @PathVariable productId: String,
        authentication: JwtAuthenticationToken
    ): ProductResponse {
        // Automatic tenant isolation
    }
}

// ❌ INCORRECT - Missing security
@RestController
class BadController {
    @PostMapping("/products")
    fun createProduct(@RequestBody request: Any): Any {
        // No security annotations
        // No tenant validation
        // No authentication required
    }
}
```

### Input Validation

```kotlin
// ✅ CORRECT - Comprehensive validation
data class CreateProductRequest(
    @field:NotBlank(message = "Product name is required")
    @field:Size(min = 1, max = 255, message = "Product name must be 1-255 characters")
    val name: String,

    @field:NotBlank(message = "SKU is required")
    @field:Pattern(
        regexp = "^[A-Z]{3}-[0-9]{6}$",
        message = "SKU must match pattern ABC-123456"
    )
    val sku: String,

    @field:DecimalMin(value = "0.0", inclusive = true, message = "Price cannot be negative")
    @field:Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
    val price: BigDecimal,

    @field:Valid
    val features: List<@Valid ProductFeatureRequest> = emptyList(),

    @field:Size(max = 1000, message = "Description cannot exceed 1000 characters")
    val description: String? = null
) {
    fun toCommand(tenantId: String, userId: String): CreateProductCommand {
        return CreateProductCommand(
            productId = UUID.randomUUID().toString(),
            tenantId = tenantId,
            userId = userId,
            name = name.trim(),
            sku = sku.uppercase(),
            description = description?.trim(),
            price = price,
            features = features.map { it.toFeature() }.toSet()
        )
    }
}

// ❌ INCORRECT - Missing validation
data class BadRequest(
    val name: String,  // No validation
    val price: String,  // Wrong type
    val anything: Any   // Unsafe type
)
```

## Testing Standards

### Nullable Pattern Implementation

```kotlin
// ✅ CORRECT - Nullable implementation with factory
class NullableProductRepository : ProductRepository, NullableFactory<ProductRepository> {
    private val storage = ConcurrentHashMap<String, Product>()
    private val events = mutableListOf<DomainEvent>()

    override fun save(product: Product): Either<DomainError, Product> {
        // Maintain business logic validation
        if (storage.values.any { it.sku == product.sku && it.tenantId == product.tenantId }) {
            return DomainError.BusinessRuleViolation(
                rule = "product.sku.unique",
                reason = "Product with SKU already exists"
            ).left()
        }

        storage[product.productId] = product
        events.add(ProductSavedEvent(product.productId))
        return product.right()
    }

    override fun findById(id: String): Either<DomainError, Product?> {
        return storage[id].right()
    }

    // Test utilities
    fun count(): Int = storage.size
    fun clear() {
        storage.clear()
        events.clear()
    }

    override fun createNull(): ProductRepository = this

    override fun createNull(state: Map<String, Any>): ProductRepository {
        val instance = NullableProductRepository()
        state["products"]?.let { products ->
            (products as List<Product>).forEach { product ->
                instance.storage[product.productId] = product
            }
        }
        return instance
    }
}

// ❌ INCORRECT - Mocking business logic
class BadTest : FunSpec({
    test("bad test with mocked business logic") {
        val mockRepository = mockk<ProductRepository>()
        every { mockRepository.save(any()) } returns Product().right()

        val service = ProductService(mockRepository)
        // You're not testing real business logic!
    }
})
```

### Integration Test Standards

```kotlin
// ✅ CORRECT - Proper integration test with Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ["spring.profiles.active=test"])
class ProductIntegrationTest : IntegrationTestBase() {

    @Autowired
    private lateinit var commandGateway: CommandGateway

    @Autowired
    private lateinit var queryGateway: QueryGateway

    init {
        context("Product CQRS Integration") {
            test("should create product and update projection") {
                // Given
                val command = CreateProductCommand(
                    productId = UUID.randomUUID().toString(),
                    tenantId = "test-tenant",
                    name = "Integration Test Product",
                    sku = "ITP-123456",
                    price = BigDecimal("199.99")
                )

                // When
                val result = commandGateway.sendAndWait<String>(command, Duration.ofSeconds(5))

                // Then
                result shouldBe command.productId

                // And projection should be updated
                eventually(Duration.ofSeconds(10)) {
                    val query = FindProductByIdQuery(command.productId, command.tenantId)
                    val projection = queryGateway.query(query, ProductProjection::class.java).join()

                    projection.shouldNotBeNull()
                    projection.name shouldBe command.name
                    projection.sku shouldBe command.sku
                }
            }
        }
    }
}

// ❌ INCORRECT - Using H2 or mocked infrastructure
@SpringBootTest
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb"  // FORBIDDEN
])
class BadIntegrationTest {
    @MockBean  // FORBIDDEN - Don't mock infrastructure in integration tests
    private lateinit var mockRepository: ProductRepository
}
```

## Code Quality Tools

### ktlint Configuration

```kotlin
// .editorconfig
[*.{kt,kts}]
indent_style = space
indent_size = 4
insert_final_newline = true
trim_trailing_whitespace = true
max_line_length = 120

# Kotlin-specific rules
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

# Import organization
ij_kotlin_name_count_to_use_star_import = 2147483647
ij_kotlin_name_count_to_use_star_import_for_members = 2147483647
```

### Detekt Configuration

```yaml
# config/detekt.yml
build:
  maxIssues: 0  # Zero violations policy

complexity:
  ComplexMethod:
    threshold: 15
  LongMethod:
    threshold: 60
  TooManyFunctions:
    thresholdInFiles: 20

style:
  MagicNumber:
    ignoreNumbers: ['-1', '0', '1', '2', '100', '1000']
  WildcardImport:
    excludeImports: []  # No wildcard imports allowed
  UnusedImports:
    active: true

naming:
  FunctionNaming:
    functionPattern: '[a-z][a-zA-Z0-9]*'
  ClassNaming:
    classPattern: '[A-Z][a-zA-Z0-9]*'
  PackageNaming:
    packagePattern: '[a-z]+(\.[a-z][A-Za-z0-9]*)*'
```

### Konsist Architecture Tests

```kotlin
// shared/testing/src/test/kotlin/com/axians/eaf/testing/ArchitectureTest.kt
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
    }

    context("Coding Standards") {
        test("no wildcard imports allowed") {
            Konsist.scopeFromProject()
                .imports
                .assertNone { it.isWildcard }
        }

        test("all aggregates must be annotated") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.hasAnnotation("Aggregate") }
                .assertTrue { it.hasAnnotation("org.axonframework.modelling.command.AggregateRoot") }
        }

        test("all test classes must use Kotest") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.name.endsWith("Test") }
                .assertTrue {
                    it.parents().any { parent ->
                        parent.name in listOf("FunSpec", "BehaviorSpec", "DescribeSpec")
                    }
                }
        }
    }

    context("Security Standards") {
        test("controllers must have security annotations") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.hasAnnotation("RestController") }
                .assertTrue {
                    it.hasAnnotation("PreAuthorize") ||
                    it.hasAnnotation("SecurityRequirement")
                }
        }

        test("tenant-aware entities must implement TenantAware") {
            Konsist.scopeFromProject()
                .classes()
                .filter { it.hasAnnotation("Entity") }
                .filter { it.properties().any { prop -> prop.name == "tenantId" } }
                .assertTrue { it.hasParent { parent -> parent.name == "TenantAware" } }
        }
    }
})
```

## Related Documentation

- **[Technology Stack](tech-stack.md)** - Tool versions and compatibility requirements
- **[Testing Strategy](test-strategy-and-standards-revision-3.md)** - Testing patterns and nullable implementation
- **[Development Workflow](development-workflow.md)** - Quality gate enforcement in development
- **[Security Architecture](security.md)** - Security implementation standards
- **[Error Handling Strategy](error-handling-strategy.md)** - Error handling patterns and conventions

---

**Next Steps**: Review [Development Workflow](development-workflow.md) for quality gate enforcement, then proceed to [Testing Strategy](test-strategy-and-standards-revision-3.md) for testing implementation details.