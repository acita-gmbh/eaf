package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * Convention plugin for testing in EAF.
 * Configures JUnit 6, AssertJ assertions, and Testcontainers integration tests.
 * Migrated from Kotest to JUnit 6 on 2025-11-20.
 */
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog =
            loadCatalog(
                target.rootProject.projectDir
                    .resolve("gradle/libs.versions.toml")
                    .toPath(),
            )

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("org.jetbrains.kotlin.plugin.serialization") // Keep for other serialization needs
            }

            // JUnit 6 Platform configuration
            tasks.named<Test>("test") {
                useJUnitPlatform()
                description = "Runs unit tests via JUnit 6 Jupiter"
                group = "verification"
            }

            tasks.named("check") {
                dependsOn("test")
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            // Story 1.2: Removed ciTest task - test task now uses JUnit Platform directly
            val integrationTest =
                sourceSets.create("integrationTest") {
                    compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                    runtimeClasspath += output + compileClasspath
                    java.srcDirs("src/integration-test/kotlin")
                }
            val konsistTest =
                sourceSets.create("konsistTest") {
                    compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                    runtimeClasspath += output + compileClasspath
                    java.srcDirs("src/konsist-test/kotlin")
                }

            // Story 8.3: Create dedicated performance test source set - ONLY for Nightly builds
            // Use providers API for configuration cache compatibility
            val isNightlyBuild = project.providers.gradleProperty("nightlyBuild").isPresent

            if (isNightlyBuild) {
                val perfTest =
                    sourceSets.create("perfTest") {
                        compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                        runtimeClasspath += output + compileClasspath
                        java.srcDirs("src/perf-test/kotlin")
                    }

                configurations.named("perfTestImplementation") {
                    extendsFrom(configurations.getByName("testImplementation"))
                }
                configurations.named("perfTestRuntimeOnly") {
                    extendsFrom(configurations.getByName("testRuntimeOnly"))
                }
            }

            configurations.named("integrationTestImplementation") {
                extendsFrom(configurations.getByName("testImplementation"))
            }
            configurations.named("integrationTestRuntimeOnly") {
                extendsFrom(configurations.getByName("testRuntimeOnly"))
            }
            configurations.named("konsistTestImplementation") {
                extendsFrom(configurations.getByName("testImplementation"))
            }
            configurations.named("konsistTestRuntimeOnly") {
                extendsFrom(configurations.getByName("testRuntimeOnly"))
            }

            dependencies {
                fun DependencyHandlerScope_addAll(
                    configuration: String,
                    aliases: List<String>,
                ) {
                    aliases.forEach { alias ->
                        val library = catalog.library(alias)
                        add(configuration, "${library.module}:${library.version}")
                    }
                }

                DependencyHandlerScope_addAll(
                    "testImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "mockk",
                        "kotlinx-serialization-json",
                        "kotlinx-serialization-core",
                        "konsist",
                    ),
                )

                // Add JUnit 6 engine for test execution
                val jupiterEngine = catalog.library("junit-jupiter-engine")
                add("testRuntimeOnly", "${jupiterEngine.module}:${jupiterEngine.version}")

                val platformLauncher = catalog.library("junit-platform-launcher")
                add("testRuntimeOnly", "${platformLauncher.module}:${platformLauncher.version}")

                val sharedTestingProject = rootProject.findProject(":shared:testing")
                if (sharedTestingProject != null) {
                    add("testImplementation", sharedTestingProject)
                }

                DependencyHandlerScope_addAll(
                    "integrationTestImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "mockk",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak",
                        "testcontainers-junit-jupiter",
                        "spring-boot-starter-security",
                        "spring-boot-starter-oauth2-resource-server",
                    ),
                )

                add("integrationTestRuntimeOnly", "${jupiterEngine.module}:${jupiterEngine.version}")
                add("integrationTestRuntimeOnly", "${platformLauncher.module}:${platformLauncher.version}")

                val testcontainersBom = catalog.library("testcontainers-bom")
                add("integrationTestImplementation", platform("${testcontainersBom.module}:${testcontainersBom.version}"))

                if (sharedTestingProject != null) {
                    add("integrationTestImplementation", sharedTestingProject)
                    add("integrationTestRuntimeOnly", sharedTestingProject)
                }

                DependencyHandlerScope_addAll(
                    "konsistTestImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "konsist",
                    ),
                )

                add("konsistTestRuntimeOnly", "${jupiterEngine.module}:${jupiterEngine.version}")
                add("konsistTestRuntimeOnly", "${platformLauncher.module}:${platformLauncher.version}")

                // Story 8.3: Performance test dependencies (similar to integration tests) - ONLY for Nightly builds
                if (isNightlyBuild) {
                    DependencyHandlerScope_addAll(
                        "perfTestImplementation",
                        listOf(
                            "kotlin-test",
                            "junit-jupiter-api",
                            "junit-jupiter-params",
                            "assertj-core",
                            "assertj-kotlin",
                            "mockk",
                            "testcontainers-postgresql",
                            "testcontainers-junit-jupiter",
                            "spring-boot-starter-security",
                        ),
                    )

                    add("perfTestRuntimeOnly", "${jupiterEngine.module}:${jupiterEngine.version}")
                    add("perfTestRuntimeOnly", "${platformLauncher.module}:${platformLauncher.version}")
                    add("perfTestImplementation", platform("${testcontainersBom.module}:${testcontainersBom.version}"))

                    if (sharedTestingProject != null) {
                        add("perfTestImplementation", sharedTestingProject)
                        add("perfTestRuntimeOnly", sharedTestingProject)
                    }
                }
            }

            // Create test tasks for custom source sets using JUnit 6 Platform
            val integrationTestTask =
                tasks.register("integrationTest", Test::class.java) {
                    description = "Runs integration tests with Testcontainers and JUnit 6."
                    group = "verification"

                    // Configuration cache compatible: capture values at configuration time
                    val integrationTestClassesDirs = integrationTest.output.classesDirs
                    val integrationTestClasspath = integrationTest.runtimeClasspath

                    testClassesDirs = integrationTestClassesDirs
                    classpath = integrationTestClasspath

                    useJUnitPlatform {
                        // Exclude performance tests from fast CI (run in nightly only)
                        excludeTags("Performance")
                    }

                    shouldRunAfter(tasks.named("test"))

                    doFirst {
                        try {
                            Class
                                .forName("com.axians.eaf.testing.containers.TestContainers")
                                .getDeclaredMethod("startAll")
                                .invoke(null)
                        } catch (ignored: ClassNotFoundException) {
                            // shared-testing module not on classpath; nothing to bootstrap
                        }
                    }
                }

            val konsistTestTask =
                tasks.register("konsistTest", Test::class.java) {
                    description = "Runs Konsist architecture and coding standards checks."
                    group = "verification"

                    // Configuration cache compatible: capture values at configuration time
                    val konsistTestClassesDirs = konsistTest.output.classesDirs
                    val konsistTestClasspath = konsistTest.runtimeClasspath

                    testClassesDirs = konsistTestClassesDirs
                    classpath = konsistTestClasspath

                    useJUnitPlatform()

                    shouldRunAfter(tasks.named("test"))
                }

            // Story 8.3: Performance test task for benchmark execution - ONLY for Nightly builds
            if (isNightlyBuild) {
                val perfTest = sourceSets.getByName("perfTest")
                val perfTestTask =
                    tasks.register("perfTest", Test::class.java) {
                        description = "Runs performance benchmark tests."
                        group = "verification"

                        // Configuration cache compatible: capture values at configuration time
                        val perfTestClassesDirs = perfTest.output.classesDirs
                        val perfTestClasspath = perfTest.runtimeClasspath

                        testClassesDirs = perfTestClassesDirs
                        classpath = perfTestClasspath

                        useJUnitPlatform()

                        shouldRunAfter(tasks.named("test"), integrationTestTask)

                        // Performance tests need Testcontainers
                        doFirst {
                            try {
                                Class
                                    .forName("com.axians.eaf.testing.containers.TestContainers")
                                    .getDeclaredMethod("startAll")
                                    .invoke(null)
                            } catch (ignored: ClassNotFoundException) {
                                // shared-testing module not on classpath
                            }
                        }
                    }
            }

            tasks.named("check") {
                dependsOn(integrationTestTask)
                dependsOn(konsistTestTask)
                // Note: perfTest not in check by default (opt-in for performance validation)
            }

            // All test tasks use JUnit 6 Platform directly via useJUnitPlatform()
            // Configure parallel test execution for all Test tasks
            tasks.withType<Test>().configureEach {
                // Parallel test execution: cores/2 (minimum 1)
                // CI gets 2 forks (2-core GitHub Actions runners)
                // Local gets 5 forks (10-core development machines)
                // Performance-sensitive tests run sequentially (maxParallelForks = 1)
                val isPerformanceTest = name.contains("perf", ignoreCase = true) ||
                                       name.contains("performance", ignoreCase = true) ||
                                       name.contains("benchmark", ignoreCase = true)

                maxParallelForks = if (isPerformanceTest) {
                    1  // Sequential execution for accurate performance measurements
                } else {
                    (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
                }

                // Fork new JVM every 100 tests to prevent memory leaks
                forkEvery = 100

                // JVM args for test processes (separate from Gradle daemon)
                jvmArgs(
                    "-Xmx1g",
                    "-XX:+UseParallelGC",
                    "-XX:MaxMetaspaceSize=512m"
                )

                // Improved test logging
                testLogging {
                    events("passed", "skipped", "failed")
                    showStandardStreams = false
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showCauses = true
                    showStackTraces = true
                }
            }

            afterEvaluate {
                enforceCatalogAlignment(
                    "testImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "mockk",
                        "konsist",
                    ),
                    catalog,
                )

                enforceCatalogAlignment(
                    "integrationTestImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "mockk",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak",
                        "testcontainers-junit-jupiter",
                    ),
                    catalog,
                )

                enforceCatalogAlignment(
                    "konsistTestImplementation",
                    listOf(
                        "kotlin-test",
                        "junit-jupiter-api",
                        "junit-jupiter-params",
                        "assertj-core",
                        "assertj-kotlin",
                        "konsist",
                    ),
                    catalog,
                )
            }
        }
    }
}

private fun Project.enforceCatalogAlignment(
    configurationName: String,
    aliases: List<String>,
    catalog: Catalog,
) {
    val configuration = configurations.findByName(configurationName) ?: return
    aliases.forEach { alias ->
        val expected = catalog.library(alias)
        val matches =
            configuration.dependencies
                .filterIsInstance<ExternalModuleDependency>()
                .filter { dep -> "${dep.group}:${dep.name}" == expected.module }

        require(matches.isNotEmpty()) {
            "Expected dependency ${expected.module} (alias '$alias') in configuration '$configurationName'."
        }

        matches.forEach { dependency ->
            val declaredVersion = dependency.versionConstraint.requiredVersion.takeIf { it.isNotBlank() }
            require(declaredVersion == expected.version) {
                val found = declaredVersion ?: "unspecified"
                "Version drift detected for ${expected.module} in configuration '$configurationName': found $found, expected ${expected.version}."
            }
        }
    }
}
