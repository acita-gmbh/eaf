# Unified Project Structure

(This is the mandatory Gradle Monorepo layout with Spring Modulith integration).

## Monorepo Structure with Spring Modulith Integration

```plaintext
eaf-monorepo/
├── .github/                     # CI/CD workflows (Story 1.4)
├── framework/                   # Core framework modules
│   ├── core/
│   │   ├── src/main/kotlin/com/axians/eaf/core/
│   │   │   ├── ModuleMetadata.kt  # @ApplicationModule configuration
│   │   │   └── domain/
│   │   └── build.gradle.kts
│   ├── security/
│   │   ├── src/main/kotlin/com/axians/eaf/security/
│   │   │   ├── ModuleMetadata.kt  # Security module configuration
│   │   │   ├── jwt/               # 10-layer JWT validation
│   │   │   └── tenant/            # 3-layer tenant isolation
│   │   └── build.gradle.kts
│   ├── cqrs/
│   │   ├── src/main/kotlin/com/axians/eaf/cqrs/
│   │   │   ├── ModuleMetadata.kt  # CQRS/ES module configuration
│   │   │   ├── commands/          # Command handlers
│   │   │   ├── events/            # Event handlers
│   │   │   └── queries/           # Query handlers
│   │   └── build.gradle.kts
│   ├── observability/
│   │   ├── src/main/kotlin/com/axians/eaf/observability/
│   │   │   ├── ModuleMetadata.kt  # Observability module configuration
│   │   │   ├── metrics/           # Micrometer/Prometheus
│   │   │   ├── logging/           # Structured logging
│   │   │   └── tracing/           # OpenTelemetry
│   │   └── build.gradle.kts
│   ├── workflow/
│   │   ├── src/main/kotlin/com/axians/eaf/workflow/
│   │   │   ├── ModuleMetadata.kt  # Flowable module configuration
│   │   │   ├── engine/            # Flowable engine integration
│   │   │   └── processes/         # BPMN definitions
│   │   └── build.gradle.kts
│   ├── persistence/
│   │   ├── src/main/kotlin/com/axians/eaf/persistence/
│   │   │   ├── ModuleMetadata.kt  # Persistence module configuration
│   │   │   ├── adapters/          # jOOQ/Axon adapters
│   │   │   └── projections/       # Read model projections
│   │   └── build.gradle.kts
│   └── web/
│       ├── src/main/kotlin/com/axians/eaf/web/
│       │   ├── ModuleMetadata.kt  # Web module configuration
│       │   ├── controllers/       # REST controllers
│       │   └── advice/            # Global exception handling
│       └── build.gradle.kts
├── products/                    # Deployable Spring Boot Product Apps
│   └── licensing-server/        # (Epic 8)
│       ├── src/main/kotlin/com/axians/eaf/licensing/
│       │   ├── ModuleMetadata.kt  # Product module configuration
│       │   ├── LicensingServerApplication.kt # @SpringBootApplication
│       │   └── domain/
│       ├── compose.yml          # Product-specific Docker Compose
│       ├── Dockerfile           # Product container definition
│       └── build.gradle.kts
├── shared/                      # Shared code
│   ├── shared-api/              # Shared Kotlin (Axon API: Commands, Events, Queries)
│   │   ├── src/main/kotlin/com/axians/eaf/shared/api/
│   │   │   ├── ModuleMetadata.kt  # Shared API module configuration
│   │   │   ├── commands/          # Shared command definitions
│   │   │   ├── events/            # Shared event definitions
│   │   │   └── queries/           # Shared query definitions
│   │   └── build.gradle.kts
│   ├── shared-types/            # Code-generated TypeScript interfaces
│   │   ├── package.json
│   │   └── src/
│   └── testing/                 # Testing utilities
│       ├── src/main/kotlin/com/axians/eaf/shared/testing/
│       │   ├── ModuleMetadata.kt  # Testing module configuration
│       │   ├── TestTokenGenerator.kt
│       │   ├── TestSecurityConfig.kt
│       │   └── containers/        # Testcontainers setup
│       └── build.gradle.kts
├── apps/                        # Frontend workspaces
│   └── admin/                   # React-Admin Portal (FE Component)
│       ├── package.json
│       └── src/
│           ├── App.tsx
│           └── resources/
├── build-logic/                 # Gradle Convention Plugins (Story 1.1)
│   └── convention/
│       └── src/main/kotlin/
│           ├── eaf.common-conventions.gradle.kts
│           ├── eaf.spring-boot-conventions.gradle.kts
│           ├── eaf.kotlin-conventions.gradle.kts
│           └── eaf.testing-conventions.gradle.kts
├── config/
│   ├── detekt/
│   │   ├── detekt.yml           # Main detekt configuration
│   │   └── detekt-test.yml      # Test-specific detekt rules
│   └── konsist/
│       └── konsist-rules.kt     # Architectural compliance rules
├── gradle/
│   └── libs.versions.toml       # Version Catalog (Story 1.1)
├── scripts/
│   ├── init-dev.sh              # One-Command Onboarding (Story 1.3)
│   ├── quality-check.sh         # Quality gate runner
│   └── test-all.sh              # Test execution
├── docs/                        # Documentation
│   ├── architecture/            # Sharded architecture docs
│   └── prd/                     # Sharded PRD docs
├── .ai/
│   └── debug-log.md             # Development debug log
├── compose.yml                  # Local dev stack (Postgres, Keycloak)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── CLAUDE.md                    # Claude Code guidance
```

## Spring Modulith ModuleMetadata Pattern

Each module requires a Kotlin-compatible ModuleMetadata class instead of Java's package-info.java files.

**Core Module Configuration:**

```kotlin
// framework/core/src/main/kotlin/com/axians/eaf/core/ModuleMetadata.kt
package com.axians.eaf.core

import org.springframework.modulith.ApplicationModule
import kotlin.annotation.AnnotationTarget.FILE

@file:PackageInfo

@ApplicationModule(
    displayName = "EAF Core Module",
    allowedDependencies = ["shared.api", "shared.testing"]
)
class CoreModule

@Target(FILE)
annotation class PackageInfo
```

**Security Module Configuration:**

```kotlin
// framework/security/src/main/kotlin/com/axians/eaf/security/ModuleMetadata.kt
package com.axians.eaf.security

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core", "shared.api", "shared.testing"]
)
class SecurityModule
```

**CQRS Module Configuration:**

```kotlin
// framework/cqrs/src/main/kotlin/com/axians/eaf/cqrs/ModuleMetadata.kt
package com.axians.eaf.cqrs

import org.springframework.modulith.ApplicationModule

@ApplicationModule(
    displayName = "EAF CQRS Module",
    allowedDependencies = ["core", "security", "persistence", "shared.api"]
)
class CqrsModule
```

## Framework vs. Products: Roles & Boundaries

* **Framework** (`framework/*`) modules are libraries. They MUST NOT include `@SpringBootApplication` entry points, `bootRun` tasks, or product-specific logic. They expose stable, versioned APIs and SPIs for extension.
* **Products** (`products/*`) own executables. Each product provides the Spring Boot application class, `bootJar` packaging, environment configuration, and deployment artifacts (e.g., Compose services).
* **Dependency rule**: Products depend on framework modules; framework MUST NOT depend on any product code. Enforced by Modulith/Konsist checks.
* **Testing rule**: Framework focuses on unit/module tests plus selective integration tests; products add end‑to‑end and operational tests.

**Spring Boot Application Configuration:**

```kotlin
// products/licensing-server/src/main/kotlin/LicensingServerApplication.kt
@SpringBootApplication(
    scanBasePackages = [
        "com.axians.eaf.core",
        "com.axians.eaf.security",
        "com.axians.eaf.cqrs",
        "com.axians.eaf.observability",
        "com.axians.eaf.workflow",
        "com.axians.eaf.persistence",
        "com.axians.eaf.web",
        "com.axians.eaf.licensing"
    ]
)
@EnableModulithSupport
class LicensingServerApplication

fun main(args: Array<String>) {
    runApplication<LicensingServerApplication>(*args)
}
```

## Architectural Testing with Konsist

Spring Modulith boundaries are verified through architectural tests using Konsist.

**Architectural Compliance Test:**

```kotlin
// shared/testing/src/main/kotlin/com/axians/eaf/shared/testing/ArchitecturalTest.kt
class ArchitecturalTest : FreeSpec({

    "Domain modules must not depend on infrastructure" {
        Konsist.scopeFromProject()
            .classes()
            .withPackage("..domain..")
            .should {
                it.dependencies().none { dep ->
                    dep.packagee.startsWith("com.axians.eaf.web") ||
                    dep.packagee.startsWith("com.axians.eaf.persistence")
                }
            }
    }

    "Spring Modulith boundaries must be respected" {
        Konsist.scopeFromProject()
            .classes()
            .withPackage("com.axians.eaf.security..")
            .should {
                it.dependencies()
                    .filter { dep -> dep.packagee.startsWith("com.axians.eaf") }
                    .all { dep ->
                        dep.packagee.startsWith("com.axians.eaf.core") ||
                        dep.packagee.startsWith("com.axians.eaf.shared")
                    }
            }
    }

    "All modules must have ModuleMetadata class" {
        listOf("core", "security", "cqrs", "observability", "workflow", "persistence", "web").forEach { module ->
            Konsist.scopeFromProject()
                .files
                .withPath("**/framework/$module/**/ModuleMetadata.kt")
                .shouldNotBeEmpty()
        }
    }

    "No generic exceptions allowed" {
        Konsist.scopeFromProject()
            .classes()
            .should {
                it.functions().none { func ->
                    func.hasThrowsAnnotation() &&
                    func.throwsTypes.any { type ->
                        type.name == "Exception" || type.name == "RuntimeException"
                    }
                }
            }
    }

    "Domain must use Either for error handling" {
        Konsist.scopeFromProject()
            .classes()
            .withPackage("..domain..")
            .functions()
            .withPublicOrInternalModifier()
            .should {
                it.returnType?.name?.startsWith("Either") == true ||
                it.returnType?.name == "Unit"
            }
    }
})
```

## Module Integration Testing

**Spring Modulith Integration Test:**

```kotlin
@SpringBootTest
@Import(TestSecurityConfig::class)
class ModulithIntegrationTest : BehaviorSpec({

    given("Spring Modulith module boundaries") {
        `when`("validating module structure") {
            then("all boundaries should be respected") {
                val modules = ApplicationModules.of(LicensingServerApplication::class.java)
                modules.verify()
            }
        }

        `when`("testing module events") {
            then("events should be properly published and consumed") {
                val modules = ApplicationModules.of(LicensingServerApplication::class.java)

                modules.forEach { module ->
                    module.should {
                        it.isBootstrapped() shouldBe true
                    }
                }
            }
        }
    }
})
```

## Quality Gate Integration

**Module-Level Quality Checks:**

```kotlin
// build.gradle.kts (in each module)
tasks.register("verifyArchitecture", Test::class) {
    description = "Verifies architectural compliance"
    useJUnitPlatform {
        includeTags("architecture")
    }

    doLast {
        println("✅ Module architectural compliance verified")
    }
}

tasks.register("verifyModulith", Test::class) {
    description = "Verifies Spring Modulith boundaries"
    useJUnitPlatform {
        includeTags("modulith")
    }

    doLast {
        println("✅ Spring Modulith boundaries verified")
    }
}
```

**Root Project Quality Orchestration:**

```kotlin
// Root build.gradle.kts
tasks.register("verifyAllModules") {
    description = "Verifies all modules comply with architectural rules"
    group = "verification"

    dependsOn(subprojects.map { "${it.path}:verifyArchitecture" })
    dependsOn(subprojects.map { "${it.path}:verifyModulith" })
}
```

## Module Dependency Validation

**settings.gradle.kts Module Registration:**

```kotlin
rootProject.name = "eaf-framework"

// Framework modules
include("framework:core")
include("framework:security")
include("framework:cqrs")
include("framework:observability")
include("framework:workflow")
include("framework:persistence")
include("framework:web")

// Product modules
include("products:licensing-server")

// Shared modules
include("shared:shared-api")
include("shared:shared-types")
include("shared:testing")

// Build logic
includeBuild("build-logic")
```

-----
