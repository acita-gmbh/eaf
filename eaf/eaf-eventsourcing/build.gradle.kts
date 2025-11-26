plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

dependencies {
    api(project(":eaf:eaf-core"))

    // Kotlin Coroutines
    implementation(libs.bundles.kotlin.coroutines)

    // jOOQ for database access
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)

    // Jackson for JSON serialization
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.jsr310)

    // Flyway for migrations (runtime only)
    runtimeOnly(libs.flyway.core)

    // PostgreSQL driver
    runtimeOnly(libs.postgresql)

    // Test dependencies
    testImplementation(project(":eaf:eaf-testing"))
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
}
