plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

// dvmm-domain: Aggregates, Events, Value Objects
// Pure domain logic, no framework dependencies
// WARNING: This module MUST NOT import org.springframework.* (enforced by Konsist)
dependencies {
    api(project(":eaf:eaf-core"))
    api(project(":eaf:eaf-eventsourcing"))
}
