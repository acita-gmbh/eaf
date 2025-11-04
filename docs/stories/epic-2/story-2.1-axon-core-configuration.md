# Story 2.1: Axon Framework Core Configuration

**Story Context:** [2-1-axon-core-configuration.context.xml](2-1-axon-core-configuration.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** Review
**Story Points:** 2
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

- [x] Create framework/cqrs module structure
- [x] Add Axon Framework 4.12.1 to version catalog
- [x] Create AxonConfiguration.kt with CommandGateway and QueryGateway beans
- [x] Enable Axon auto-configuration in application.yml
- [x] Write unit tests for gateway injection
- [x] Run `./gradlew :framework:cqrs:test` - verify <10s (executed in 1s)
- [x] Document module in framework/cqrs/README.md
- [x] Commit: "Add Axon Framework core configuration with gateways"

---

## Test Evidence

- [x] CommandGateway and QueryGateway beans injectable
- [x] Unit tests pass in <10 seconds (1s actual)
- [x] Axon auto-configuration successful
- [x] No dependency conflicts

---

## Definition of Done

- [x] All acceptance criteria met
- [x] All tests pass
- [x] Module documented
- [x] Gateways functional
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

---

## File List

**Created:**
- `framework/cqrs/src/main/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfiguration.kt`
- `framework/cqrs/src/test/kotlin/com/axians/eaf/framework/cqrs/config/AxonConfigurationTest.kt`
- `framework/cqrs/README.md`

**Modified:**
- `docs/sprint-status.yaml` (story status: ready-for-dev → in-progress → review)
- `docs/stories/epic-2/story-2.1-axon-core-configuration.md` (checkboxes, status)

---

## Change Log

**2025-11-04** - Initial Implementation
- Created AxonConfiguration with CommandGateway and QueryGateway beans
- Implemented unit tests (4 tests, 1s execution time)
- Documented module in README.md
- All acceptance criteria met
- Story ready for code review

---

## Dev Agent Record

### Implementation Notes

**Approach:**
- Leveraged existing framework/cqrs module structure (build.gradle.kts already present)
- Axon Framework 4.12.1 already in version catalog (libs.bundles.axon.framework)
- Created minimal configuration class with CommandGateway and QueryGateway beans
- Axon auto-configuration handles CommandBus, EventBus, QueryBus setup automatically

**Test Strategy:**
- Unit tests verify bean creation and gateway initialization
- Tests execute in 1s (well under 10s requirement)
- No integration tests needed at this stage (covered in later stories)

**Completion Notes:**
- All 7 acceptance criteria verified
- Test execution time: 1s (< 10s ✅)
- Module fully documented with usage examples
- Ready for code review
