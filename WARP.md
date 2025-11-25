# WARP.md

Terminal AI rules for the EAF/DVMM Gradle monorepo.

## Project Type

This is a **Gradle 9.2 multi-module Kotlin monorepo** with Spring Boot 3.5.

## Gradle Wrapper

Always use the Gradle wrapper (`./gradlew`), never system Gradle.

---

## Essential Commands

### Build & Test

```bash
# Full build with all quality checks (USE THIS BEFORE PUSH)
./gradlew clean build

# Quick build without tests
./gradlew assemble

# Run all tests
./gradlew test

# Run tests with output
./gradlew test --info
```

### Module-Specific Commands

```bash
# Build specific module
./gradlew :dvmm:dvmm-app:build
./gradlew :eaf:eaf-core:build

# Test specific module
./gradlew :dvmm:dvmm-domain:test
./gradlew :eaf:eaf-testing:test

# Run single test class
./gradlew :dvmm:dvmm-app:test --tests "com.acita.dvmm.architecture.ArchitectureTest"

# Run single test method (use quotes for spaces)
./gradlew :dvmm:dvmm-app:test --tests "ArchitectureTest.eaf modules must not depend on dvmm modules"

# Run tests matching pattern
./gradlew test --tests "*EventStore*"
```

### Quality Checks

```bash
# Code coverage report (JaCoCo)
./gradlew jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Mutation testing (Pitest)
./gradlew pitest
# Report: build/reports/pitest/index.html

# Architecture tests only
./gradlew :dvmm:dvmm-app:test --tests "*ArchitectureTest*"
```

### Dependency Management

```bash
# Show dependency tree for module
./gradlew :dvmm:dvmm-app:dependencies

# Show outdated dependencies
./gradlew dependencyUpdates

# Refresh dependencies
./gradlew --refresh-dependencies build
```

### Cleaning

```bash
# Clean all build outputs
./gradlew clean

# Clean specific module
./gradlew :dvmm:dvmm-app:clean

# Nuclear option: clean + delete caches
./gradlew clean && rm -rf ~/.gradle/caches/build-cache-*
```

---

## Module Paths

| Module | Gradle Path |
|--------|-------------|
| EAF Core | `:eaf:eaf-core` |
| EAF Event Sourcing | `:eaf:eaf-eventsourcing` |
| EAF Tenant | `:eaf:eaf-tenant` |
| EAF Auth | `:eaf:eaf-auth` |
| EAF Testing | `:eaf:eaf-testing` |
| DVMM Domain | `:dvmm:dvmm-domain` |
| DVMM Application | `:dvmm:dvmm-application` |
| DVMM API | `:dvmm:dvmm-api` |
| DVMM Infrastructure | `:dvmm:dvmm-infrastructure` |
| DVMM App | `:dvmm:dvmm-app` |

---

## Troubleshooting

### Build Fails

```bash
# Run with stacktrace for details
./gradlew build --stacktrace

# Run with full debug output
./gradlew build --debug

# Check for daemon issues
./gradlew --stop
./gradlew build
```

### Test Failures

```bash
# Rerun failed tests only
./gradlew test --rerun

# Run with more output
./gradlew test --info

# Skip tests temporarily (NOT for commits!)
./gradlew build -x test
```

### Dependency Conflicts

```bash
# See conflict resolution
./gradlew :dvmm:dvmm-app:dependencyInsight --dependency spring-boot

# Force refresh
./gradlew build --refresh-dependencies
```

### Memory Issues

```bash
# Increase heap for large builds
./gradlew build -Dorg.gradle.jvmargs="-Xmx4g"

# Check daemon status
./gradlew --status
```

---

## Useful Flags

| Flag | Purpose |
|------|---------|
| `--info` | Verbose output |
| `--debug` | Full debug output |
| `--stacktrace` | Print stacktrace on error |
| `--scan` | Generate build scan (online) |
| `--offline` | Work offline |
| `--parallel` | Parallel execution |
| `--continue` | Continue after failures |
| `--rerun` | Ignore up-to-date checks |
| `-x <task>` | Exclude task |

---

## Common Workflows

### Before Committing

```bash
./gradlew clean build
```

### After Pulling Changes

```bash
./gradlew clean build --refresh-dependencies
```

### Quick Test Cycle

```bash
./gradlew test --tests "MyTest" --rerun
```

### Check What Will Run

```bash
./gradlew build --dry-run
```

### List Available Tasks

```bash
# All tasks
./gradlew tasks

# Tasks for specific module
./gradlew :dvmm:dvmm-app:tasks
```

---

## Quality Thresholds

| Check | Threshold | Command |
|-------|-----------|---------|
| Test Coverage | ≥80% | `./gradlew jacocoTestReport` |
| Mutation Score | ≥70% | `./gradlew pitest` |
| Architecture | All pass | `./gradlew :dvmm:dvmm-app:test --tests "*ArchitectureTest*"` |

---

## Git Commands (Project Conventions)

### Commit Message Format

```bash
# Conventional commit
git commit -m "feat: implement VM request validation"
git commit -m "fix: correct tenant isolation in event store"
git commit -m "docs: add API documentation"

# With Jira reference
git commit -m "[DVMM-123] feat: implement VM request validation"
```

### Branch Creation

```bash
# Feature branch
git checkout -b feature/story-1.2-eaf-core-module

# Fix branch
git checkout -b fix/tenant-leak-in-projections

# Docs branch
git checkout -b docs/api-documentation
```

---

## Directory Structure

```
eaf/                    # Root
├── build-logic/        # Convention plugins
├── dvmm/               # Product modules
│   ├── dvmm-api/
│   ├── dvmm-app/
│   ├── dvmm-application/
│   ├── dvmm-domain/
│   └── dvmm-infrastructure/
├── eaf/                # Framework modules
│   ├── eaf-auth/
│   ├── eaf-core/
│   ├── eaf-eventsourcing/
│   ├── eaf-tenant/
│   └── eaf-testing/
├── docs/               # Documentation
└── gradle/             # Gradle wrapper + version catalog
    └── libs.versions.toml
```
