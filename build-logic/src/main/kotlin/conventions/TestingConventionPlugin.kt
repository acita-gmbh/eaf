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
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("io.kotest")
            }

            // Configure native Kotest test execution (no JUnit Platform needed)
            // Kotest 6.0 native plugin handles test execution directly

            val sourceSets = extensions.getByType(SourceSetContainer::class.java)
            val integrationTest = sourceSets.create("integrationTest") {
                compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                runtimeClasspath += output + compileClasspath
            }
            val konsistTest = sourceSets.create("konsistTest") {
                compileClasspath += sourceSets.getByName("main").output + sourceSets.getByName("test").output
                runtimeClasspath += output + compileClasspath
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
                fun DependencyHandlerScope_addAll(configuration: String, aliases: List<String>) {
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
                        "konsist"
                    )
                )

                val sharedTestingProject = rootProject.findProject(":shared:testing")
                if (sharedTestingProject != null) {
                    add("testImplementation", sharedTestingProject)
                }

                DependencyHandlerScope_addAll(
                    "integrationTestImplementation",
                    listOf(
                        "kotlin-test",
                        "kotest-framework-engine-jvm",
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "testcontainers-junit-jupiter",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak"
                    )
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
                        "kotest-assertions-core-jvm",
                        "kotest-property-jvm",
                        "kotest-extensions-spring",
                        "konsist"
                    )
                )
            }

            val integrationTestTask = tasks.register<Test>("integrationTest") {
                description = "Runs integration tests with Testcontainers."
                group = "verification"
                testClassesDirs = integrationTest.output.classesDirs
                classpath = integrationTest.runtimeClasspath
                shouldRunAfter(tasks.named("test"))
                useJUnitPlatform()
                doFirst {
                    try {
                        Class.forName("com.axians.eaf.testing.containers.TestContainers")
                            .getDeclaredMethod("startAll")
                            .invoke(null)
                    } catch (ignored: ClassNotFoundException) {
                        // shared-testing module not on classpath; nothing to bootstrap
                    }
                }
            }

            val konsistTestTask = tasks.register<Test>("konsistTest") {
                description = "Runs Konsist architecture and coding standards checks."
                group = "verification"
                testClassesDirs = konsistTest.output.classesDirs
                classpath = konsistTest.runtimeClasspath
                shouldRunAfter(tasks.named("test"))
                // Note: Konsist may still need JUnit Platform
                useJUnitPlatform()
            }

            tasks.named("check") {
                dependsOn(integrationTestTask)
                dependsOn(konsistTestTask)
            }

            afterEvaluate {
                enforceCatalogAlignment("testImplementation", listOf(
                    "kotlin-test",
                    "kotest-framework-engine-jvm",
                    "kotest-assertions-core-jvm",
                    "kotest-property-jvm",
                    "kotest-extensions-spring",
                    "konsist"
                ), catalog)

                enforceCatalogAlignment("integrationTestImplementation", listOf(
                    "kotlin-test",
                    "kotest-framework-engine-jvm",
                    "kotest-assertions-core-jvm",
                    "kotest-property-jvm",
                    "kotest-extensions-spring",
                    "testcontainers-junit-jupiter",
                    "testcontainers-postgresql",
                    "testcontainers-keycloak"
                ), catalog)

                enforceCatalogAlignment("konsistTestImplementation", listOf(
                    "kotlin-test",
                    "kotest-framework-engine-jvm",
                    "kotest-assertions-core-jvm",
                    "kotest-property-jvm",
                    "kotest-extensions-spring",
                    "konsist"
                ), catalog)
            }
        }
    }
}

private fun Project.enforceCatalogAlignment(configurationName: String, aliases: List<String>, catalog: Catalog) {
    val configuration = configurations.findByName(configurationName) ?: return
    aliases.forEach { alias ->
        val expected = catalog.library(alias)
        val matches = configuration.dependencies
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
