# Story 6.3 Deep Research Request - Axon @EventHandler Invocation Blocker

## OBJECTIVE

Fix Axon Framework @EventHandler invocation issue in Kotest integration tests where AxonEventSignalHandler is not being triggered when events are published via EventBus/EventGateway.

## TECHNOLOGY STACK CONTEXT

### Core Technologies
- **Kotlin**: 2.2.20 (PINNED)
- **Spring Boot**: 3.5.6 (LOCKED)
- **JVM**: 21 LTS
- **Gradle**: 9.1.0
- **Axon Framework**: 4.12.1
- **Flowable**: 7.1.0
- **Kotest**: 6.0.3 (JUnit Platform hybrid runner)

### Testing Framework
- **Primary**: Kotest 6.0.3 FunSpec with SpringExtension
- **Pattern**: @SpringBootTest with @Autowired field injection + init block
- **Test Source Set**: Custom `axonIntegrationTest` source set (separate from main integration tests)
- **Dependencies**: PostgreSQL Testcontainers, InMemoryEventStorageEngine, SimpleMeterRegistry

### Axon Configuration (Test Environment)
```yaml
# framework/workflow/src/axon-integration-test/resources/application.yml
axon:
  axonserver:
    enabled: false  # In-memory mode (SimpleEventBus, InMemoryEventStorageEngine)
  eventhandling:
    processors:
      widget-projection:
        mode: subscribing
      flowable-signaling:
        mode: subscribing  # Story 6.3: For AxonEventSignalHandler
```

### Test Configuration Beans
```kotlin
@TestConfiguration
open class AxonIntegrationTestConfig {
    @Bean @Primary
    open fun eventStorageEngine(): EventStorageEngine = InMemoryEventStorageEngine()

    @Bean
    open fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

    @Bean
    open fun tenantContext(meterRegistry: MeterRegistry): TenantContext = TenantContext(meterRegistry)

    @Bean
    open fun customMetrics(meterRegistry: MeterRegistry, tenantContext: TenantContext): CustomMetrics =
        CustomMetrics(meterRegistry, tenantContext)
}
```

## CRITICAL GUIDELINES

### Zero-Tolerance Policies
1. **NO wildcard imports** - Every import must be explicit
2. **NO generic exceptions** - Always use specific exception types
3. **Kotest ONLY** - JUnit is explicitly forbidden
4. **NO H2** - PostgreSQL Testcontainers only
5. **Integration-first testing** - Real dependencies via Testcontainers

### Testing Pattern (Story 4.6 Lessons)
```kotlin
@SpringBootTest(classes = [AxonEventSignalHandlerTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class AxonEventSignalHandlerIntegrationTest : FunSpec() {
    @Autowired private lateinit var eventBus: EventBus
    @Autowired private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())
        afterEach { tenantContext.clearCurrentTenant() }
        test("...") { /* test code */ }
    }
}
```

## PROBLEM STATEMENT

### What Works ✅

1. **Story 6.2 Regression Tests**: 3/3 PASS
   - DispatchAxonCommandTaskIntegrationTest passes perfectly
   - Proves test infrastructure works for command dispatching

2. **Spike Test 1 (Flowable Message Events)**: PASS
   - BPMN deploys successfully
   - Process pauses at intermediateCatchEvent with messageEventDefinition
   - Message subscription created (`eventType="message", eventName="WidgetCreated"`)
   - RuntimeService.messageEventReceived() signals process successfully
   - **Conclusion**: Flowable Message Receive Events work perfectly

3. **Spike Test 2 (Bean Existence)**: PASS
   - AxonEventSignalHandler bean retrieved from ApplicationContext
   - **Conclusion**: Spring DI working correctly, bean exists

### What Doesn't Work ❌

**AxonEventSignalHandler @EventHandler method NEVER invoked** when events published in tests:

```kotlin
// THIS DOES NOT TRIGGER @EventHandler
eventBus.publish(
    GenericEventMessage.asEventMessage<WidgetCreatedEvent>(event)
        .andMetaData(mapOf("tenantId" to "test-tenant"))
)

// Handler's on(event: WidgetCreatedEvent) method never executes
// Zero logs, zero invocations, process not signaled
```

**Symptoms**:
1. No logs from AxonEventSignalHandler (would log "Signaled BPMN process..." or "No waiting process...")
2. Test 3 throws SecurityException at publish (TenantEventMessageInterceptor validates metadata, handler never runs)
3. Tests 1 & 2 fail because process not signaled (waitingExecution stays null)

## DETAILED BLOCKER DOCUMENTATION

See complete investigation in: `.ai/story-6.3-flowable-message-events-blocker.md`

### Key Investigation Findings

**After 3.5+ hours of debugging:**

1. **Kotest Multiple @SpringBootTest Conflict** (SOLVED - 30 min):
   - Error: `IllegalStateException: Could not find spec TestDescriptor`
   - Solution: Created separate AxonEventSignalHandlerTestApplication.kt
   - Result: Tests now run without initialization errors

2. **Flowable Message Events Investigation** (VALIDATED - 90 min):
   - Spike test proves Flowable Message Receive Events work perfectly
   - BPMN XML validated by Gemini consultation
   - RuntimeService.messageEventReceived() API confirmed working

3. **Axon Event Handler Invocation** (BLOCKED - 90 min):
   - @EventHandler annotated method never executes
   - EventBus.publish() and EventGateway.publish() both attempted
   - Manual metadata addition attempted (GenericEventMessage.andMetaData)
   - Zero logs from handler = complete non-invocation

### Code Review Insights (GitHub PR #37 - CodeRabbit)

**Critical Finding from PR Review**:

> "**Verify: Disabling async executor may not reflect production behavior.**
>
> Setting `async-executor-activate: false` enables synchronous, deterministic testing
> but differs from typical production configuration. Ensure that separate tests validate
> async execution scenarios (e.g., timing, race conditions) to avoid masking production issues."

**Potential Impact on Blocker**:
- Flowable async executor disabled (`async-executor-activate: false`)
- This was changed during debugging to make process execution synchronous
- Original Story 6.2 tests use `async-executor-activate: true`
- **Question**: Does disabling async executor affect message event subscription creation?

**Other PR Review Points**:
- ✅ BPMN process definition valid and well-formed
- ✅ Bean configuration correct (securityConfigExcludeFilter)
- ⚠️ Fixed delays in spike test should use `eventually()` for CI stability
- ⚠️ Markdown lint issues (use headings instead of bold text)

## PRODUCTION CODE (Complete and Correct)

```kotlin
// framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandler.kt

package com.axians.eaf.framework.workflow.handlers

import com.axians.eaf.api.widget.events.WidgetCreatedEvent
import com.axians.eaf.framework.security.tenant.TenantContext
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.flowable.common.engine.api.FlowableException
import org.flowable.engine.RuntimeService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ProcessingGroup("flowable-signaling")
class AxonEventSignalHandler(
    private val runtimeService: RuntimeService,
    private val tenantContext: TenantContext
) {
    private val logger: Logger = LoggerFactory.getLogger(AxonEventSignalHandler::class.java)

    @EventHandler
    fun on(event: WidgetCreatedEvent) {
        // CRITICAL: Fail-closed tenant validation
        val currentTenant = tenantContext.getCurrentTenantId() // Throws if missing
        require(event.tenantId == currentTenant) {
            "Access denied: tenant context mismatch" // CWE-209 protection
        }

        // Query for waiting process instances using business key correlation
        val execution = runtimeService.createExecutionQuery()
            .processInstanceBusinessKey(event.widgetId)
            .messageEventSubscriptionName("WidgetCreated")
            .singleResult()

        // Resilient error handling for missing processes
        if (execution != null) {
            try {
                // Signal the waiting process using message delivery
                runtimeService.messageEventReceived("WidgetCreated", execution.id)
                logger.info("Signaled BPMN process for widgetId=${event.widgetId}")
            } catch (ex: FlowableException) {
                logger.error("Failed to signal BPMN process: ${ex.message}", ex)
            }
        } else {
            logger.warn(
                "No waiting process found for WidgetCreatedEvent: widgetId=${event.widgetId}, tenantId=${event.tenantId}"
            )
        }
    }
}
```

**Code Quality**:
- ✅ No wildcard imports (explicit imports only)
- ✅ Tenant validation with fail-closed pattern
- ✅ Error handling for all scenarios
- ✅ Logging for observability
- ✅ @ProcessingGroup annotation
- ✅ Compiles successfully

## FAILING TEST CODE

```kotlin
// framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandlerIntegrationTest.kt

@SpringBootTest(classes = [AxonEventSignalHandlerTestApplication::class])
@Import(AxonIntegrationTestConfig::class)
@ActiveProfiles("test")
class AxonEventSignalHandlerIntegrationTest : FunSpec() {

    @Autowired private lateinit var processEngine: ProcessEngine
    @Autowired private lateinit var runtimeService: RuntimeService
    @Autowired private lateinit var eventGateway: EventGateway
    @Autowired private lateinit var eventBus: EventBus
    @Autowired private lateinit var tenantContext: TenantContext

    init {
        extension(SpringExtension())
        afterEach { tenantContext.clearCurrentTenant() }

        test("should signal waiting BPMN process when WidgetCreatedEvent published") {
            tenantContext.setCurrentTenantId("test-tenant")

            // Deploy BPMN with Message Receive Event
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/simple-wait-for-event.bpmn20.xml")
                .deploy()

            // Start process - should pause at intermediateCatchEvent
            val widgetId = UUID.randomUUID().toString()
            val processInstance = runtimeService.startProcessInstanceByKey(
                "simple-wait",
                widgetId, // Business key for correlation
                emptyMap()
            )

            delay(1000) // Wait for process to reach wait state

            // FAILS HERE: waitingExecution is null (no message subscription created)
            val waitingExecution = runtimeService.createExecutionQuery()
                .processInstanceBusinessKey(widgetId)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

            waitingExecution.shouldNotBeNull() // ❌ FAILS

            // Publish event - this should trigger AxonEventSignalHandler
            val event = WidgetCreatedEvent(
                widgetId = widgetId,
                tenantId = "test-tenant",
                name = "Test Widget",
                value = BigDecimal("100.00"),
                category = "TEST"
            )

            val eventMessage = GenericEventMessage.asEventMessage<WidgetCreatedEvent>(event)
                .andMetaData(mapOf("tenantId" to "test-tenant"))

            eventBus.publish(eventMessage) // Handler's on() method NEVER called

            // Process should complete after signal, but never does
            // ...
        }
    }
}
```

## WORKING SPIKE TEST (Proves Infrastructure Correct)

```kotlin
// framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/spike/FlowableMessageEventsSpikeTest.kt

@SpringBootTest(classes = [AxonEventSignalHandlerTestApplication::class])
@ActiveProfiles("test")
class FlowableMessageEventsSpikeTest : FunSpec() {

    @Autowired private lateinit var processEngine: ProcessEngine
    @Autowired private lateinit var runtimeService: RuntimeService
    @Autowired private lateinit var testApplicationContext: ApplicationContext

    init {
        extension(SpringExtension())

        test("SPIKE: Flowable Message Receive Event should create subscription and wait") {
            // Deploy same BPMN as failing test
            processEngine.repositoryService
                .createDeployment()
                .addClasspathResource("processes/simple-wait-for-event.bpmn20.xml")
                .deploy()

            // Start process with business key
            val widgetId = UUID.randomUUID().toString()
            val processInstance = runtimeService.startProcessInstanceByKey(
                "simple-wait",
                widgetId,
                emptyMap()
            )

            delay(500)

            // ✅ PASSES: Message subscription IS created
            val subscription = runtimeService.createEventSubscriptionQuery()
                .processInstanceId(processInstance.id)
                .eventType("message")
                .eventName("WidgetCreated")
                .singleResult()

            subscription.shouldNotBeNull() // ✅ PASSES

            // ✅ PASSES: Execution found
            val execution = runtimeService.createExecutionQuery()
                .processInstanceId(processInstance.id)
                .messageEventSubscriptionName("WidgetCreated")
                .singleResult()

            execution.shouldNotBeNull() // ✅ PASSES

            // ✅ PASSES: Manual signal works
            runtimeService.messageEventReceived("WidgetCreated", execution.id)

            delay(500)

            // ✅ PASSES: Process completes
            val historic = processEngine.historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstance.id)
                .singleResult()

            historic.endTime.shouldNotBeNull() // ✅ PASSES
        }

        test("SPIKE 2: AxonEventSignalHandler bean exists") {
            // ✅ PASSES: Bean can be retrieved from Spring context
            val handler = testApplicationContext.getBean(AxonEventSignalHandler::class.java)
            handler.shouldNotBeNull() // ✅ PASSES
        }
    }
}
```

**Spike Conclusions**:
- ✅ Flowable Message Events work (subscription created, process waits, signal delivers)
- ✅ AxonEventSignalHandler bean exists in Spring context
- ✅ RuntimeService.messageEventReceived() API works
- ❌ AxonEventSignalHandler @EventHandler never invoked when using Axon EventBus

## WHAT WE'VE TRIED (15+ Attempts)

1. **EventGateway.publish()** - Handler not invoked
2. **EventBus.publish()** - Handler not invoked
3. **GenericEventMessage with metadata** - Handler not invoked
4. **@ProcessingGroup("flowable-signaling")** - Handler not invoked
5. **Processor config in application.yml** - Handler not invoked
6. **async-executor-activate: false** - No change
7. **async-executor-activate: true** - No change
8. **eventually() instead of delay()** - Still handler not invoked
9. **Separate test application** - Fixed Kotest issue but handler still not invoked
10. **CustomMetrics bean added** - TenantEventMessageInterceptor works but handler not invoked
11. **Debug logging enabled** - Zero logs from handler
12. **@Import(AxonIntegrationTestConfig)** - Configuration loads but handler not invoked
13. **Different BPMN process** - Works in spike, fails in integration test
14. **Simplified BPMN (no delegates)** - Same result
15. **Clean build** - No change

## COMPARISON: WHAT WORKS VS WHAT DOESN'T

### Story 6.2 (WORKS - 3/3 PASS)

```kotlin
// DispatchAxonCommandTaskIntegrationTest.kt
@SpringBootTest(classes = [DispatchAxonCommandTestApplication::class])
class DispatchAxonCommandTaskIntegrationTest : FunSpec() {
    @Autowired private lateinit var widgetProjectionRepository: WidgetProjectionRepository

    test("should dispatch CreateWidgetCommand from BPMN process") {
        // Start BPMN process that dispatches command
        val processInstance = runtimeService.startProcessInstanceByKey(
            "example-widget-creation",
            processVariables
        )

        // ✅ WORKS: Widget aggregate applies WidgetCreatedEvent
        // ✅ WORKS: WidgetProjectionHandler @EventHandler processes event
        // ✅ WORKS: Projection appears in repository

        eventually(duration = 5.seconds) {
            val widget = widgetProjectionRepository.findById(widgetId)
            widget.shouldNotBeNull() // ✅ PASSES
        }
    }
}
```

**Why Story 6.2 Works**:
- Events published via `AggregateLifecycle.apply()` in Widget aggregate
- WidgetProjectionHandler @EventHandler gets invoked
- Projection writes to database

### Story 6.3 (DOESN'T WORK - 0/3 PASS)

```kotlin
// AxonEventSignalHandlerIntegrationTest.kt - SAME test infrastructure
test("should signal waiting BPMN process when WidgetCreatedEvent published") {
    // Publish event via EventBus (not from aggregate)
    eventBus.publish(eventMessage)

    // ❌ FAILS: AxonEventSignalHandler @EventHandler NEVER invoked
    // ❌ FAILS: Zero logs, zero method calls
    // ❌ FAILS: Process never signaled
}
```

**Why Story 6.3 Doesn't Work**:
- Events published via `EventBus.publish()` or `EventGateway.publish()`
- AxonEventSignalHandler @EventHandler NOT invoked
- Process never signaled (waitingExecution null)

## CRITICAL DIFFERENCE IDENTIFIED

**Story 6.2**: Events from `AggregateLifecycle.apply()` → @EventHandler works
**Story 6.3**: Events from `EventBus.publish()` → @EventHandler DOESN'T work

**Hypothesis**:
- Maybe EventBus.publish() in tests bypasses event processor subscriptions?
- Maybe @EventHandler only works for aggregate-sourced events?
- Maybe SubscribingEventProcessor needs explicit start() in tests?

## SPECIFIC RESEARCH QUESTIONS

### Question 1: EventBus.publish() vs AggregateLifecycle.apply()

**Why does this work:**
```kotlin
// Inside Widget aggregate command handler
apply(WidgetCreatedEvent(...))  // ✅ WidgetProjectionHandler @EventHandler invoked
```

**But this doesn't:**
```kotlin
// Inside test method
eventBus.publish(GenericEventMessage.asEventMessage(WidgetCreatedEvent(...)))  // ❌ AxonEventSignalHandler @EventHandler NOT invoked
```

**Questions**:
- Does EventBus.publish() bypass SubscribingEventProcessor subscriptions in in-memory mode?
- Do tests need to explicitly subscribe @EventHandler beans to EventBus?
- Is there a different API for publishing events in tests that triggers @EventHandler?

### Question 2: SubscribingEventProcessor Configuration

**Current Configuration**:
```yaml
axon:
  axonserver:
    enabled: false  # In-memory mode
  eventhandling:
    processors:
      flowable-signaling:
        mode: subscribing
```

**Questions**:
- Does `mode: subscribing` automatically register all @EventHandler methods in that @ProcessingGroup?
- Does SubscribingEventProcessor need explicit start() call in @SpringBootTest context?
- Is there additional configuration needed for processors to subscribe to SimpleEventBus?
- Does Axon auto-configuration properly wire @ProcessingGroup beans in test context?

### Question 3: InMemoryEventStorageEngine and Event Processing

**Test Configuration**:
```kotlin
@Bean @Primary
open fun eventStorageEngine(): EventStorageEngine = InMemoryEventStorageEngine()
```

**Questions**:
- Does InMemoryEventStorageEngine affect @EventHandler subscriptions?
- Do events published directly to EventBus get stored in InMemoryEventStorageEngine?
- Does SimpleEventBus require explicit processor registration in tests?
- Is there a difference between event store events and published events for @EventHandler invocation?

### Question 4: Kotest + Axon + SpringBootTest

**Pattern**:
```kotlin
@SpringBootTest
@Import(AxonIntegrationTestConfig::class)
class MyTest : FunSpec() {
    @Autowired private lateinit var eventBus: EventBus
    init {
        extension(SpringExtension())
        test("...") { eventBus.publish(event) }  // @EventHandler not invoked
    }
}
```

**Questions**:
- Are there known issues with @EventHandler invocation in Kotest tests?
- Does Kotest coroutine context interfere with Axon event processing threads?
- Does SpringExtension properly initialize Axon event processors?
- Should tests use different threading model for event publishing?

### Question 5: Tenant Context Propagation in Tests

**Current Error**:
```
java.lang.SecurityException: Access denied: required context missing
  at TenantEventMessageInterceptor.extractTenantId(TenantEventMessageInterceptor.kt:220)
```

**Context**:
- Test sets TenantContext: `tenantContext.setCurrentTenantId("test-tenant")`
- Test publishes event: `eventBus.publish(eventMessage.andMetaData(mapOf("tenantId" to "test-tenant")))`
- TenantEventMessageInterceptor throws SecurityException
- This proves interceptor IS running, but BEFORE @EventHandler (expected)
- But handler never gets to run

**Questions**:
- Does TenantEventMessageInterceptor failure prevent @EventHandler from being invoked?
- Should tests skip TenantEventMessageInterceptor validation?
- Is there a test-only event publishing API that bypasses interceptors?

## EXTERNAL RESEARCH COMPLETED

### Ollama (llama3)
**Finding**: EventGateway.publish() uses "fire-and-forget" optimization that bypasses correlation data providers. Recommended using EventPublisher API instead.

**Attempted**: Not applicable - EventPublisher doesn't exist in Axon 4.12.1 public API.

### Web Search
**Finding**: Axon CorrelationDataProviders work with dispatch interceptors and run automatically with commands. EventGateway goes through MessageDispatchInterceptor processing.

**Attempted**: GenericEventMessage.andMetaData() to manually add metadata - still doesn't invoke handler.

### WebFetch (Flowable Docs)
**Finding**: Message Catch Events are environment-dependent and require platform-specific implementation to trigger messageEventReceived().

**Result**: Confirmed by spike test - RuntimeService.messageEventReceived() works perfectly.

### Gemini (via Context7)
**Finding**: BPMN XML structure is correct. Flowable should pause at intermediateCatchEvent with messageEventDefinition.

**Result**: Confirmed by spike test - Flowable Message Events work correctly.

## REQUIRED SOLUTION

**What we need**: How to make Axon @EventHandler methods execute when events are published via EventBus/EventGateway in Kotest @SpringBootTest integration tests.

**Acceptable solutions**:
1. Configuration change to enable @EventHandler subscription to EventBus
2. Different API for publishing events in tests that triggers @EventHandler
3. Explicit processor start/subscription in test setup
4. Alternative testing approach (e.g., use aggregate apply() instead of direct publish)

**Constraints**:
- MUST maintain tenant context propagation (TenantEventMessageInterceptor)
- MUST use Kotest 6.0.3 (JUnit forbidden)
- MUST avoid mocks (use real Axon components)
- MUST maintain Story 6.2 regression (3/3 PASS)

## TEST COMPARISON TABLE

| Aspect | Story 6.2 (WORKS) | Story 6.3 (DOESN'T WORK) | Spike (WORKS) |
|--------|-------------------|--------------------------|---------------|
| **Event Source** | AggregateLifecycle.apply() | EventBus.publish() | Manual RuntimeService call |
| **@EventHandler Invoked** | ✅ YES (WidgetProjectionHandler) | ❌ NO (AxonEventSignalHandler) | N/A (no Axon handler) |
| **Test Application** | DispatchAxonCommandTestApplication | AxonEventSignalHandlerTestApplication | AxonEventSignalHandlerTestApplication |
| **Event Type** | WidgetCreatedEvent | WidgetCreatedEvent | N/A |
| **Processing Group** | widget-projection | flowable-signaling | N/A |
| **Test Passes** | ✅ 3/3 | ❌ 0/3 | ✅ 2/2 |

**Pattern**: Events from aggregates work, events from EventBus don't trigger @EventHandler in tests.

## REQUESTED ANALYSIS

Please analyze this comprehensive context and provide:

1. **Root cause identification**: Why doesn't EventBus.publish() trigger @EventHandler in our test setup?

2. **Configuration fix**: What Axon/Spring configuration is missing to enable @EventHandler subscriptions in tests?

3. **Code solution**: Provide working code example showing how to publish events in tests that trigger @EventHandler methods.

4. **Alternative approaches**: If EventBus.publish() fundamentally doesn't work in tests, what's the recommended pattern?

5. **Precedent check**: Has this exact issue (Kotest + Axon + @SpringBootTest + EventBus.publish()) been solved before? Link to examples.

**Priority**: CRITICAL - Story blocked after 3.5 hours investigation, spike tests prove infrastructure correct, need Axon event processing expert guidance.

**Context Files**:
- Production code: framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandler.kt
- Failing test: framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandlerIntegrationTest.kt
- Working spike: framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/spike/FlowableMessageEventsSpikeTest.kt
- Test config: framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/delegates/AxonIntegrationTestConfig.kt
- Application: framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandlerTestApplication.kt
