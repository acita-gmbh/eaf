# Story 2.1: Axon Framework Core Configuration

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR014 (Data Consistency)

---

## User Story

As a framework developer,
I want Axon Framework configured with Command and Query Gateways,
So that I can dispatch commands and execute queries through Axon infrastructure.

---

## Acceptance Criteria

1. ✅ framework/cqrs module created with Axon Framework 4.12.1 dependency
2. ✅ AxonConfiguration.kt configures CommandGateway and QueryGateway beans
3. ✅ CommandBus, EventBus, and QueryBus configured with default settings
4. ✅ Axon auto-configuration enabled in Spring Boot
5. ✅ Unit tests verify gateways are injectable and functional
6. ✅ ./gradlew :framework:cqrs:test passes in <10 seconds
7. ✅ Module documented in README.md

---

## Prerequisites

**Epic 1 complete** - Foundation infrastructure must be in place

---

## Technical Notes

### Axon Configuration

**framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt:**
```kotlin
@Configuration
class AxonConfiguration {

    @Bean
    fun commandGateway(commandBus: CommandBus): CommandGateway {
        return DefaultCommandGateway.builder()
            .commandBus(commandBus)
            .build()
    }

    @Bean
    fun queryGateway(queryBus: QueryBus): QueryGateway {
        return DefaultQueryGateway.builder()
            .queryBus(queryBus)
            .build()
    }
}
```

### Gradle Dependencies

**framework/cqrs/build.gradle.kts:**
```kotlin
plugins {
    id("eaf.kotlin-common")
    id("eaf.spring-boot")
    id("eaf.testing")
}

dependencies {
    implementation(libs.axon.spring.boot.starter)
    implementation(project(":framework:core"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.axon.test)
}
```

---

## Implementation Checklist

- [ ] Create framework/cqrs module structure
- [ ] Add Axon Framework 4.12.1 to version catalog
- [ ] Create AxonConfiguration.kt with CommandGateway and QueryGateway beans
- [ ] Enable Axon auto-configuration in application.yml
- [ ] Write unit tests for gateway injection
- [ ] Run `./gradlew :framework:cqrs:test` - verify <10s
- [ ] Document module in framework/cqrs/README.md
- [ ] Commit: "Add Axon Framework core configuration with gateways"

---

## Test Evidence

- [ ] CommandGateway and QueryGateway beans injectable
- [ ] Unit tests pass in <10 seconds
- [ ] Axon auto-configuration successful
- [ ] No dependency conflicts

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All tests pass
- [ ] Module documented
- [ ] Gateways functional
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Epic:** Epic 1 - Foundation complete
**Next Story:** Story 2.2 - PostgreSQL Event Store Setup

---

## References

- PRD: FR003 (Event Store), FR014 (Data Consistency)
- Architecture: Section 7 (Axon Framework details)
- Tech Spec: Section 6.2 (Axon ↔ PostgreSQL Integration)
