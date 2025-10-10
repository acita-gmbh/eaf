# Pitest Mutation Testing Troubleshooting Guide

**Story 8.6 - AC24 Task 6.4**: Troubleshooting guide for common mutation testing issues.

---

## Common Issues and Solutions

### Issue 1: "Mutation coverage below 80%"

**Symptom**: Pitest fails with threshold violation
```text
Exception in thread "main" java.lang.RuntimeException:
  Mutation coverage of 29 is below threshold of 80
```

**Diagnosis**:
- Check HTML report: `framework/security/build/reports/pitest/index.html`
- Review surviving mutants by package
- Identify NO_COVERAGE vs SURVIVED mutations

**Solution**:
1. **NO_COVERAGE mutations** (highest priority):
   - These represent untested code paths
   - Add unit/integration tests to execute the code
   - Focus on critical paths (security, multi-tenancy)

2. **SURVIVED mutations** (covered but not killed):
   - Review mutant description in HTML report
   - Add assertions to kill specific mutants
   - See "Common Surviving Mutants" section below

3. **Temporary threshold adjustment** (if needed):
   ```kotlin
   // framework/security/build.gradle.kts
   configure<PitestPluginExtension> {
       mutationThreshold.set(60) // Lower temporarily
   }
   ```

---

### Issue 2: "No mutations found"

**Symptom**: Pitest succeeds but shows 0 mutations
```text
14:00:33 PIT >> INFO : Created 0 mutation test units in pre scan
Exception in thread "main" org.pitest.help.PitHelpError:
  No mutations found. This probably means there is an issue with
  either the supplied classpath or filters.
```

**Diagnosis**:
- Check `targetClasses` filter matches your package structure
- Verify compiled classes exist in `build/classes/kotlin/main`

**Solution**:
```kotlin
// Override targetClasses to match actual package structure
configure<PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
}
```

**Common cause**: Group name (`eaf-monorepo.framework`) doesn't match package structure (`com.axians.eaf.*`).

---

### Issue 3: "Coverage generator Minion exited abnormally"

**Symptom**: Pitest crashes during coverage generation
```text
14:00:54 PIT >> SEVERE : Coverage generator Minion exited abnormally
  due to UNKNOWN_ERROR
java.lang.NoSuchMethodError: 'org.junit.runner.Description
  org.junit.runner.Description.createSuiteDescription(...)'
```

**Diagnosis**:
- JUnit 4/5 version conflict on test classpath
- Check for `junit:junit:4.13.2` in dependencies

**Solution**:
```kotlin
// Exclude JUnit 4 from test runtime classpath
configurations.named("testRuntimeClasspath") {
    exclude(group = "junit", module = "junit")
}

// Configure pitest for JUnit 5 + Kotest
configure<PitestPluginExtension> {
    testPlugin.set(null as String?) // Disable deprecated setting
    junit5PluginVersion.set("1.2.1")
    useClasspathFile.set(true)
}
```

---

### Issue 4: Pitest timeout locally

**Symptom**: Pitest runs >10 minutes on single module

**Diagnosis**:
- Multi-threading may not be working
- Check CPU utilization during pitest run

**Solution**:
```kotlin
// Verify multi-threading configuration
configure<PitestPluginExtension> {
    threads.set(Runtime.getRuntime().availableProcessors())
}
```

**Workaround**: Skip pitest locally, rely on CI
```bash
./gradlew check -x pitest  # Fast local feedback
```

---

## Common Surviving Mutants

### Category 1: Boundary Conditions

**Mutant**: `removed conditional - replaced equality check with true/false`

**Example**:
```kotlin
// Original
if (value > MAX) { error() }

// Mutant
if (true) { error() }  // or if (false)
```

**Fix**: Add test at exact boundary
```kotlin
test("should accept value at MAX") {
    validate(MAX) shouldBe valid
}
test("should reject value at MAX+1") {
    validate(MAX + 1) shouldBe invalid
}
```

---

### Category 2: Null Handling

**Mutant**: `replaced return value with null`

**Example**:
```kotlin
// Original
fun getValue(): String? = result

// Mutant
fun getValue(): String? = null
```

**Fix**: Assert on actual return value
```kotlin
test("should return expected value") {
    val result = getValue()
    result shouldNotBe null
    result shouldBe "expected"  // Not just null check!
}
```

---

### Category 3: Void Method Calls (Logging/Metrics)

**Mutant**: `removed call to Logger::info` or `removed call to Timer::record`

**Example**:
```kotlin
// Original
log.info("Processing")
metrics.counter("processed").increment()

// Mutant - calls removed
// (no-op)
```

**Decision**: Often **ACCEPTABLE** - these are infrastructure concerns

**Documentation** (if accepting):
```markdown
## Accepted Equivalent Mutants

**File**: TenLayerJwtValidator.kt:196
**Mutant**: removed call to Timer::record
**Rationale**: Metrics recording is observability infrastructure, not business logic.
Removing the call doesn't affect correctness, only observability.
**Security Impact**: None
**Accepted**: Yes
```

---

### Category 4: Math Mutator

**Mutant**: `Replaced long subtraction with addition`

**Example**:
```kotlin
// Original
val duration = end - start

// Mutant
val duration = end + start
```

**Fix**: Assert on calculated value
```kotlin
test("should calculate correct duration") {
    val duration = calculateDuration(start = 100, end = 150)
    duration shouldBe 50  // Not just "shouldNotBe null"!
}
```

---

## Fast Feedback Workflow

### Local Development (Skip Pitest)
```bash
# Fast feedback cycle (~2.5 minutes)
./gradlew check -x pitest

# What runs:
# - ktlint (formatting)
# - detekt (static analysis)
# - jvmKotest (unit tests)
# - integrationTest
# - konsistTest (architecture)
# - jacoco (coverage)
```

**Rationale**: Preserve TDD fast feedback loops. Mutation testing runs in CI.

### Pre-PR Validation (Full Quality Gate)
```bash
# Complete quality gate (~6-8 minutes with pitest)
./gradlew check

# Includes everything above PLUS:
# - pitest (mutation testing)
```

**Rationale**: Validate mutation coverage before pushing to PR.

---

## Monitoring Mutation Coverage

### Monthly Review Process
```bash
# 1. Run mutation testing
./gradlew pitest

# 2. Review HTML reports
open framework/security/build/reports/pitest/index.html

# 3. Analyze trend
# - Target: ≥80% mutation coverage
# - Alert: <75% (drift from target)

# 4. Action if <80%
# - Add tests for NO_COVERAGE mutations (highest priority)
# - Address SURVIVED mutations in covered code
# - OR document equivalent mutants
```

### Handling Surviving Mutants

**Process**:
1. **Categorize**: Boundary/boolean/return/null/void-call
2. **Test Addition**: Add tests to kill mutants (see categories above)
3. **Equivalence Check**: If unkillable, verify semantic equivalence
4. **Documentation**: Record in `framework/*/docs/mutation-coverage.md`

**Example Documentation**:
```markdown
## framework/security Accepted Equivalent Mutants

### Metrics Recording (15 mutants)
**Mutator**: VoidMethodCallMutator
**Lines**: 196, 198, 203, ...
**Description**: Removed calls to metrics recording
**Rationale**: Observability infrastructure, not business logic
**Security Impact**: None
**Accepted**: Yes (all VoidMethodCall to metrics/logging)
```

---

## CI Integration

### Expected CI Behavior
```yaml
# .github/workflows/quality.yml
mutation-testing:
  runs-on: ubuntu-latest
  timeout-minutes: 45
  steps:
    - run: ./gradlew pitest
```

**Success Criteria**:
- Framework modules execute (not skipped)
- Reports generated in build/reports/pitest/
- Job fails if coverage <80%
- Job completes <40 minutes

### If CI Times Out

**Diagnosis**: Check execution time in CI logs

**Solutions**:
1. **Incremental approach**: Enable modules one at a time
2. **Threshold adjustment**: Lower temporarily
3. **Non-blocking**: `continue-on-error: true` until optimized

---

## Pitest Configuration Reference

### Framework Module Pattern
```kotlin
plugins {
    id("eaf.kotlin-common")
    id("eaf.testing")
    id("eaf.quality-gates") // Applies pitest
}

// Override package filter and exclude JUnit 4
configure<PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?) // Disable deprecated setting
    junit5PluginVersion.set("1.2.1")
    useClasspathFile.set(true)
}

// Prevent JUnit 4/5 conflict
configurations.named("testRuntimeClasspath") {
    exclude(group = "junit", module = "junit")
}
```

### Quality Gates Plugin Settings
```kotlin
// build-logic/src/main/kotlin/conventions/QualityGatesConventionPlugin.kt
pitest {
    pitestVersion = "1.19.0-rc.1" // Gradle 9 compatible
    mutators = "STRONGER" // Aggressive mutations
    mutationThreshold = 80 // Minimum 80%
    coverageThreshold = 85 // Minimum 85%
    threads = Runtime.getRuntime().availableProcessors()
    outputFormats = ["XML", "HTML"]
    failWhenNoMutations = hasSources // Smart detection
}
```

---

## Getting Help

**Pitest Documentation**: [https://pitest.org](https://pitest.org)
**Kotest Pitest Extension**: [https://kotest.io/docs/extensions/pitest.html](https://kotest.io/docs/extensions/pitest.html)
**Gradle Pitest Plugin**: [https://gradle-pitest-plugin.solidsoft.info/](https://gradle-pitest-plugin.solidsoft.info/)

**Internal**:
- Story 8.6: Enable Mutation Testing (this story)
- Dev Notes: Mutation Testing Overview
- QA Assessments: docs/qa/assessments/8.6-*

---

**Last Updated**: 2025-10-10 (Story 8.6)
**Maintainer**: Framework Team
