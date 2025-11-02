# Story 1.7: DDD Base Classes in framework/core

**Epic:** Epic 1 - Foundation & Project Infrastructure
**Status:** ready-for-dev
**Story Points:** TBD
**Related Requirements:** FR010 (Hexagonal Architecture)

## Dev Agent Record

**Context Reference:**
- [Story Context XML](../1-7-ddd-base-classes.context.xml) - Generated 2025-11-02

---

## User Story

As a framework developer,
I want DDD base classes (AggregateRoot, Entity, ValueObject, DomainEvent),
So that all domain models have consistent foundations.

---

## Acceptance Criteria

1. ✅ framework/core module structure created with src/main/kotlin/com/axians/eaf/framework/core/
2. ✅ Base classes implemented:
   - domain/AggregateRoot.kt (abstract base with identity)
   - domain/Entity.kt (abstract base with equals/hashCode)
   - domain/ValueObject.kt (abstract base for immutability)
   - domain/DomainEvent.kt (marker interface)
3. ✅ Common types implemented: Money.kt, Quantity.kt, Identifier.kt
4. ✅ Exception hierarchy: EafException, ValidationException, TenantIsolationException, AggregateNotFoundException
5. ✅ Unit tests for all base classes using Kotest
6. ✅ All tests pass in <5 seconds
7. ✅ Module compiles and publishes to local Maven repository

---

## Prerequisites

**Story 1.4** - Create Version Catalog (Kotest dependency)

---

## Technical Notes

### DDD Base Classes

**AggregateRoot.kt:**
```kotlin
abstract class AggregateRoot<ID : Identifier>(
    open val id: ID
) {
    private val domainEvents = mutableListOf<DomainEvent>()

    protected fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    fun clearEvents() = domainEvents.clear()
    fun getEvents(): List<DomainEvent> = domainEvents.toList()
}
```

**Entity.kt:**
```kotlin
abstract class Entity<ID : Identifier>(
    open val id: ID
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Entity<*>) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
```

**ValueObject.kt:**
```kotlin
abstract class ValueObject {
    // Immutable by convention
    // Equality based on all properties
}
```

**DomainEvent.kt:**
```kotlin
interface DomainEvent {
    val occurredAt: Instant
    val eventId: UUID
}
```

### Common Types

**Money.kt** (Value Object):
```kotlin
data class Money(
    val amount: BigDecimal,
    val currency: Currency
) : ValueObject()
```

**Quantity.kt** (Value Object):
```kotlin
data class Quantity(
    val value: BigDecimal,
    val unit: String
) : ValueObject()
```

**Identifier.kt** (Interface):
```kotlin
interface Identifier {
    val value: String
}
```

### Exception Hierarchy

```kotlin
open class EafException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ValidationException(message: String) : EafException(message)

class TenantIsolationException(message: String) : EafException(message)

class AggregateNotFoundException(aggregateId: String, aggregateType: String) :
    EafException("Aggregate $aggregateType with ID $aggregateId not found")
```

---

## Implementation Checklist

- [ ] Create framework/core/src/main/kotlin/com/axians/eaf/framework/core/domain/
- [ ] Implement AggregateRoot.kt with event tracking
- [ ] Implement Entity.kt with identity-based equality
- [ ] Implement ValueObject.kt base class
- [ ] Implement DomainEvent.kt marker interface
- [ ] Create common/ package with Money.kt, Quantity.kt, Identifier.kt
- [ ] Create exceptions/ package with exception hierarchy
- [ ] Write unit tests in framework/core/src/test/kotlin/ using Kotest
- [ ] Test AggregateRoot event registration
- [ ] Test Entity equality based on ID
- [ ] Test ValueObject immutability
- [ ] Run `./gradlew :framework:core:test` - all tests pass <5s
- [ ] Commit: "Add DDD base classes and common types to framework/core"

---

## Test Evidence

- [ ] All base classes compile
- [ ] Unit tests cover all base classes
- [ ] `./gradlew :framework:core:test` passes in <5 seconds
- [ ] Code coverage >85% for framework/core
- [ ] No test failures

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All tests pass
- [ ] Code documented with KDoc
- [ ] Base classes follow DDD patterns
- [ ] Module published to local Maven
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 1.6 - One-Command Initialization Script
**Next Story:** Story 1.8 - Spring Modulith Boundary Enforcement

---

## References

- PRD: FR010 (Hexagonal Architecture)
- Architecture: Section 5 (Project Structure - framework/core)
- Tech Spec: Section 3 (FR010 Implementation)
