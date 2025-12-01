plugins {
    id("eaf.kotlin-conventions")
    id("eaf.logging-conventions")
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
    id("java-test-fixtures")
}

// eaf-testing is a library module, not an executable application
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

// eaf-testing: Test utilities for EAF-based applications
dependencies {
    api(project(":eaf:eaf-core"))

    // Spring WebFlux for test WebClient
    api(libs.spring.boot.webflux)

    // JUnit 6 BOM for consistent versioning
    api(platform(libs.junit.bom))

    // Testing utilities exposed as API for consumers
    api(libs.junit.jupiter)
    api(libs.junit.platform.launcher)
    api(libs.junit.platform.engine)
    api(libs.mockk)

    // Testcontainers 2.x
    api(platform(libs.testcontainers.bom))
    api(libs.testcontainers.core)
    api(libs.testcontainers.junit)
    api(libs.testcontainers.jdbc)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.keycloak)

    // Database & Migration (needed for test setups)
    api(libs.postgresql)
    api(libs.flyway.core)

    // JWT for TestUserFixture
    api(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Bouncy Castle for certificate generation (VCSIM TLS)
    // Implementation scope since BC types aren't exposed in public API
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcprov)

    testImplementation(testFixtures(project(":eaf:eaf-testing")))

    // Coroutines test for testing suspend functions
    testImplementation(libs.kotlin.coroutines.test)
}
