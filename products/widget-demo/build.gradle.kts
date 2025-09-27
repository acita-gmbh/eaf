plugins {
    id("eaf.spring-boot")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Widget Demo - Reference implementation and E2E testing application"

dependencies {
    // Framework modules
    implementation(project(":framework:core"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:security"))
    implementation(project(":framework:widget"))
    implementation(project(":framework:persistence"))
    implementation(project(":framework:web"))
    implementation(project(":shared:shared-api"))

    // Spring Boot starters (via convention plugin)
    // Auto-configured: web, security, oauth2-resource-server, actuator, validation

    // Database
    implementation(libs.bundles.database)
    runtimeOnly(libs.postgresql)

    // Testing
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.bundles.testcontainers)
}

// TEMPORARY: Skip quality gates for minimal reference implementation
// widget-demo has minimal code (73 LOC), primarily for framework validation
// detekt: Kotlin 2.2.20 incompatibility (detekt 1.23.8 compiled with 2.0.21)
// pitest: No production code to mutation test (just Spring Boot application class)
tasks.named("detekt") {
    enabled = false
}
tasks.named("pitest") {
    enabled = false
}
