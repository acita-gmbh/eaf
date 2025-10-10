plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.spring.boot.security)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.jose4j)

    testImplementation(libs.bundles.kotest)
    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "junit", module = "junit") // Exclude JUnit 4
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine") // Exclude JUnit 4 engine
    }
    testImplementation(libs.bundles.testcontainers)
    testImplementation(project(":shared:testing"))

    // Ensure only JUnit 5 for Pitest
    configurations.named("testRuntimeClasspath") {
        exclude(group = "junit", module = "junit")
    }

    // Integration test specific dependencies for security framework
    integrationTestImplementation("org.springframework.security:spring-security-test:6.5.5")
    integrationTestImplementation(libs.spring.boot.starter.test)
}

// Pitest configuration - override targetClasses and configure for Kotest
// Per Kotest docs: With PIT 1.6.7+, kotest-extensions-pitest on classpath is enough
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?) // Disable deprecated testPlugin
    junit5PluginVersion.set("1.2.1") // Use JUnit 5 platform (Kotest runs on JUnit Platform)
    useClasspathFile.set(true)
    verbose.set(true)
    outputFormats.set(setOf("XML", "HTML"))
}
