# Backend Patterns Guide

> Referenced from CLAUDE.md - Read when working on Kotlin backend code.

## MockK Unit Testing

**Use `any()` for ALL parameters when stubbing functions with default arguments.**

```kotlin
// ❌ MockK evaluates defaults at setup time, creating eq(specific-uuid)
coEvery { handler.handle(any()) } returns result.success()

// ✅ Explicitly match ALL parameters including defaulted ones
coEvery { handler.handle(any(), any()) } returns result.success()
```

MockK stub setup evaluates all parameters immediately. Default parameter expressions (like `UUID.randomUUID()`) execute during setup, becoming `eq()` matchers instead of `any()`.

## Coroutine Patterns

### Independent Event Handlers

Launch handlers independently when multiple handlers react to the same event:

```kotlin
// ✅ Handlers execute independently - failure of one doesn't block others
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch {
        try { handlerA.handle(event) }
        catch (e: Exception) { logger.error(e) { "Handler A failed" } }
    }
    scope.launch {
        try { handlerB.handle(event) }
        catch (e: Exception) { logger.error(e) { "Handler B failed" } }
    }
}
```

### CancellationException Handling

**NEVER catch `CancellationException` with a broad `catch (e: Exception)` - always rethrow.**

```kotlin
import kotlin.coroutines.cancellation.CancellationException

private suspend fun doWork() {
    try {
        eventStore.append(aggregateId, events, version)
    } catch (e: CancellationException) {
        throw e  // Allow proper coroutine cancellation
    } catch (e: Exception) {
        logger.error(e) { "Failed to append events" }
        return
    }
}
```

Without this, `withTimeout`, `Job.cancel()`, and scope cancellation break. Application shutdown can hang.

## Event Sourcing Patterns

### Check for Empty Event Lists

Always validate that events were returned before calculating `expectedVersion`:

```kotlin
val currentEvents = eventStore.load(aggregateId)
if (currentEvents.isEmpty()) {
    logger.error { "Cannot append: aggregate $aggregateId not found" }
    return
}
val expectedVersion = currentEvents.size.toLong()
eventStore.append(aggregateId, newEvents, expectedVersion)
```

Empty results with `expectedVersion = 0` cause concurrency conflicts (version 0 is for new aggregates only).

### Register New Domain Events

**When adding new domain events, ALWAYS update event deserializers.**

```kotlin
// In JacksonVmRequestEventDeserializer:
private fun resolveEventClass(eventType: String): Class<out DomainEvent> {
    return when (eventType) {
        "VmRequestCreated" -> VmRequestCreated::class.java
        "VmRequestApproved" -> VmRequestApproved::class.java
        "VmRequestProvisioningStarted" -> VmRequestProvisioningStarted::class.java  // Don't forget!
        else -> throw IllegalArgumentException("Unknown event type: $eventType")
    }
}
```

**Checklist for new domain events:**
1. Create event class in `dvmm-domain/.../events/`
2. Add case to `resolveEventClass()` in deserializer
3. Add deserialization test
4. If aggregate handles event, add `apply()` method and test

## CQRS Patterns

### Update Write-Side AND Read-Side Together

Command handlers must update both write-side (event store) and read-side (projections):

```kotlin
public suspend fun handle(command: MarkVmRequestProvisioningCommand): Result<Unit, Error> {
    // 1. Write-side
    aggregate.markProvisioning(metadata)
    eventStore.append(aggregate.id.value, aggregate.uncommittedEvents, expectedVersion)

    // 2. Read-side - DON'T FORGET!
    timelineUpdater.addTimelineEvent(NewTimelineEvent(
        eventType = TimelineEventType.PROVISIONING_STARTED,
        details = "VM provisioning has started"
    ))
    return Unit.success()
}
```

### Partial Failure Logging

Use "CRITICAL" prefix for multi-aggregate operations to enable alerting:

```kotlin
when (requestAppendResult) {
    is Result.Failure -> {
        logger.error {
            "CRITICAL: [Step 2/3] Failed to emit VmRequestReady for request $requestId " +
                "after VM $vmId was already marked provisioned. " +
                "System may be in inconsistent state. Error: ${requestAppendResult.error}"
        }
        return
    }
}
```

### Projection Column Symmetry

CQRS projections must handle all columns symmetrically in read and write. Use sealed class pattern:

```kotlin
sealed interface ProjectionColumns {
    data object Id : ProjectionColumns
    data object TenantId : ProjectionColumns
    data object NewColumn : ProjectionColumns

    companion object {
        val all = listOf(Id, TenantId, NewColumn)
    }
}
```

See `VmRequestProjectionRepository.kt` for reference implementation.
