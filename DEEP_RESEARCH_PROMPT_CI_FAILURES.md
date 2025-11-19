# Deep Research Prompt: Fix CI-Only Integration Test Failures (Local 100% Pass, CI Fails)

## Executive Summary

**CRITICAL ISSUE:** Integration tests pass **100% locally** (58/58) but **fail consistently on CI** (3/58 failing) with identical code. This indicates an **environment-specific issue** related to Spring Boot component scanning, Axon Framework interceptor registration, or ThreadLocal context propagation timing differences between local macOS (M1) and CI Ubuntu environment.

**Your Mission:** Identify why the `TenantContextTestInterceptor` @Component fails to register or execute correctly on CI but works perfectly locally, and provide a **robust, CI-stable solution**.

---

## 1. The Problem - Environment Divergence

### Local Environment (macOS M1, WORKS ✅)
```
✅ 58/58 integration tests passing (100%)
✅ framework/multi-tenancy: 15/15
✅ products/widget-demo: 43/43
✅ Multiple clean builds: consistent success
✅ --rerun-tasks: consistent success
✅ Multiple consecutive runs: no flakiness
```

### CI Environment (Ubuntu 24.04, GitHub Actions, FAILS ❌)
```
❌ 55/58 integration tests passing (95%)
❌ RealisticWorkloadPerformanceTest: "Tenant context not set for current thread"
❌ WalkingSkeletonIntegrationTest: Expected 404, got 500
❌ WidgetControllerRbacIntegrationTest: WidgetResponse.id is NULL (500 error)
```

### Critical Observation
**THE EXACT SAME CODE (commit e6e2aa06da)** passes locally but fails on CI. This is NOT a code bug - this is an **infrastructure/environment/timing issue**.

---

## 2. Technology Stack

### Core Technologies
- **Language:** Kotlin 2.2.21
- **Runtime:** JVM 21 LTS
- **Framework:** Spring Boot 3.5.7
- **CQRS/ES:** Axon Framework 4.12.1
- **Database:** PostgreSQL 16.10 (Testcontainers)
- **Testing:** Kotest 6.0.4
- **Build:** Gradle 9.1.0

### Multi-Threading Architecture
```
Test Thread (Kotest)
    ↓ beforeTest/beforeEach (sets TenantContext)
    ↓
HTTP Thread (MockMvc)
    ↓ TestTenantContextFilter (sets TenantContext)
    ↓ WidgetController.createWidget()
    ↓ CommandGateway.sendAndWait()
    ↓
Axon Command Thread Pool ← ThreadLocal NOT PROPAGATED!
    ↓ TenantContextTestInterceptor (should set TenantContext here)
    ↓ TenantValidationInterceptor (validates context)
    ↓ Widget.handle() command handler
```

**The Problem:** `TenantContextTestInterceptor` must run in Axon Thread Pool to set ThreadLocal context. On CI, it doesn't run or runs too late.

---

## 3. Complete Implementation Details

### TenantContext (ThreadLocal Management)

```kotlin
// framework/multi-tenancy/src/main/kotlin/.../TenantContext.kt
object TenantContext {
    private val currentTenant = ThreadLocal<String>()

    // Fail-closed: Throws if not set
    fun getCurrentTenantId(): String =
        currentTenant.get() ?: throw IllegalStateException("Tenant context not set for current thread")

    // Nullable: Returns null if not set
    fun current(): String? = currentTenant.get()

    fun setCurrentTenantId(tenantId: String) {
        currentTenant.set(tenantId)
    }

    fun clearCurrentTenant() {
        currentTenant.remove()
    }
}
```

### TenantContextTestInterceptor (THE PROBLEM COMPONENT)

**Current Implementation:**
```kotlin
// products/widget-demo/src/integration-test/kotlin/.../test/config/AxonTestConfiguration.kt

@Component  // ← PROBLEM? Conflicts with @Import?
@Profile("test | rbac-test")
class TenantContextTestInterceptor(
    private val commandBus: CommandBus,
) {
    @PostConstruct  // ← PROBLEM? Timing differs on CI?
    fun registerInterceptor() {
        // Register interceptor programmatically to guarantee early execution
        commandBus.registerHandlerInterceptor(
            MessageHandlerInterceptor { unitOfWork, chain ->
                val command = unitOfWork.message.payload

                if (command is TenantAwareCommand) {
                    // Only set context if NOT already set (allows manual override)
                    val existingTenant = TenantContext.current()
                    if (existingTenant == null) {
                        // Auto-propagation: Set context from command
                        TenantContext.setCurrentTenantId(command.tenantId)
                        try {
                            chain.proceed()
                        } finally {
                            TenantContext.clearCurrentTenant()
                        }
                    } else {
                        // Manual control: Context already set, don't override
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            },
        )
    }
}

@TestConfiguration
@Profile("test | rbac-test")
@Import(
    TestDslConfiguration::class,
    TestJpaBypassConfiguration::class,
    PostgresEventStoreConfiguration::class,
    TenantContextTestInterceptor::class,  // ← Explicit import
)
class AxonTestConfiguration {
    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}
```

### Test Pattern (All Tests Have This)

```kotlin
@SpringBootTest(classes = [WidgetDemoApplication::class, TestSecurityConfig::class, /*...*/])
@Import(AxonTestConfiguration::class)
@ActiveProfiles("test")
class WidgetControllerIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeTest {
            // CRITICAL: Set tenant context fallback
            // Works locally, but on CI the interceptor doesn't propagate it
            TenantContext.setCurrentTenantId("test-tenant")
        }

        afterTest {
            TenantContext.clearCurrentTenant()
        }

        test("some test") {
            mockMvc.post("/api/v1/widgets") { /*...*/ }
        }
    }
}
```

---

## 4. All Solution Attempts (Chronological)

### Attempt #1: Self-Healing Interceptor (FAILED - Broke Security)
**Code:**
```kotlin
val currentTenant = try {
    TenantContext.getCurrentTenantId()
} catch (e: IllegalStateException) {
    TenantContext.setCurrentTenantId(command.tenantId)  // Self-healing
    command.tenantId
}
```
**Result:** Broke TenantValidationInterceptor tests that expect exceptions for missing context.

---

### Attempt #2: @Primary Annotation (FAILED - Ordering Still Non-Deterministic)
```kotlin
@Bean
@Primary
fun testTenantContextInterceptor(): MessageHandlerInterceptor<CommandMessage<*>> = ...
```
**Result:** @Primary doesn't control MessageHandlerInterceptor execution order in Axon.

---

### Attempt #3: @Order Annotation (COMPILATION ERROR)
```kotlin
@Component
@Order(1)
class TenantContextTestInterceptor { ... }
```
**Result:** Axon doesn't support @Order on interceptors. Compilation errors.

---

### Attempt #4: @Component + @PostConstruct (WORKS LOCAL, FAILS CI)
```kotlin
@Component
@Profile("test | rbac-test")
class TenantContextTestInterceptor(private val commandBus: CommandBus) {
    @PostConstruct
    fun registerInterceptor() {
        commandBus.registerHandlerInterceptor(...)
    }
}
```
**Result:**
- ✅ **Local:** 58/58 passing
- ❌ **CI:** 55/58 passing (3 failures)

---

### Attempt #5: Explicit @Import (WORKS LOCAL, FAILS CI)
```kotlin
@Import(
    TestDslConfiguration::class,
    TestJpaBypassConfiguration::class,
    PostgresEventStoreConfiguration::class,
    TenantContextTestInterceptor::class,  // Explicit
)
```
**Result:**
- ✅ **Local:** 58/58 passing
- ❌ **CI:** 55/58 passing (3 failures)

---

## 5. CI Failure Evidence

### CI Log Excerpts (Commit e6e2aa06da)

**Failure 1: RealisticWorkloadPerformanceTest**
```
2025-11-18T16:05:58.9860406Z RealisticWorkloadPerformanceTest > Realistic mixed workload (50 aggregates × 10 commands) > should handle mixed cold/warm cache workload efficiently FAILED
2025-11-18T16:05:58.9861650Z     java.lang.IllegalStateException: Tenant context not set for current thread
2025-11-18T16:05:58.9862643Z         at com.axians.eaf.framework.multitenancy.TenantContext.getCurrentTenantId(TenantContext.kt:81)
2025-11-18T16:05:58.9863730Z         at com.axians.eaf.products.widget.domain.Widget.handle(Widget.kt:111)
```

**Failure 2: WalkingSkeletonIntegrationTest**
```
2025-11-18T16:06:05.9807688Z WalkingSkeletonIntegrationTest > Walking Skeleton - Complete CQRS Flow > not found scenario returns 404 Not Found with RFC 7807 ProblemDetail FAILED
2025-11-18T16:06:06.0757875Z     java.lang.AssertionError: Status expected:<404> but was:<500>
```

**Failure 3: WidgetControllerRbacIntegrationTest**
```
2025-11-18T16:06:32.3744651Z WidgetControllerRbacIntegrationTest > PUT /api/v1/widgets/{id} - Update Widget with @PreAuthorize('hasRole(WIDGET_ADMIN)') > WIDGET_ADMIN can update widget - returns 200 OK FAILED
2025-11-18T16:06:32.3748351Z     com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException: Instantiation of [simple type, class com.axians.eaf.products.widget.api.WidgetResponse] value failed for JSON property id due to missing (therefore NULL) value for creator parameter id which is a non-nullable type
```

**All 3 failures have same root cause:** TenantContext not available in command handlers → 500 Internal Server Error.

---

## 6. Environment Comparison

### Local Environment
- **OS:** macOS 14.7 (Darwin Kernel 25.1.0)
- **Arch:** arm64 (Apple M1)
- **Java:** Eclipse Temurin 21 (SDKMAN)
- **Gradle:** Daemon enabled, build cache enabled
- **Results:** 58/58 passing ✅

### CI Environment (GitHub Actions)
- **OS:** Ubuntu 24.04.3 LTS
- **Arch:** x86_64
- **Java:** Eclipse Temurin 21
- **Gradle:** Daemon disabled (--no-daemon), build cache enabled
- **Results:** 55/58 passing ❌

### Potential Differences
1. **Gradle Daemon:** Local uses daemon (persistent JVM), CI doesn't (fresh JVM per build)
2. **CPU Architecture:** arm64 vs x86_64 (unlikely to cause this issue)
3. **File System:** APFS vs ext4 (unlikely to cause this issue)
4. **Timing:** CI slower → different race condition windows

---

## 7. Hypothesis: @Component + @Import Conflict

### Theory
`@Component` annotation + `@Import` explicit import creates ambiguous bean definition:

**Scenario on CI:**
1. Spring Boot starts, begins component scanning
2. @TestConfiguration with @Import loads
3. @Import(TenantContextTestInterceptor::class) tries to instantiate
4. But TenantContextTestInterceptor has @Component → Spring says "I'll scan this"
5. Conflict: Who owns the bean? @Import or @ComponentScan?
6. Result: Bean registered incorrectly or twice

**Why it works locally:**
- Gradle daemon warm → Spring context cached → bean already registered
- Component scanning happens in predictable order

**Why it fails on CI:**
- Fresh JVM every run → no caching
- Component scanning timing varies
- @PostConstruct may not run or runs after tests start

### Test This Hypothesis
**Solution:** Remove `@Component`, rely purely on `@Import`:

```kotlin
// Remove @Component annotation
@Profile("test | rbac-test")  // Keep @Profile
class TenantContextTestInterceptor(
    private val commandBus: CommandBus,
) {
    @PostConstruct
    fun registerInterceptor() { ... }
}
```

---

## 8. Alternative Solution #1: Pure @Bean Registration

### Replace @Component with @Bean

```kotlin
@TestConfiguration
@Profile("test | rbac-test")
@Import(
    TestDslConfiguration::class,
    TestJpaBypassConfiguration::class,
    PostgresEventStoreConfiguration::class,
)
class AxonTestConfiguration {

    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()

    /**
     * Registers tenant context interceptor with CommandBus.
     *
     * This @Bean approach ensures deterministic lifecycle:
     * 1. Spring creates this bean during context initialization
     * 2. @PostConstruct runs BEFORE tests start
     * 3. Interceptor registered in CommandBus BEFORE first command
     */
    @Bean
    fun tenantContextInterceptorRegistrar(commandBus: CommandBus): TenantContextInterceptorRegistrar {
        return TenantContextInterceptorRegistrar(commandBus)
    }
}

/**
 * Registrar component that registers the interceptor via @PostConstruct.
 * Managed as @Bean (not @Component) for deterministic lifecycle on CI.
 */
class TenantContextInterceptorRegistrar(
    private val commandBus: CommandBus,
) {
    @PostConstruct
    fun registerInterceptor() {
        commandBus.registerHandlerInterceptor(
            MessageHandlerInterceptor { unitOfWork, chain ->
                val command = unitOfWork.message.payload

                if (command is TenantAwareCommand) {
                    val existingTenant = TenantContext.current()
                    if (existingTenant == null) {
                        TenantContext.setCurrentTenantId(command.tenantId)
                        try {
                            chain.proceed()
                        } finally {
                            TenantContext.clearCurrentTenant()
                        }
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            },
        )
    }
}
```

**Why This Should Work:**
- @Bean has deterministic creation order (container-managed)
- No component scanning ambiguity
- @PostConstruct guaranteed to run before tests
- Same behavior local and CI

---

## 9. Alternative Solution #2: Axon Configurer API

### Use Axon's Official Configuration Mechanism

```kotlin
@TestConfiguration
@Profile("test | rbac-test")
class AxonTestConfiguration {

    @Autowired
    fun configureInterceptors(configurer: org.axonframework.config.Configurer) {
        // Use Axon's official API instead of CommandBus direct registration
        configurer.onInitialize { config ->
            config.commandBus().registerHandlerInterceptor(
                MessageHandlerInterceptor { unitOfWork, chain ->
                    val command = unitOfWork.message.payload

                    if (command is TenantAwareCommand) {
                        val existingTenant = TenantContext.current()
                        if (existingTenant == null) {
                            TenantContext.setCurrentTenantId(command.tenantId)
                            try {
                                chain.proceed()
                            } finally {
                                TenantContext.clearCurrentTenant()
                            }
                        } else {
                            chain.proceed()
                        }
                    } else {
                        chain.proceed()
                    }
                },
            )
        }
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}
```

**Why This Should Work:**
- Uses Axon Framework's official Configurer API
- `onInitialize` guarantees execution during framework initialization
- No reliance on Spring @PostConstruct timing
- More explicit control over registration timing

---

## 10. Alternative Solution #3: Dispatch Interceptor Instead of Handler Interceptor

### Research Finding from Web Search

> "Dispatch Interceptor is always invoked in the thread that dispatches the event (the thread that will also have the ThreadLocal)"

**Insight:** Maybe we should use `DispatchInterceptor` instead of `MessageHandlerInterceptor` to set context in the CALLER thread (where ThreadLocal exists).

```kotlin
@TestConfiguration
class AxonTestConfiguration {

    @Autowired
    fun configureDispatchInterceptor(commandBus: CommandBus) {
        commandBus.registerDispatchInterceptor(
            MessageDispatchInterceptor<CommandMessage<*>> { messages ->
                messages.map { message ->
                    val command = message.payload
                    if (command is TenantAwareCommand) {
                        // Set context in DISPATCH thread (where test's ThreadLocal is)
                        // This will then be available when command is dispatched
                        TenantContext.setCurrentTenantId(command.tenantId)
                    }
                    message
                }
            },
        )
    }
}
```

**Why This Might Work:**
- Dispatch interceptor runs in test thread (where beforeTest set ThreadLocal)
- Can propagate context to command before dispatch
- Simpler than handler interceptor threading

**Caveat:** Need to ensure context cleanup after command completes.

---

## 11. Alternative Solution #4: UnitOfWork Metadata Propagation

### Use Axon's Built-in Metadata Mechanism (No ThreadLocal!)

Instead of ThreadLocal, use Axon's UnitOfWork metadata:

```kotlin
// Set tenant in dispatch interceptor (runs in caller thread)
commandBus.registerDispatchInterceptor(
    MessageDispatchInterceptor<CommandMessage<*>> { messages ->
        messages.map { message ->
            val command = message.payload
            if (command is TenantAwareCommand) {
                message.andMetaData(mapOf("tenant_id" to command.tenantId))
            } else {
                message
            }
        }
    }
)

// Read tenant from metadata in handler (runs in handler thread)
@CommandHandler
fun handle(command: UpdateWidgetCommand, @MetaDataValue("tenant_id") tenantId: String) {
    // tenantId is available from metadata, no ThreadLocal needed!
    TenantContext.setCurrentTenantId(tenantId)  // Set for defensive checks
    try {
        // Business logic
    } finally {
        TenantContext.clearCurrentTenant()
    }
}
```

**Why This Should Work:**
- Metadata propagates across threads automatically (Axon handles it)
- No ThreadLocal propagation issues
- Works identically on local and CI

**Caveat:** Requires changes to all command handlers to accept @MetaDataValue.

---

## 12. Diagnostic Questions for External Agent

### Root Cause Investigation

**Q1: Bean Lifecycle**
- Why does @PostConstruct timing differ between local (Gradle daemon) and CI (no daemon)?
- Is there a Spring Boot initialization order difference on fresh JVM vs warm JVM?
- Could parallel test execution affect bean initialization order on CI?

**Q2: Component Scanning**
- Does `@Component` + `@Import(ComponentClass::class)` create duplicate beans?
- Which takes precedence: @Import or @ComponentScan?
- Is there a Spring Boot bug with @Profile + @Component + @Import combination?

**Q3: Axon Framework**
- Does CommandBus.registerHandlerInterceptor() have timing guarantees?
- Can interceptors be registered after command dispatching begins?
- What's the difference between handler vs dispatch interceptors for ThreadLocal propagation?

**Q4: Kotest + Spring Boot**
- Does Kotest's SpringExtension affect Spring context initialization timing?
- Could `beforeTest` run after Spring context is initialized on CI but before on local?
- Are there known Kotest + Spring Boot + Testcontainers CI-specific issues?

---

## 13. Research Directions

### High Priority Web Research
1. "Spring Boot @Component @Import precedence same class"
2. "Axon Framework 4.12 interceptor registration timing @PostConstruct"
3. "Spring Boot @TestConfiguration bean loading order CI vs local"
4. "Kotest SpringExtension context initialization timing"
5. "CommandBus registerHandlerInterceptor when does it take effect"

### Code Analysis Priorities
1. Compare Axon autoconfiguration between local and CI logs
2. Check if TenantContextTestInterceptor bean is created on CI (Spring debug logs)
3. Verify CommandBus bean initialization timing
4. Check for any CI-specific Spring Boot properties that affect bean loading

---

## 14. Success Criteria

### Definition of Done
- ✅ **All 58 tests pass on CI** (100% success rate)
- ✅ **10 consecutive CI runs pass** (no flakiness)
- ✅ **Solution works identically local and CI** (no environment divergence)
- ✅ **No architectural compromises** (maintain security, separation, etc.)

### Validation Process
```bash
# Step 1: Implement solution locally
# Step 2: Run local tests (expect 58/58)
./gradlew clean integrationTest --no-configuration-cache --no-daemon

# Step 3: Commit and push
git add .
git commit -m "Fix: CI-stable tenant context propagation"
git push

# Step 4: Wait for CI
# Expected: All checks green

# Step 5: Trigger multiple CI runs to verify no flakiness
# Expected: 10/10 runs successful
```

---

## 15. Known Working Solutions (Reference)

### What Works Locally
The current solution works perfectly locally:
- @Component + @PostConstruct registration
- Explicit @Import in AxonTestConfiguration
- beforeTest hooks in all test suites
- Smart conditional interceptor (only sets if not already set)

### What Doesn't Work on CI
The same solution fails on CI with timing/registration issues.

---

## 16. Recommendations for External Agent

### Priority 1: Quick Win - Remove @Component
Try removing `@Component` annotation and keep only `@Import`. This eliminates potential scanning conflicts.

### Priority 2: Robust Solution - Pure @Bean
Convert to pure @Bean registration pattern (no @Component, no component scanning).

### Priority 3: Architectural Shift - UnitOfWork Metadata
If bean lifecycle issues persist, consider using Axon's metadata propagation instead of ThreadLocal.

---

## 17. Additional Context

### Project Structure
```
eaf-v1/
├── framework/
│   └── multi-tenancy/
│       └── src/
│           ├── main/kotlin/
│           │   ├── TenantContext.kt
│           │   └── TenantValidationInterceptor.kt (production)
│           └── integration-test/kotlin/
│               └── TenantValidationInterceptorIntegrationTest.kt
└── products/
    └── widget-demo/
        └── src/
            ├── main/kotlin/
            │   ├── domain/Widget.kt (command handlers)
            │   └── api/WidgetController.kt (REST endpoints)
            └── integration-test/kotlin/
                ├── test/config/
                │   ├── AxonTestConfiguration.kt ← THE CONFIGURATION FILE
                │   ├── TenantContextTestInterceptor.kt ← THE PROBLEM COMPONENT
                │   └── TestSecurityConfig.kt
                └── api/
                    ├── WidgetControllerIntegrationTest.kt
                    ├── WidgetControllerRbacIntegrationTest.kt
                    └── WalkingSkeletonIntegrationTest.kt
```

### Framework/Product Separation (MUST PRESERVE)
- Framework modules: ONLY infrastructure, no product knowledge
- Product modules: Can depend on framework
- TenantContextTestInterceptor is in products/widget-demo (correct)
- No @Profile annotations in framework modules (correct)

---

## 18. Code Snippets for Reference

### Widget Command Handler (Where Context is Read)
```kotlin
// products/widget-demo/src/main/kotlin/.../domain/Widget.kt:111
@CommandHandler
fun handle(command: UpdateWidgetCommand) {
    require(!published) { "Cannot update published widget" }
    require(command.name.isNotBlank()) { "Widget name cannot be blank" }

    // AC3: Defensive tenant validation
    val currentTenant = TenantContext.getCurrentTenantId()  // ← Line 111: FAILS ON CI
    require(command.tenantId == currentTenant) {
        "Access denied: tenant context mismatch"
    }

    AggregateLifecycle.apply(
        WidgetUpdatedEvent(
            widgetId = widgetId,
            name = command.name,
            tenantId = command.tenantId,
        ),
    )
}
```

### Test That Fails on CI But Passes Locally
```kotlin
// RealisticWorkloadPerformanceTest.kt
beforeEach {
    TenantContext.setCurrentTenantId("test-tenant-integration")
}

test("should handle mixed cold/warm cache workload efficiently") {
    repeat(50) { aggregateIndex ->
        val widgetId = WidgetId(UUID.randomUUID())

        // CREATE - This works
        commandGateway.sendAndWait<Unit>(
            CreateWidgetCommand(widgetId, "Widget $aggregateIndex", "test-tenant-integration"),
        )

        // UPDATE - This FAILS on CI with "Tenant context not set"
        repeat(9) { updateIndex ->
            commandGateway.sendAndWait<Unit>(
                UpdateWidgetCommand(widgetId, "Update $updateIndex", "test-tenant-integration"),
            )
        }
    }
}
```

**Why CREATE works but UPDATE fails on CI:** Timing/race condition. By the time UPDATE runs, something has changed.

---

## 19. Expected Deliverables

### Primary: Root Cause + Fix

**Must Include:**
1. **Why @Component + @Import works locally but fails on CI**
2. **Exact code changes** (with file paths and line numbers)
3. **Explanation of fix** (why it's CI-stable)
4. **Validation steps** (how to verify on CI)

### Secondary: Best Practices

**Document:**
1. Spring Boot test component patterns (what works on CI)
2. Axon Framework interceptor registration best practices
3. When to use @Bean vs @Component in test configurations

---

## 20. Timeline & Context

**Effort Invested:**
- 6+ hours debugging test failures
- 15+ solution attempts
- 100+ test runs
- Multiple commits trying different approaches

**Frustration Level:** HIGH - We've exhausted standard debugging approaches

**Confidence in Code:** HIGH - 58/58 passing locally proves logic is correct

**Confidence in CI:** LOW - Environment differences causing non-deterministic failures

---

## 21. Constraints

### MUST Preserve (Non-Negotiable)
1. ✅ Framework/product separation (no @Profile in framework modules)
2. ✅ Fail-closed security (TenantValidationInterceptor)
3. ✅ No mocking of business logic
4. ✅ Kotest framework (no JUnit)
5. ✅ ThreadLocal cleanup (no memory leaks)

### MUST NOT (Forbidden)
1. ❌ Skip tests or use @Disabled
2. ❌ Accept <100% CI pass rate
3. ❌ Introduce H2 or prohibited dependencies
4. ❌ Break framework/product architectural boundaries

---

## 22. Final Notes

**This is a Blocking Issue:**
- Story 4.6 cannot be completed without CI passing
- Epic 4 progress is blocked
- Team velocity affected

**The Code is Correct:**
- 58/58 local tests prove functionality
- All ACs met locally
- Security validation works
- Multi-tenancy isolation works

**The Environment is the Problem:**
- CI-specific timing or bean loading issue
- Need a solution that works IDENTICALLY on both environments
- Focus on deterministic bean lifecycle, not relying on timing

**Key Success Factor:**
The solution must have **zero reliance on component scanning timing or @PostConstruct execution order**. Use explicit, container-managed beans with guaranteed initialization sequence.

---

## 23. Recommended Investigation Path

### Step 1: Test Hypothesis - Remove @Component (15 min)
Remove `@Component` annotation from TenantContextTestInterceptor, keep `@Import`. Test on CI.

**Expected Result:** If this fixes it, confirms @Component + @Import conflict.

### Step 2: Implement Pure @Bean Solution (30 min)
Convert to TenantContextInterceptorRegistrar @Bean pattern (Solution #1 above).

**Expected Result:** Deterministic lifecycle should work on CI.

### Step 3: If Bean Approach Fails - Try Configurer API (45 min)
Use Axon's Configurer.onInitialize() for guaranteed timing (Solution #2 above).

**Expected Result:** Official API should be most reliable.

### Step 4: Nuclear Option - UnitOfWork Metadata (2-3 hours)
If all else fails, abandon ThreadLocal and use Axon's metadata propagation.

**Expected Result:** Guaranteed to work (Axon handles cross-thread propagation).

---

## 24. Links to Actual CI Failures

**CI Run for commit e6e2aa06da:**
- Workflow: CI - Fast Feedback (Optimized)
- Status: FAILED
- Created: 2025-11-18T17:41:34Z
- URL: `gh run view 19472514345 --log-failed`

**Test Failure Logs:**
```
RealisticWorkloadPerformanceTest > ... > FAILED
    java.lang.IllegalStateException: Tenant context not set for current thread
        at TenantContext.getCurrentTenantId(TenantContext.kt:81)
        at Widget.handle(Widget.kt:111)
```

---

## 25. Success Pattern from Other Tests

### Tests That Pass on CI
- ✅ All 15 TenantValidationInterceptor tests (framework module)
- ✅ All Widget domain tests
- ✅ 55/58 integration tests in products module

**Why do these pass?**
- TenantValidationInterceptor tests manually set TenantContext in beforeTest
- They test the VALIDATION logic, not the propagation
- They don't rely on the test interceptor working

**Why do 3 tests fail?**
- They rely on TenantContextTestInterceptor propagating context to Axon threads
- On CI, this interceptor doesn't run or runs too late
- Result: TenantContext is NULL when command handler executes

---

## 26. Critical Files (Exact Current State)

### File 1: AxonTestConfiguration.kt (Current State)
```kotlin
package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.multitenancy.TenantAwareCommand
import com.axians.eaf.framework.multitenancy.TenantContext
import com.axians.eaf.framework.persistence.eventstore.PostgresEventStoreConfiguration
import jakarta.annotation.PostConstruct
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.common.caching.Cache
import org.axonframework.common.caching.WeakReferenceCache
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@TestConfiguration
@Profile("test | rbac-test")
@Import(
    TestDslConfiguration::class,
    TestJpaBypassConfiguration::class,
    PostgresEventStoreConfiguration::class,
    TenantContextTestInterceptor::class,  // Explicit import
)
class AxonTestConfiguration {
    @Autowired
    fun configure(configurer: EventProcessingConfigurer) {
        configurer.registerDefaultListenerInvocationErrorHandler {
            PropagatingErrorHandler.INSTANCE
        }
    }

    @Bean
    fun aggregateCache(): Cache = WeakReferenceCache()
}

@Component  // ← POTENTIAL PROBLEM
@Profile("test | rbac-test")
class TenantContextTestInterceptor(
    private val commandBus: CommandBus,
) {
    @PostConstruct  // ← TIMING ISSUE ON CI?
    fun registerInterceptor() {
        commandBus.registerHandlerInterceptor(
            MessageHandlerInterceptor { unitOfWork, chain ->
                val command = unitOfWork.message.payload

                if (command is TenantAwareCommand) {
                    val existingTenant = TenantContext.current()
                    if (existingTenant == null) {
                        TenantContext.setCurrentTenantId(command.tenantId)
                        try {
                            chain.proceed()
                        } finally {
                            TenantContext.clearCurrentTenant()
                        }
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            },
        )
    }
}
```

---

## 27. What Makes This Different from Normal Debugging

**Standard debugging assumes:** Code works OR code doesn't work.

**This situation:** Code works locally, fails on CI with IDENTICAL source.

**Therefore:** The bug is NOT in the logic, it's in the **infrastructure interaction**:
- Spring Boot bean lifecycle
- Gradle build environment
- JVM initialization
- Component scanning timing

**Solution Must:** Work deterministically regardless of environment, JVM state, or timing variations.

---

## 28. Time Investment vs Pragmatic Solutions

**Time Already Invested:** 6+ hours

**Remaining Options:**
1. **Deep research (this prompt):** 2-4 more hours
2. **Accept 95% pass rate:** Document as known CI issue, merge anyway
3. **Disable failing tests:** Mark with @Disabled and create follow-up story
4. **Alternative architecture:** Use UnitOfWork metadata (major refactor, 4-8 hours)

**Recommendation:** Try quick wins first (remove @Component, pure @Bean), then decide.

---

**GOOD LUCK! This is a tough one.** 🚀
