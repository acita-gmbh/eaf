# Backend Patterns Guide

> This guide is referenced from the main CLAUDE.md documentation

## Type Safety

### Value Classes Over Type Aliases

**Use `@JvmInline value class` for domain identifiers, NOT `typealias`.**

```kotlin
// ❌ typealias creates only an alias - no compile-time safety
typealias UserId = String
typealias TenantId = String

fun process(userId: UserId, tenantId: TenantId) { }
process(tenantId, userId)  // Compiles! Arguments swapped silently

// ✅ Value classes provide true type safety with zero runtime overhead
import java.util.UUID

@JvmInline
public value class UserId(public val value: String)

@JvmInline
public value class TenantId(public val value: UUID)

fun process(userId: UserId, tenantId: TenantId) { }
process(tenantId, userId)  // Compile error! Type mismatch
```

Value classes are inlined at runtime (no object allocation) but enforce type safety at compile time. Use them for all domain identifiers. Validate inputs at construction boundaries to ensure value objects never hold invalid state:

```kotlin
@JvmInline
public value class VmName(public val value: String) {
    init {
        require(value.isNotBlank()) { "VM name cannot be blank" }
        require(value.length <= 80) { "VM name exceeds 80 characters" }
    }
}
```

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
import kotlin.coroutines.cancellation.CancellationException

// ✅ Handlers execute independently - failure of one doesn't block others
@EventListener
fun onEvent(event: SomeEvent) {
    scope.launch {
        try { handlerA.handle(event) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { logger.error(e) { "Handler A failed" } }
    }
    scope.launch {
        try { handlerB.handle(event) }
        catch (e: CancellationException) { throw e }
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

### Dispatcher Injection

**Accept dispatchers as constructor parameters (with sensible defaults) so tests can override them.**

```kotlin
// ❌ Direct dispatcher usage at call site - tests cannot substitute
class VmwareService {
    suspend fun cloneVm() = withContext(Dispatchers.IO) {
        // blocking I/O
    }
}

// ✅ Injectable dispatcher with sensible default - tests can override
class VmwareService(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun cloneVm() = withContext(ioDispatcher) {
        // blocking I/O
    }
}
```

For high-concurrency scenarios, limit parallelism to prevent thread exhaustion:

```kotlin
private val limitedIo = Dispatchers.IO.limitedParallelism(64)
```

### Structured Concurrency with supervisorScope

**Use `supervisorScope` when child failures should NOT cancel siblings.**

```kotlin
import kotlin.coroutines.cancellation.CancellationException

// ❌ coroutineScope - one failure cancels all children
suspend fun notifyAll(users: List<User>) = coroutineScope {
    users.forEach { user ->
        launch { notificationService.send(user) }  // One failure cancels ALL
    }
}

// ✅ supervisorScope - failures are isolated
suspend fun notifyAll(users: List<User>) = supervisorScope {
    users.forEach { user ->
        launch {
            try { notificationService.send(user) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { logger.error(e) { "Failed to notify ${user.id}" } }
        }
    }
}
```

## Async Testing

### Deterministic Coroutine Tests

**Use `runTest` with `StandardTestDispatcher` for deterministic async testing.**

```kotlin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle

class VmProvisioningServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `should complete provisioning`() = runTest(testDispatcher) {
        val service = VmProvisioningService(ioDispatcher = testDispatcher)

        service.startProvisioning(vmId)

        advanceUntilIdle()  // Fast-forward virtual time - no real delays!

        // Assert final state
    }
}
```

Key benefits:
- **No flaky tests** - virtual time eliminates real delays
- **Fast CI/CD** - tests complete in milliseconds
- **Deterministic** - same input always produces same timing

### Flow Testing with Turbine

**Use Turbine for assertion-based Flow validation.**

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ProvisioningProgressServiceTest {
    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `should emit progress updates`() = runTest(testDispatcher) {
        // Service uses injectable dispatcher (see Dispatcher Injection section)
        val service = ProvisioningProgressService(ioDispatcher = testDispatcher)

        service.progressFlow(vmRequestId).test {
            assertEquals(VmProvisioningStage.CLONING, awaitItem().stage)
            assertEquals(VmProvisioningStage.CUSTOMIZING, awaitItem().stage)
            assertEquals(VmProvisioningStage.READY, awaitItem().stage)
            awaitComplete()
        }
    }
}
```

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
1. Create event class in `dcm-domain/.../events/`
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

---

## References

- [How Backend Development Teams Use Kotlin in 2025](https://blog.jetbrains.com/kotlin/2025/12/how-backend-development-teams-use-kotlin-in-2025/) - JetBrains survey and best practices
