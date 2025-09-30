plugins {
    id("eaf.workflow")
    id("eaf.testing")
}

description = "EAF Workflow Framework - Flowable BPMN integration"

dependencies {
    implementation(project(":framework:core"))

    testImplementation(libs.bundles.kotest)

    // Integration test dependencies (Story 6.1)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.postgresql)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.kotest.runner.junit5.jvm) // Required for custom source sets
}
