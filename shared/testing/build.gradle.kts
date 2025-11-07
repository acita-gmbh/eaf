// Shared Testing Module - Test utilities and nullable implementations
plugins {
    id("eaf.testing")
    id("eaf.kotlin-common")
}

dependencies {
    // Testing frameworks (provided to dependents)
    api(libs.kotest.runner.junit5.jvm)
    api(libs.kotest.assertions.core.jvm)
    api(libs.testcontainers.postgresql)

    // Architecture testing (provided to dependents)
    api(libs.konsist)

    // Spring Modulith documentation generation
    api(libs.spring.modulith.docs)

    // Spring Test support (granular dependencies - no JUnit leak)
    api(libs.spring.boot.test)
    api(libs.spring.boot.test.autoconfigure)
    api(libs.spring.test)

    // jOOQ for Nullable DSLContext implementation (Story 2.8)
    api(libs.bundles.jooq)

    // H2 in-memory database for Nullable Pattern ONLY (Story 2.8)
    // CRITICAL: This is the ONLY approved use of H2 in EAF
    // Integration tests MUST use Testcontainers PostgreSQL
    api(libs.h2)
}
