# Final Deep Research Prompt: Module-Specific Kotest + Spring Boot Failure

**Date**: 2025-09-28
**Context**: Validated solution FAILS in specific module despite working in others
**Urgency**: CRITICAL - Story blocked after 6 failed solution attempts

---

## Executive Summary

**CRITICAL FAILURE**: The validated @Autowired field injection pattern (confirmed by 3 independent research sources) **FAILS to compile** in `products/widget-demo` module while **WORKING perfectly** in `framework/security` and `framework/cqrs` modules.

**Same pattern, same dependencies, same TestingConventionPlugin, same integrationTest source set** → **DIFFERENT RESULTS**

This suggests a **module-specific configuration issue** that prevents Kotest + @SpringBootTest integration in product modules vs framework modules.

---

## Problem Statement (Updated)

### Validated Solution FAILS in Practice

**Research Validation**: 3 independent external sources unanimously recommended:
- Convert from `class Test(params) : FunSpec({...})` (constructor injection)
- To `class Test : FunSpec() { @Autowired fields; init {...} }` (field injection)
- Reason: Constructor injection creates lifecycle timing conflict

**Implementation Result**: Solution **DOES NOT WORK** in target module

### The Paradox

**WORKING** (framework/security):
```kotlin
@SpringBootTest(classes = [SecurityFrameworkTestApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("framework-test")
class TenantContextFilterIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())
        test("filter extracts tenant") { /* works */ }
    }
}
```
**Result**: ✅ COMPILES, ✅ RUNS, ✅ PASSES

**FAILING** (products/widget-demo - IDENTICAL PATTERN):
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("test")
class WidgetRestApiIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var commandGateway: CommandGateway

    init {
        extension(SpringExtension())
        test("should create widget") { /* fails */ }
    }
}
```
**Result**: ❌ 60+ "Unresolved reference" errors on ALL imports and Kotest DSL

### Environment Comparison

| Aspect | framework/security (WORKS) | products/widget-demo (FAILS) |
|--------|---------------------------|------------------------------|
| **Pattern** | @Autowired field injection + init | @Autowired field injection + init |
| **Imports** | IDENTICAL (verified) | IDENTICAL |
| **Source Set** | integrationTest | integrationTest |
| **Convention Plugin** | eaf.testing | eaf.testing |
| **Kotest Version** | 6.0.3 | 6.0.3 |
| **Spring Boot** | 3.5.6 | 3.5.6 |
| **Result** | ✅ COMPILES | ❌ 60+ ERRORS |

**Key Difference**: Plugin configuration
- framework/security: `eaf.kotlin-common` + `eaf.testing`
- widget-demo: `eaf.spring-boot` + `eaf.testing` + `eaf.quality-gates`

---

## Investigation History (6 Failed Attempts)

### Previously Attempted Solutions (ALL FAILED)

1. **Companion object → top-level object** (85 errors remain)
2. **Fix SpringExtension syntax** (still fails)
3. **Configure TestingConventionPlugin** (KotestPluginExtension not accessible)
4. **Add kotest{} block** (extension unresolved)
5. **Rewrite as Axon fixtures** (infrastructure broke)
6. **Convert to field injection** (research solution - STILL 60+ errors)

### What We've Proven

✅ **TestingConventionPlugin is correct** (framework modules work)
✅ **Dependencies are correct** (verified in build.gradle.kts)
✅ **Pattern is correct** (framework/security proves it)
✅ **Imports are correct** (verified identical)
✅ **Source set is correct** (integrationTest works for others)

**Remaining Question**: What module-specific configuration prevents Kotest compilation?

---

## Critical Research Questions

### Primary Question (URGENT)

**Why does the EXACT same @SpringBootTest + Kotest FunSpec pattern compile in framework modules but fail in product modules?**

**Specific Investigation Points**:

1. **Plugin Interaction**: Does `eaf.spring-boot` plugin conflict with Kotest + @SpringBootTest?
   - framework/security uses `eaf.kotlin-common` + `eaf.testing` → WORKS
   - widget-demo uses `eaf.spring-boot` + `eaf.testing` + `eaf.quality-gates` → FAILS
   - Could eaf.spring-boot override classpath configurations?

2. **Classpath Resolution**: Does eaf.spring-boot change how dependencies are resolved?
   - Does it override testImplementation configurations?
   - Does it change the integrationTest source set classpath?
   - Does it add conflicting Spring Boot dependencies?

3. **Annotation Processing**: Does eaf.spring-boot affect Spring annotation processing?
   - Could it change how @SpringBootTest is processed?
   - Could it interfere with Kotest's SpringExtension?
   - Could it change Spring context initialization order?

4. **Source Set Configuration**: Does eaf.spring-boot modify source set behavior?
   - Does it change how integrationTest source set is configured?
   - Does it override TestingConventionPlugin configurations?
   - Does it affect Kotlin compiler classpath?

### Secondary Questions

**Q1**: Can we explicitly apply required plugins to widget-demo to match framework?
- Add `id("io.kotest")` explicitly to widget-demo/build.gradle.kts?
- Remove `eaf.spring-boot` temporarily to test if it's the culprit?
- Add missing kotest-extensions-spring dependencies explicitly?

**Q2**: Are there hidden dependency conflicts in widget-demo?
- Does eaf.spring-boot pull in conflicting Kotest versions?
- Are there Jackson/Spring Boot version mismatches?
- Does the extra complexity of product module dependencies break something?

**Q3**: Is there a different TestContainers configuration affecting compilation?
- framework modules: minimal TestContainers usage
- widget-demo: complex TestContainers with Keycloak, Redis, PostgreSQL
- Could TestContainers listeners affect Kotest DSL resolution?

---

## Technical Context (Updated)

### Module Configuration Details

#### Framework Security (WORKING)

**File**: framework/security/build.gradle.kts
```kotlin
plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
}

dependencies {
    implementation(project(":framework:core"))

    integrationTestImplementation("org.springframework.security:spring-security-test:6.4.2")
    integrationTestImplementation(libs.spring.boot.starter.test)
}
```

**Working Test**:
- File: TenantContextFilterIntegrationTest.kt
- Uses: @SpringBootTest + @Autowired fields + init block
- Result: ✅ COMPILES, ✅ RUNS

#### Widget Demo (FAILING)

**File**: products/widget-demo/build.gradle.kts
```kotlin
plugins {
    id("eaf.spring-boot")      // ← KEY DIFFERENCE
    id("eaf.testing")
    id("eaf.quality-gates")    // ← ADDITIONAL PLUGIN
}

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:security"))
    implementation(project(":framework:persistence"))
    implementation(project(":framework:web"))
    implementation(project(":shared:shared-api"))

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.axon.framework)
    implementation(libs.spring.modulith.starter.core)
    implementation("com.fasterxml.jackson.core:jackson-databind")

    implementation(libs.bundles.database)
    runtimeOnly(libs.postgresql)

    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation(libs.bundles.testcontainers)
}
```

**Failing Test**:
- File: WidgetRestApiIntegrationTest.kt (renamed from WidgetApiIntegrationTest.kt)
- Uses: IDENTICAL pattern to framework/security
- Result: ❌ 60+ "Unresolved reference" errors

### Plugin Analysis Required

**eaf.spring-boot Plugin**: What does this plugin do differently?
- Does it override Spring Boot configurations?
- Does it change how @SpringBootTest annotation is processed?
- Does it conflict with Kotest's SpringExtension?
- Does it modify classpath resolution for test source sets?

**eaf.quality-gates Plugin**: Could this interfere?
- Does it modify test execution?
- Does it change dependency configurations?
- Does it affect compilation classpath?

### Dependency Analysis Required

**Complex Dependencies**: widget-demo has many more dependencies than framework/security
- Axon Framework bundles
- Arrow FP library
- Spring Modulith
- Multiple framework modules
- Shared API

**Questions**:
- Could dependency version conflicts cause classpath issues?
- Could multiple framework dependencies create circular references?
- Could Axon Framework conflict with Spring Boot test context?

---

## Error Analysis (Detailed)

### Current Error Pattern (60+ errors)

```
e: file:///.../WidgetRestApiIntegrationTest.kt:15:33 Unresolved reference 'test'.
e: file:///.../WidgetRestApiIntegrationTest.kt:31:2 Unresolved reference 'SpringBootTest'.
e: file:///.../WidgetRestApiIntegrationTest.kt:32:2 Unresolved reference 'AutoConfigureWebMvc'.
e: file:///.../WidgetRestApiIntegrationTest.kt:37:35 Unresolved reference 'MockMvc'.
e: file:///.../WidgetRestApiIntegrationTest.kt:48:9 Unresolved reference 'listener'.
```

**Analysis**:
- Line 15: Import statement `org.springframework.test.context.DynamicPropertySource` → "Unresolved reference 'test'"
- Line 31: Annotation `@SpringBootTest` → "Unresolved reference 'SpringBootTest'"
- Line 37: Type `MockMvc` → "Unresolved reference 'MockMvc'"

**The compiler cannot resolve ANY Spring Boot or Kotest symbols**

### Comparison: Working vs Failing Compilation

**framework/security compilation**:
```bash
./gradlew :framework:security:compileIntegrationTestKotlin
> BUILD SUCCESSFUL in 1s
```

**widget-demo compilation**:
```bash
./gradlew :products:widget-demo:compileIntegrationTestKotlin
> BUILD FAILED - 60+ unresolved reference errors
```

**Same environment, same pattern, different module** → Something module-specific breaks compilation

---

## Hypothesis: Plugin Interaction Conflict

### eaf.spring-boot Plugin Investigation Required

**Hypothesis**: The `eaf.spring-boot` plugin used by widget-demo (but NOT framework modules) interferes with Kotest + @SpringBootTest compilation.

**Evidence Supporting**:
1. framework/security (eaf.kotlin-common + eaf.testing) → WORKS
2. framework/cqrs (eaf.kotlin-common + eaf.testing) → WORKS
3. widget-demo (eaf.spring-boot + eaf.testing + eaf.quality-gates) → FAILS

**Research Needed**:
- What does eaf.spring-boot plugin do to classpath resolution?
- Does it override Spring Boot dependencies in ways that conflict with Kotest?
- Does it change how @SpringBootTest annotation is processed?
- Does it interfere with TestingConventionPlugin configurations?

### eaf.quality-gates Plugin Investigation

**Additional Factor**: widget-demo also uses eaf.quality-gates (framework doesn't)

**Research Needed**:
- Could quality gates plugin interfere with test compilation?
- Does it modify test source set configurations?
- Does it add annotation processors that conflict?

---

## Required Deep Investigation

### Investigation 1: Plugin Source Code Analysis

**Research eaf.spring-boot Plugin**:
```kotlin
// File: build-logic/src/main/kotlin/conventions/SpringBootConventionPlugin.kt
// Need to analyze what this plugin does to:
// 1. Dependency management (does it override testImplementation?)
// 2. Spring Boot configuration (does it conflict with @SpringBootTest?)
// 3. Source set modifications (does it change integrationTest?)
// 4. Annotation processing (does it interfere with Kotest?)
```

**Research eaf.quality-gates Plugin**:
```kotlin
// File: build-logic/src/main/kotlin/conventions/QualityGatesConventionPlugin.kt
// Need to analyze impact on:
// 1. Test compilation (any annotation processors?)
// 2. Dependency modifications
// 3. Source set configurations
```

### Investigation 2: Dependency Tree Analysis

**Compare dependency trees**:
```bash
# Framework security (working)
./gradlew :framework:security:dependencies --configuration integrationTestCompileClasspath

# Widget demo (failing)
./gradlew :products:widget-demo:dependencies --configuration integrationTestCompileClasspath
```

**Look for**:
- Version conflicts (different Kotest versions?)
- Missing dependencies (Kotest extensions?)
- Extra dependencies (conflicting Spring Boot versions?)
- Classpath ordering issues

### Investigation 3: Kotlin Compiler Classpath Analysis

**Debug compilation with verbose output**:
```bash
./gradlew :products:widget-demo:compileIntegrationTestKotlin --debug 2>&1 | grep -i "kotlin.*source\|classpath"
```

**Compare with working module**:
```bash
./gradlew :framework:security:compileIntegrationTestKotlin --debug 2>&1 | grep -i "kotlin.*source\|classpath"
```

**Look for**:
- Different Kotlin compiler classpaths
- Missing JAR files
- Different source root configurations

### Investigation 4: Plugin Application Order Analysis

**Hypothesis**: Plugin application order affects classpath resolution

**framework/security**:
```kotlin
plugins {
    id("eaf.kotlin-common")  // Base Kotlin setup
    id("eaf.testing")        // Adds Kotest + test source sets
}
```

**widget-demo**:
```kotlin
plugins {
    id("eaf.spring-boot")    // Adds Spring Boot + dependencies
    id("eaf.testing")        // Adds Kotest (after Spring Boot)
    id("eaf.quality-gates")  // Adds quality tools
}
```

**Research**: Does the order matter? Does eaf.spring-boot override configurations that eaf.testing needs?

---

## Specific Research Tasks

### Task 1: Isolate the Conflicting Plugin

**Experiment**: Temporarily modify widget-demo/build.gradle.kts to match framework pattern

```kotlin
plugins {
    id("eaf.kotlin-common")  // Change from eaf.spring-boot
    id("eaf.testing")
    // Remove eaf.quality-gates temporarily
}
```

**Test**: Does the compilation succeed with this change?
**Expected**: If YES → eaf.spring-boot is the culprit
**Expected**: If NO → Deeper issue (dependencies, source set, etc.)

### Task 2: Plugin Source Code Deep Dive

**Analyze SpringBootConventionPlugin.kt**:
```kotlin
// What does this plugin do to:
class SpringBootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // 1. Dependency management - does it override testImplementation?
        // 2. Source set configuration - does it modify integrationTest?
        // 3. Spring Boot setup - does it conflict with @SpringBootTest?
        // 4. Annotation processing - does it interfere with Kotest?
    }
}
```

**Analyze QualityGatesConventionPlugin.kt**:
```kotlin
// Does this plugin:
// 1. Add annotation processors that conflict with Kotest?
// 2. Modify test compilation settings?
// 3. Change classpath resolution order?
```

### Task 3: Dependency Conflict Detection

**Generate dependency reports**:
```bash
# Check for version conflicts
./gradlew :products:widget-demo:dependencyInsight --dependency io.kotest
./gradlew :products:widget-demo:dependencyInsight --dependency org.springframework.boot

# Compare with working module
./gradlew :framework:security:dependencyInsight --dependency io.kotest
./gradlew :framework:security:dependencyInsight --dependency org.springframework.boot
```

**Look for**:
- Different io.kotest versions
- Conflicting Spring Boot versions
- Missing kotest-extensions-spring
- Conflicting Jackson versions

### Task 4: Test a Minimal Reproduction

**Create minimal test** in widget-demo:
```kotlin
package com.axians.eaf.products.widgetdemo.api

import io.kotest.core.spec.style.FunSpec

class MinimalTest : FunSpec() {
    init {
        test("minimal test") {
            println("Hello")
        }
    }
}
```

**Test**: Does even this minimal Kotest test compile?
**If NO**: Kotest DSL completely broken in widget-demo
**If YES**: Issue is with Spring Boot integration specifically

### Task 5: Progressive Complexity Testing

**Add complexity incrementally**:

**Step 1**: Minimal Kotest test (above)
**Step 2**: Add SpringExtension only
**Step 3**: Add @SpringBootTest annotation only
**Step 4**: Add @Autowired field
**Step 5**: Add MockMvc usage

**Find the EXACT point where compilation breaks**

---

## Module Architecture Analysis

### Framework Module Characteristics

**Purpose**: Infrastructure libraries
**Dependencies**: Minimal, focused
**Spring Boot**: Framework components only
**Plugin Stack**: Minimal (kotlin-common + testing)

### Product Module Characteristics

**Purpose**: Deployable applications
**Dependencies**: Complex (multiple frameworks, domain logic)
**Spring Boot**: Full application context
**Plugin Stack**: Complex (spring-boot + testing + quality-gates)

**Research Question**: Are product modules fundamentally incompatible with Kotest + @SpringBootTest due to complexity?

---

## Alternative Architecture Investigations

### Option 1: Separate Integration Test Module

**Hypothesis**: Move integration tests to dedicated submodule

**Structure**:
```
products/
├── widget-demo/           # Main application (keep current plugins)
└── widget-demo-integration/  # Integration tests only
    ├── build.gradle.kts   # Use ONLY eaf.kotlin-common + eaf.testing
    └── src/test/kotlin/   # Standard test source set
```

**Benefits**:
- Isolates from eaf.spring-boot plugin
- Uses standard test source set (not integrationTest)
- Minimal plugin stack like framework modules

**Research**: Would this approach work?

### Option 2: Plugin Ordering Fix

**Hypothesis**: Change plugin application order

```kotlin
plugins {
    id("eaf.testing")        // Apply first
    id("eaf.spring-boot")    # Apply after Kotest setup
    id("eaf.quality-gates")  # Apply last
}
```

**Research**: Does plugin order affect classpath resolution?

### Option 3: Explicit Kotest Configuration

**Hypothesis**: Override convention plugin configuration

```kotlin
plugins {
    id("eaf.spring-boot")
    id("eaf.testing")
    id("io.kotest") version "6.0.3"  // Explicit application
}

kotest {
    // Force kotest DSL for integrationTest source set
}
```

**Research**: Can explicit configuration override plugin conflicts?

---

## Debug Commands for Research

### Classpath Comparison

```bash
# Framework security (working)
./gradlew :framework:security:compileIntegrationTestKotlin --debug 2>&1 | grep -E "classpath|kotlin.*args" > /tmp/framework-compile.log

# Widget demo (failing)
./gradlew :products:widget-demo:compileIntegrationTestKotlin --debug 2>&1 | grep -E "classpath|kotlin.*args" > /tmp/widget-compile.log

# Compare
diff /tmp/framework-compile.log /tmp/widget-compile.log
```

### Plugin Analysis

```bash
# Check applied plugins
./gradlew :framework:security:plugins
./gradlew :products:widget-demo:plugins

# Check effective plugin configurations
./gradlew :framework:security:properties | grep -i kotest
./gradlew :products:widget-demo:properties | grep -i kotest
```

### Dependency Tree Analysis

```bash
# Full dependency comparison
./gradlew :framework:security:dependencies --configuration integrationTestImplementation > /tmp/framework-deps.txt
./gradlew :products:widget-demo:dependencies --configuration integrationTestImplementation > /tmp/widget-deps.txt

diff /tmp/framework-deps.txt /tmp/widget-deps.txt
```

---

## Research Deliverables Needed

### Deliverable 1: Root Cause Identification

**Provide**:
1. **Exact plugin/dependency causing the conflict**
2. **Why it works in framework but not product modules**
3. **Specific configuration that breaks Kotest DSL resolution**
4. **Evidence from plugin source code analysis**

### Deliverable 2: Working Solution

**Provide ONE of**:
1. **Plugin configuration fix** (modify widget-demo plugins)
2. **Dependency fix** (add/remove specific dependencies)
3. **Source set workaround** (different approach to integration tests)
4. **Alternative architecture** (separate integration test module)

### Deliverable 3: Validation Steps

**Provide**:
1. **Exact commands to verify the fix**
2. **Expected vs actual results**
3. **Rollback plan if solution fails**
4. **Testing strategy for the fix**

---

## Critical Success Criteria

### A solution is considered successful if:

1. ✅ **Compilation succeeds**: `./gradlew :products:widget-demo:compileIntegrationTestKotlin` → BUILD SUCCESSFUL
2. ✅ **No regressions**: framework modules still compile and run
3. ✅ **Kotest DSL available**: test, context, beforeEach functions resolved
4. ✅ **Spring Boot integration works**: @SpringBootTest, @Autowired, MockMvc resolved
5. ✅ **Pattern consistency**: Same pattern across all modules
6. ✅ **Sustainable**: Solution works for Epic 8 (licensing-server) integration tests

### Failure Criteria (Abandon Story)

❌ **If no solution found**: Mark Story 4.6 as "Cannot Be Completed" due to architectural limitations
❌ **If solution requires major changes**: Framework-wide plugin rewrite out of scope
❌ **If solution breaks other modules**: Risk too high

---

## Context for External Research

### Investigation Status

**Time Invested**: 6+ hours (James dev agent + external research)
**Attempts**: 6 failed solutions
**Research Sources**: 3 independent analyses (all pointed to same solution that failed)
**Blocker**: Module-specific issue preventing validated solution

### Stakeholder Impact

**Epic 4**: Multi-Tenancy Baseline completion blocked
**Epic 8**: licensing-server integration tests depend on this pattern
**Technical Debt**: Inconsistent test patterns across modules
**Quality**: 5 integration test suites disabled (reduced coverage)

### Constraints (CANNOT CHANGE)

- ❌ Kotlin version: 2.0.10 (PINNED)
- ❌ Spring Boot version: 3.3.5 (LOCKED)
- ❌ Kotest version: 6.0.3 (current stable)
- ❌ Gradle version: 9.1.0 (required for Kotest)
- ❌ Testing mandate: Kotest only (NO JUnit)
- ❌ Framework modules: Cannot modify (risk too high)

---

## Research Scope

### In Scope

✅ **Plugin configuration changes** (widget-demo only)
✅ **Dependency additions/modifications** (widget-demo only)
✅ **Alternative source set approaches** (integrationTest alternatives)
✅ **Workarounds that don't break other modules**
✅ **Creative solutions within constraints**

### Out of Scope

❌ **Framework plugin modifications** (affects all modules)
❌ **Version upgrades/downgrades** (constraints locked)
❌ **JUnit 5 conversion** (violates mandate)
❌ **TestingConventionPlugin rewrite** (high risk)

---

## Research Questions for External Agents

### Technical Questions

1. **What does the eaf.spring-boot Gradle plugin likely do that could break Kotest DSL resolution?**
2. **How can we debug exactly where in the classpath resolution Kotest symbols are lost?**
3. **Are there known conflicts between Spring Boot plugins and Kotest in Gradle 9.1.0?**
4. **Can we override plugin configurations at the module level to fix this?**
5. **What's the minimal configuration needed to make @SpringBootTest + Kotest work?**

### Diagnostic Questions

6. **What Gradle debug commands show exactly why symbols are unresolved?**
7. **How do we compare effective classpaths between working and failing modules?**
8. **What dependency tree differences indicate the source of conflict?**
9. **How do we test if removing specific plugins fixes the issue?**

### Solution Questions

10. **Can we apply plugins in different order to fix classpath issues?**
11. **Can we explicitly add dependencies to override plugin defaults?**
12. **Should integration tests be in standard test/ source set instead of integrationTest/?**
13. **Would a separate gradle submodule work around the plugin conflicts?**

---

## Expected Research Depth

### Go Deep Into

- **Gradle plugin source code** (eaf.spring-boot, eaf.quality-gates)
- **Classpath resolution mechanics** (how Gradle resolves dependencies)
- **Kotlin compiler behavior** (symbol resolution in complex plugin environments)
- **Plugin interaction patterns** (how multiple plugins affect each other)
- **Spring Boot test integration** (annotation processing, context loading)

### Focus Areas

1. **Plugin Conflict Analysis**: Systematic analysis of how eaf.spring-boot affects Kotest
2. **Classpath Debugging**: Deep dive into why symbols become unresolved
3. **Alternative Configurations**: Creative workarounds within constraints
4. **Module Architecture**: Why product modules behave differently than framework

---

## Files for Deep Analysis

### Plugin Configurations (CRITICAL - FULL SOURCE CODE AVAILABLE)

**PRIORITY: Analyze actual plugin source code directly**

**These files contain the exact logic causing the conflict**:
1. `build-logic/src/main/kotlin/conventions/SpringBootConventionPlugin.kt` ⚡ (suspected culprit)
2. `build-logic/src/main/kotlin/conventions/QualityGatesConventionPlugin.kt` (potential contributor)
3. `build-logic/src/main/kotlin/conventions/TestingConventionPlugin.kt` (working baseline)

**Full source code access**: All convention plugins are available in the repository for direct analysis

**Analysis Approach**:
- Read complete plugin source code
- Identify how SpringBootConventionPlugin modifies dependency/classpath resolution
- Compare with TestingConventionPlugin to find conflicts
- Look for specific lines that override integrationTest source set configuration

### Module Configurations
4. `framework/security/build.gradle.kts` (working reference)
5. `products/widget-demo/build.gradle.kts` (failing module)
6. `gradle/libs.versions.toml` (version constraints)

### Working vs Failing Tests
7. `framework/security/src/integration-test/kotlin/.../TenantContextFilterIntegrationTest.kt` (working template)
8. `products/widget-demo/src/integration-test/kotlin-disabled/WidgetApiIntegrationTest.kt` (original failing)

### Build Files
9. `build-logic/build.gradle.kts` (convention plugin dependencies)
10. `settings.gradle.kts` (project structure)

---

## Research Output Format

### Primary Output: Root Cause Analysis

**Required**:
1. **Exact plugin/configuration causing the failure**
2. **Why it affects widget-demo but not framework modules**
3. **Specific line of code in plugin that breaks Kotest**
4. **Evidence from debug commands/logs**

### Secondary Output: Working Solution

**Required**:
1. **Exact configuration changes to make it work**
2. **Step-by-step implementation guide**
3. **Validation commands to prove success**
4. **Impact assessment on other modules**

### Tertiary Output: Alternative Approaches

**If primary solution not viable**:
1. **Separate integration test submodule approach**
2. **Different source set strategy**
3. **Plugin removal/replacement options**
4. **Effort estimates for each alternative**

---

## Research Methodology Suggestions

### Phase 1: Plugin Isolation (High Priority)

1. **Test minimal plugin stack** (remove eaf.spring-boot temporarily)
2. **Add plugins one by one** (find the breaking point)
3. **Compare plugin source code** (SpringBootConventionPlugin vs KotlinCommonConventionPlugin)

### Phase 2: Dependency Analysis (Medium Priority)

1. **Generate full dependency trees** (working vs failing)
2. **Check for version conflicts** (dependency insight commands)
3. **Validate classpath ordering** (debug compilation logs)

### Phase 3: Alternative Architecture (Low Priority)

1. **Test separate module approach**
2. **Test standard test/ source set** (vs integrationTest/)
3. **Research plugin ordering effects**

---

## Success Metrics

### Research Quality

**Excellent Research** would provide:
- ✅ Root cause identified with evidence
- ✅ Working solution with validation
- ✅ Minimal impact on existing code
- ✅ Clear implementation steps

**Good Research** would provide:
- ✅ Root cause identified
- ✅ Workaround solution
- ⚠️ Some impact on existing code
- ✅ Clear next steps

**Acceptable Research** would provide:
- ✅ Contributing factors identified
- ⚠️ Partial solution or workaround
- ⚠️ Significant implementation effort
- ✅ Alternative options

### Implementation Success

**Solution validates if**:
```bash
# 1. Compilation succeeds
./gradlew :products:widget-demo:compileIntegrationTestKotlin
# Expected: BUILD SUCCESSFUL

# 2. No regressions
./gradlew :framework:security:compileIntegrationTestKotlin
./gradlew :framework:cqrs:compileIntegrationTestKotlin
# Expected: Both still BUILD SUCCESSFUL

# 3. Test execution possible
./gradlew :products:widget-demo:integrationTest --tests "*WidgetRestApiIntegrationTest"
# Expected: Test RUNS (may fail for business reasons, but Spring context loads)
```

---

## Critical Research Focus

### Most Valuable Research Direction

**Plugin Conflict Analysis** - Focus 80% effort here
- SpringBootConventionPlugin.kt source code analysis
- How it modifies testImplementation configurations
- Whether it overrides TestingConventionPlugin settings
- Whether it interferes with Kotest plugin registration

### Secondary Research Direction

**Dependency Tree Analysis** - Focus 20% effort here
- Compare integrationTestImplementation between modules
- Look for version conflicts or missing dependencies
- Validate classpath differences

---

## Contact Information

- **Repository**: acita-gmbh/eaf (private)
- **Branch**: feature/story-4.6-re-enable-widget-integration-tests
- **Investigation Files**: `.ai/story-4.6-*` (research documentation)
- **Current Status**: 6th solution attempt failed, validated pattern doesn't work
- **Urgency**: HIGH - Epic 4 completion blocked

---

## Quick Start for External Research

**TL;DR**:
1. **The validated field injection pattern FAILS in widget-demo despite working in framework modules**
2. **Focus on eaf.spring-boot plugin** - likely culprit
3. **Compare plugin source code** - SpringBootConventionPlugin vs others
4. **Test plugin isolation** - remove eaf.spring-boot temporarily
5. **Provide working fix** - exact configuration changes needed

**Most Critical Question**: Why does `eaf.spring-boot` plugin break Kotest + @SpringBootTest compilation when `eaf.kotlin-common` works fine?

**Success**: Make this compile:
```bash
./gradlew :products:widget-demo:compileIntegrationTestKotlin
```

**Time Budget**: Unlimited - this is the final blocker for Story 4.6 and Epic 4 completion.

---

## Plugin Source Code (FULL ACCESS PROVIDED)

### SpringBootConventionPlugin.kt (SUSPECTED CULPRIT)

```kotlin
package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class SpringBootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")           // ← Applies TestingConventionPlugin
                apply("org.springframework.boot")    // ← Could override configurations?
                apply("io.spring.dependency-management")  // ← Could conflict with Kotest deps?
                apply("org.jetbrains.kotlin.plugin.spring")  // ← Kotlin Spring plugin
                apply("org.jetbrains.kotlin.plugin.jpa")     // ← JPA plugin
            }

            dependencies {
                // Adds Spring Boot starters to implementation configuration
                // Could this interfere with testImplementation/integrationTestImplementation?
                addAll("implementation", listOf(
                    "spring-boot-starter-web",
                    "spring-boot-starter-actuator",
                    "spring-boot-starter-validation",
                    "spring-boot-starter-security",
                    "spring-boot-starter-oauth2-resource-server",
                    "spring-modulith-starter-core",
                    "spring-modulith-starter-jpa"
                ))
            }
        }
    }
}
```

**Critical Analysis Points**:
1. **Line 17**: Applies `eaf.kotlin-common` which includes TestingConventionPlugin
2. **Line 18**: `org.springframework.boot` plugin - could override @SpringBootTest annotation processing?
3. **Line 19**: `io.spring.dependency-management` - could override dependency versions?
4. **Lines 20-21**: Kotlin plugins for Spring/JPA - could affect annotation processing?

### QualityGatesConventionPlugin.kt Excerpt

```kotlin
class QualityGatesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")  // ← DUPLICATE TestingConventionPlugin application!
                apply("jacoco")
                apply("info.solidsoft.pitest")
                apply("org.owasp.dependencycheck")
            }

            // Lines 189-194: Explicit integrationTest task dependency
            listOf("konsistTest", "integrationTest", "pitest").forEach { taskName ->
                if (tasks.findByName(taskName) != null) {
                    tasks.named("check") { dependsOn(taskName) }
                }
            }

            // Line 97: Pitest configuration with Kotest
            configure<PitestPluginExtension> {
                testPlugin.set("kotest")  // ← Could interfere with Kotest compilation?
            }
        }
    }
}
```

**Critical Discovery**: Multiple Plugin Application Issue

**widget-demo plugin chain**:
```
plugins {
    id("eaf.spring-boot")     // → applies eaf.kotlin-common → TestingConventionPlugin
    id("eaf.testing")         // → TestingConventionPlugin (DUPLICATE!)
    id("eaf.quality-gates")   // → applies eaf.kotlin-common → TestingConventionPlugin (TRIPLE!)
}
```

**Result**: TestingConventionPlugin applied **3 TIMES** in widget-demo vs **1 TIME** in framework modules

**Hypothesis**: Multiple plugin applications corrupt integrationTest source set configuration

---

**Quick Start for External Research**

**URGENT DISCOVERY**: TestingConventionPlugin applied 3 times due to plugin chain

**Primary Investigation**:
1. **Multiple plugin application effect** - Does 3x TestingConventionPlugin break integrationTest?
2. **Spring Boot plugin conflicts** - Does `org.springframework.boot` override @SpringBootTest processing?
3. **Dependency management conflicts** - Does `io.spring.dependency-management` override Kotest versions?
4. **Kotlin Spring plugin effects** - Do `kotlin.plugin.spring` affect annotation processing?

**Plugin Analysis Priority**:
1. **TestingConventionPlugin** multiple application (3x in widget-demo vs 1x in framework)
2. **SpringBootConventionPlugin** dependencies and plugin applications
3. **io.spring.dependency-management** version override behavior
4. **org.springframework.boot** annotation processing changes

**Most Critical Question**: Does applying TestingConventionPlugin 3 times corrupt the integrationTest source set?

**Success**: Make this compile:
```bash
./gradlew :products:widget-demo:compileIntegrationTestKotlin
```

**Evidence**: Full plugin source code provided above for direct analysis

**Time Budget**: Unlimited - final blocker for Story 4.6 and Epic 4 completion