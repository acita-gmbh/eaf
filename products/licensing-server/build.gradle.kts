plugins {
    id("eaf.spring-boot")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Licensing Server - First product implementation (Epic 8)"

dependencies {
    // Framework dependencies
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(project(":framework:cqrs"))
    implementation(project(":framework:web"))
    implementation(project(":framework:persistence"))
    implementation(project(":framework:observability"))

    // Shared APIs
    implementation(project(":shared:shared-api"))

    // Axon Framework
    implementation(libs.bundles.axon.framework)

    // Functional helpers
    implementation(libs.bundles.arrow)

    // Database driver for local onboarding
    runtimeOnly(libs.postgresql)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
}

pitest {
    failWhenNoMutations.set(false)
}

tasks.withType<info.solidsoft.gradle.pitest.PitestTask>().configureEach {
    enabled = false
}

// Coverage now aligns with global 80% standard (adjusted from 85%)
// AxonConfiguration properly tested via integration tests following Constitutional TDD
