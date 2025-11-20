package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Story 2.1: Modernized testing convention plugin - V2 SIMPLIFIED APPROACH
 *
 * PIVOT RATIONALE:
 * After debugging jvm-test-suite plugin, discovered that the API is incomplete/broken
 * in binary convention plugins (Gradle 9.1). Methods like useJUnitPlatform() are unresolved.
 *
 * V2 IMPROVEMENTS OVER V1 (TestingConventionPlugin):
 * 1. ✅ Consolidated test suites: test, integrationTest, nightlyTest (3 instead of 6)
 * 2. ✅ Removed ci* task variants (40% task reduction from Phase 1)
 * 3. ✅ Simplified conditional logic (nightlyTest replaces konsist + perf)
 * 4. ✅ Better providers API usage (configuration cache friendly)
 * 5. ✅ Cleaner dependency management
 * 6. ✅ Reduced code complexity (~30% smaller than v1)
 *
 * TEST SUITES:
 * - test: Unit tests + architecture tests (JUnit 6 + AssertJ + Konsist)
 * - integrationTest: Testcontainers integration tests
 * - nightlyTest: Long-running tests (property, fuzz, concurrency, mutation, perf)
 */
class EafTestingV2Plugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(
            target.rootProject.projectDir
                .resolve("gradle/libs.versions.toml")
                .toPath()
        )

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            // ═══════════════════════════════════════════════════════════════
            // SUITE 1: test (Unit + Architecture)
            // ═══════════════════════════════════════════════════════════════
            tasks.named<Test>("test") {
                useJUnitPlatform()
                description = "Runs unit and architecture tests via JUnit Platform"
                group = "verification"
            }

            tasks.named("check") {
                dependsOn("test")
            }

            // ═══════════════════════════════════════════════════════════════
            // SUITE 2: integrationTest (Testcontainers)
            // ═══════════════════════════════════════════════════════════════
            val integrationTest = sourceSets.create("integrationTest") {
                compileClasspath += sourceSets.getByName("main").output +
                    sourceSets.getByName("test").output
                runtimeClasspath += output + compileClasspath
                java.srcDirs("src/integration-test/kotlin")
            }

            configurations.named("integrationTestImplementation") {
                extendsFrom(configurations.getByName("testImplementation"))
            }
            configurations.named("integrationTestRuntimeOnly") {
                extendsFrom(configurations.getByName("testRuntimeOnly"))
            }

            val integrationTestTask = tasks.register("integrationTest", Test::class.java) {
                description = "Runs integration tests with Testcontainers"
                group = "verification"

                val integrationTestClassesDirs = integrationTest.output.classesDirs
                val integrationTestClasspath = integrationTest.runtimeClasspath

                testClassesDirs = integrationTestClassesDirs
                classpath = integrationTestClasspath

                useJUnitPlatform {
                    excludeTags("Performance", "Nightly")
                }

                shouldRunAfter(tasks.named("test"))

                doFirst {
                    try {
                        Class.forName("com.axians.eaf.testing.containers.TestContainers")
                            .getDeclaredMethod("startAll")
                            .invoke(null)
                    } catch (ignored: ClassNotFoundException) {
                        // shared-testing module not on classpath
                    }
                }
            }

            tasks.named("check") {
                dependsOn(integrationTestTask)
            }

            // ═══════════════════════════════════════════════════════════════
            // SUITE 3: nightlyTest (Nightly builds only - framework modules)
            // ═══════════════════════════════════════════════════════════════
            val isFrameworkModule = project.path.startsWith(":framework:") ||
                project.path.startsWith(":shared:")
            val isNightlyBuild = project.providers.gradleProperty("nightlyBuild").isPresent

            if (isFrameworkModule && isNightlyBuild) {
                val nightlyTest = sourceSets.create("nightlyTest") {
                    compileClasspath += sourceSets.getByName("main").output +
                        sourceSets.getByName("test").output
                    runtimeClasspath += output + compileClasspath
                    java.srcDirs("src/nightly-test/kotlin")
                }

                configurations.named("nightlyTestImplementation") {
                    extendsFrom(configurations.getByName("testImplementation"))
                }
                configurations.named("nightlyTestRuntimeOnly") {
                    extendsFrom(configurations.getByName("testRuntimeOnly"))
                }

                tasks.register("nightlyTest", Test::class.java) {
                    description = "Runs nightly tests (property, fuzz, concurrency, mutation, perf)"
                    group = "verification"

                    val nightlyTestClassesDirs = nightlyTest.output.classesDirs
                    val nightlyTestClasspath = nightlyTest.runtimeClasspath

                    testClassesDirs = nightlyTestClassesDirs
                    classpath = nightlyTestClasspath

                    useJUnitPlatform {
                        includeTags("Nightly", "Property", "Fuzz", "Concurrency", "Mutation", "Performance")
                    }

                    shouldRunAfter(tasks.named("test"), integrationTestTask)

                    // Nightly tests run sequentially for accurate measurements
                    maxParallelForks = 1

                    doFirst {
                        try {
                            Class.forName("com.axians.eaf.testing.containers.TestContainers")
                                .getDeclaredMethod("startAll")
                                .invoke(null)
                        } catch (ignored: ClassNotFoundException) {
                            // shared-testing module not on classpath
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // DEPENDENCIES
            // ═══════════════════════════════════════════════════════════════
            dependencies {
                fun DependencyHandlerScope_addAll(
                    configuration: String,
                    aliases: List<String>
                ) {
                    aliases.forEach { alias ->
                        val library = catalog.library(alias)
                        add(configuration, "${library.module}:${library.version}")
                    }
                }

                // Base test dependencies (all suites extend from this)
                DependencyHandlerScope_addAll(
                    "testImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "awaitility-kotlin",
                        "mockk",
                        "kotlinx-serialization-json",
                        "kotlinx-serialization-core",
                        "konsist"
                    )
                )

                val junitEngine = catalog.library("junit-jupiter-engine")
                add("testRuntimeOnly", "${junitEngine.module}:${junitEngine.version}")

                val sharedTestingProject = rootProject.findProject(":shared:testing")
                if (sharedTestingProject != null) {
                    add("testImplementation", sharedTestingProject)
                }

                // Integration test specific dependencies
                DependencyHandlerScope_addAll(
                    "integrationTestImplementation",
                    listOf(
                        "testcontainers-postgresql",
                        "testcontainers-keycloak",
                        "spring-boot-starter-security",
                        "spring-boot-starter-oauth2-resource-server"
                    )
                )

                val testcontainersBom = catalog.library("testcontainers-bom")
                add(
                    "integrationTestImplementation",
                    platform("${testcontainersBom.module}:${testcontainersBom.version}")
                )

                if (sharedTestingProject != null) {
                    add("integrationTestImplementation", sharedTestingProject)
                    add("integrationTestRuntimeOnly", sharedTestingProject)
                }

                // Nightly test dependencies (if applicable)
                if (isFrameworkModule && isNightlyBuild) {
                    DependencyHandlerScope_addAll(
                        "nightlyTestImplementation",
                        listOf(
                            "testcontainers-postgresql",
                            "spring-boot-starter-security"
                        )
                    )

                    add(
                        "nightlyTestImplementation",
                        platform("${testcontainersBom.module}:${testcontainersBom.version}")
                    )

                    if (sharedTestingProject != null) {
                        add("nightlyTestImplementation", sharedTestingProject)
                        add("nightlyTestRuntimeOnly", sharedTestingProject)
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // GLOBAL TEST CONFIGURATION
            // ═══════════════════════════════════════════════════════════════
            tasks.withType<Test>().configureEach {
                val isNightlyTask = name.contains("nightly", ignoreCase = true)

                maxParallelForks = if (isNightlyTask) {
                    1 // Sequential for accurate measurements
                } else {
                    (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                }

                forkEvery = 100

                jvmArgs(
                    "-Xmx1g",
                    "-XX:+UseParallelGC",
                    "-XX:MaxMetaspaceSize=512m"
                )

                testLogging {
                    events("passed", "skipped", "failed")
                    showStandardStreams = false
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
            }
        }
    }
}
