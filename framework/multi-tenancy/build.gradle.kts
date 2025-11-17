plugins {
    id("eaf.kotlin-common")
    id("eaf.testing-v2")
    id("eaf.quality-gates")
}

description = "EAF Multi-Tenancy Framework - ThreadLocal tenant context and 3-layer isolation"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Framework dependencies
    implementation(libs.spring.boot.starter.web) // For Filter, HttpServletRequest/Response
    implementation(libs.jakarta.servlet.api) // For Filter interface
    implementation(libs.spring.security.core) // For SecurityContextHolder (lightweight)
    // For JwtAuthenticationToken, JWT (includes security-core transitively)
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    // Axon Framework (for TenantValidationInterceptor - Story 4.3)
    implementation(libs.bundles.axon.framework)

    // Metrics
    implementation(libs.micrometer.core) // For MeterRegistry, Timer, Counter
    implementation(libs.spring.boot.starter.actuator) // Provides MeterRegistry auto-configuration

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    // Testing dependencies
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "junit", module = "junit") // Exclude JUnit 4
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation(project(":shared:testing"))

    // Integration test dependencies
    // Required for JWT validation in TenantContextFilter tests
    integrationTestImplementation(project(":framework:security"))

    // Ensure only JUnit 5 for Pitest
    configurations.named("testRuntimeClasspath") {
        exclude(group = "junit", module = "junit")
    }
}

// Pitest configuration
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    junit5PluginVersion.set("1.2.1")
    useClasspathFile.set(true)
    verbose.set(true)
    outputFormats.set(setOf("XML", "HTML"))
}
