plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing-v2") // Story 2.2: Migrated to v2
    id("eaf.quality-gates")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test and Fuzz Test Source Sets
// ============================================================================
// CRITICAL: Only create these source-sets for Nightly builds to avoid CI overhead
val isNightlyBuild = project.hasProperty("nightlyBuild")

if (isNightlyBuild) {
    // CRITICAL: Configuration cache compatibility - lazy evaluation with afterEvaluate
    afterEvaluate {
        val mainOutput = sourceSets.main.get().output
        val testOutput = sourceSets.test.get().output
        val integrationTestOutput = sourceSets.getByName("integrationTest").output

        sourceSets {
            named("propertyTest") {
                compileClasspath += mainOutput + testOutput
                runtimeClasspath += mainOutput + testOutput
            }

            named("fuzzTest") {
                compileClasspath += mainOutput + testOutput
                runtimeClasspath += mainOutput + testOutput
            }

            // nightlyTest source-set (created by eaf.testing convention plugin) needs integrationTest source-set access
            // SecurityTestApplication is in integrationTest, not test
            named("nightlyTest") {
                compileClasspath += integrationTestOutput
                runtimeClasspath += integrationTestOutput
            }
        }
    }
}

// Source directories defined immediately (not deferred) - only for Nightly builds
if (isNightlyBuild) {
    sourceSets {
        create("propertyTest") {
            java.srcDir("src/propertyTest/java")
            kotlin.srcDir("src/propertyTest/kotlin")
            resources.srcDir("src/propertyTest/resources")
        }

        create("fuzzTest") {
            java.srcDir("src/fuzzTest/java")
            kotlin.srcDir("src/fuzzTest/kotlin")
            resources.srcDir("src/fuzzTest/resources")
        }
    }
}

dependencies {
    implementation(project(":framework:core"))
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.arrow)

    // Spring Modulith for module boundary enforcement
    implementation(libs.spring.modulith.api)
    testImplementation(libs.spring.modulith.test)

    implementation(libs.bundles.spring.boot.security)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.redis)
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.jose4j)
    implementation(libs.jooq.core) // Story 9.2: Required for TenantDatabaseSessionInterceptor transaction participation

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "junit", module = "junit") // Exclude JUnit 4
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine") // Exclude JUnit 4 engine
    }
    testImplementation(libs.bundles.testcontainers)
    // Story 3.3: keycloak-admin-client provided transitively by testcontainers-keycloak
    testImplementation(project(":shared:testing"))
    integrationTestImplementation(project(":shared:testing"))

    // Ensure only JUnit 5 for Pitest
    configurations.named("testRuntimeClasspath") {
        exclude(group = "junit", module = "junit")
    }

    // Integration test specific dependencies for security framework
    integrationTestImplementation(libs.bundles.testcontainers)
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
    integrationTestImplementation(libs.spring.boot.testcontainers)
    integrationTestImplementation(libs.spring.security.test)
    integrationTestImplementation(libs.spring.boot.starter.test)
}

// Property test, fuzz test, and performance test dependencies - only for Nightly builds
if (isNightlyBuild) {
    configurations.named("propertyTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    configurations.named("propertyTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }

    configurations.named("fuzzTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    configurations.named("fuzzTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }

    dependencies {
        // Story 8.6: Fuzz testing with Jazzer (Google OSS-Fuzz standard)
        "fuzzTestImplementation"(libs.jazzer.junit)
        "fuzzTestImplementation"(libs.jazzer.api)

        // Performance test dependencies (same as integration tests)
        "nightlyTestImplementation"(libs.spring.boot.starter.test)
        "nightlyTestImplementation"(libs.bundles.testcontainers)
        // Required for @Testcontainers, @Container annotations
        "nightlyTestImplementation"(libs.testcontainers.junit.jupiter)
        "nightlyTestImplementation"(project(":shared:testing"))
    }
}

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test and Fuzz Test Tasks
// ============================================================================
// CRITICAL: Only register tasks for Nightly builds to avoid CI overhead
if (isNightlyBuild) {
    // Helper function to determine if nightly-only tests should run
    fun shouldRunNightlyTest(taskName: String): Boolean =
        project.hasProperty("nightlyBuild") ||
            project.gradle.startParameter.taskNames
                .any { it.substringAfterLast(':') == taskName }

    afterEvaluate {
        tasks.register<Test>("propertyTest") {
            group = "verification"
            description = "Runs property-based tests (nightly/main branch only)"
            testClassesDirs = sourceSets["propertyTest"].output.classesDirs
            classpath = sourceSets["propertyTest"].runtimeClasspath
            useJUnitPlatform {
                // Only run tests tagged with @PBT
                includeTags("PBT")
            }
            // Nightly-only execution
            onlyIf { shouldRunNightlyTest("propertyTest") }
        }

        tasks.register<Test>("fuzzTest") {
            group = "verification"
            description = "Runs Jazzer fuzz tests (nightly/security validation)"
            testClassesDirs = sourceSets["fuzzTest"].output.classesDirs
            classpath = sourceSets["fuzzTest"].runtimeClasspath
            useJUnitPlatform()

            // Story 8.8: Jazzer corpus and fuzzing configuration
            // CRITICAL LIMITATION: Jazzer 0.24.0 with JAZZER_FUZZ=1 runs ONLY ONE @FuzzTest
            // per invocation. Even filtering by class runs only 1 method from that class!
            // WORKAROUND: GitHub Actions workflow invokes this task 7 times (once per method)
            // Total: 7 methods × 5min = ~35-40 minutes with Gradle overhead

            // Configure Jazzer for fuzzing mode (not just test mode)
            systemProperty("jazzer.instrumentation_includes", "com.axians.eaf.**")
            environment("JAZZER_FUZZ", "1")

            // Story 8.8: Jazzer time limits (EMPIRICAL FINDINGS)
            // DISCOVERY: System properties are NOT read by Jazzer 0.24.0
            // PROVEN: Only @FuzzTest annotation maxDuration parameter controls duration
            // LIMITATION: Annotation values cannot be overridden at runtime

            // Nightly-only execution
            onlyIf { shouldRunNightlyTest("fuzzTest") }
        }

        // Exclude PBT from default test task (fast feedback loop)
        tasks.named<Test>("test") {
            useJUnitPlatform {
                excludeTags("PBT")
            }
        }

        // Story 8.7: Prevent Kover from triggering nightly-only test tasks
        tasks.named("koverXmlReport") {
            mustRunAfter("propertyTest", "fuzzTest")
        }
        tasks.named("koverVerify") {
            mustRunAfter("propertyTest", "fuzzTest")
        }
    }
}

// Pitest configuration - override targetClasses and configure for JUnit 6
configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    targetClasses.set(setOf("com.axians.eaf.*"))
    targetTests.set(setOf("com.axians.eaf.*"))
    testPlugin.set(null as String?) // Disable deprecated testPlugin
    junit5PluginVersion.set("1.2.1") // JUnit 6 runs on JUnit Platform (junit5PluginVersion refers to platform, not Jupiter version)
    useClasspathFile.set(true)
    verbose.set(true)
    outputFormats.set(setOf("XML", "HTML"))

    // Story 8.6: Exclude property-based tests from mutation testing
    // PBT + mutation testing = exponential time complexity
    excludedTestClasses.set(setOf("*PropertyTest", "*PropertySpec"))
}

// ============================================================================
// Performance-Critical Test Configuration
// ============================================================================
// Security tests validate JWT performance (<50ms requirement in AC6)
// Parallel test execution increases CPU contention → invalidates performance measurements
// Solution: Run all security tests sequentially for accurate performance validation
tasks.withType<Test>().configureEach {
    maxParallelForks = 1 // Sequential execution for accurate JWT performance measurements
}
