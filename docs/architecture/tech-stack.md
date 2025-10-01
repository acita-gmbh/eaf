# Technology Stack

## Overview

The EAF technology stack is carefully curated to deliver enterprise-grade reliability, developer productivity, and long-term maintainability. All technology choices are production-tested and version-locked to ensure compatibility and stability.

## Critical Version Constraints

⚠️ **CRITICAL**: These version constraints are MANDATORY and must not be changed without architecture review:

| Technology | Version | Constraint Type | Rationale |
|------------|---------|----------------|-----------|
| **Kotlin** | 2.2.20 | CURRENT | Latest stable with detekt workaround |
| **Spring Boot** | 3.5.6 | CURRENT | Latest stable with full compatibility |
| **JVM** | 21 LTS | Required | Spring Boot 3.5.6 baseline requirement |

## Core Technologies

### Language & Runtime

```kotlin
// gradle/libs.versions.toml
[versions]
kotlin = "2.2.20"          # CURRENT - Latest stable
java = "21"                # LTS requirement
```

**Kotlin 2.2.20 Features Used**:
- Null safety for reduced runtime errors
- Data classes for immutable domain objects
- Coroutines for async processing
- Extension functions for clean APIs
- Sealed classes for domain modeling

**JVM 21 LTS Benefits**:
- Virtual threads for improved concurrency
- Pattern matching (preview features)
- Enhanced garbage collection
- Security improvements
- Long-term support until 2031

### Framework Stack

```kotlin
// Spring Boot 3.5.6 (CURRENT)
[versions]
spring-boot = "3.5.6"     # CURRENT - Latest stable
spring-modulith = "1.4.3" # Module boundary enforcement
axon = "4.12.1"            # CQRS/Event Sourcing
arrow = "1.2.4"           # Functional programming
```

#### Spring Boot 3.5.6 (CURRENT)

**Why This Version**:
- Latest stable with full Spring Modulith 1.4.3 compatibility
- Stable foundation for enterprise applications
- Complete Jakarta EE 9+ migration
- Native compilation ready (GraalVM)

**Key Dependencies**:
```kotlin
dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.modulith.starter.core)
}
```

#### Axon Framework 4.12.1

**CQRS/Event Sourcing Implementation**:
```kotlin
[versions]
axon = "4.12.1"

[libraries]
axon-spring-boot-starter = { module = "org.axonframework:axon-spring-boot-starter", version.ref = "axon" }
axon-test = { module = "org.axonframework:axon-test", version.ref = "axon" }

[bundles]
axon-framework = ["axon-spring-boot-starter"]
```

**Migration Path**: Axon 5.x migration planned after initial implementation
- Current: 4.9.4 (stable, production-tested)
- Target: 5.x (improved performance, modern APIs)
- Timeline: Post-MVP implementation

#### Arrow 1.2.4 (Functional Programming)

**Either Types for Error Handling**:
```kotlin
[versions]
arrow = "1.2.4"

[libraries]
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
arrow-fx = { module = "io.arrow-kt:arrow-fx-coroutines", version.ref = "arrow" }
```

**Usage Pattern**:
```kotlin
fun createProduct(command: CreateProductCommand): Either<DomainError, Product> = either {
    // Validation and business logic
    val validatedCommand = validateCommand(command).bind()
    val product = Product.create(validatedCommand).bind()
    repository.save(product).bind()
}
```

### Database Stack

#### PostgreSQL 16.1+ (Primary Database)

**Version Requirements**:
```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16.1-alpine
    environment:
      POSTGRES_VERSION: "16.1"  # Minimum version
```

**Mandatory Optimizations**:
```sql
-- BRIN Indexes for time-series event data
CREATE INDEX CONCURRENTLY idx_events_timestamp_brin
ON domain_event_entry USING BRIN (time_stamp, tenant_id);

-- Time-based partitioning
CREATE TABLE domain_event_entry_2025_01
PARTITION OF domain_event_entry
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

-- Connection pooling settings
max_connections = 200
shared_buffers = 256MB
effective_cache_size = 1GB
```

**Prohibited Alternatives**:
- ❌ H2 Database (forbidden in all environments)
- ❌ MySQL (limited event sourcing support)
- ❌ SQLite (not enterprise-grade)

#### jOOQ (Type-Safe SQL)

**Read Projection Queries**:
```kotlin
[versions]
jooq = "3.19.15"

[libraries]
jooq = { module = "org.jooq:jooq", version.ref = "jooq" }
jooq-codegen = { module = "org.jooq:jooq-codegen", version.ref = "jooq" }
```

**Code Generation**:
```kotlin
// Generated type-safe queries
val products = dsl.select()
    .from(PRODUCT_PROJECTION)
    .where(PRODUCT_PROJECTION.TENANT_ID.eq(tenantId))
    .and(PRODUCT_PROJECTION.STATUS.eq(ProductStatus.ACTIVE))
    .fetchInto(ProductProjection::class.java)
```

### Security Stack

#### Keycloak 25.0.6 (Identity Provider)

```yaml
# docker-compose.yml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:25.0.6
    command: start-dev
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
```

**Features Used**:
- OpenID Connect / OAuth 2.0
- Multi-realm tenant isolation
- Role-based access control
- Token revocation support
- Admin REST API

#### Spring Security 6.x

```kotlin
[libraries]
spring-security-oauth2-jose = { module = "org.springframework.security:spring-security-oauth2-jose" }
spring-security-oauth2-client = { module = "org.springframework.security:spring-security-oauth2-client" }
```

**JWT Validation Configuration**:
```kotlin
@Configuration
class SecurityConfig {
    @Bean
    fun jwtDecoder(): JwtDecoder {
        return JwtDecoders.fromOidcIssuerLocation("http://localhost:8180/realms/eaf")
    }
}
```

#### Prometheus Access Control

Access to the `/actuator/prometheus` endpoint is restricted to operators with the `ROLE_eaf-admin` role. This role must be seeded in the Keycloak realm, and operators must present a valid JWT with this role to scrape metrics.

### Workflow Engine

#### Flowable 7.1.x (BPMN)

```kotlin
[versions]
flowable = "7.1.0"

[libraries]
flowable-spring-boot-starter = { module = "org.flowable:flowable-spring-boot-starter", version.ref = "flowable" }
```

**Integration with Axon**:
```kotlin
@Component
class FlowableAxonBridge(
    private val commandGateway: CommandGateway
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val command = createCommandFromExecution(execution)
        commandGateway.sendAndWait<Any>(command)
    }
}
```

**Starter Dependency**: The project uses the full `flowable-spring-boot-starter` dependency to enable all Flowable engines (Process, CMMN, DMN, etc.) for future use cases, even though only the Process engine is currently used.

**Schema Isolation (Technical Debt - ARCH-001)**: Flowable tables are currently created in the `public` schema. A follow-up story is planned to move them to a dedicated `flowable` schema using a multi-step remediation plan involving schema pre-creation and a custom `EngineConfigurationConfigurer`.

## Development & Quality Tools

### Testing Framework (Kotest 6.0.3 Native Runner)

⚠️ **CRITICAL**: JUnit is explicitly FORBIDDEN. Use Kotest 6.0.3 with native runner exclusively.

#### Kotest 6.0.3 Migration Success (2025-09)

**Migration Achievement**: ✅ **Complete success** - migrated from 5.9.1 to 6.0.3 with enhanced testing experience
- **27 tests passing** with zero failures across all modules
- **Constitutional TDD compliance** maintained with enhanced developer experience
- **Native runner approach** providing beautiful colorized Given-When-Then output

#### JVM-Specific Dependencies (Native Approach)

```kotlin
[versions]
kotest = "6.0.3"           # Native Kotest with enhanced developer experience
testcontainers = "1.21.3"  # Latest stable for integration testing

[libraries]
# JVM-specific Kotest 6.0.3 dependencies (native approach)
kotest-framework-engine-jvm = { module = "io.kotest:kotest-framework-engine-jvm", version.ref = "kotest" }
kotest-runner-junit5-jvm = { module = "io.kotest:kotest-runner-junit5-jvm", version.ref = "kotest" }  # For custom source sets only
kotest-assertions-core-jvm = { module = "io.kotest:kotest-assertions-core-jvm", version.ref = "kotest" }
kotest-property-jvm = { module = "io.kotest:kotest-property-jvm", version.ref = "kotest" }
kotest-extensions-spring = { module = "io.kotest.extensions:kotest-extensions-spring", version = "1.3.0" }
kotest-extensions-pitest = { module = "io.kotest:kotest-extensions-pitest", version.ref = "kotest" }  # CRITICAL: GroupId changed to io.kotest in 6.0.3
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }

[bundles]
kotest = ["kotest-framework-engine-jvm", "kotest-assertions-core-jvm", "kotest-property-jvm", "kotest-extensions-spring"]

[plugins]
kotest-plugin = { id = "io.kotest", version.ref = "kotest" }
```

#### Migration Comparison: 5.9.1 → 6.0.3

| **Aspect** | **Kotest 5.9.1 (Previous)** | **Kotest 6.0.3 (Current)** | **Benefits** |
|------------|------------------------------|------------------------------|--------------|
| **Plugin** | JUnit Platform integration | `id("io.kotest")` native plugin | Direct execution, better IDE integration |
| **Dependencies** | `kotest-runner-junit5` | `kotest-framework-engine-jvm` | JVM-optimized, no multiplatform overhead |
| **Test Runner** | `./gradlew test` | `./gradlew jvmKotest` | Enhanced output, motivation quotes |
| **Execution** | JUnit Platform bridge | Native Kotest engine | Faster, more reliable test discovery |
| **Output** | Basic JUnit-style reporting | Colorized Given-When-Then formatting | Beautiful developer experience |
| **Performance** | Standard execution | Enhanced with native optimizations | Maintained Constitutional TDD speed |

#### Native Kotest Workflow

```bash
# Development workflow with native Kotest
./gradlew jvmKotest              # Run main tests with beautiful output
./gradlew :module:jvmKotest      # Run specific module tests
./gradlew integrationTest        # Run integration tests
./gradlew konsistTest            # Run architecture tests
./gradlew check                  # Run all tests in sequence

# Enhanced output example:
>> Kotest
- Test hard, test often
- Test plan has 2 specs

1. WidgetTest
+ Given: Widget aggregate creation
    + When: creating a widget with valid data
        - Then: widget should be created successfully ✅ OK
```

#### Technical Migration Details

**⚠️ CRITICAL**: Hybrid Approach for Custom Source Sets

The Kotest Gradle plugin has a **known limitation**: it only creates the `jvmKotest` task for the main `test` source set. Custom source sets (`integrationTest`, `konsistTest`) require a hybrid approach:

| Source Set | Task Type | Runner | Dependencies Required |
|------------|-----------|--------|----------------------|
| `test` | `jvmKotest` (automatic) | Native Kotest | `kotest-framework-engine-jvm` only |
| `integrationTest` | `Test` (manual) | JUnit Platform | `kotest-runner-junit5-jvm` required |
| `konsistTest` | `Test` (manual) | JUnit Platform | `kotest-runner-junit5-jvm` required |

**Plugin Configuration** (TestingConventionPlugin):
```kotlin
plugins {
    id("io.kotest") version "6.0.3"  // Native Kotest plugin (for main tests)
}

// Main test dependencies (native runner)
dependencies {
    testImplementation("io.kotest:kotest-framework-engine-jvm")
    testImplementation("io.kotest:kotest-assertions-core-jvm")
    testImplementation("io.kotest:kotest-property-jvm")
    testImplementation("io.kotest.extensions:kotest-extensions-spring")
}

// Custom source set dependencies (JUnit Platform runner)
dependencies {
    integrationTestImplementation("io.kotest:kotest-framework-engine-jvm")
    integrationTestImplementation("io.kotest:kotest-runner-junit5-jvm")  // Required!
    integrationTestImplementation("io.kotest:kotest-assertions-core-jvm")

    konsistTestImplementation("io.kotest:kotest-framework-engine-jvm")
    konsistTestImplementation("io.kotest:kotest-runner-junit5-jvm")  // Required!
    konsistTestImplementation("io.kotest:kotest-assertions-core-jvm")
}

// Custom source set task configuration
val integrationTestTask = tasks.register("integrationTest", Test::class.java) {
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()  // Uses JUnit Platform to discover Kotest tests
}
```

**Key Changes Made**:
- ✅ **Eliminated JUnit Platform**: No more `native Kotest runner` configuration
- ✅ **JVM-Specific Dependencies**: Fixed `ClassNotFoundException: io.kotest.mpp.ReflectionKt`
- ✅ **Native Plugin**: `id("io.kotest")` provides enhanced developer experience
- ✅ **CI Integration**: Updated workflow to use `jvmKotest` task

**Test Structure**:
```kotlin
class ProductServiceTest : BehaviorSpec({
    Given("a product service") {
        When("creating a product") {
            Then("product should be saved") {
                // Test implementation
            }
        }
    }
})
```

**❌ PROHIBITED JUnit Usage**:
```kotlin
// NEVER USE THESE
@Test            // JUnit annotation - FORBIDDEN
@Disabled        // JUnit annotation - IGNORED by Kotest
@BeforeEach      // JUnit annotation - FORBIDDEN
```

### Code Quality Tools

#### ktlint 1.7.1 (Code Formatting)

```kotlin
[versions]
ktlint = "1.7.1"  # Latest stable with Kotlin 2.2.20 support

[plugins]
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version = "12.1.1" }
```

**Configuration**:
```kotlin
ktlint {
    version.set(libs.versions.ktlint)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.JSON)
    }
}
```

**Zero Violations Policy**: All code must pass ktlint without warnings

#### Detekt 1.23.8 (Static Analysis)

```kotlin
[versions]
detekt = "1.23.8"  # Latest stable with Kotlin compatibility workaround

[plugins]
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

**Configuration (detekt.yml)**:
```yaml
style:
  WildcardImport:
    active: true
    excludeImports: []  # No wildcard imports allowed

complexity:
  ComplexMethod:
    active: true
    threshold: 15
```

#### Konsist 0.17.3 (Architecture Testing)

```kotlin
[versions]
konsist = "0.17.3"

[libraries]
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
```

**Module Boundary Verification**:
```kotlin
@Test
fun `modules should not have circular dependencies`() {
    Konsist.scopeFromProject()
        .modules()
        .assertDoesNotHaveCircularDependencies()
}
```

#### Pitest 1.19.0-rc.1 (Mutation Testing)

**IMPORTANT UPDATE**: Upgraded to 1.19.0-rc.1 for Gradle 9.1.0 compatibility.

```kotlin
[versions]
pitest = "1.19.0-rc.1"  # Gradle 9 compatible (fixes 'baseDir' property issue)

[plugins]
pitest = { id = "info.solidsoft.pitest", version.ref = "pitest" }

// Configuration
pitest {
    targetClasses.set(setOf("com.axians.eaf.*"))
    excludedClasses.set(setOf("*Test*", "*Spec*"))
    mutationThreshold.set(80) // Minimum 80% mutation coverage
}
```

### Build System

#### Gradle 9.1.0 (Build Tool)

**IMPORTANT UPDATE (2025-01)**: Upgraded from Gradle 8.14 to 9.1.0 to resolve Kotlin version compatibility with Kotest 6.0.3.

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip
```

**Why Gradle 9.1.0**:
- Embeds Kotlin 2.2.0 (required for Kotest 6.0.3 compatibility)
- Resolves `NoSuchMethodError: kotlin.time.Clock` issues
- Enables native Kotest runner without workarounds
- Full compatibility with all enterprise features

**Version Catalog (gradle/libs.versions.toml)**:
```toml
[versions]
# Core (Current Stable)
kotlin = "2.2.20"
spring-boot = "3.5.6"
java = "21"

# Framework
axon = "4.12.1"
spring-modulith = "1.4.3"
arrow = "1.2.4"

# Database
postgresql = "42.7.4"
jooq = "3.19.15"

# Security
spring-security = "6.4.2"

# Testing (Kotest only)
kotest = "6.0.3"
kotest-plugin = "6.0.3"  # Native Gradle plugin
testcontainers = "1.21.3"
pitest = "1.19.0-rc.1"  # Gradle 9 compatible version

# Quality
ktlint = "1.7.1"
detekt = "1.23.8"
konsist = "0.17.3"

# Workflow
flowable = "7.1.0"

# Documentation
dokka = "1.9.10"
```

**Convention Plugins**:
```kotlin
// build-logic/src/main/kotlin/eaf.kotlin-common.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
```

## Infrastructure Requirements

### Container Runtime

**Supported Platforms**:
```dockerfile
# Multi-architecture support
FROM openjdk:21-jdk-slim

# Platform support
ARG TARGETPLATFORM
ARG BUILDPLATFORM

# Supported: linux/amd64, linux/arm64, linux/ppc64le
```

**Requirements**:
- Docker 24.x / Podman 4.x
- Multi-architecture image support
- Resource limits enforcement

### Minimum System Requirements

| Environment | vCPU | Memory | Storage | Notes |
|-------------|------|---------|---------|-------|
| **Development** | 2 | 4GB | 20GB | Local development |
| **Testing** | 4 | 8GB | 50GB | CI/CD pipelines |
| **Staging** | 4 | 8GB | 100GB | Pre-production |
| **Production** | 8+ | 16GB+ | 500GB+ | Customer hosting |

### Network Requirements

```yaml
# Required ports
ports:
  - "8080:8080"    # Application
  - "5432:5432"    # PostgreSQL
  - "8180:8180"    # Keycloak
  - "6379:6379"    # Redis
  - "9090:9090"    # Prometheus (monitoring)
  - "3000:3000"    # React Admin
```

## Compatibility Matrix

### Supported Architectures

| Architecture | Status | Target Platform | Notes |
|--------------|--------|-----------------|-------|
| **linux/amd64** | ✅ Primary | Intel/AMD x86_64 | Most common deployment |
| **linux/arm64** | ✅ Supported | Apple Silicon, AWS Graviton | Growing adoption |
| **linux/ppc64le** | ✅ Supported | IBM Power Systems | Enterprise requirement |

### Version Compatibility

```mermaid
graph TD
    K["Kotlin 2.2.20<br/>CURRENT"] --> SB["Spring Boot 3.5.6<br/>CURRENT"]
    SB --> SM["Spring Modulith 1.4.3<br/>Compatible"]
    SB --> SS["Spring Security 6.4.2<br/>Compatible"]
    K --> KL["ktlint 1.7.1<br/>Compatible"]
    K --> D["Detekt 1.23.8<br/>Workaround"]

    SB --> A["Axon 4.9.4<br/>Current"]
    A --> A5["Axon 5.x<br/>Future Migration"]

    PG["PostgreSQL 16.1+<br/>Minimum"] --> J["jOOQ 3.19.15<br/>Compatible"]
```

## Quality Gate Configuration

### CI/CD Pipeline Tools

```yaml
# .github/workflows/quality.yml
jobs:
  formatting:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew ktlintCheck  # Zero violations required

  static-analysis:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew detekt      # Zero violations required

  architecture:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew konsistTest # Architecture compliance

  mutation-testing:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew pitest      # 80% minimum coverage
```

### Quality Enforcement

All quality gates are enforced with **zero violations policy**:

1. **ktlint**: Code formatting must be perfect
2. **Detekt**: No static analysis violations
3. **Konsist**: Architecture rules must pass
4. **Pitest**: 80% minimum mutation coverage
5. **Test Coverage**: 85% minimum line coverage

## Migration Considerations

### Axon Framework 5.x Migration

**Current State**: Axon 4.9.4 (stable)
**Target State**: Axon 5.x (planned)

**Migration Timeline**:
1. Complete initial implementation on 4.9.4
2. Evaluate 5.x stability and features
3. Plan migration during maintenance window
4. Implement migration with backward compatibility

**Breaking Changes to Expect**:
- Event upcasting improvements
- Configuration simplification
- Performance optimizations
- API modernization

### Spring Boot Upgrades

**Current Version**: 3.5.6 (fully compatible with Spring Modulith 1.4.3)
**Future Considerations**: Monitor Spring Modulith compatibility

## Integration Testing Lessons (Story 4.6/4.7)

### Kotest + Spring Boot Configuration

**CRITICAL DISCOVERY** (Story 4.6): Plugin application order affects compilation in product modules.

#### Plugin Conflicts in Product Modules

**Problem**: Products modules using @SpringBootTest + Kotest fail with 150+ compilation errors
- Symptom: "Unresolved reference 'test'" on all Kotest DSL functions
- Root Cause: Multiple TestingConventionPlugin applications + Spring Boot dependency override

**Plugin Application Chain** (widget-demo):
```
eaf.spring-boot → eaf.kotlin-common → TestingConventionPlugin (1st)
eaf.testing → TestingConventionPlugin (2nd - duplicate)
eaf.quality-gates → eaf.kotlin-common → TestingConventionPlugin (3rd - triple)
```

**Solution** (validated by 3 external research sources):

```kotlin
// products/*/build.gradle.kts - MANDATORY plugin order
plugins {
    id("eaf.testing")     // FIRST - Kotest DSL setup before Spring Boot
    id("eaf.spring-boot") // SECOND - After Kotest established
    id("eaf.quality-gates")
}

dependencies {
    // REQUIRED: Override Spring Boot BOM with explicit Kotest versions
    integrationTestImplementation("io.kotest:kotest-runner-junit5:6.0.3")
    integrationTestImplementation("io.kotest:kotest-assertions-core:6.0.3")
    integrationTestImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
}
```

#### @SpringBootTest Pattern Requirements

**WORKING Pattern** (framework modules + fixed product modules):
```kotlin
@SpringBootTest
@ActiveProfiles("test")
class MyIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())
        test("my test") { /* dependencies available */ }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.startAll()
            registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
        }
    }
}
```

**FORBIDDEN Pattern** (causes 150+ compilation errors):
```kotlin
// ❌ Constructor injection with FunSpec lambda
class MyTest(
    private val mockMvc: MockMvc  // ← Timing conflict
) : FunSpec({
    test("my test") { /* Never reached - compilation fails */ }
})
```

**Reference**: framework/security/TenantContextFilterIntegrationTest.kt

#### Framework vs Product Module Differences

| Aspect | Framework Modules | Product Modules |
|--------|------------------|-----------------|
| **Plugins** | eaf.kotlin-common + eaf.testing | eaf.testing + eaf.spring-boot + eaf.quality-gates |
| **TestingConventionPlugin** | 1x application | 3x application (causes conflicts) |
| **Dependencies** | Minimal Spring Boot | Full Spring Boot BOM (overrides Kotest) |
| **Pattern** | @Autowired field injection | Must use @Autowired field injection |
| **Compilation** | ✅ Works | ❌ Fails without plugin order fix |

**Solution Impact**: Epic 4 unblocked, Epic 8 pattern established, framework consistency achieved

---

## Tool Integration

### IDE Requirements

**IntelliJ IDEA** (Recommended):
```kotlin
// .editorconfig
[*.kt]
ij_kotlin_imports_layout = *
ij_kotlin_code_style_defaults = KOTLIN_OFFICIAL
```

**VS Code** (Supported):
- Kotlin Language Server
- Gradle extension
- Test runner integration

### Local Development Tools

```bash
# Required tools
java -version      # Java 21
docker --version   # Docker 24.x
gradle --version   # Gradle 9.1.0
kotlin -version    # Kotlin 2.2.20

# Optional but recommended
helm version       # Kubernetes deployments
kubectl version    # Kubernetes management
```

## Performance Considerations

### JVM Tuning

```bash
# Production JVM flags
JAVA_OPTS="-Xmx2g -Xms2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+EnableJVMCI"
```

### Database Optimization

```sql
-- PostgreSQL configuration
shared_preload_libraries = 'pg_stat_statements'
max_connections = 200
shared_buffers = 256MB
effective_cache_size = 1GB
random_page_cost = 1.1
```

## Related Documentation

- **[High-Level Architecture](high-level-architecture.md)** - System overview and patterns
- **[System Components](components.md)** - Implementation using these technologies
- **[Development Workflow](development-workflow.md)** - Setup procedures and tooling
- **[Coding Standards](coding-standards-revision-2.md)** - Implementation guidelines

---

**Next Steps**: Review [System Components](components.md) for implementation examples using this technology stack, then proceed to [Development Workflow](development-workflow.md) for setup procedures.