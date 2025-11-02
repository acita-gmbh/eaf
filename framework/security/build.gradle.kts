plugins {
    id("eaf.kotlin-common")
    id("eaf.observability")
    id("eaf.testing")
    id("eaf.quality-gates")
}

description = "EAF Security Framework - 10-layer JWT validation and tenant isolation"

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test and Fuzz Test Source Sets
// ============================================================================
sourceSets {
    create("propertyTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        java.srcDir("src/propertyTest/java")
        kotlin.srcDir("src/propertyTest/kotlin")
        resources.srcDir("src/propertyTest/resources")
    }

    create("fuzzTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
        java.srcDir("src/fuzzTest/java")
        kotlin.srcDir("src/fuzzTest/kotlin")
        resources.srcDir("src/fuzzTest/resources")
    }
}

// Property test dependencies extend from test dependencies
val propertyTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val propertyTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// Fuzz test dependencies
val fuzzTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val fuzzTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
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
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.jose4j)
    implementation(libs.jooq.core) // Story 9.2: Required for TenantDatabaseSessionInterceptor transaction participation

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

    // Story 8.6: Fuzz testing with Jazzer (Google OSS-Fuzz standard)
    fuzzTestImplementation(libs.jazzer.junit)
    fuzzTestImplementation(libs.jazzer.api)
}

// ============================================================================
// Story 8.6: Multi-Stage Testing - Property Test and Fuzz Test Tasks
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

val fuzzTest =
    tasks.register<Test>("fuzzTest") {
        group = "verification"
        description = "Runs Jazzer fuzz tests (nightly/security validation)"
        testClassesDirs = sourceSets["fuzzTest"].output.classesDirs
        classpath = sourceSets["fuzzTest"].runtimeClasspath
        useJUnitPlatform()

        // Story 8.8: Jazzer corpus and fuzzing configuration
        // CRITICAL LIMITATION: Jazzer 0.24.0 with JAZZER_FUZZ=1 runs ONLY ONE @FuzzTest per invocation
        //                      Even filtering by class (--tests "...ClassName") runs only 1 method from that class!
        // WORKAROUND: GitHub Actions workflow invokes this task 7 times (once per @FuzzTest method):
        //             1. JwtFormatFuzzer.fuzzJwtBasicFormatValidation (5min)
        //             2. TokenExtractorFuzzer.fuzzTokenExtraction (5min)
        //             3. TokenExtractorFuzzer.fuzzTokenExtractionWithNull (5min)
        //             4. RoleNormalizationFuzzer.fuzzRoleNormalization (5min)
        //             5. RoleNormalizationFuzzer.fuzzRoleNormalizationWithNull (5min)
        //             6. RoleNormalizationFuzzer.fuzzRoleNormalizationInjectionPatterns (5min)
        //             7. RoleNormalizationFuzzer.fuzzRoleNormalizationUnicodeAttacks (5min)
        //             Total: 7 methods × 5min = ~35-40 minutes with Gradle overhead
        // CORPUS DIRECTORY: Jazzer uses .cifuzz-corpus/<package>/<class>/<method>/ (cannot be overridden)
        // CORPUS CACHING: GitHub Actions caches with run_id-based keys for incremental fuzzing

        // Configure Jazzer for fuzzing mode (not just test mode)
        systemProperty("jazzer.instrumentation_includes", "com.axians.eaf.**")
        environment("JAZZER_FUZZ", "1")

        // Story 8.8: Jazzer time limits (EMPIRICAL FINDINGS)
        // DISCOVERY: System properties (jazzer.max_duration, jazzer.max_total_time, jazzer.flags)
        //            are NOT read by Jazzer 0.24.0 in JUnit integration mode
        // PROVEN: Only @FuzzTest annotation maxDuration parameter controls fuzzing duration
        // LIMITATION: Annotation values are static - cannot be overridden at runtime via -D flags
        // SOLUTION: Rely on @FuzzTest annotation defaults (5m per test) + GitHub Actions timeout (50m)
        // VALIDATION: Empirical tests confirmed:
        //   - @FuzzTest(maxDuration="15s") → stopped at 16s ✅
        //   - @FuzzTest default "5m" → stopped at 301s ✅
        //   - Property jazzer.max_duration=20s → ignored, ran until Gradle timeout ❌
        //   - JAZZER_FUZZ=1 → only ONE @FuzzTest executes per invocation (even within same class) ✅
        // See: .ai/jazzer-flags-research-prompt.md for multi-agent research findings
        // See: https://github.com/CodeIntelligenceTesting/jazzer/issues/599 (Jazzer design limitation)
        // See: Empirical validation from nightly runs #18453404174 (3/7 tests ran when filtering by class)
    }

// Exclude PBT from default test task (fast feedback loop)
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("PBT")
    }
}

// Story 8.7: Exclude advanced tests from Kover coverage in CI
// propertyTest and fuzzTest should ONLY run in nightly pipeline, not PR pipeline
// Kover automatically depends on ALL Test tasks - we must explicitly exclude nightly-only tests
tasks.named("koverXmlReport") {
    // Prevent Kover from triggering nightly-only test tasks during CI
    mustRunAfter("propertyTest", "fuzzTest")
}
tasks.named("koverVerify") {
    // Prevent Kover from triggering nightly-only test tasks during CI
    mustRunAfter("propertyTest", "fuzzTest")
}

// Helper function to determine if nightly-only tests should run
// Handles both bare task names ("propertyTest") and qualified paths (":framework:security:propertyTest")
// Returns true when: (1) nightlyBuild property set, OR (2) task explicitly requested by developer
fun shouldRunNightlyTest(taskName: String): Boolean =
    project.hasProperty("nightlyBuild") ||
        project.gradle.startParameter.taskNames
            .any { it.substringAfterLast(':') == taskName }

// Explicitly mark nightly-only tests to not run automatically
tasks.named("propertyTest") {
    onlyIf { shouldRunNightlyTest("propertyTest") }
}
tasks.named("fuzzTest") {
    onlyIf { shouldRunNightlyTest("fuzzTest") }
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
