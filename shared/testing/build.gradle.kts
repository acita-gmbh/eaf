// Shared Testing Module - Test utilities and nullable implementations
plugins {
    id("eaf.testing-v2") // Story 2.2: Migrated to v2
    id("eaf.kotlin-common")
}

dependencies {
    // Testing frameworks (provided to dependents)
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.assertj.core)
    api(libs.assertj.kotlin)
    api(libs.testcontainers.postgresql)
    api(libs.testcontainers.keycloak)

    // Architecture testing (provided to dependents)
    api(libs.konsist)

    // Spring Modulith docs are production-only and intentionally omitted from the test profile

    // Spring Test support (granular dependencies - no JUnit leak)
    api(libs.spring.boot.test)
    api(libs.spring.boot.test.autoconfigure)
    api(libs.spring.test)

    // Web client utilities for Keycloak token generation
    api(libs.spring.boot.starter.web)

    // jOOQ for Nullable DSLContext implementation (Story 2.8)
    api(libs.bundles.jooq)

    // H2 in-memory database for Nullable Pattern ONLY (Story 2.8)
    // CRITICAL: This is the ONLY approved use of H2 in EAF and integration tests MUST use Testcontainers PostgreSQL.
    // Exported via api() so downstream Nullable-pattern unit tests can reuse the shared DSLContext helpers defined in this module.
    api(libs.h2)
}
