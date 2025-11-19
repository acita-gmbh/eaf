# Deep Research Prompt: Fix 2 Failing Integration Tests in Multi-Tenant CQRS/Event Sourcing Framework

## Executive Summary

You are tasked with fixing **2 failing integration tests** in a Kotlin-based enterprise application framework that implements **CQRS/Event Sourcing with Axon Framework 4.12.1** and **3-layer multi-tenancy defense-in-depth**. The failures occurred after implementing tenant isolation features (Epic 4, Story 4.6).

**Your mission:** Analyze the test failures and provide **precise, production-ready fixes** that maintain the architectural integrity of the multi-tenancy system.

---

## 1. Technology Stack & Architecture

### Core Technologies
- **Language:** Kotlin 2.2.21
- **Runtime:** JVM 21 LTS
- **Framework:** Spring Boot 3.5.7
- **CQRS/ES:** Axon Framework 4.12.1
- **Database:** PostgreSQL 16.10 (event store + projections)
- **Testing:** Kotest 6.0.4, Testcontainers
- **Build:** Gradle 9.1.0 monorepo

### Architectural Pattern: CQRS + Event Sourcing + Hexagonal

```
┌─────────────────────────────────────────────────────────────┐
│                    REST API Layer (HTTP)                     │
│  WidgetController → CommandGateway/QueryGateway              │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              CQRS Write Side (Command Model)                 │
│  Commands → Axon MessageHandlerInterceptors →                │
│  Aggregate (Widget) → Events → Event Store                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              CQRS Read Side (Query Model)                    │
│  Events → Event Handlers → jOOQ Projections →                │
│  Query Handlers → Read Models                                │
└─────────────────────────────────────────────────────────────┘
```

### 3-Layer Multi-Tenancy Defense-in-Depth

**Layer 1: TenantContextFilter (HTTP)**
- Extracts `tenant_id` from JWT claims
- Sets `TenantContext.setCurrentTenantId(tenantId)` in ThreadLocal
- Runs for every HTTP request

**Layer 2: TenantValidationInterceptor (Axon Command)**
- Axon `MessageHandlerInterceptor<CommandMessage<*>>`
- Validates `TenantContext.getCurrentTenantId() == command.tenantId`
- **FAIL-CLOSED:** Missing or mismatched tenant → `TenantIsolationException`
- Only validates commands implementing `TenantAwareCommand` interface

**Layer 3: PostgreSQL RLS (Database)**
- Row-Level Security policies on all tables
- Database-level tenant isolation

---

## 2. Multi-Tenancy Implementation Details

### TenantContext (ThreadLocal Management)

```kotlin
// framework/multi-tenancy/src/main/kotlin/.../TenantContext.kt
object TenantContext {
    private val currentTenant = ThreadLocal<String>()

    // Fail-closed: Throws if not set
    fun getCurrentTenantId(): String =
        currentTenant.get() ?: throw IllegalStateException("Tenant context not set")

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

### TenantAwareCommand Interface

```kotlin
// framework/multi-tenancy/src/main/kotlin/.../TenantAwareCommand.kt
interface TenantAwareCommand {
    val tenantId: String
}
```

### TenantValidationInterceptor (Layer 2 - Production)

```kotlin
// framework/multi-tenancy/src/main/kotlin/.../TenantValidationInterceptor.kt
@Component
class TenantValidationInterceptor(
    private val meterRegistry: MeterRegistry,
) : MessageHandlerInterceptor<CommandMessage<*>> {

    override fun handle(
        unitOfWork: UnitOfWork<out CommandMessage<*>>,
        chain: InterceptorChain,
    ): Any? {
        val command = unitOfWork.message.payload

        if (command is TenantAwareCommand) {
            validateTenantContext(command)
        }

        return chain.proceed()
    }

    private fun validateTenantContext(command: TenantAwareCommand) {
        // AC5: Fail-closed - getCurrentTenantId() throws if context not set
        val currentTenant = try {
            TenantContext.getCurrentTenantId()
        } catch (e: IllegalStateException) {
            // AC5: Missing TenantContext → reject with generic error
            meterRegistry.counter("tenant.validation.failures").increment()
            throw TenantIsolationException("Tenant context not set")
        }

        // AC2: Validate command.tenantId matches current tenant
        if (command.tenantId != currentTenant) {
            // AC7: Increment validation failure metrics
            meterRegistry.counter("tenant.validation.failures").increment()
            meterRegistry.counter("tenant.mismatch.attempts").increment()

            // AC4: Generic error message (CWE-209 protection)
            throw TenantIsolationException("Access denied: tenant context mismatch")
        }
    }
}
```

### Widget Commands (All TenantAware)

```kotlin
// products/widget-demo/src/main/kotlin/.../domain/WidgetCommands.kt
data class CreateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    override val tenantId: String,  // ADDED in Story 4.6
) : TenantAwareCommand

data class UpdateWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    val name: String,
    override val tenantId: String,  // ADDED in Story 4.6
) : TenantAwareCommand

data class PublishWidgetCommand(
    @TargetAggregateIdentifier
    val widgetId: WidgetId,
    override val tenantId: String,  // ADDED in Story 4.6
) : TenantAwareCommand
```

### Widget Aggregate (Defensive Validation)

```kotlin
// products/widget-demo/src/main/kotlin/.../domain/Widget.kt
@Aggregate(cache = "aggregateCache")
class Widget : Serializable {
    @AggregateIdentifier
    private lateinit var widgetId: WidgetId
    private lateinit var name: String
    private lateinit var tenantId: String
    private var published: Boolean = false

    constructor()

    @CommandHandler
    constructor(command: CreateWidgetCommand) {
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC3: Defensive tenant validation (Layer 2 already checked)
        val currentTenant = TenantContext.getCurrentTenantId()
        require(command.tenantId == currentTenant) {
            "Access denied: tenant context mismatch"
        }

        AggregateLifecycle.apply(
            WidgetCreatedEvent(
                widgetId = command.widgetId,
                name = command.name,
                tenantId = command.tenantId,
            ),
        )
    }

    @CommandHandler
    fun handle(command: UpdateWidgetCommand) {
        require(!published) { "Cannot update published widget" }
        require(command.name.isNotBlank()) { "Widget name cannot be blank" }

        // AC3: Defensive tenant validation
        val currentTenant = TenantContext.getCurrentTenantId()
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

    @EventSourcingHandler
    fun on(event: WidgetCreatedEvent) {
        this.widgetId = event.widgetId
        this.name = event.name
        this.tenantId = event.tenantId
        this.published = false
    }

    @EventSourcingHandler
    fun on(event: WidgetUpdatedEvent) {
        this.name = event.name
    }
}
```

### Widget REST Controller

```kotlin
// products/widget-demo/src/main/kotlin/.../api/WidgetController.kt
@RestController
@RequestMapping("/api/v1/widgets")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
) {
    companion object {
        private val COMMAND_TIMEOUT_SECONDS = Duration.ofSeconds(10)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createWidget(@RequestBody @Valid request: CreateWidgetRequest): WidgetResponse {
        // Extract tenant from TenantContext (set by Layer 1 HTTP filter)
        val tenantId = TenantContext.getCurrentTenantId()

        val widgetId = WidgetId(UUID.randomUUID().toString())

        commandGateway.sendAndWait<Any>(
            CreateWidgetCommand(
                widgetId = widgetId,
                name = request.name,
                tenantId = tenantId,
            ),
            COMMAND_TIMEOUT_SECONDS,
        )

        // Query for consistent projection (with retry)
        return queryForWidgetWithRetry(widgetId)
    }

    @PutMapping("/{id}")
    fun updateWidget(
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateWidgetRequest,
    ): WidgetResponse {
        val tenantId = TenantContext.getCurrentTenantId()

        commandGateway.sendAndWait<Any>(
            UpdateWidgetCommand(
                widgetId = WidgetId(id),
                name = request.name,
                tenantId = tenantId,
            ),
            COMMAND_TIMEOUT_SECONDS,
        )

        // Query for updated projection (with retry)
        return queryForWidgetWithRetry(WidgetId(id))
    }

    private fun queryForWidgetWithRetry(widgetId: WidgetId): WidgetResponse {
        // Eventual consistency retry logic
        var attempts = 0
        val maxAttempts = 10
        val retryDelay = Duration.ofMillis(50)

        while (attempts < maxAttempts) {
            val projection = queryGateway.query(
                FindWidgetQuery(widgetId),
                WidgetProjection::class.java,
            ).get()

            if (projection != null) {
                return WidgetResponse(/* map from projection */)
            }

            attempts++
            Thread.sleep(retryDelay.toMillis())
        }

        throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Widget not found: ${widgetId.value}",
        )
    }
}
```

---

## 3. Test Infrastructure

### Test Configuration (Smart Conditional Interceptor)

```kotlin
// products/widget-demo/src/integration-test/kotlin/.../test/config/AxonTestConfiguration.kt
@TestConfiguration
@Profile("test | rbac-test")
@Import(TestDslConfiguration::class, TestJpaBypassConfiguration::class, PostgresEventStoreConfiguration::class)
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
     * Test interceptor that propagates tenant context from commands to command handlers.
     *
     * **Smart Behavior:**
     * - If TenantContext already set (manual test setup) → Don't override
     * - If TenantContext NOT set → Extract from command.tenantId
     *
     * **This allows two test patterns:**
     * 1. Auto-propagation: Widget REST tests (no manual setup)
     * 2. Manual control: TenantValidationInterceptor tests (explicit beforeTest)
     */
    @Bean
    fun testTenantContextInterceptor(): MessageHandlerInterceptor<CommandMessage<*>> =
        MessageHandlerInterceptor { unitOfWork, chain ->
            val command = unitOfWork.message.payload

            if (command is TenantAwareCommand) {
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
        }
}
```

### Widget Controller Integration Test

```kotlin
// products/widget-demo/src/integration-test/kotlin/.../api/WidgetControllerIntegrationTest.kt
@Testcontainers
@SpringBootTest(
    classes = [WidgetDemoApplication::class, TestSecurityConfig::class, ProblemDetailExceptionHandler::class],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        "spring.mvc.problemdetails.enabled=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WidgetControllerIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        beforeTest {
            // CRITICAL: Set tenant context for HTTP request tests
            // Widget REST Controller extracts tenant from TenantContext
            TenantContext.setCurrentTenantId("test-tenant")
        }

        afterTest {
            TenantContext.clearCurrentTenant()
        }

        context("PUT /api/v1/widgets/{id} - Update Widget") {

            test("should update widget and return 200 OK") {
                // Given - Create widget first
                val createRequest = CreateWidgetRequest(name = "Original Name")
                val createBody = objectMapper.writeValueAsString(createRequest)

                val createResult = mockMvc
                    .post("/api/v1/widgets") {
                        contentType = MediaType.APPLICATION_JSON
                        content = createBody
                    }
                    .andReturn()

                val createdWidget = objectMapper.readValue(
                    createResult.response.contentAsString,
                    WidgetResponse::class.java,
                )

                // When - PUT update widget
                val updateRequest = UpdateWidgetRequest(name = "Updated Name")
                val updateBody = objectMapper.writeValueAsString(updateRequest)

                val updateResult = mockMvc
                    .put("/api/v1/widgets/${createdWidget.id}") {
                        contentType = MediaType.APPLICATION_JSON
                        content = updateBody
                    }
                    .andExpect {
                        status { isOk() }  // FAILS HERE: Expected 200, got 500
                        content { contentType(MediaType.APPLICATION_JSON) }
                    }
                    .andReturn()

                // Then - Response contains updated widget
                val response = objectMapper.readValue(
                    updateResult.response.contentAsString,
                    WidgetResponse::class.java,
                )
                response.id shouldBe createdWidget.id
                response.name shouldBe "Updated Name"
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .apply {
                    withDatabaseName("testdb")
                    withUsername("test")
                    withPassword("test")
                }
    }
}
```

---

## 4. The 2 Failing Tests

### Test Failure #1: WidgetControllerIntegrationTest

**Test Name:** `PUT /api/v1/widgets/{id} - Update Widget > should update widget and return 200 OK`

**Expected:** HTTP 200 OK
**Actual:** HTTP 500 Internal Server Error

**Error Location:** Line 316 in WidgetControllerIntegrationTest.kt

**Failure Message:**
```
java.lang.AssertionError: Status expected:<200> but was:<500>
    at org.springframework.test.web.servlet.result.StatusResultMatchers.lambda$matcher$9(StatusResultMatchers.java:640)
    at com.axians.eaf.products.widget.api.WidgetControllerIntegrationTest$4$1.invokeSuspend$lambda$2$0(WidgetControllerIntegrationTest.kt:316)
```

**Context:**
- The test creates a widget successfully (POST returns 201)
- The test then attempts to update the widget (PUT)
- The PUT request fails with 500 instead of 200
- **Other update tests in the same suite PASS:**
  - "should return 404 Not Found for non-existent widget" ✅
  - "should return 400 Bad Request for invalid update" ✅
  - "should complete full lifecycle: POST → GET → PUT → GET" ✅

**Hypothesis:**
The specific test "should update widget and return 200 OK" might have a race condition or TenantContext timing issue that the other tests don't encounter.

---

### Test Failure #2: RealisticWorkloadPerformanceTest

**Test Name:** `Realistic mixed workload (50 aggregates × 10 commands) > should handle mixed cold/warm cache workload efficiently`

**Context:**
- This is a **performance test** that creates 50 aggregates and executes 10 commands each
- The test measures aggregate cache performance (cold vs warm cache)
- **This test was passing BEFORE multi-tenancy changes**
- Now it fails after adding `tenantId` to commands

**Hypothesis:**
- The test might not be setting TenantContext correctly for bulk operations
- The test might have performance degradation due to multi-tenancy overhead
- The test might encounter tenant validation failures during bulk command execution

**Test Code (Approximate):**
```kotlin
test("should handle mixed cold/warm cache workload efficiently") {
    val aggregateCount = 50
    val commandsPerAggregate = 10

    // Create 50 aggregates
    val aggregateIds = (1..aggregateCount).map { WidgetId(UUID.randomUUID().toString()) }

    // Execute commands in mixed pattern
    aggregateIds.forEach { widgetId ->
        repeat(commandsPerAggregate) { iteration ->
            commandGateway.sendAndWait<Any>(
                UpdateWidgetCommand(
                    widgetId = widgetId,
                    name = "Update $iteration",
                    tenantId = ??? // PROBLEM: tenantId might not be set correctly
                ),
            )
        }
    }

    // Assert performance metrics
}
```

---

## 5. Previous Solution Attempts (All Failed)

### Attempt #1: Self-Healing Interceptor (FAILED)
**Approach:** Modified `TenantValidationInterceptor` to defensively set `TenantContext` from command if not already set.

```kotlin
private fun validateTenantContext(command: TenantAwareCommand) {
    val currentTenant = try {
        TenantContext.getCurrentTenantId()
    } catch (e: IllegalStateException) {
        // SELF-HEALING: Set from command if missing
        TenantContext.setCurrentTenantId(command.tenantId)
        command.tenantId
    }

    if (command.tenantId != currentTenant) {
        throw TenantIsolationException("...")
    }
}
```

**Result:** ❌ **FAILED**
**Reason:** Broke `TenantValidationInterceptorIntegrationTest` - tests expect exceptions when context is missing/mismatched, but self-healing prevented exceptions from being thrown.

---

### Attempt #2: @Primary Test Interceptor (FAILED)
**Approach:** Created separate `testTenantContextInterceptor` with `@Primary` annotation to override production interceptor.

```kotlin
@Bean
@Primary
fun testTenantContextInterceptor(): MessageHandlerInterceptor<CommandMessage<*>> =
    MessageHandlerInterceptor { unitOfWork, chain ->
        val command = unitOfWork.message.payload
        if (command is TenantAwareCommand) {
            TenantContext.setCurrentTenantId(command.tenantId)
            try {
                chain.proceed()
            } finally {
                TenantContext.clearCurrentTenant()
            }
        } else {
            chain.proceed()
        }
    }
```

**Result:** ❌ **FAILED**
**Reason:** The `@Primary` interceptor **ALWAYS** set context from command, preventing `TenantValidationInterceptor` tests from testing mismatch scenarios. Tests that manually set context to "tenant-a" but sent commands with "tenant-b" would have context overridden to "tenant-b" before validation.

---

### Attempt #3: @Order Annotation (FAILED)
**Approach:** Attempted to use `@Order` annotation to control interceptor execution order.

```kotlin
@Component
@Order(1)
class TenantContextTestInterceptor { ... }

@Component
@Order(2)
class TenantValidationInterceptor { ... }
```

**Result:** ❌ **COMPILATION ERROR**
**Reason:** Axon Framework doesn't support `@Order` on `MessageHandlerInterceptor` beans. The order is determined by Spring's bean discovery order, which is non-deterministic.

---

### Attempt #4: Smart Conditional Interceptor (CURRENT - PARTIAL SUCCESS)
**Approach:** Test interceptor checks if `TenantContext` already set before overriding.

```kotlin
@Bean
fun testTenantContextInterceptor(): MessageHandlerInterceptor<CommandMessage<*>> =
    MessageHandlerInterceptor { unitOfWork, chain ->
        val command = unitOfWork.message.payload
        if (command is TenantAwareCommand) {
            val existingTenant = TenantContext.current()
            if (existingTenant == null) {
                // Auto-propagation
                TenantContext.setCurrentTenantId(command.tenantId)
                try {
                    chain.proceed()
                } finally {
                    TenantContext.clearCurrentTenant()
                }
            } else {
                // Manual control - don't override
                chain.proceed()
            }
        } else {
            chain.proceed()
        }
    }
```

**Result:** ⚠️ **PARTIAL SUCCESS**
**Success:**
- ✅ TenantValidationInterceptorIntegrationTest: 15/15 PASSED
- ✅ Most Widget Controller tests: 42/44 PASSED

**Still Failing:**
- ❌ WidgetControllerIntegrationTest > "should update widget and return 200 OK" (1/44)
- ❌ RealisticWorkloadPerformanceTest > "should handle mixed cold/warm cache workload efficiently" (1/44)

---

## 6. Key Constraints & Requirements

### Architectural Constraints (MUST PRESERVE)
1. **Fail-Closed Security:** `TenantValidationInterceptor` MUST reject commands when:
   - `TenantContext` is not set → `TenantIsolationException`
   - `TenantContext != command.tenantId` → `TenantIsolationException`

2. **Framework/Product Separation:** Framework modules (`framework/multi-tenancy`) MUST NOT know about product modules (`products/widget-demo`)

3. **No Profile Awareness in Framework:** Framework code MUST NOT use `@Profile` annotations to distinguish test vs production

4. **ThreadLocal Cleanup:** `TenantContext.clearCurrentTenant()` MUST be called after each request/command to prevent leaks

5. **Eventual Consistency:** Read projections are async - tests MUST handle eventual consistency with retry logic

### Testing Patterns (MUST SUPPORT)
1. **Unit Tests (Framework):** Manually set `TenantContext` in `beforeTest`, test validation logic
2. **Integration Tests (Product):** Auto-propagation from commands for convenience
3. **REST Tests (Product):** `beforeTest` hook sets default tenant for HTTP requests

### Code Quality Standards
- **Kotlin:** No wildcard imports, explicit types, no generic exceptions (except infrastructure interceptors)
- **Testing:** Kotest ONLY (JUnit forbidden), Testcontainers for stateful deps
- **Spring Boot:** `@Autowired` field injection + `init` block pattern for `@SpringBootTest`

---

## 7. Diagnostic Questions to Answer

### For Test Failure #1 (Update Widget 500 Error):

1. **TenantContext State:**
   - Is `TenantContext` set correctly in `beforeTest` for this specific test?
   - Is there a race condition where `afterTest` clears context before the test completes?
   - Does the UPDATE command execution clear `TenantContext` prematurely?

2. **Interceptor Execution Order:**
   - Does `testTenantContextInterceptor` run before or after `TenantValidationInterceptor`?
   - Is there a scenario where BOTH interceptors try to manage `TenantContext`?
   - Could Axon's interceptor chain be executing in a different thread for UPDATE vs CREATE?

3. **Eventual Consistency:**
   - Does the `queryForWidgetWithRetry()` method fail to find the updated projection?
   - Is the 500 error from the UPDATE command dispatch or the subsequent query?
   - Does the retry logic interfere with `TenantContext` ThreadLocal?

4. **Test Isolation:**
   - Why do other UPDATE tests pass but this specific one fails?
   - Is there shared state contamination from previous tests?
   - Does test execution order matter?

### For Test Failure #2 (Performance Test):

1. **Bulk Operations:**
   - Does the performance test set `TenantContext` before executing bulk commands?
   - Does `TenantContext` persist across multiple `commandGateway.sendAndWait()` calls?
   - Is there a threading issue with Axon's command dispatching pool?

2. **Multi-Tenancy Overhead:**
   - Has the multi-tenancy validation introduced performance degradation beyond test thresholds?
   - Does the test need updated performance assertions?

3. **Cache Interaction:**
   - Does `WeakReferenceCache` interact poorly with tenant validation?
   - Are aggregate reloads failing due to missing `TenantContext`?

---

## 8. Expected Deliverables

### Primary Deliverable: Root Cause Analysis + Fixes

Provide:
1. **Root Cause Identification:**
   - Precise explanation of WHY each test fails
   - Stack trace analysis if needed
   - Threading/timing diagrams if relevant

2. **Proposed Solution:**
   - Code changes (with full context, file paths, line numbers)
   - Explanation of how the fix addresses the root cause
   - Proof that the fix maintains architectural constraints

3. **Alternative Approaches:**
   - If multiple solutions exist, compare trade-offs
   - Recommend the optimal approach with justification

### Secondary Deliverable: Test Infrastructure Improvements

If the root cause reveals systemic test infrastructure issues:
1. Suggest refactoring of `AxonTestConfiguration`
2. Propose better patterns for TenantContext management in tests
3. Recommend documentation updates for test authors

---

## 9. Success Criteria

### Definition of Done:
- ✅ Both failing tests pass consistently (100% success rate over 10 runs)
- ✅ All existing passing tests remain passing (no regressions)
- ✅ `TenantValidationInterceptorIntegrationTest` still validates fail-closed behavior
- ✅ Solution maintains framework/product separation
- ✅ No `@Profile` usage in framework modules
- ✅ Code follows Kotlin standards (no wildcard imports, explicit types)

### Validation Commands:
```bash
# Run failing tests specifically
./gradlew :products:widget-demo:integrationTest \
  --tests "*WidgetControllerIntegrationTest*should update widget and return 200 OK*" \
  --no-configuration-cache --no-daemon

./gradlew :products:widget-demo:integrationTest \
  --tests "*RealisticWorkloadPerformanceTest*" \
  --no-configuration-cache --no-daemon

# Run full integration test suite
./gradlew clean :framework:multi-tenancy:integrationTest \
  :products:widget-demo:integrationTest \
  --no-configuration-cache --no-daemon

# Verify no regressions
./gradlew clean build --no-configuration-cache --no-daemon
```

---

## 10. Additional Context

### Kotest Spring Boot Pattern (CRITICAL)
```kotlin
// ✅ CORRECT: @Autowired fields + init block
@SpringBootTest
class MyTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        extension(SpringExtension())

        beforeTest {
            // Setup runs BEFORE each test
        }

        test("my test") {
            // mockMvc available here
        }
    }
}

// ❌ WRONG: Constructor injection causes lifecycle issues
@SpringBootTest
class BadTest(
    private val mockMvc: MockMvc,  // FAILS
) : FunSpec({ ... })
```

### Axon Interceptor Registration
- Axon automatically discovers `MessageHandlerInterceptor` beans
- Execution order is non-deterministic (Spring bean discovery order)
- Interceptors run in a chain: `interceptor1 → interceptor2 → handler`
- Each interceptor calls `chain.proceed()` to continue the chain

### ThreadLocal Pitfalls
- ThreadLocal values don't propagate to child threads
- Axon may use thread pools for command/event processing
- Always use try-finally to ensure cleanup:
  ```kotlin
  try {
      TenantContext.setCurrentTenantId(tenantId)
      // work
  } finally {
      TenantContext.clearCurrentTenant()
  }
  ```

---

## 11. Research Methodology

### Phase 1: Hypothesis Generation (30 minutes)
- Analyze error messages and stack traces
- Review TenantContext lifecycle for both tests
- Map interceptor execution flow
- Identify threading/timing concerns

### Phase 2: Focused Investigation (60 minutes)
- Deep dive into Axon Framework interceptor chain mechanics
- Research Kotest beforeTest/afterTest timing with Spring Boot
- Investigate eventual consistency patterns in CQRS tests
- Analyze WeakReferenceCache interaction with multi-tenancy

### Phase 3: Solution Design (30 minutes)
- Design 2-3 candidate solutions
- Evaluate each against architectural constraints
- Select optimal solution with justification

### Phase 4: Validation (30 minutes)
- Provide detailed implementation instructions
- Describe expected test behavior after fix
- List potential edge cases to validate

---

## 12. Final Notes

**Priority:** HIGH - Blocking Story 4.6 completion
**Complexity:** MEDIUM-HIGH - Requires deep understanding of Axon Framework, ThreadLocal management, and test lifecycle
**Risk:** MEDIUM - Solutions must not break existing 57 passing tests

**Remember:** The framework is production-ready except for these 2 test failures. The multi-tenancy architecture is sound - we just need to fix the test infrastructure interaction.

Good luck! 🚀
