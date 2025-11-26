plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
    id("java-test-fixtures")
}

// eaf-testing: Test utilities for EAF-based applications
dependencies {
    api(project(":eaf:eaf-core"))

    // JUnit 6 BOM for consistent versioning
    api(platform(libs.junit.bom))

    // Testing utilities exposed as API for consumers
    api(libs.junit.jupiter)
    api(libs.junit.platform.launcher)
    api(libs.mockk)

    // Testcontainers 2.x
    api(platform(libs.testcontainers.bom))
    api(libs.testcontainers.core)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.keycloak)

    // Database & Migration (needed for test setups)
    api(libs.postgresql)
    api(libs.flyway.core)

    // JWT for TestUserFixture
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(testFixtures(project(":eaf:eaf-testing")))

    // Coroutines test for testing suspend functions
    testImplementation(libs.kotlin.coroutines.test)
}
