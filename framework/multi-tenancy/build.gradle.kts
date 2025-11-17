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

    // Ensure only JUnit 5 for Pitest
    configurations.named("testRuntimeClasspath") {
        exclude(group = "junit", module = "junit")
    }
}

// Pitest configuration
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?)
    junit5PluginVersion.set("1.2.1")
    useClasspathFile.set(true)
    verbose.set(true)
    outputFormats.set(setOf("XML", "HTML"))
}
