# Deep Research Prompt: Kotest 6.0.3 + Spring Boot + Custom Gradle Source Sets

## Executive Summary

We need a comprehensive technical solution for enabling Kotest 6.0.3 FunSpec DSL in a custom Gradle source set (`integrationTest`) for Spring Boot integration tests. Current configuration causes 150+ compilation errors.

## Problem Statement

### Current Situation

**Project**: Enterprise Application Framework (EAF) - Kotlin/JVM monorepo
**Build Tool**: Gradle 9.1.0 with Kotlin DSL
**Testing Framework**: Kotest 6.0.3 (native plugin + JUnit Platform hybrid)
**Spring Boot**: 3.5.6
**Kotlin**: 2.0.10 (PINNED - critical constraint)

**Architecture**:
- Convention plugins in `build-logic/` apply to all modules
- TestingConventionPlugin creates custom source sets: `integrationTest`, `konsistTest`
- Main tests use native Kotest runner (`jvmKotest` task)
- Custom source sets use JUnit Platform (`useJUnitPlatform()`)

### The Blocker

**Symptom**: 150+ compilation errors when @SpringBootTest integration tests are moved from `kotlin-disabled/` to `kotlin/` directory

**Error Pattern**:
```
e: file:///path/WidgetApiIntegrationTest.kt:14:33 Unresolved reference 'test'.
e: file:///path/WidgetApiIntegrationTest.kt:15:33 Unresolved reference 'test'.
...
e: file:///path/WidgetApiIntegrationTest.kt:30:2 Unresolved reference 'SpringBootTest'.
```

**Observations**:
1. Line numbers reported by compiler don't match actual file content (line 14 is an import, not test code)
2. ALL Kotest DSL functions unresolved: `test`, `context`, `beforeEach`, `afterEach`
3. ALL Spring Boot imports unresolved: `@SpringBootTest`, `@ActiveProfiles`, `MockMvc`
4. ONLY affects tests with `@SpringBootTest` annotation
5. Pure Kotest tests (no Spring) compile and run successfully

**Working Test Pattern** (compiles successfully):
```kotlin
package com.axians.eaf.products.widgetdemo.api

import io.kotest.core.spec.style.FunSpec
import org.axonframework.test.aggregate.AggregateTestFixture

class TenantBoundaryValidationIntegrationTest : FunSpec() {
    private lateinit var fixture: FixtureConfiguration<Widget>

    init {
        beforeEach { fixture = AggregateTestFixture(Widget::class.java) }

        test("test name") {
            // Pure Axon fixture test - NO Spring Boot
        }
    }
}
```

**Broken Test Pattern** (150+ errors):
```kotlin
package com.axians.eaf.products.widgetdemo.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.testcontainers.perSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WidgetApiIntegrationTest(
    private val mockMvc: MockMvc,
    private val commandGateway: CommandGateway,
) : FunSpec({
    extension(SpringExtension())
    listener(TestContainers.postgres.perSpec())

    test("should create widget via REST API") {
        // Spring Boot + MockMvc test
    }
}) {
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

## Configuration Details

### TestingConventionPlugin.kt (build-logic/)

```kotlin
package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("io.kotest") // Native Kotest plugin v6.0.3
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            // Native Kotest execution for main tests
            tasks.named("check") {
                dependsOn("jvmKotest")
            }

            tasks.named("test").configure {
                onlyIf { false } // Disable standard test task
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            // Create integrationTest source set
            val integrationTest = sourceSets.create("integrationTest") {
                compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                runtimeClasspath += output + compileClasspath
                java.srcDirs("src/integration-test/kotlin")
            }

            configurations.named("integrationTestImplementation") {
                extendsFrom(configurations.getByName("testImplementation"))
            }

            dependencies {
                // integrationTest gets ALL testImplementation dependencies
                add("integrationTestImplementation", "io.kotest:kotest-framework-engine-jvm:6.0.3")
                add("integrationTestImplementation", "io.kotest:kotest-runner-junit5-jvm:6.0.3")
                add("integrationTestImplementation", "io.kotest:kotest-assertions-core-jvm:6.0.3")
                add("integrationTestImplementation", "io.kotest:kotest-extensions-spring:1.3.0")
                add("integrationTestImplementation", "io.kotest:kotest-extensions-testcontainers:6.0.3")
                // ... plus Spring Boot test dependencies
            }

            // Integration test task using JUnit Platform
            val integrationTestTask = tasks.register("integrationTest", Test::class.java) {
                description = "Runs integration tests with Testcontainers."
                group = "verification"
                testClassesDirs = integrationTest.output.classesDirs
                classpath = integrationTest.runtimeClasspath
                useJUnitPlatform() // Uses JUnit Platform, not native Kotest
            }

            tasks.named("check") {
                dependsOn(integrationTestTask)
            }
        }
    }
}
```

### build-logic/build.gradle.kts

```kotlin
plugins {
    `kotlin-dsl`
    id("io.kotest") version "6.0.3"
}

dependencies {
    implementation("io.kotest:io.kotest.gradle.plugin:6.0.3")
    // ... other dependencies
}
```

### gradle/libs.versions.toml (relevant versions)

```toml
[versions]
kotlin = "2.0.10"  # PINNED - cannot change
gradle = "9.1.0"
kotest = "6.0.3"
kotest-plugin = "6.0.3"
spring-boot = "3.3.5"  # LOCKED for Spring Modulith 1.3.0

[libraries]
kotest-framework-engine-jvm = { module = "io.kotest:kotest-framework-engine-jvm", version.ref = "kotest" }
kotest-runner-junit5-jvm = { module = "io.kotest:kotest-runner-junit5-jvm", version.ref = "kotest" }
kotest-assertions-core-jvm = { module = "io.kotest:kotest-assertions-core-jvm", version.ref = "kotest" }
kotest-extensions-spring = { module = "io.kotest.extensions:kotest-extensions-spring", version = "1.3.0" }
kotest-extensions-testcontainers = { module = "io.kotest:kotest-extensions-testcontainers", version.ref = "kotest" }

[plugins]
kotest-plugin = { id = "io.kotest", version.ref = "kotest-plugin" }
```

## Investigation History

### Attempts Made (All Failed)

**Attempt 1**: Move companion object with @DynamicPropertySource outside class to top-level object
```kotlin
object WidgetApiTestContainersConfig {
    @JvmStatic
    @DynamicPropertySource
    fun configureProperties(registry: DynamicPropertyRegistry) {
        TestContainers.startAll()
        registry.add("spring.datasource.url") { TestContainers.postgres.jdbcUrl }
    }
}

@SpringBootTest(...)
class WidgetApiIntegrationTest(...) : FunSpec({...})
```
**Result**: Still 85 compilation errors

**Attempt 2**: Fix SpringExtension syntax
- Changed `extension(SpringExtension)` to `extension(SpringExtension())`
- Changed `extensions(SpringExtension)` to `extension(SpringExtension())`
**Result**: Still fails

**Attempt 3**: Configure TestingConventionPlugin with KotestPluginExtension
```kotlin
import io.kotest.gradle.plugin.KotestPluginExtension

afterEvaluate {
    configure<KotestPluginExtension> {
        sourceSets.add(integrationTest)
    }
}
```
**Result**: "Unresolved reference 'gradle'" - KotestPluginExtension not accessible from convention plugin

**Attempt 4**: Add kotest{} block to widget-demo/build.gradle.kts
```kotlin
kotest {
    sourceSets {
        integrationTest { }
    }
}
```
**Result**: "Unresolved reference. None of the following candidates is applicable" - Extension not found

**Attempt 5**: Use extensions.configure() with fully qualified class name
```kotlin
extensions.configure(io.kotest.gradle.plugin.KotestPluginExtension::class.java) { }
```
**Result**: "Unresolved reference 'gradle'" - Class not on classpath

### Ollama Consultation Results

**Model Used**: huihui_ai/gpt-oss-abliterated:latest (20.9B parameters, Q4_K_M quantization)

**Key Insights**:
1. Confirmed `@DynamicPropertySource` + companion object breaks Kotlin compiler → Use top-level object (tested, still fails)
2. Recommended `configure<KotestPluginExtension> { sourceSets.add(integrationTest) }` (tested, classpath issue)
3. Confirmed KotestPluginExtension.sourceSets.add() is correct API for registering source sets
4. Suggested adding kotest{} block directly in module build.gradle.kts (tested, extension unresolved)
5. Concluded: Not fundamentally incompatible, but requires proper configuration

### Why Original Story Assumptions Failed

**Story Assumed**:
- Tests were "successfully migrated" in Story 4.5
- Just need to update `LicensingServerApplication` → `WidgetDemoApplication` (1 line change)
- Tests would "just work" after reference update
- Estimated effort: 3-5 hours

**Reality Discovered**:
- Tests were disabled in Story 3.4 (commit 612acfd), moved disabled to products in Story 4.5
- Tests use @SpringBootTest pattern incompatible with current Kotest + Gradle config
- Tests require complete architectural fix OR rewrite (12-16 hours)
- Multiple compilation issues beyond application reference

## Research Questions

### Primary Question (Critical)

**How do you configure Kotest 6.0.3 Gradle plugin to register Kotest DSL (test, context, beforeEach, etc.) for a custom Gradle source set created by a convention plugin?**

**Context**:
- Source set `integrationTest` created programmatically by TestingConventionPlugin
- Plugin applies `id("io.kotest")` version 6.0.3
- Integration tests use JUnit Platform (`useJUnitPlatform()`), not native Kotest runner
- Need Kotest DSL available for @SpringBootTest FunSpec tests
- Convention plugin is precompiled (build-logic/src/main/kotlin/conventions/)

**Specific Sub-Questions**:
1. Can `KotestPluginExtension` be accessed from a precompiled convention plugin?
2. If yes, what's the correct import path and configuration API?
3. If no, how can a module's build.gradle.kts configure Kotest for source sets created by convention plugin?
4. Is there a `kotest {}` DSL available in module build scripts when plugin is applied transitively?
5. Does Kotest 6.0.3 auto-discover custom source sets or must they be explicitly registered?

### Secondary Questions (Important)

**Q1**: Is @SpringBootTest + Kotest 6.0.3 FunSpec officially supported?
- Kotest documentation mentions Spring Boot support
- Our tests use `io.kotest.extensions:kotest-extensions-spring:1.3.0`
- Is there a specific pattern for @SpringBootTest + FunSpec + constructor injection?

**Q2**: How should @DynamicPropertySource work with Kotest FunSpec?
- Spring Boot expects static method with @DynamicPropertySource
- Kotest FunSpec uses constructor-injected dependencies
- Companion object inside FunSpec causes compiler errors
- Is top-level object the correct pattern? If not, what is?

**Q3**: JUnit Platform vs Native Kotest Runner
- Main tests use native Kotest (`jvmKotest` task)
- Integration tests use JUnit Platform (`useJUnitPlatform()`)
- Does this hybrid approach affect Kotest DSL availability?
- Should integration tests also use native runner?

**Q4**: Kotlin Compiler Behavior
- Why do error line numbers not match file content?
- Why does renaming `WidgetApiIntegrationTest.kt` to `WidgetApiIntTest.kt` fix import resolution?
- Is this a Kotlin compiler bug or configuration issue?

### Tertiary Questions (Nice to Have)

**Q5**: TestContainers Integration Best Practices
- Current pattern: companion object with @DynamicPropertySource
- Alternative: Kotest lifecycle listeners (listener(TestContainers.postgres.perSpec()))
- Which pattern is recommended for Kotest 6.0.3 + Spring Boot?
- Can we avoid @DynamicPropertySource entirely?

**Q6**: Alternative Architectures
- Should integration tests be in separate Gradle submodule instead of source set?
- Should we use JUnit 5 for @SpringBootTest tests and Kotest only for unit tests?
- Is there a "blessed" pattern for Kotlin + Spring Boot + Kotest integration testing?

## Technical Context

### Version Constraints (CRITICAL - Cannot Change)

```
Kotlin: 2.0.10 (PINNED for ktlint 1.4.0 + detekt 1.23.7 compatibility)
Spring Boot: 3.3.5 (LOCKED for Spring Modulith 1.3.0)
Gradle: 9.1.0 (required for Kotest 6.0.3 - embeds Kotlin 2.2.0)
Kotest: 6.0.3 (upgraded from 5.9.1 in January 2025)
```

**Note**: Gradle 9.1.0 embeds Kotlin 2.2.0 but project uses Kotlin 2.0.10. This mismatch may contribute to issues.

### Kotest 6.0.3 Migration Context

Project migrated from Kotest 5.9.1 to 6.0.3 in January 2025:
- Upgraded Gradle 8.14 → 9.1.0 (for Kotlin 2.2.0 embedded in Kotest 6.0.3)
- Hybrid approach: Native runner for main tests, JUnit Platform for custom source sets
- Migration commit: 53cf0b2 "build(deps): bump kotest from 5.9.1 to 6.0.3 with full migration"

**Key Migration Decision**:
> "Custom source sets (integrationTest, konsistTest) use JUnit Platform due to Kotest Gradle plugin limitation with custom source sets"

This suggests the issue was KNOWN but not fully solved.

### Current Directory Structure

```
products/widget-demo/
├── src/
│   ├── main/kotlin/
│   │   └── com/axians/eaf/products/widgetdemo/
│   │       ├── WidgetDemoApplication.kt
│   │       └── domain/Widget.kt
│   ├── test/kotlin/
│   │   └── com/axians/eaf/products/widgetdemo/
│   │       └── domain/WidgetTest.kt
│   └── integration-test/
│       ├── kotlin/
│       │   └── com/axians/eaf/products/widgetdemo/
│       │       ├── api/TenantBoundaryValidationIntegrationTest.kt (WORKS ✅)
│       │       └── test/WidgetDemoTestApplication.kt
│       └── kotlin-disabled/
│           ├── WidgetApiIntegrationTest.kt (BROKEN ❌)
│           ├── WidgetWalkingSkeletonIntegrationTest.kt (BROKEN ❌)
│           ├── WidgetIntegrationTest.kt (BROKEN ❌)
│           ├── persistence/WidgetEventStoreIntegrationTest.kt (BROKEN ❌)
│           └── projections/WidgetEventProcessingIntegrationTest.kt (BROKEN ❌)
└── build.gradle.kts
```

### Dependencies (integrationTest source set)

```kotlin
integrationTestImplementation:
- io.kotest:kotest-framework-engine-jvm:6.0.3
- io.kotest:kotest-runner-junit5-jvm:6.0.3
- io.kotest:kotest-assertions-core-jvm:6.0.3
- io.kotest:kotest-extensions-spring:1.3.0
- io.kotest:kotest-extensions-testcontainers:6.0.3
- org.springframework.boot:spring-boot-starter-test:3.5.6
- org.springframework.boot:spring-boot-starter-security:3.5.6
- org.testcontainers:postgresql:1.21.3
- org.testcontainers:testcontainers:1.21.3
- com.github.dasniko:testcontainers-keycloak:3.8.0
- shared:testing (contains TestContainers object)
```

## Desired End State

### Goal

Enable 5 disabled integration tests to compile and run successfully in `src/integration-test/kotlin/` directory.

### Requirements

**Must Have**:
1. ✅ Tests compile without errors
2. ✅ Tests run and pass (or fail for valid business reasons)
3. ✅ Use Kotest 6.0.3 FunSpec (MANDATORY per CLAUDE.md)
4. ✅ Use @SpringBootTest for full Spring context (needed for MockMvc, real DB, projections)
5. ✅ TestContainers integration (PostgreSQL, Keycloak, Redis)
6. ✅ No changes to version constraints (Kotlin 2.0.10, Spring Boot 3.3.5, Kotest 6.0.3)

**Should Have**:
7. ✅ Minimal changes to convention plugin (avoid breaking other modules)
8. ✅ Pattern reusable for Epic 8 (licensing-server integration tests)
9. ✅ Clear documentation of solution for future developers

**Nice to Have**:
10. ✅ Performance <5 minutes total execution time
11. ✅ No JUnit dependencies (pure Kotest)
12. ✅ Idiomatic Kotlin + Kotest patterns

## Research Deliverables Requested

### Deliverable 1: Root Cause Analysis

**Provide**:
1. **Exact technical reason** why Kotest DSL is unavailable in integrationTest source set
2. **Gradle/Kotest plugin behavior** regarding custom source sets
3. **Kotlin compiler behavior** explaining line number mismatch errors
4. **Classpath analysis** showing where Kotest DSL resolution fails
5. **Comparison** with working main `test` source set configuration

### Deliverable 2: Solution Architecture

**Provide**:
1. **Recommended approach** (with pros/cons of alternatives)
2. **Step-by-step implementation guide** with exact code changes
3. **Configuration locations** (convention plugin vs module build script)
4. **Migration path** from current state to working state
5. **Validation steps** to confirm solution works

### Deliverable 3: Working Code Patterns

**Provide complete, working code for**:

1. **TestingConventionPlugin.kt modifications** (if needed)
   - How to configure KotestPluginExtension for integrationTest source set
   - Correct imports and API calls
   - Timing (immediate vs afterEvaluate)

2. **@SpringBootTest + Kotest FunSpec integration test template**
   - Correct pattern for @DynamicPropertySource with TestContainers
   - Constructor injection for MockMvc, Spring beans
   - Kotest lifecycle (beforeSpec, beforeEach, etc.)
   - TestContainers integration pattern

3. **Module build.gradle.kts additions** (if needed)
   - Any per-module configuration required
   - Dependency declarations
   - Plugin applications

### Deliverable 4: Fallback Options

**If primary solution not viable**, provide:

**Option A**: JUnit 5 + @SpringBootTest pattern
- How to convert Kotest FunSpec tests to JUnit 5
- Impact on testing standards (violates "NEVER use JUnit" mandate)
- Migration effort estimate

**Option B**: Separate integration-test submodule
- Architecture for dedicated integration test Gradle module
- How to share code between modules
- Build configuration changes

**Option C**: Pure Axon fixture tests (no Spring Boot)
- How to achieve E2E coverage without @SpringBootTest
- What coverage is lost (REST API, projections, etc.)
- Compensating patterns for lost coverage

## Reference Materials to Consult

### Official Documentation
1. Kotest 6.0.3 Gradle Plugin: https://kotest.io/docs/framework/project-setup.html
2. Kotest Spring Extension: https://kotest.io/docs/extensions/spring.html
3. Kotest Extensions: https://github.com/kotest/kotest-extensions-spring
4. Spring Boot Testing: https://docs.spring.io/spring-boot/docs/3.3.5/reference/html/features.html#features.testing
5. Gradle Kotlin DSL: https://docs.gradle.org/9.1.0/userguide/kotlin_dsl.html

### Related Issues to Search
- "Kotest FunSpec @SpringBootTest custom source set"
- "Kotest Gradle plugin integrationTest source set configuration"
- "Kotest 6.0.3 @DynamicPropertySource pattern"
- "Kotlin compiler unresolved reference test Kotest"
- "Gradle custom source set Kotest DSL unavailable"

### Similar Projects to Study
- Spring Petclinic with Kotest (if exists)
- Axon Framework samples with Kotest
- Kotest Spring Boot examples in kotest-examples repo

## Success Criteria

### A solution is considered successful if:

1. ✅ All 5 disabled integration tests compile without errors in `src/integration-test/kotlin/`
2. ✅ Tests execute via `./gradlew :products:widget-demo:integrationTest`
3. ✅ Kotest 6.0.3 FunSpec DSL fully available (test, context, beforeEach)
4. ✅ @SpringBootTest annotation works with full Spring context
5. ✅ TestContainers integration functional
6. ✅ No version constraint violations (Kotlin 2.0.10, Spring Boot 3.3.5)
7. ✅ Solution documented and repeatable
8. ✅ No regressions in existing tests (TenantBoundaryValidationIntegrationTest still passes)

### Acceptance Tests

Run these commands to validate solution:

```bash
# 1. Compile integration tests
./gradlew :products:widget-demo:compileIntegrationTestKotlin
# Expected: BUILD SUCCESSFUL, zero errors

# 2. Run integration tests
./gradlew :products:widget-demo:integrationTest
# Expected: BUILD SUCCESSFUL, all tests pass

# 3. Verify no regressions in main tests
./gradlew :products:widget-demo:jvmKotest
# Expected: BUILD SUCCESSFUL, existing tests still pass

# 4. Full quality check
./gradlew :products:widget-demo:check
# Expected: BUILD SUCCESSFUL
```

## Additional Context

### Why This Matters

**Business Impact**:
- Story 4.6 blocks Epic 4 completion (Multi-Tenancy Baseline)
- 5 integration test suites provide critical E2E CQRS validation
- Walking Skeleton test validates Epic 2 completion marker
- Pattern needed for Epic 8 (licensing-server) integration tests

**Technical Debt**:
- TestingConventionPlugin incomplete (doesn't fully support integrationTest)
- Kotest migration (5.9.1 → 6.0.3) left custom source sets partially broken
- "Limitation with custom source sets" acknowledged but not resolved

### Files for Deep Analysis

**Core Configuration**:
1. `build-logic/src/main/kotlin/conventions/TestingConventionPlugin.kt` (362 lines)
2. `build-logic/build.gradle.kts` (146 lines)
3. `gradle/libs.versions.toml` (version catalog)

**Working Test** (for comparison):
4. `products/widget-demo/src/integration-test/kotlin/.../TenantBoundaryValidationIntegrationTest.kt` (120 lines)

**Broken Tests** (to analyze):
5. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetApiIntegrationTest.kt` (245 lines)
6. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetWalkingSkeletonIntegrationTest.kt` (305 lines)

**Architecture Documentation**:
7. `docs/architecture/test-strategy-and-standards-revision-3.md`
8. `docs/architecture/tech-stack.md`
9. `CLAUDE.md` (project coding standards)

## Output Format Requested

### Provide research findings as:

**1. Executive Summary** (1-2 paragraphs)
- What's the root cause?
- What's the recommended solution?
- Can it be done within constraints?

**2. Technical Analysis** (detailed)
- How Kotest Gradle plugin handles custom source sets
- Why current configuration fails
- What configuration is missing
- Gradle + Kotlin + Kotest interaction details

**3. Solution Guide** (step-by-step)
- Exact code changes required
- File-by-file modifications
- Validation steps
- Risk assessment

**4. Working Code Examples**
- Complete test file template
- Convention plugin configuration
- Module build script additions
- All necessary imports

**5. Alternative Approaches** (if primary solution not viable)
- Options ranked by preference
- Effort estimates for each
- Trade-offs clearly stated

## Research Constraints

### Must Research

- ✅ Kotest 6.0.3 Gradle plugin source code (if needed)
- ✅ Kotest Spring Boot integration patterns
- ✅ Gradle custom source set best practices
- ✅ Similar projects solving same problem

### Cannot Change

- ❌ Kotlin version (2.0.10 - PINNED)
- ❌ Spring Boot version (3.3.5 - LOCKED)
- ❌ Kotest version (6.0.3 - current stable)
- ❌ Gradle version (9.1.0 - required for Kotest)
- ❌ Testing mandate (Kotest only, NO JUnit except for runners)

### Out of Scope

- Upgrading to Kotest 7.x (doesn't exist)
- Downgrading to Kotest 5.x (regression)
- Switching to JUnit 5 as primary framework (violates mandate)
- Complete test infrastructure rewrite (too risky)

## Expected Outcome

After this research, we should have:

1. ✅ **Clear understanding** of why Kotest DSL is unavailable in integrationTest
2. ✅ **Working solution** to enable Kotest DSL in custom source sets
3. ✅ **Code templates** for @SpringBootTest + Kotest FunSpec integration tests
4. ✅ **Migration guide** to fix Story 4.6 and unblock Epic 4
5. ✅ **Documentation** for future developers encountering similar issues

## Contact Information for Follow-up

- **Story**: 4.6 - Re-enable Widget Integration Tests
- **Epic**: 4 (Multi-Tenancy Baseline)
- **Repository**: acita-gmbh/eaf (private)
- **Branch**: feature/story-4.6-re-enable-widget-integration-tests
- **Investigation Duration**: 3+ hours (2025-09-28)
- **AI Assistance**: Claude Sonnet 4 + Ollama (huihui_ai/gpt-oss-abliterated:latest)

---

## Quick Start for External Agent

**TL;DR**:
1. Read "Problem Statement" section above
2. Focus on Primary Question: "How to configure Kotest 6.0.3 for custom source sets?"
3. Research Kotest Gradle plugin 6.0.3 documentation and source
4. Provide working code for TestingConventionPlugin.kt
5. Validate solution against Success Criteria

**Most Valuable Output**: Working code snippet that makes this compile:
```bash
./gradlew :products:widget-demo:compileIntegrationTestKotlin
```

Currently: 150+ errors
Target: BUILD SUCCESSFUL

---

**Research Depth**: Go as deep as needed. This is a critical blocker affecting multiple epics. Investigate Kotest plugin source code, Gradle internals, Kotlin compiler behavior - whatever is needed to find the solution.

**Time Budget**: Unlimited - quality over speed. A working solution is worth days of research.