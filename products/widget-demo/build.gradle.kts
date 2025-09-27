plugins {
    id("eaf.spring-boot")
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
