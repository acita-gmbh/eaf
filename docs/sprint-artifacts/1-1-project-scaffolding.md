# Story 1.1: Project Scaffolding

Status: review

## Story

As a **developer**,
I want a properly configured Kotlin/Spring Boot project structure,
so that I can start implementing domain logic immediately.

## Acceptance Criteria

1. **AC-1: Build Success**
   - Given I clone the repository
   - When I run `./gradlew build`
   - Then the build succeeds with zero errors
   - And all modules compile successfully

2. **AC-2: Module Structure**
   - Given the project structure exists
   - Then the following modules exist:
     - `eaf-core` (shared kernel)
     - `eaf-eventsourcing` (event store abstractions)
     - `eaf-tenant` (multi-tenancy)
     - `eaf-auth` (authentication)
     - `eaf-testing` (test utilities)
     - `dvmm-domain` (aggregates, events)
     - `dvmm-application` (commands, queries, handlers)
     - `dvmm-api` (REST controllers)
     - `dvmm-infrastructure` (adapters, projections)
     - `dvmm-app` (main application)

3. **AC-3: Kotlin Configuration**
   - Given the project is configured
   - Then Kotlin 2.2+ with K2 compiler is enabled
   - And coroutines are configured with reactor-kotlin-extensions

4. **AC-4: Spring Boot Configuration**
   - Given the project is configured
   - Then Spring Boot 3.5+ with WebFlux is configured
   - And reactive dependencies are properly set up

5. **AC-5: Build Conventions**
   - Given the build-logic module exists
   - Then the following conventions are defined:
     - `eaf.kotlin-conventions.gradle.kts`
     - `eaf.spring-conventions.gradle.kts`
     - `eaf.test-conventions.gradle.kts`

6. **AC-6: Quality Tooling**
   - Given quality gates are configured
   - Then JaCoCo code coverage reporting is configured
   - And Pitest mutation testing is configured
   - And Konsist architecture tests scaffold exists

7. **AC-7: Version Catalog**
   - Given dependencies are managed centrally
   - Then `gradle/libs.versions.toml` defines all versions
   - And all modules reference catalog entries (no hardcoded versions)

## Tasks / Subtasks

- [x] **Task 1: Create Monorepo Structure** (AC: 2)
  - [x] 1.1 Create root `settings.gradle.kts` with all module includes
  - [x] 1.2 Create root `build.gradle.kts` with common configurations
  - [x] 1.3 Create directory structure for all eaf/* modules
  - [x] 1.4 Create directory structure for all dvmm/* modules

- [x] **Task 2: Set Up Version Catalog & Properties** (AC: 7, 3)
  - [x] 2.1 Create `gradle/libs.versions.toml`
  - [x] 2.2 Define versions: kotlin=2.2.21, spring-boot=3.5.8, jooq=3.20.8, testcontainers=2.0.2, konsist=0.17.3, mockk=1.14.6, junit=6.0.1
  - [x] 2.3 Define libraries with version.ref references
  - [x] 2.4 Define plugins section
  - [x] 2.5 Create `gradle.properties` with JVM args and Kotlin settings

- [x] **Task 3: Create Build-Logic Conventions** (AC: 5)
  - [x] 3.1 Create `build-logic/settings.gradle.kts`
  - [x] 3.2 Create `build-logic/conventions/build.gradle.kts`
  - [x] 3.3 Implement `eaf.kotlin-conventions.gradle.kts` (K2 compiler, coroutines, explicit API)
  - [x] 3.4 Implement `eaf.spring-conventions.gradle.kts` (WebFlux, reactor-kotlin)
  - [x] 3.5 Implement `eaf.test-conventions.gradle.kts` (JUnit 6, Testcontainers, MockK)

- [x] **Task 4: Configure EAF Modules** (AC: 2, 3, 4)
  - [x] 4.1 Configure `eaf-core/build.gradle.kts` (zero external deps except kotlin-stdlib)
  - [x] 4.2 Configure `eaf-eventsourcing/build.gradle.kts` (depends on eaf-core)
  - [x] 4.3 Configure `eaf-tenant/build.gradle.kts` (depends on eaf-core)
  - [x] 4.4 Configure `eaf-auth/build.gradle.kts` (IdP-agnostic interfaces)
  - [x] 4.5 Configure `eaf-testing/build.gradle.kts` (test utilities)

- [x] **Task 5: Configure DVMM Modules** (AC: 2)
  - [x] 5.1 Configure `dvmm-domain/build.gradle.kts` (depends on eaf-core)
  - [x] 5.2 Configure `dvmm-application/build.gradle.kts` (depends on domain, eaf-eventsourcing)
  - [x] 5.3 Configure `dvmm-api/build.gradle.kts` (depends on application, eaf-auth)
  - [x] 5.4 Configure `dvmm-infrastructure/build.gradle.kts` (adapters, jOOQ)
  - [x] 5.5 Configure `dvmm-app/build.gradle.kts` (main application, assembles all)

- [x] **Task 6: Configure Quality Gates** (AC: 6)
  - [x] 6.1 Configure JaCoCo in test-conventions (minimum 80% coverage)
  - [x] 6.2 Configure Pitest for mutation testing (minimum 70% mutation score)
  - [x] 6.3 Create initial Konsist test file `ArchitectureTest.kt` in dvmm-app
  - [x] 6.4 Add rule: eaf modules must not depend on dvmm modules

- [x] **Task 7: Verify Build** (AC: 1)
  - [x] 7.1 Run `./gradlew build` and verify success
  - [x] 7.2 Run `./gradlew test` and verify test framework works
  - [x] 7.3 Run `./gradlew jacocoTestReport` and verify report generation
  - [x] 7.4 Verify all modules compile without warnings

## Dev Notes

### Scope Clarification

**In Scope (Story 1.1):**
- 5 EAF modules: eaf-core, eaf-eventsourcing, eaf-tenant, eaf-auth, eaf-testing
- 5 DVMM modules: dvmm-domain, dvmm-application, dvmm-api, dvmm-infrastructure, dvmm-app
- Build-logic with 3 convention plugins
- Version catalog and quality tooling

**Out of Scope (Later Stories/Epics):**
- `eaf-cqrs-core` - Story 1.3+
- `eaf-audit` - Epic 2+
- `eaf-observability` - Epic 2+
- `eaf-notifications` - Epic 3+
- `eaf-starters/*` - Epic 2+
- `eaf-auth-keycloak` - Story 1.7

### Architecture References

- **ADR-001:** EAF Framework-First Architecture defines the module structure
- **Dependency Direction:** EAF ← DVMM (never the reverse)
- **Hexagonal Boundaries:** All integrations via Ports & Adapters

[Source: docs/architecture.md#ADR-001]

### Key Implementation Decisions

1. **Kotlin 2.2.21:** K2 compiler is now default, no special flag needed
2. **Explicit API Mode:** Enforce in kotlin-conventions for public API clarity
3. **Gradle Version Catalogs:** Centralized dependency management, no hardcoded versions
4. **Composite Build for build-logic:** Enables IDE support for convention plugins

### Breaking Changes Notes

**JUnit 6.0.1** ([Release Notes](https://docs.junit.org/6.0.0/release-notes/)):
- Unified versioning: Platform, Jupiter, Vintage all use same version
- Native Kotlin `suspend` test support - no more `runBlocking` wrapper needed
- Requires Java 17+ and Kotlin 2.2+

**Testcontainers 2.0.2** ([GitHub](https://github.com/testcontainers/testcontainers-java/releases)):
- Module names changed: `org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql`
- Package relocated to `org.testcontainers.<module-name>`
- Import changes required in test files

[Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.1]

### Project Structure Notes

Target directory structure:

```
eaf-monorepo/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── build-logic/
│   ├── settings.gradle.kts
│   └── conventions/
│       └── src/main/kotlin/
│           ├── eaf.kotlin-conventions.gradle.kts
│           ├── eaf.spring-conventions.gradle.kts
│           └── eaf.test-conventions.gradle.kts
├── eaf/
│   ├── eaf-core/build.gradle.kts
│   ├── eaf-eventsourcing/build.gradle.kts
│   ├── eaf-tenant/build.gradle.kts
│   ├── eaf-auth/build.gradle.kts
│   └── eaf-testing/build.gradle.kts
└── dvmm/
    ├── dvmm-domain/build.gradle.kts
    ├── dvmm-application/build.gradle.kts
    ├── dvmm-api/build.gradle.kts
    ├── dvmm-infrastructure/build.gradle.kts
    └── dvmm-app/build.gradle.kts
```

### Version Catalog Template

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "2.2.21"
spring-boot = "3.5.8"
jooq = "3.20.8"
testcontainers = "2.0.2"
konsist = "0.17.3"
coroutines = "1.10.2"
reactor-kotlin = "1.2.3"
jackson = "2.20.1"
mockk = "1.14.6"
junit = "6.0.1"

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlin-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlin-coroutines-reactor = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-reactor", version.ref = "coroutines" }
spring-boot-webflux = { module = "org.springframework.boot:spring-boot-starter-webflux" }
reactor-kotlin-extensions = { module = "io.projectreactor.kotlin:reactor-kotlin-extensions", version.ref = "reactor-kotlin" }
jooq = { module = "org.jooq:jooq", version.ref = "jooq" }
# Testcontainers 2.x: module names now have 'testcontainers-' prefix
testcontainers-postgresql = { module = "org.testcontainers:testcontainers-postgresql", version.ref = "testcontainers" }
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
# JUnit 6: unified versioning across platform, jupiter, vintage
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
```

### gradle.properties Template

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true

# Kotlin
kotlin.code.style=official
kotlin.jvm.target=21
```

### References

- [Source: docs/architecture.md#Monorepo-Structure]
- [Source: docs/sprint-artifacts/tech-spec-epic-1.md#Story-1.1]
- [Source: docs/epics.md#Story-1.1]

## Dev Agent Record

### Context Reference

- `docs/sprint-artifacts/1-1-project-scaffolding.context.xml` (generated 2025-11-25)

### Agent Model Used

claude-opus-4-5-20251101

### Debug Log References

### Completion Notes List

- **JUnit 6 Compatibility**: Required Gradle 9.2.1 for JUnit 6.0.1 support. Added explicit JUnit BOM and platform-engine dependencies.
- **Kotlin Context Parameters**: Changed from deprecated `-Xcontext-receivers` to `-Xcontext-parameters` in Kotlin 2.2.
- **Library Module Configuration**: dvmm-api and dvmm-infrastructure use Spring Boot plugin but are libraries (not runnable apps). Disabled bootJar task, enabled regular jar task.
- **Konsist Scope Fix**: `scopeFromProject()` scans generated Gradle DSL accessors in build-logic. Modified architecture test to use `scopeFromModule()` for specific modules only.
- **Testcontainers 2.x**: Module names changed to `testcontainers-<module>` format (e.g., `testcontainers-postgresql`).

### File List

**Root Configuration:**
- `settings.gradle.kts` - Project settings with all module includes
- `build.gradle.kts` - Root build configuration
- `gradle.properties` - Gradle JVM and Kotlin settings
- `gradle/libs.versions.toml` - Version catalog with all dependencies

**Build Logic:**
- `build-logic/settings.gradle.kts` - Build-logic settings
- `build-logic/conventions/build.gradle.kts` - Convention plugins project
- `build-logic/conventions/src/main/kotlin/eaf.kotlin-conventions.gradle.kts` - Kotlin 2.2+ K2 compiler config
- `build-logic/conventions/src/main/kotlin/eaf.spring-conventions.gradle.kts` - Spring Boot 3.5+ WebFlux config
- `build-logic/conventions/src/main/kotlin/eaf.test-conventions.gradle.kts` - JUnit 6, Testcontainers 2.x, JaCoCo config

**EAF Modules:**
- `eaf/eaf-core/build.gradle.kts` - Core module (no external deps)
- `eaf/eaf-eventsourcing/build.gradle.kts` - Event sourcing abstractions
- `eaf/eaf-tenant/build.gradle.kts` - Multi-tenancy module
- `eaf/eaf-auth/build.gradle.kts` - Authentication interfaces
- `eaf/eaf-testing/build.gradle.kts` - Test utilities (exposes JUnit 6, Testcontainers as API)

**DVMM Modules:**
- `dvmm/dvmm-domain/build.gradle.kts` - Domain aggregates and events
- `dvmm/dvmm-application/build.gradle.kts` - Commands, queries, handlers
- `dvmm/dvmm-api/build.gradle.kts` - REST controllers (library, bootJar disabled)
- `dvmm/dvmm-infrastructure/build.gradle.kts` - Adapters, jOOQ projections (library, bootJar disabled)
- `dvmm/dvmm-app/build.gradle.kts` - Main application (runnable)
- `dvmm/dvmm-app/src/main/kotlin/com/acita/dvmm/DvmmApplication.kt` - Application entry point
- `dvmm/dvmm-app/src/test/kotlin/com/acita/dvmm/architecture/ArchitectureTest.kt` - Konsist architecture tests (7 rules)

