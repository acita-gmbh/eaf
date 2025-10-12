# Jazzer Fuzz Testing Configuration Research Request

## Problem Statement

I need to determine the **correct way to pass libFuzzer flags** (specifically `-max_total_time=1800`) to Jazzer fuzz tests when running via Gradle test tasks.

I have **conflicting code review feedback** from two AI code reviewers:

**Reviewer 1 (Copilot) says**:
> "The jazzer.flags system property is being passed through as another system property rather than being parsed into JVM arguments. This may not work correctly since Jazzer typically expects flags as JVM arguments. Consider parsing the flags and adding them as JVM arguments: `jvmArgs(flags.split(" "))` instead of setting them as a system property."

**Reviewer 2 (CodeRabbitAI) says**:
> "Do not forward `jazzer.flags` via `jvmArgs`. `jvmArgs("--max_total_time=1800")` ends up on the JVM command line, and the JVM will abort with `Unrecognized option: --max_total_time=1800`. Jazzer expects these flags as a system property (or env var), so we still need to pass them through as the property instead of raw JVM arguments."

**These are contradictory recommendations!** I need to know which is correct.

---

## Current Technical Setup

### Technology Stack
- **Fuzzing Tool**: Jazzer 0.24.0 (from Code Intelligence - https://github.com/CodeIntelligenceTesting/jazzer)
- **Jazzer Integration**: `jazzer-junit` library (JUnit 5 integration)
- **Test Framework**: Kotest 6.0.3 (primary framework), but fuzz tests use Jazzer's `@FuzzTest` annotation
- **Build Tool**: Gradle 9.1.0 with Kotlin DSL (`build.gradle.kts`)
- **JVM**: OpenJDK 21 (Temurin distribution)
- **Execution**: Tests run via Gradle `Test` task with `useJUnitPlatform()`

### Current Gradle Configuration

```kotlin
// In framework/security/build.gradle.kts

val fuzzTest = tasks.register<Test>("fuzzTest") {
    group = "verification"
    description = "Runs Jazzer fuzz tests (nightly/security validation)"
    testClassesDirs = sourceSets["fuzzTest"].output.classesDirs
    classpath = sourceSets["fuzzTest"].runtimeClasspath
    useJUnitPlatform()

    val corpusDirPath = "${project.projectDir}/.jazzer/corpus"

    doFirst {
        val corpusDir = file(corpusDirPath)
        corpusDir.mkdirs()
        logger.lifecycle("✅ Created Jazzer corpus directory: ${corpusDir.absolutePath}")
    }

    // These work correctly:
    systemProperty("jazzer.instrumentation_includes", "com.axians.eaf.**")
    systemProperty("jazzer.corpus_dir", corpusDirPath)
    environment("JAZZER_FUZZ", "1")

    // ⚠️ THIS IS THE QUESTION - WHICH APPROACH IS CORRECT?

    // APPROACH A (Current - CodeRabbitAI recommendation):
    System.getProperty("jazzer.flags")?.takeIf { it.isNotBlank() }?.let { flags ->
        systemProperty("jazzer.flags", flags)
    }

    // APPROACH B (Alternative - Copilot recommendation):
    System.getProperty("jazzer.flags")?.takeIf { it.isNotBlank() }?.let { flags ->
        jvmArgs(flags.split(" "))
    }
}
```

### How It's Invoked

**In GitHub Actions CI workflow**:
```bash
./gradlew fuzzTest -Djazzer.flags="-max_total_time=1800"
```

**By local developers**:
```bash
./gradlew fuzzTest -Djazzer.flags="-max_total_time=60"
```

### Example Fuzz Test Code

```kotlin
package com.axians.eaf.framework.security.fuzz

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest

class RoleNormalizationFuzzer {
    @FuzzTest
    fun fuzzRoleNormalization(data: FuzzedDataProvider) {
        val roleName = data.consumeRemainingAsString()
        try {
            JwtValidationFilter.normalizeRoleAuthority(roleName)
        } catch (e: IllegalArgumentException) {
            // Expected for invalid input
        }
    }
}
```

### Dependencies

```toml
# gradle/libs.versions.toml
[versions]
jazzer = "0.24.0"

[libraries]
jazzer-junit = { module = "com.code-intelligence:jazzer-junit", version.ref = "jazzer" }
jazzer-api = { module = "com.code-intelligence:jazzer-api", version.ref = "jazzer" }
```

```kotlin
// In build.gradle.kts dependencies
fuzzTestImplementation(libs.jazzer.junit)
fuzzTestImplementation(libs.jazzer.api)
```

---

## Research Questions

### PRIMARY QUESTION (Most Critical)

**When using Jazzer's `@FuzzTest` annotation with JUnit 5 Platform via Gradle's `Test` task, how should libFuzzer command-line flags like `-max_total_time=1800` be passed from Gradle to Jazzer?**

Specifically, which of these approaches is correct:

**APPROACH A**: Pass as system property
```kotlin
systemProperty("jazzer.flags", "-max_total_time=1800")
```

**APPROACH B**: Pass as JVM arguments
```kotlin
jvmArgs("-max_total_time=1800".split(" "))
```

**APPROACH C**: Some other method entirely (please describe)

### SECONDARY QUESTIONS

1. **Does Jazzer's JUnit integration (`@FuzzTest` annotation) read libFuzzer options from**:
   - System properties set on the test JVM?
   - JVM command-line arguments?
   - JUnit Platform configuration parameters (`junit-platform.properties`)?
   - Environment variables?
   - All of the above with a specific precedence order?

2. **Property Naming Convention**:
   - If using system properties, should it be a single `jazzer.flags` property containing all flags?
   - Or individual properties like `jazzer.max_total_time=1800`, `jazzer.use_value_profile=1`, etc.?

3. **JVM Argument Format**:
   - If using jvmArgs(), should flags include the `-D` prefix?
   - Should they be formatted as `-max_total_time=1800` or `--max_total_time=1800` or something else?

4. **Gradle Test Task Integration**:
   - When a Gradle `Test` task uses `useJUnitPlatform()`, how are Jazzer-specific configurations passed through to the Jazzer agent?
   - Does the Jazzer agent run as a Java agent (`-javaagent:jazzer.jar`) or through JUnit's extension mechanism?

5. **Command-Line Property Propagation**:
   - How does a property passed via `-Djazzer.flags="..."` to the Gradle JVM get forwarded to the spawned test executor JVM?
   - Do we need special handling with `System.getProperty()` + `systemProperty()`, or is there automatic propagation?

---

## What We've Observed

### Symptoms of Current Configuration

1. **Without any flag propagation**: Fuzz tests run but complete very quickly (~19 seconds) instead of the expected 30 minutes
2. **Corpus directory creation works**: Using `systemProperty("jazzer.corpus_dir", ...)` successfully configures Jazzer to use our corpus directory
3. **JAZZER_FUZZ environment variable works**: Setting `environment("JAZZER_FUZZ", "1")` enables fuzzing mode
4. **Goal**: We need `-max_total_time=1800` to actually limit fuzzing duration to 30 minutes

### Test Execution Context

- Test task spawns separate JVM for test execution (Gradle's standard test isolation)
- JUnit Platform engine discovers and runs `@FuzzTest` methods
- Jazzer agent instruments code and performs coverage-guided fuzzing
- Need to pass configuration from Gradle JVM → Test executor JVM → Jazzer agent

---

## Research Objectives

Please provide comprehensive research with:

### 1. Definitive Answer
Which approach (A, B, or C) is correct for our setup, with high confidence

### 2. Evidence
- Links to official Jazzer documentation
- GitHub issues or discussions demonstrating the correct approach
- Example projects using Jazzer + Gradle + JUnit Platform successfully
- Source code references from Jazzer repository showing how it reads flags

### 3. Technical Explanation
- How does Jazzer's JUnit integration actually read configuration?
- What's the data flow: Gradle JVM → Test JVM → Jazzer agent?
- Why does one approach work and the other fail?

### 4. Working Code Example
Complete, tested Gradle configuration that:
- Accepts flags via `-Djazzer.flags="..."` on command line
- Properly forwards them to Jazzer agent
- Works with JUnit Platform runner
- Compatible with Jazzer 0.24.0

### 5. Validation Method
How can we verify the configuration is working correctly?
- What log messages should we see?
- How to confirm the time limit is being respected?
- Any Jazzer-specific output to look for?

---

## Additional Information That Might Help

### What We Know Works
```kotlin
// These system properties definitely work:
systemProperty("jazzer.instrumentation_includes", "com.axians.eaf.**")
systemProperty("jazzer.corpus_dir", "/path/to/corpus")

// This environment variable definitely works:
environment("JAZZER_FUZZ", "1")
```

### Jazzer Repository
- Main repo: https://github.com/CodeIntelligenceTesting/jazzer
- JUnit integration: `jazzer-junit` module
- Version: 0.24.0

### Related Technologies
- JUnit Platform: 5.10.x (used by Kotest 6.0.3)
- Gradle Test task: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html
- Kotest: https://kotest.io/ (but fuzz tests use Jazzer's annotations, not Kotest's)

---

## Why This Matters

This is a **P0 critical configuration** for our security testing pipeline:
- Fuzz tests run nightly to discover security vulnerabilities
- Without correct time limits, tests either run too short (ineffective) or too long (timeout)
- We need 30-minute fuzzing sessions to achieve adequate coverage
- Incorrect configuration wastes CI resources and provides false confidence

---

## Success Criteria

Your research will be successful if:
1. ✅ We can definitively choose between Approach A, B, or C
2. ✅ We understand WHY it works (not just trial and error)
3. ✅ We have evidence from official sources or proven examples
4. ✅ The solution works in both CI and local development
5. ✅ We can validate that time limits are actually being respected

---

## Request

Please conduct comprehensive research using:
- Official Jazzer documentation and GitHub repository
- Jazzer GitHub issues and discussions
- Example projects using Jazzer + Gradle
- Gradle documentation on test task configuration
- JUnit Platform configuration documentation
- Any other authoritative sources

**Focus on**: Evidence-based recommendations with links to documentation or working examples.

Thank you!
