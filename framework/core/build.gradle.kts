plugins {
    id("eaf.testing-v2") // PILOT: Testing v2 plugin (Story 2.1)
    id("eaf.kotlin-common")
}

description = "EAF Core Framework - Domain patterns and utilities"

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    // Resilience patterns (OWASP A10:2025 - Exception Handling)
    api(libs.bundles.resilience4j)

    testImplementation(libs.bundles.kotest)
}
