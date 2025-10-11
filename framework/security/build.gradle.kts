plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test Source Set
// ============================================================================
sourceSets {
    create("propertyTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        java.srcDir("src/propertyTest/java")
        kotlin.srcDir("src/propertyTest/kotlin")
        resources.srcDir("src/propertyTest/resources")
    }
}

// Property test dependencies extend from test dependencies
val propertyTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val propertyTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

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
    integrationTestImplementation(libs.spring.security.test)
    integrationTestImplementation(libs.spring.boot.starter.test)
}

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test Task
// ============================================================================
val propertyTest =
    tasks.register<Test>("propertyTest") {
        group = "verification"
        description = "Runs property-based tests (nightly/main branch only)"
        testClassesDirs = sourceSets["propertyTest"].output.classesDirs
        classpath = sourceSets["propertyTest"].runtimeClasspath
        useJUnitPlatform {
            // Only run tests tagged with @PBT
            includeTags("PBT")
        }
    }

// Exclude PBT from default test task (fast feedback loop)
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("PBT")
    }
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

    // Story 8.6: Exclude property-based tests from mutation testing
    // PBT + mutation testing = exponential time complexity
    excludedTestClasses.set(setOf("*PropertyTest", "*PropertySpec"))
}
