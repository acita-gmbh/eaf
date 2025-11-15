# Deep Research Request: Gradle jvm-test-suite Plugin in Convention Plugins

## Mission

Research and provide a **complete, working, compilable** Gradle convention plugin that uses the `jvm-test-suite` plugin to configure multiple test suites in a Kotlin monorepo project using Gradle 9.1.0.

## Clarifying Questions - ANSWERED

**Q1: Do all subprojects apply "java" or "java-library" plugin?**
**A:** YES. All subprojects apply `eaf.kotlin-common` convention plugin, which applies `kotlin("jvm")`. The Kotlin JVM plugin automatically applies the `java` plugin. Therefore, the default `test` suite already exists in all modules.

**Q2: How to identify framework modules for nightlyTest suite?**
**A:** Based on module path:
```kotlin
val isFrameworkModule = project.path.startsWith(":framework:") ||
                        project.path.startsWith(":shared:")
```
Framework modules: `framework/*` and `shared/*` (9 + 3 = 12 modules)
Product modules: `products/*` and `tools/*` (10 modules)

**Q3: Should test suite tasks be wired into standard lifecycle?**
**A:**
- `test`: Already in `check` (default)
- `integrationTest`: YES, should be added to `check` task
- `nightlyTest`: NO, standalone only (runs with `-DrunNightly=true` flag)

Example:
```kotlin
tasks.named("check") {
    dependsOn(tasks.named("integrationTest"))
    // nightlyTest NOT included - optional/nightly only
}
```

## Context

### Project Setup
- **Gradle Version:** 9.1.0 (wrapper)
- **Kotlin Version:** 2.2.21
- **Build Structure:** Monorepo with convention plugins in `build-logic/` module
- **Plugin Type:** Binary Gradle plugin (`.kt` file, not precompiled script `.gradle.kts`)
- **Location:** `build-logic/src/main/kotlin/conventions/EafTestingV2Plugin.kt`

### build-logic Configuration

The `build-logic` module uses:
```kotlin
plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}
```

Dependencies include Gradle plugin portal and various Gradle plugins. The module compiles convention plugins that are then used across 22 subprojects.

### What We're Trying To Achieve

Create a convention plugin named `EafTestingV2Plugin` that:

1. **Applies the `jvm-test-suite` plugin**
2. **Configures 3 test suites:**
   - `test`: Default suite (unit tests + architecture) - MODIFY existing
   - `integrationTest`: New suite for Testcontainers tests - REGISTER new
   - `nightlyTest`: New suite for nightly tests - REGISTER new (conditionally, framework modules only)

3. **Each suite needs:**
   - `useJUnitPlatform()` configuration
   - Dependencies from version catalog (`libs`)
   - Task configuration (parallel execution, timeouts, etc.)
   - Kotlin friend paths (access to `internal` members)

4. **Must work in a binary convention plugin** (not build.gradle.kts)

## The Problem

### Errors Encountered

When trying various approaches, we get these compilation errors:

**Approach 1: Using `configure<TestingExtension>`**
```kotlin
configure<TestingExtension> {
    suites {
        val test by getting(JvmTestSuite::class) { ... }
    }
}
```
**Error:**
- `Unresolved reference 'TestingExtension'` with import `org.gradle.testing.base.TestingExtension`
- `Unresolved reference 'TestingExtension'` with import `org.gradle.api.plugins.testing.TestingExtension`

**Approach 2: Using `testing { }` directly**
```kotlin
testing {
    suites { ... }
}
```
**Error:**
- `Unresolved reference 'testing'`

**Approach 3: Using `extensions.getByType(TestingExtension::class.java)`**
```kotlin
val testingExtension = extensions.getByType(TestingExtension::class.java)
testingExtension.suites.apply { ... }
```
**Error:**
- `Unresolved reference 'TestingExtension'` (same as Approach 1)

**Verification:**
- Checked Gradle 9.1 Javadocs: `https://docs.gradle.org/9.1/javadoc/org/gradle/testing/base/TestingExtension.html` returns **404**
- This suggests `TestingExtension` may not exist in Gradle 9.1, or is in a different package

### Attempted Imports

We've tried all these imports without success:
```kotlin
import org.gradle.testing.base.TestingExtension  // Unresolved
import org.gradle.api.plugins.testing.TestingExtension  // Unresolved
import org.gradle.api.plugins.TestingExtension  // Unresolved
import org.gradle.api.plugins.jvm.JvmTestSuite  // Works!
```

**Note:** `JvmTestSuite` import WORKS with `org.gradle.api.plugins.jvm.JvmTestSuite`

## Research Questions

### PRIMARY QUESTIONS (MUST ANSWER):

1. **What is the correct, fully-qualified class name for the testing extension in Gradle 9.1.0?**
   - Provide the exact package and class name
   - Verify it exists in Gradle 9.1 (not 9.2, not 8.x)
   - Provide Javadoc link if available

2. **How do you access test suites in a BINARY Gradle plugin (`.kt` file) vs. build.gradle.kts?**
   - In build.gradle.kts: `testing { suites { } }` works
   - In binary plugin: What's the equivalent?
   - Do you need `extensions.getByType()`? If so, what type?

3. **What are ALL the necessary imports for jvm-test-suite in a convention plugin?**
   - Provide complete import block
   - Include imports for: TestingExtension (or equivalent), JvmTestSuite, configuration methods
   - Verify these imports compile in a `kotlin-dsl` plugin project

### SECONDARY QUESTIONS (NICE TO HAVE):

4. **How do you configure the default 'test' suite in a convention plugin?**
   - It already exists (created by java plugin)
   - Do you use `.named("test")` or `.getByName("test")`?
   - What's the lambda receiver type?

5. **How do you register a NEW suite (like 'integrationTest') in a convention plugin?**
   - Do you use `.register("integrationTest", JvmTestSuite::class.java)`?
   - What's the correct syntax?

6. **How do you access the version catalog (`libs`) in a convention plugin?**
   - We're using: `extensions.getByType<VersionCatalogsExtension>().named("libs")`
   - Is this correct?

7. **Are there any differences between Gradle 9.0, 9.1, and 9.2 for jvm-test-suite?**
   - Any API changes we should know about?
   - Any deprecations or migrations?

## Expected Output

### CRITICAL: Provide a complete, copy-pasteable convention plugin file

**Requirements:**
1. ✅ Must compile without errors in Gradle 9.1.0
2. ✅ Must be a binary plugin (`.kt` file, class extends `Plugin<Project>`)
3. ✅ Must use jvm-test-suite plugin (or explain why it can't be used in conventions)
4. ✅ Must configure 3 test suites (test, integrationTest, nightlyTest)
5. ✅ Must include ALL necessary imports (nothing unresolved)
6. ✅ Must access version catalog correctly
7. ✅ Must work in a `kotlin-dsl` convention plugin context

**Format:**
```kotlin
package conventions

// ALL IMPORTS HERE (complete list)
import org.gradle...
import org.gradle...

class EafTestingV2Plugin : Plugin<Project> {
    override fun apply(project: Project) {
        // COMPLETE WORKING IMPLEMENTATION
    }
}
```

### Alternative: If jvm-test-suite doesn't work in convention plugins

If the jvm-test-suite plugin fundamentally cannot be used in binary convention plugins, please:

1. **Explain WHY** (technical limitation, API restriction, etc.)
2. **Provide evidence** (Gradle issue, documentation, Stack Overflow)
3. **Suggest best alternative** (precompiled scripts? manual source sets? other?)

## Research Strategy

### Recommended Approach:

1. **Check Gradle 9.1.0 Official Javadocs**
   - Search for TestingExtension class
   - Find its actual package
   - Verify it exists

2. **Find Working Examples**
   - Search GitHub for: "jvm-test-suite convention plugin Gradle 9"
   - Look for: Kotlin DSL, binary plugins, NOT build.gradle.kts examples
   - Filter by: Recent (2024-2025), Gradle 9.x

3. **Check Gradle Source Code**
   - Look at how jvm-test-suite plugin actually works
   - Find the extension it registers
   - Check if it's compatible with convention plugins

4. **Consult Gradle Forums/Issues**
   - Search for: jvm-test-suite in convention plugins
   - Common pitfalls or known limitations

### Sources to Prioritize:

- ✅ Gradle 9.1.0 official documentation (docs.gradle.org)
- ✅ Gradle 9.1.0 Javadocs
- ✅ Gradle GitHub repository (gradle/gradle)
- ✅ Recent Stack Overflow (2024-2025)
- ✅ Working open-source projects using Gradle 9.x
- ❌ Older Gradle versions (7.x, 8.x) - API may have changed

## Validation Criteria

The solution is correct if:

1. ✅ Code compiles without errors: `./gradlew :build-logic:compileKotlin`
2. ✅ All imports resolve (no "Unresolved reference" errors)
3. ✅ Can be applied to a module: `plugins { id("eaf.testing-v2") }`
4. ✅ Test suites are created (verify with `./gradlew :module:tasks --group verification`)
5. ✅ Tests run: `./gradlew :module:test :module:integrationTest`

## Additional Context

### What We Know Works:

**In build.gradle.kts files (NOT convention plugins):**
```kotlin
plugins {
    id("jvm-test-suite")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitPlatform()
        }
    }
}
```
This syntax WORKS in build.gradle.kts but NOT in our convention plugin.

### Our Current Working Plugin (v1)

For reference, our old working plugin does this:
```kotlin
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            val integrationTest = sourceSets.create("integrationTest") {
                compileClasspath += sourceSets.getByName("main").output
                runtimeClasspath += output + compileClasspath
            }

            tasks.register("integrationTest", Test::class.java) {
                testClassesDirs = integrationTest.output.classesDirs
                classpath = integrationTest.runtimeClasspath
                useJUnitPlatform()
            }
        }
    }
}
```

This works but is manual. We want to use jvm-test-suite for:
- Automatic source set creation
- Automatic configuration management
- Better Gradle integration
- Modern, declarative approach

## Success Metrics

**Your research is successful if you provide:**

1. ✅ Working code that compiles
2. ✅ Explanation of why previous approaches failed
3. ✅ Clear documentation of the correct API
4. ✅ OR: Clear explanation of why jvm-test-suite can't be used in convention plugins

**Bonus:**
- Links to working examples
- Gradle version compatibility notes
- Migration path if API changed between versions

## Deadline

**This is urgent research.** We need a solution to unblock Phase 2 of a testing modernization effort.

---

**RESEARCHERS: Please provide your findings in a structured format with:**
1. Answer to each primary question
2. Complete working code
3. Explanation and evidence
4. Links to sources

Thank you!
