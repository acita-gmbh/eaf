plugins {
    id("eaf.testing") // FIRST - Kotest DSL before Spring Boot (Story 4.6)
    id("eaf.spring-boot") // SECOND - After Kotest established
    id("eaf.observability")
    id("eaf.quality-gates")
}

dependencyManagement {
    imports {
        mavenBom("io.opentelemetry:opentelemetry-bom:1.54.1")
    }
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

    // OpenTelemetry for observability
    implementation(libs.opentelemetry.instrumentation.spring.boot)

    // Database driver for local onboarding
    runtimeOnly(libs.postgresql)

    // Testing - Kotest-only policy (JUnit explicitly excluded)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
    testImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.junit.jupiter", module = "junit-jupiter")
    }
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
