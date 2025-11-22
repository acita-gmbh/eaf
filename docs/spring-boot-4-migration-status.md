# Spring Boot 4.0 Migration Status Report

**Date:** 2025-11-22
**Branch:** `claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b`
**Status:** ⚠️ **PREPARATORY PHASE COMPLETE - BLOCKED BY NETWORK CONSTRAINTS**

---

## Executive Summary

I've successfully completed the preparatory phase of the Spring Boot 4.0 / Spring Framework 7.0 upgrade, but am currently blocked by network access limitations that prevent downloading Gradle dependencies. All critical configuration changes have been committed and pushed.

### ✅ Completed Steps

1. **Version Catalog Updated** (Commit: 82cc006)
   - Spring Boot 3.5.7 → 4.0.0
   - Spring Framework 6.2.12 → 7.0.0
   - Spring Modulith 1.4.4 → 2.0.0
   - Jackson 2.20.1 → 3.0.0

2. **JUnit Platform Version Forcing** (Commit: 32a8de7)
   - Added resolution strategy to force JUnit Platform 6.0.1
   - Prevents version conflicts between Jupiter and Platform

3. **OpenRewrite Configuration** (Commit: 99393b6)
   - Added OpenRewrite plugin 6.29.6
   - Configured Spring Boot 4.0 upgrade recipe
   - Configured Jackson 3.0 migration recipe

4. **Comprehensive Documentation** (Commit: b44af68)
   - Created 60-page upgrade plan with detailed analysis
   - Documented all breaking changes
   - Provided migration strategies and risk assessment

### 🚫 Blocked Steps (Network Access Required)

The following steps require network access to Maven Central and Gradle repositories:

1. **OpenRewrite Execution**
   - Command: `./gradlew rewriteDryRun` (preview changes)
   - Command: `./gradlew rewriteRun` (apply changes)
   - Would automate Jackson 3.0 package renames

2. **Build Execution**
   - Command: `./gradlew clean build`
   - Would download Spring Boot 4.0, Spring Framework 7.0, Spring Modulith 2.0 dependencies
   - Would reveal compilation errors to fix

3. **Dependency Resolution**
   - Download ~200+ transitive dependencies
   - Resolve version conflicts
   - Verify Axon Framework 4.12.2 compatibility

---

## Manual Migration Steps (For Environments With Network Access)

Since OpenRewrite cannot run in this environment, here's a comprehensive manual migration guide:

### Step 1: Jackson 3.0 Package Renames

**Search & Replace** across all `.kt` files:

```bash
# Primary package rename
com.fasterxml.jackson.databind → tools.jackson.databind
com.fasterxml.jackson.core → tools.jackson.core
com.fasterxml.jackson.annotation → tools.jackson.annotation
com.fasterxml.jackson.module.kotlin → tools.jackson.module.kotlin

# Exception: jackson-annotations stays at com.fasterxml.jackson.core
# (this is handled by Jackson 3.0 automatically)
```

**Affected Files (11 files):**
```
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestSecurityConfig.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/auth/AuthControllerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/config/RestConfigurationTest.kt
framework/web/src/main/kotlin/com/axians/eaf/framework/web/config/RestConfiguration.kt
framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/KeycloakUserDirectory.kt
framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/eventstore/PostgresEventStoreConfiguration.kt
framework/multi-tenancy/src/test/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilterTest.kt
framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilter.kt
```

**Example Migration:**

```kotlin
// BEFORE (Jackson 2.x)
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.core.JsonProcessingException

class MyClass {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun serialize(data: Any): String {
        return try {
            mapper.writeValueAsString(data)
        } catch (e: JsonProcessingException) {
            throw RuntimeException("Serialization failed", e)
        }
    }
}

// AFTER (Jackson 3.0)
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.core.JacksonException

class MyClass {
    // Jackson 3.0: ObjectMapper is immutable, use builder
    private val mapper: ObjectMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    fun serialize(data: Any): String {
        return try {
            mapper.writeValueAsString(data)
        } catch (e: JacksonException) { // Exception hierarchy changed
            throw RuntimeException("Serialization failed", e)
        }
    }
}
```

### Step 2: Jackson Exception Hierarchy Updates

**Exception Renames:**
```kotlin
// Old → New
JsonProcessingException → JacksonException
JsonParseException → StreamReadException
JsonGenerationException → StreamWriteException
JsonMappingException → DatabindException
```

**Search for:**
- `catch (e: JsonProcessingException)`
- `throws JsonProcessingException`
- `import com.fasterxml.jackson.core.JsonProcessingException`

**Replace with:**
- `catch (e: JacksonException)`
- `throws JacksonException`
- `import tools.jackson.core.JacksonException`

### Step 3: Jackson Configuration Changes

**Immutable ObjectMapper Configuration:**

```kotlin
// BEFORE (Jackson 2.x - Mutable)
val mapper = ObjectMapper()
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
mapper.registerModule(JavaTimeModule())
mapper.registerModule(KotlinModule())

// AFTER (Jackson 3.0 - Immutable Builder)
import tools.jackson.databind.json.JsonMapper
import tools.jackson.datatype.jsr310.JavaTimeModule
import tools.jackson.module.kotlin.kotlinModule

val mapper = JsonMapper.builder()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Important: now enabled by default!
    .addModule(JavaTimeModule())
    .addModule(kotlinModule())
    .build()
```

**CRITICAL DEFAULT CHANGES:**

Jackson 3.0 changes several default configurations:

1. **WRITE_DATES_AS_TIMESTAMPS** = `true` (was `false` in 2.x)
   - **Impact:** Dates serialize as numbers instead of ISO-8601 strings
   - **Fix:** Explicitly disable if you need ISO-8601 format

2. **FAIL_ON_UNKNOWN_PROPERTIES** = `false` (was `true` in 2.x)
   - **Impact:** Unknown JSON properties silently ignored
   - **Fix:** Explicitly enable if you want strict validation

3. **FAIL_ON_NULL_FOR_PRIMITIVES** = `true` (was `false` in 2.x)
   - **Impact:** Null values for `int`, `long`, etc. now fail
   - **Fix:** Use nullable types (`Int?`) or provide defaults

4. **FAIL_ON_TRAILING_TOKENS** = `true` (was `false` in 2.x)
   - **Impact:** Extra content after JSON value causes failure
   - **Fix:** Cleaner JSON parsing (usually desired)

5. **SORT_PROPERTIES_ALPHABETICALLY** = `true` (was `false` in 2.x)
   - **Impact:** JSON property order changes
   - **Fix:** Usually not an issue unless order matters

### Step 4: Configuration Property Updates

**Search and replace in all `application.yml` / `application.properties`:**

```yaml
# OLD
management:
  tracing:
    enabled: true

spring:
  dao:
    exceptiontranslation:
      enabled: true

# NEW
management:
  tracing:
    export:
      enabled: true

spring:
  persistence:
    exceptiontranslation:
      enabled: true
```

**Files to check:**
```bash
find . -name "application*.yml" -o -name "application*.properties"
```

### Step 5: Fix @Nested Test Classes (14 Files)

**Spring Framework 7.0 changes SpringExtension behavior for @Nested classes.**

**If tests fail**, add this annotation to the **top-level test class**:

```kotlin
import org.springframework.test.context.junit.jupiter.SpringExtensionConfig

@SpringBootTest
@SpringExtensionConfig(useTestClassScopedExtensionContext = true) // ADD THIS
class MyIntegrationTest {

    @Nested
    inner class WhenSomething {
        @Test
        fun `should do something`() {
            // Test code
        }
    }
}
```

**Files with @Nested (14):**
```
shared/testing/src/test/kotlin/com/axians/eaf/testing/architecture/ArchitectureTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/query/WidgetProjectionEventHandlerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/query/WidgetQueryHandlerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/RealisticWorkloadPerformanceTest.kt
products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/SnapshotPerformanceTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/rest/ProblemDetailExceptionHandlerTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/pagination/CursorPaginationSupportTest.kt
framework/web/src/test/kotlin/com/axians/eaf/framework/web/config/RestConfigurationTest.kt
framework/security/src/perfTest/kotlin/com/axians/eaf/framework/security/validation/Jwt10LayerValidationIntegrationTest.kt
framework/security/src/konsist-test/kotlin/com/axians/eaf/framework/security/SecurityModuleArchitectureTest.kt
framework/multi-tenancy/src/integration-test/kotlin/com/axians/eaf/framework/multitenancy/TenantValidationInterceptorIntegrationTest.kt
```

### Step 6: Build and Resolve Compilation Errors

```bash
# Clean build
./gradlew clean build

# If errors occur, fix them iteratively:
# 1. Jackson package import errors → Step 1
# 2. Jackson exception errors → Step 2
# 3. Jackson configuration errors → Step 3
# 4. Null safety errors → Review Kotlin null safety
# 5. Spring API changes → Consult Spring Framework 7.0 docs
```

**Expected compilation errors:**

1. **Import errors** - Jackson package renames
2. **Type errors** - Jackson exception hierarchy changes
3. **Null safety errors** - JSpecify annotations may change nullable/non-nullable inference
4. **Deprecated API errors** - Spring Boot 4.0 removed 36 deprecated classes

### Step 7: Run Tests and Fix Failures

```bash
# Unit tests
./gradlew test

# Integration tests (CRITICAL - Axon Framework compatibility)
./gradlew integrationTest

# Watch for:
# - @Nested test failures → Add @SpringExtensionConfig
# - JSON serialization test failures → Jackson default config changes
# - Axon command/event/query failures → BLOCKER (no official support)
# - Multi-tenancy test failures → TenantContextFilter may need Servlet API updates
```

### Step 8: Axon Framework Compatibility Testing (CRITICAL)

**This is the highest risk area - Axon Framework 4.12.2 has NO official Spring Boot 4.0 support.**

**Test Priority Order:**

1. **Command Handling**
   ```bash
   ./gradlew :products:widget-demo:integrationTest --tests "*CommandHandlerTest*"
   ```

2. **Event Processing**
   ```bash
   ./gradlew :products:widget-demo:integrationTest --tests "*EventHandlerTest*"
   ```

3. **Projections**
   ```bash
   ./gradlew :products:widget-demo:integrationTest --tests "*ProjectionTest*"
   ```

4. **Event Store**
   ```bash
   ./gradlew :framework:persistence:integrationTest --tests "EventStoreIntegrationTest"
   ```

5. **Walking Skeleton** (End-to-End)
   ```bash
   ./gradlew :products:widget-demo:integrationTest --tests "WalkingSkeletonIntegrationTest"
   ```

**If Axon tests fail:**
- Document ALL error messages and stack traces
- Check Axon Framework GitHub for issues: https://github.com/AxonFramework/AxonFramework/issues
- Post on AxonIQ forum: https://discuss.axoniq.io/
- Consider ROLLBACK if failures are severe

### Step 9: Performance Baseline Comparison

```bash
# Run performance tests
./gradlew perfTest

# Compare with baseline (from pre-upgrade)
# - API p95 latency should be <200ms (within 10% of baseline)
# - Event processor lag should be <10s (within 10% of baseline)
# - Snapshot performance (within 10% of baseline)
```

**If regression >10%:** Investigate Spring Boot 4.0 / Spring Framework 7.0 performance characteristics.

---

## Rollback Procedure

**If migration fails or Axon compatibility issues are severe:**

```bash
# Revert all changes
git reset --hard b44af68  # Last commit before version changes

# Force push to branch (CAREFUL - this is destructive)
git push -f origin claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b

# Or create new branch from main
git checkout main
git checkout -b spring-boot-4-attempt-2
```

---

## Current Branch Status

**Branch:** `claude/spring-7-boot-4-upgrade-01NKnFygygnNLAwDMsVStz4b`

**Commits:**
1. `b44af68` - docs: Add comprehensive Spring Boot 4.0 & Spring Framework 7.0 upgrade plan
2. `82cc006` - chore: Upgrade to Spring Boot 4.0 & Spring Framework 7.0 (version catalog)
3. `32a8de7` - chore: Force JUnit Platform 6.0.1 for unified versioning
4. `99393b6` - chore: Setup OpenRewrite for Spring Boot 4.0 migration

**Next commit (pending):** Manual Jackson 3.0 migration (if done manually)

---

## Recommendations

### Option 1: Continue Manually (Tedious but Possible)

1. Follow manual migration steps above
2. Fix compilation errors iteratively
3. Test extensively (especially Axon Framework)
4. Document all issues encountered
5. Estimated effort: 10-15 days

### Option 2: Wait for Better Environment

1. Execute this upgrade in a development environment with network access
2. Use OpenRewrite for automated Jackson 3.0 migration
3. Faster feedback loop with `./gradlew build`
4. Estimated effort: 5-7 days

### Option 3: Defer (Recommended from Upgrade Plan)

1. Wait for Axon Framework official Spring Boot 4.0 support
2. Monitor: https://github.com/AxonFramework/AxonFramework/releases
3. Revisit in 3-6 months
4. Lower risk, official compatibility

---

## Files Requiring Manual Updates (Summary)

### Jackson 3.0 Package Renames (11 files)
- `framework/web/src/main/kotlin/com/axians/eaf/framework/web/config/RestConfiguration.kt`
- `framework/security/src/main/kotlin/com/axians/eaf/framework/security/user/KeycloakUserDirectory.kt`
- `framework/persistence/src/main/kotlin/com/axians/eaf/framework/persistence/eventstore/PostgresEventStoreConfiguration.kt`
- `framework/multi-tenancy/src/main/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilter.kt`
- `framework/web/src/test/kotlin/com/axians/eaf/framework/web/config/RestConfigurationTest.kt`
- `framework/multi-tenancy/src/test/kotlin/com/axians/eaf/framework/multitenancy/TenantContextFilterTest.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/test/config/RbacTestSecurityConfig.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/auth/AuthControllerIntegrationTest.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerRbacIntegrationTest.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/api/WidgetControllerIntegrationTest.kt`
- `products/widget-demo/src/integration-test/kotlin/com/axians/eaf/products/widget/WalkingSkeletonIntegrationTest.kt`

### @Nested Test Classes (14 files)
- All files listed in Step 5 above (only if tests fail)

### Configuration Files
- All `application*.yml` / `application*.properties` files (search for tracing/exceptiontranslation properties)

---

## Questions for Decision

1. **Do you want to continue with manual Jackson 3.0 migration now?**
   - I can guide you through each file
   - Tedious but possible

2. **Do you want to defer this upgrade?**
   - Wait for Axon Framework official support
   - Lower risk approach

3. **Do you want to proceed in a different environment?**
   - Execute upgrade in dev environment with network access
   - Use OpenRewrite for automation
   - Faster iteration

**My recommendation:** Given the network limitations and Axon Framework risk, I suggest **pushing the current commits** and **deferring execution** until:
- You have a development environment with network access
- Axon Framework announces Spring Boot 4.0 compatibility

---

**End of Status Report**
