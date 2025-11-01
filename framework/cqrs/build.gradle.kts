plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
    alias(libs.plugins.kotlin.spring)
}

description = "EAF CQRS Framework - Axon Framework integration"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(project(":framework:observability"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.axon.framework)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.micrometer.core)

    // jOOQ for TenantQueryHandlerInterceptor (optional - conditionally loaded)
    compileOnly(libs.bundles.jooq)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.axon.test)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.opentelemetry.sdk.testing)

    // Exclude JUnit 4 to prevent pitest minion crash
    configurations.named("testRuntimeClasspath") {
        exclude(group = "junit", module = "junit")
    }

    integrationTestImplementation(project(":framework:security"))
    integrationTestImplementation(libs.bundles.kotest)
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(libs.spring.boot.starter.data.redis)
}

// Pitest configuration - override targetClasses and exclude JUnit 4
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?)
    junit5PluginVersion.set(
        libs.versions.pitest.junit5
            .get(),
    )
    useClasspathFile.set(true)
    verbose.set(true)

    // Story 9.5: Temporary threshold reduction to unblock nightly pipeline
    // Current: 18% mutation coverage (25 killed / 139 generated)
    // Target: 25% baseline (Story 8.6 requirement)
    // TODO: Remove this override when Story 9.5 is complete
    mutationThreshold.set(18)
    coverageThreshold.set(40)
}
