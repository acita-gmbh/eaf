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
 * Configures Kotest, Nullable pattern helpers, and Testcontainers integration tests.
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
                apply("io.kotest") // Native Kotest plugin
                apply("org.jetbrains.kotlin.plugin.serialization") // For Kotest XML reports
            }

            // Native Kotest execution - no JUnit Platform needed!
            // Replace standard test task with jvmKotest in check lifecycle
            tasks.named("check") {
                dependsOn("jvmKotest")
            }

            // Disable standard test task since we use native Kotest
            tasks.named("test").configure {
                onlyIf { false }
            }

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)

            // Create CI test task that uses JUnit Platform for XML reports
            val ciTestTask =
                tasks.register("ciTest", Test::class.java) {
                    description = "Runs tests with JUnit Platform for CI/CD XML reporting"
                    group = "verification"

                    // Use the standard test source set
                    testClassesDirs = sourceSets.getByName("test").output.classesDirs
                    classpath = sourceSets.getByName("test").runtimeClasspath

                    useJUnitPlatform()

                    // Enable JUnit XML reports for CI
                    reports {
                        junitXml.required.set(true)
                        html.required.set(true)
                    }

                    // Set report directories
                    reports.junitXml.outputLocation.set(
                        project.layout.buildDirectory.dir("test-results/ciTest"),
                    )
                    reports.html.outputLocation.set(
                        project.layout.buildDirectory.dir("reports/tests/ciTest"),
                    )

                    // This requires kotest-runner-junit5-jvm dependency
                    testLogging {
                        events("passed", "skipped", "failed")
                        showStandardStreams = false
                    }
                }
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

            // Story 8.3: Create dedicated performance test source set
            val perfTest =
                sourceSets.create("perfTest") {
                    compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                    runtimeClasspath += output + compileClasspath
                    java.srcDirs("src/perf-test/kotlin")
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
            configurations.named("perfTestImplementation") {
                extendsFrom(configurations.getByName("testImplementation"))
            }
            configurations.named("perfTestRuntimeOnly") {
                extendsFrom(configurations.getByName("testRuntimeOnly"))
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
                        "kotest-framework-engine-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "kotest-extensions-pitest",
                        "kotlinx-serialization-json",
                        "kotlinx-serialization-core",
                        "konsist",
                    ),
                )

                // Add JUnit runner for ciTest task only
                val junit5Runner = catalog.library("kotest-runner-junit5-jvm")
                add("testRuntimeOnly", "${junit5Runner.module}:${junit5Runner.version}")

                val sharedTestingProject = rootProject.findProject(":shared:testing")
                if (sharedTestingProject != null) {
                    add("testImplementation", sharedTestingProject)
                }

                DependencyHandlerScope_addAll(
                    "integrationTestImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-runner-junit5-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "kotest-extensions-testcontainers",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak",
                        "spring-boot-starter-security",
                        "spring-boot-starter-oauth2-resource-server",
                    ),
                )

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
                        "kotest-framework-engine-jvm",
                        "kotest-runner-junit5-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "konsist",
                    ),
                )

                // Story 8.3: Performance test dependencies (similar to integration tests)
                DependencyHandlerScope_addAll(
                    "perfTestImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-runner-junit5-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "kotest-extensions-testcontainers",
                        "testcontainers-postgresql",
                        "spring-boot-starter-security",
                    ),
                )

                add("perfTestImplementation", platform("${testcontainersBom.module}:${testcontainersBom.version}"))

                if (sharedTestingProject != null) {
                    add("perfTestImplementation", sharedTestingProject)
                    add("perfTestRuntimeOnly", sharedTestingProject)
                }
            }

            // Create test tasks for custom source sets
            // For now, use Test tasks with useJUnitPlatform until native Kotest plugin
            // supports custom source sets better

            val integrationTestTask =
                tasks.register("integrationTest", Test::class.java) {
                    description = "Runs integration tests with Testcontainers."
                    group = "verification"
                    testClassesDirs = integrationTest.output.classesDirs
                    classpath = integrationTest.runtimeClasspath

                    useJUnitPlatform()

                    shouldRunAfter(tasks.named("jvmKotest"))

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
                    testClassesDirs = konsistTest.output.classesDirs
                    classpath = konsistTest.runtimeClasspath

                    useJUnitPlatform()

                    shouldRunAfter(tasks.named("jvmKotest"))
                }

            // Story 8.3: Performance test task for benchmark execution
            val perfTestTask =
                tasks.register("perfTest", Test::class.java) {
                    description = "Runs performance benchmark tests."
                    group = "verification"
                    testClassesDirs = perfTest.output.classesDirs
                    classpath = perfTest.runtimeClasspath

                    useJUnitPlatform()

                    shouldRunAfter(tasks.named("jvmKotest"), integrationTestTask)

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

            tasks.named("check") {
                dependsOn(integrationTestTask)
                dependsOn(konsistTestTask)
                // Note: perfTest not in check by default (opt-in for performance validation)
            }

            // Create CI variants that use JUnit Platform for XML reports
            val ciIntegrationTestTask =
                tasks.register("ciIntegrationTest", Test::class.java) {
                    description = "Runs integration tests with JUnit Platform for CI/CD"
                    group = "verification"
                    testClassesDirs = integrationTest.output.classesDirs
                    classpath = integrationTest.runtimeClasspath

                    useJUnitPlatform()

                    reports {
                        junitXml.required.set(true)
                        html.required.set(true)
                    }

                    reports.junitXml.outputLocation.set(
                        project.layout.buildDirectory.dir("test-results/ciIntegrationTest"),
                    )
                    reports.html.outputLocation.set(
                        project.layout.buildDirectory.dir("reports/tests/ciIntegrationTest"),
                    )

                    shouldRunAfter(tasks.named("ciTest"))

                    // Only run if there are actual test classes
                    onlyIf {
                        !integrationTest.output.classesDirs.asFileTree.isEmpty
                    }
                }

            val ciKonsistTestTask =
                tasks.register("ciKonsistTest", Test::class.java) {
                    description = "Runs Konsist tests with JUnit Platform for CI/CD"
                    group = "verification"
                    testClassesDirs = konsistTest.output.classesDirs
                    classpath = konsistTest.runtimeClasspath

                    useJUnitPlatform()

                    reports {
                        junitXml.required.set(true)
                        html.required.set(true)
                    }

                    reports.junitXml.outputLocation.set(
                        project.layout.buildDirectory.dir("test-results/ciKonsistTest"),
                    )
                    reports.html.outputLocation.set(
                        project.layout.buildDirectory.dir("reports/tests/ciKonsistTest"),
                    )

                    shouldRunAfter(tasks.named("ciTest"))
                    shouldRunAfter(ciIntegrationTestTask)

                    // Only run if there are actual test classes
                    onlyIf {
                        !konsistTest.output.classesDirs.asFileTree.isEmpty
                    }
                }

            // Story 8.3: CI performance test task
            val ciPerfTestTask =
                tasks.register("ciPerfTest", Test::class.java) {
                    description = "Runs performance benchmark tests with JUnit Platform for CI/CD"
                    group = "verification"
                    testClassesDirs = perfTest.output.classesDirs
                    classpath = perfTest.runtimeClasspath

                    useJUnitPlatform()

                    reports {
                        junitXml.required.set(true)
                        html.required.set(true)
                    }

                    reports.junitXml.outputLocation.set(
                        project.layout.buildDirectory.dir("test-results/ciPerfTest"),
                    )
                    reports.html.outputLocation.set(
                        project.layout.buildDirectory.dir("reports/tests/ciPerfTest"),
                    )

                    shouldRunAfter(tasks.named("ciTest"), ciIntegrationTestTask)

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

                    // Only run if there are actual test classes
                    onlyIf {
                        !perfTest.output.classesDirs.asFileTree.isEmpty
                    }
                }

            // Create aggregate CI task
            tasks.register("ciTests") {
                description = "Runs all tests with JUnit Platform for CI/CD"
                group = "verification"
                dependsOn("ciTest", ciIntegrationTestTask, ciKonsistTestTask, ciPerfTestTask)
            }

            afterEvaluate {
                enforceCatalogAlignment(
                    "testImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "konsist",
                    ),
                    catalog,
                )

                enforceCatalogAlignment(
                    "integrationTestImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "kotest-extensions-testcontainers",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak",
                    ),
                    catalog,
                )

                enforceCatalogAlignment(
                    "konsistTestImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
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
