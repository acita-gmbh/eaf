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
    implementation(project(":framework:persistence"))
    implementation(project(":framework:web"))
    implementation(project(":shared:shared-api"))

    // Domain dependencies (now that Widget domain is local)
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.axon.framework)
    implementation(libs.spring.modulith.starter.core)
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // Spring Boot starters (via convention plugin)
    // Auto-configured: web, security, oauth2-resource-server, actuator, validation

    // Database
    implementation(libs.bundles.database)
    runtimeOnly(libs.postgresql)

    // Testing
    testImplementation(project(":shared:testing"))
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation(libs.bundles.testcontainers)
}

// Skip quality gates for minimal reference implementation
// detekt: Version 1.23.8 incompatible with Kotlin 2.2.20 (compiled with 2.0.21)
//         Upgrade to detekt 2.0.0 when it reaches stable release
// pitest: No production code to mutation test (just @SpringBootApplication)
tasks.named("detekt") {
    enabled = false
}
tasks.named("pitest") {
    enabled = false
}
