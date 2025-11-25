plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

// eaf-testing: Test utilities for EAF-based applications
dependencies {
    api(project(":eaf:eaf-core"))

    // JUnit 6 BOM for consistent versioning
    api(platform("org.junit:junit-bom:6.0.1"))

    // Testing utilities exposed as API for consumers
    api("org.junit.jupiter:junit-jupiter")
    api("org.junit.platform:junit-platform-launcher")
    api("io.mockk:mockk:1.14.6")

    // Testcontainers 2.x
    api(platform("org.testcontainers:testcontainers-bom:2.0.2"))
    api("org.testcontainers:testcontainers")
    api("org.testcontainers:testcontainers-junit-jupiter")
    api("org.testcontainers:testcontainers-postgresql")
}
