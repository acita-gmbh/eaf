plugins {
    id("eaf.kotlin-common")
    id("eaf.logging")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Observability Framework - Metrics, logging, tracing"

dependencies {
    implementation(project(":framework:core"))
    implementation(project(":framework:security"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.opentelemetry.sdk.testing)

    // Integration test dependencies
    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(project(":shared:testing"))
    integrationTestImplementation(libs.spring.boot.starter.security)
}

// Pitest configuration - override targetClasses and exclude JUnit 4
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?)
    junit5PluginVersion.set("1.2.1")
    useClasspathFile.set(true)
    verbose.set(true)
}
