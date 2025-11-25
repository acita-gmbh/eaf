plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

// dvmm-domain: Aggregates, Events, Value Objects
// Pure domain logic, no framework dependencies
dependencies {
    api(project(":eaf:eaf-core"))
}
