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
}
