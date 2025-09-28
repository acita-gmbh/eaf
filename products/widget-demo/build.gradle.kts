plugins {
    id("eaf.testing")        // Apply FIRST (Kotest setup)
    id("eaf.spring-boot")    // Apply AFTER Kotest
    id("eaf.quality-gates")  // Apply LAST
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

    // PHASE 2 FIX: Explicit Kotest dependencies for integrationTest to override Spring Boot BOM
    integrationTestImplementation("io.kotest:kotest-runner-junit5:6.0.3")
    integrationTestImplementation("io.kotest:kotest-assertions-core:6.0.3")
    integrationTestImplementation("io.kotest:kotest-property:6.0.3")
    integrationTestImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-test")
    integrationTestImplementation("org.springframework.security:spring-security-test")
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
