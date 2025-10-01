# Story 6.3: Flowable Message Events Investigation Blocker

## Executive Summary

**Objective**: Implement AxonEventSignalHandler to signal waiting BPMN processes when Axon events occur
**Blocker**: BPMN Message Receive Events not working - process not pausing at intermediateCatchEvent
**Duration**: 90+ minutes investigation
**Status**: BLOCKED - Flowable Message Events configuration issue

## Technical Context

### What Works ✅
- DispatchAxonCommandTaskIntegrationTest (Story 6.2): 3/3 tests PASS
- AxonEventSignalHandler compiles successfully
- AxonEventSignalHandlerTestApplication loads Spring context
- Kotest test infrastructure working (after separate test application fix)
- @ProcessingGroup("flowable-signaling") configured with subscribing mode

### What Doesn't Work ❌
- BPMN process not pausing at `<intermediateCatchEvent>` with `<messageEventDefinition>`
- Query for waiting executions returns null: `runtimeService.createExecutionQuery().messageEventSubscriptionName("WidgetCreated").singleResult()` = null
- AxonEventSignalHandler @EventHandler never invoked (zero logs)
- All 3 tests fail on "waitingExecution.shouldNotBeNull()" assertions

## Key Findings

### Issue 1: Kotest Multiple @SpringBootTest Conflict (SOLVED)

**Problem**: `IllegalStateException: Could not find spec TestDescriptor` when running filtered tests
**Root Cause**: Two @SpringBootTest specs in same axonIntegrationTest source set sharing same test application class
**Solution**: Created separate `AxonEventSignalHandlerTestApplication.kt` for handlers tests
**Result**: ✅ Tests now run (no more init error)

### Issue 2: BPMN Message Receive Events Not Working (BLOCKED)

**Problem**: Process not pausing at intermediateCatchEvent with messageEventDefinition
**Symptoms**:
- `runtimeService.createExecutionQuery().messageEventSubscriptionName("WidgetCreated").singleResult()` returns null
- eventually() timeout after 2 seconds (process never reaches wait state)
- Zero logs from AxonEventSignalHandler (handler never invoked)

**BPMN Definition** (simple-wait-for-event.bpmn20.xml):
```xml
<message id="WidgetCreated" name="WidgetCreated"/>
<process id="simple-wait">
    <startEvent id="start"/>
    <intermediateCatchEvent id="waitForEvent">
        <messageEventDefinition messageRef="WidgetCreated"/>
    </intermediateCatchEvent>
    <endEvent id="end"/>
    <sequenceFlow sourceRef="start" targetRef="waitForEvent"/>
    <sequenceFlow sourceRef="waitForEvent" targetRef="end"/>
</process>
```

**Test Code**:
```kotlin
val processInstance = runtimeService.startProcessInstanceByKey(
    "simple-wait",
    widgetId, // Business key
    emptyMap()
)

eventually(duration = 2.seconds) {
    val exec = runtimeService.createExecutionQuery()
        .processInstanceBusinessKey(widgetId)
        .messageEventSubscriptionName("WidgetCreated")
        .singleResult()
    exec.shouldNotBeNull() // FAILS - exec is null
    exec
}
```

## Investigation Attempts

### Attempt 1: Processor Configuration
- Added `flowable-signaling` processor with `mode: subscribing` to application.yml
- Added `@ProcessingGroup("flowable-signaling")` to AxonEventSignalHandler
- Result: ❌ Handler still not invoked, process still not waiting

### Attempt 2: Simplified BPMN
- Created minimal Start → Wait → End process (no DispatchAxonCommandTask)
- Removed all error boundaries and complexity
- Result: ❌ Same issue - process not pausing at wait state

### Attempt 3: Namespace Alignment
- Fixed targetNamespace to match working example
- Result: ❌ No change

### Attempt 4: Eventually Block
- Added eventually() with 2s timeout for async state transition
- Result: ❌ Times out - process never reaches wait state

## Hypothesis

**Primary Theory**: Flowable Message Events require additional configuration or registration that's not present in the test environment.

**Evidence**:
1. Signal Events documentation exists, but Message Events might work differently
2. The Flowable RuntimeService might need message event correlation setup
3. Message definitions might need to be registered separately

**Questions**:
1. Does Flowable require message definitions to be deployed separately?
2. Is there Spring configuration needed for message event subscriptions?
3. Does the message name need to match exactly (case-sensitive)?
4. Do Message Events work differently than Signal Events in Flowable?

## Next Steps

**Option 1**: Research Flowable Message Events documentation
- Consult Flowable 7.1.0 official docs for Message Receive Event examples
- Find working examples of Message Events in Flowable community

**Option 2**: Try Signal Events Instead
- Replace Message Events with Signal Events (broadcast model)
- Accept tenant isolation risk, mitigate with handler validation
- Signal Events might be simpler for MVP

**Option 3**: Consult External AI
- Use Gemini/Ollama to analyze Flowable Message Event configuration
- Search Stack Overflow for Flowable Message Receive Event examples

**Option 4**: Spike Investigation
- Create minimal Flowable-only test (no Axon) to validate Message Events work
- If Message Events work in isolation, issue is integration-related
- If Message Events don't work at all, switch to Signal Events

## Files Created (Partial Implementation)

**Production Code**:
- `framework/workflow/src/main/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandler.kt` - Event handler (complete, untested)

**Test Infrastructure**:
- `framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandlerIntegrationTest.kt` - Integration tests (failing)
- `framework/workflow/src/axon-integration-test/kotlin/com/axians/eaf/framework/workflow/handlers/AxonEventSignalHandlerTestApplication.kt` - Test application (works)
- `framework/workflow/src/axon-integration-test/resources/processes/simple-wait-for-event.bpmn20.xml` - Simplified BPMN (not working)
- `framework/workflow/src/axon-integration-test/resources/processes/widget-lifecycle-with-wait.bpmn20.xml` - Full async workflow BPMN (not tested yet)

**Configuration**:
- Updated application.yml with flowable-signaling processor configuration

## Spike Test Results ✅ **BREAKTHROUGH**

### Spike Test: FlowableMessageEventsSpikeTest.kt (2/2 PASS)

**Test 1: Flowable Message Receive Event Validation**
- ✅ BPMN deploys successfully
- ✅ Process starts and pauses at intermediateCatchEvent
- ✅ Message subscription created (`eventType="message", eventName="WidgetCreated"`)
- ✅ Execution found via `messageEventSubscriptionName("WidgetCreated")`
- ✅ RuntimeService.messageEventReceived() signals process
- ✅ Process completes successfully

**Test 2: AxonEventSignalHandler Bean Exists**
- ✅ Handler bean retrieved from Spring ApplicationContext
- ✅ Spring DI working correctly

**Conclusion**: Flowable Message Events work PERFECTLY. Issue is Axon integration.

## Root Cause Analysis

### Issue 1: AxonEventSignalHandler @EventHandler Not Invoked

**Symptom**: Handler logs never appear, process not signaled
**Evidence**: Spike manually calls RuntimeService.messageEventReceived() and works
**Root Cause**: @EventHandler not being triggered when events published via EventBus/EventGateway in tests

**Hypothesis**:
- EventGateway.publish() might use different event bus than @EventHandler subscription
- @ProcessingGroup("flowable-signaling") might not be subscribed to SimpleEventBus
- Test configuration missing SubscribingEventProcessor registration

### Issue 2: Event Metadata Missing TenantId

**Symptom**: SecurityException: "Access denied: required context missing"
**Root Cause**: EventGateway.publish(event) doesn't use TenantCorrelationDataProvider
**Research**: Ollama confirmed EventGateway bypasses CorrelationDataProviders
**Solution Attempt**: Use GenericEventMessage with manual metadata - still fails

## Research Questions for External AI

1. **Axon @EventHandler Registration**: How to ensure @EventHandler methods subscribe to SimpleEventBus in tests?
2. **EventGateway vs EventBus**: Which API triggers @EventHandler methods in test context?
3. **SubscribingEventProcessor**: Does test need explicit processor registration?
4. **Kotest + Axon**: Known issues with @EventHandler invocation in Kotest tests?

## Time Investment

- Kotest conflict resolution: 30 minutes
- BPMN Message Events debugging: 60 minutes
- Spike test creation: 30 minutes
- EventBus/metadata investigation: 90 minutes
- **Total: 3.5+ hours**

## Current Status

**Tests**: 8 total, 5 passing (62%)
- ✅ Story 6.2 regression: 3/3 PASS
- ✅ Spike tests: 2/2 PASS
- ❌ Story 6.3 main: 0/3 PASS

**Production Code**: ✅ Complete (AxonEventSignalHandler.kt)
**Test Infrastructure**: ✅ Complete (separate test application, CustomMetrics)
**BPMN Definitions**: ✅ Validated (spike proves they work)

**Blocker**: @EventHandler invocation in Axon test environment

**Recommendation**: Pair programming session or escalate to Axon Framework expert.
