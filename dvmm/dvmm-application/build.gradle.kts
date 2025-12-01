plugins {
    id("eaf.kotlin-conventions")
    id("eaf.logging-conventions")
    id("eaf.test-conventions")
}

// dvmm-application: Commands, Queries, Handlers
// Application services orchestrating domain logic
dependencies {
    api(project(":dvmm:dvmm-domain"))
    api(project(":eaf:eaf-eventsourcing"))

    testImplementation(testFixtures(project(":eaf:eaf-testing")))
    testImplementation(libs.kotlin.coroutines.test)
}
