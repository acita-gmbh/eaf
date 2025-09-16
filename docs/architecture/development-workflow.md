# Development Workflow

## Core Development Principles

* **TDD Cycle:** Mandates "Constitutional TDD" (RED-GREEN-Refactor).
* **Quality-First:** All commits must pass quality gates before merge.
* **Integration-First:** Real dependencies via Testcontainers, no mocks.
* **Modular Development:** Spring Modulith boundaries enforced via architectural tests.

## One-Command Onboarding

A developer runs a single `./scripts/init-dev.sh` script after cloning to set up the entire environment:

```bash
git clone <repository>
cd eaf-monorepo
./scripts/init-dev.sh
```

**Onboarding Script Setup:**
- Docker services (PostgreSQL, Redis, Keycloak)
- Initial secret seeding and Vault configuration
- Git hooks for quality enforcement
- Developer documentation portal
- IDE configuration and templates
- Test data initialization

## Daily Development Commands

**Local Development Stack:**

```bash
# Terminal 1 - Infrastructure Services
./scripts/init-dev.sh

# Terminal 2 - Backend Development
./gradlew :products:licensing-server:bootRun

# Terminal 3 - Frontend Development
npm run dev --prefix apps/admin

# Terminal 4 - Documentation Portal
npm run start --prefix docs/portal
```

**Common Development Tasks:**

```bash
# Full quality check (local CI simulation)
./gradlew clean build

# Fast feedback cycle (nullable pattern tests)
./gradlew test -P fastTests=true

# Architecture compliance verification
./gradlew verifyAllModules

# Database migrations
./gradlew flywayMigrate

# Generate API documentation
./gradlew dokkaHtml
```

## Scaffolding CLI and Code Generation

The Scaffolding CLI accelerates compliant setup by generating production-ready skeletons that conform to the architecture (Hexagonal + Spring Modulith, CQRS/ES, security baselines).

**Command Surface:**

```bash
# List available commands
eaf scaffold --help

# Backend Components
eaf scaffold module <boundedContext>:<module>
eaf scaffold aggregate <Domain> --id <Name> --events <Created,Updated,...> --context <boundedContext>
eaf scaffold api-resource <Domain> --path /api/widgets --methods list,get,create,update,delete
eaf scaffold workflow <Name> --bpmn

# Frontend Components
eaf scaffold ra-resource <Domain> --path /api/widgets --fields id,name,status --title "Widgets"
eaf scaffold tui <Name>

# Testing & Quality
eaf scaffold tests <modulePath> --types modulith,integration,mutation

# Product Applications
eaf scaffold product-app <product> --with-admin

# Documentation
eaf scaffold docs --kind api|architecture|adr --title "..."
```

**Generated Artifacts (Backend Module):**

```plaintext
framework/<module>/
└── src/main/kotlin/com/axians/eaf/<context>/<module>/
    ├── ModuleMetadata.kt               # @ApplicationModule config
    ├── domain/
    │   ├── <Domain>Aggregate.kt        # Axon aggregate
    │   ├── command/Create<Domain>Command.kt
    │   └── event/<Domain>CreatedEvent.kt
    ├── application/
    │   ├── <Domain>CommandHandler.kt   # Command handling
    │   └── <Domain>Projector.kt        # Event projection
    ├── adapters/
    │   ├── api/<Domain>Controller.kt   # REST endpoints
    │   └── persistence/<Domain>Entity.kt # jOOQ/JPA entities
    └── build.gradle.kts               # Module configuration
```

**Generated Artifacts (React-Admin Resource):**

```plaintext
apps/admin/src/resources/<domain>/
├── <Domain>List.tsx                   # List view
├── <Domain>Edit.tsx                   # Edit form
├── <Domain>Create.tsx                 # Create form
├── <Domain>Show.tsx                   # Detail view
└── index.ts                           # Resource exports
```

## Testing Workflow

**Test-Driven Development Cycle:**

```bash
# 1. RED - Write failing test
./gradlew test --tests "*WidgetTest"

# 2. GREEN - Implement minimal code to pass
./gradlew test --tests "*WidgetTest" --continuous

# 3. REFACTOR - Improve design while tests pass
./gradlew test -P fastTests=true
```

**Quality Gates (Pre-Commit):**

```bash
# Automated quality check sequence
./gradlew ktlintCheck detektCheck
./gradlew verifyArchitecture verifyModulith
./gradlew test -P profile=fast
./gradlew dependencyCheckAnalyze
```

**Performance Testing:**

```bash
# Mutation testing (scheduled/CI only)
./gradlew pitest

# Container performance baseline
./scripts/testcontainer-baseline.sh

# Nullable pattern performance validation
./scripts/nullable-pattern-baseline.sh <module-path>
```

## Code Quality Enforcement

**Automated Quality Checks:**

1. **Static Analysis:** ktlint, detekt with zero violations policy
2. **Architecture Compliance:** Konsist rules for module boundaries
3. **Security Scanning:** OWASP dependency check, SAST rules
4. **Test Coverage:** 85% line coverage, 80% mutation coverage
5. **Performance Regression:** Container timing baselines

**Pre-Commit Hook Enforcement:**

```bash
#!/bin/sh
# .git/hooks/pre-commit
set -e

echo "Running quality gates..."
./gradlew ktlintCheck detektCheck --parallel
./gradlew verifyArchitecture --parallel
./gradlew test -P profile=security-lite

echo "✅ All quality gates passed"
```

**Branch Protection Rules:**

- All PRs require passing CI build
- Architectural compliance verification required
- Security scan completion mandatory
- Test coverage threshold enforcement
- Code review approval from module owners

## Module Development Patterns

**Spring Modulith Integration:**

```kotlin
// Each module requires ModuleMetadata configuration
@ApplicationModule(
    displayName = "Widget Management Module",
    allowedDependencies = ["core", "security", "shared.api"]
)
class WidgetModule
```

**Dependency Management:**

```kotlin
// Module build.gradle.kts using convention plugins
plugins {
    id("eaf.kotlin-conventions")
    id("eaf.spring-boot-conventions")
    id("eaf.testing-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.bundles.axon.framework)
    implementation(project(":framework:core"))

    testImplementation(libs.bundles.testing.kotest)
    testImplementation(libs.bundles.testing.testcontainers)
}
```

## Local Development Environment

**Required Software:**
- Docker & Docker Compose
- Java 21 (OpenJDK)
- Node.js LTS
- Git with LFS support

**IDE Configuration:**
- IntelliJ IDEA with Kotlin plugin
- EditorConfig support for consistent formatting
- Gradle refresh on build.gradle.kts changes
- Test runner configured for Kotest

**Environment Variables:**

```bash
# .env.local (created by init-dev.sh)
EAF_ENVIRONMENT=development
DATABASE_URL=jdbc:postgresql://localhost:5432/eaf_dev
KEYCLOAK_URL=http://localhost:8080
VAULT_ADDR=http://localhost:8200
REDIS_URL=redis://localhost:6379
```

## Troubleshooting Common Issues

**Container Startup Issues:**
```bash
# Reset development environment
docker-compose down -v
./scripts/init-dev.sh --clean

# Check container logs
docker-compose logs postgres
docker-compose logs keycloak
```

**Test Failures:**
```bash
# Run tests with detailed output
./gradlew test --info --stacktrace

# Check test container timing
./gradlew test -P testcontainers.debug=true

# Validate security-lite profile
./gradlew test -P profile=security-lite --tests "*SecurityTest"
```

**Build Performance:**
```bash
# Enable Gradle build cache
./gradlew build --build-cache

# Parallel execution with container reuse
./gradlew test -P testcontainers.reuse.enable=true --parallel
```

-----
