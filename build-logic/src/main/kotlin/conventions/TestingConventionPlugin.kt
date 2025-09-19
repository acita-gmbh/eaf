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
 * Convention plugin for testing in EAF.
 * Configures Kotest, Nullable pattern helpers, and Testcontainers integration tests.
 */
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
            }

            // Configure Kotlin tests to use JUnit Platform
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

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
                        "kotest-runner-junit5",
                        "kotest-assertions-core",
                        "kotest-property",
                        "kotest-extensions-spring",
                        "mockk",
                        "konsist"
                    )
                )

                DependencyHandlerScope_addAll(
                    "integrationTestImplementation",
                    listOf(
                        "kotest-runner-junit5",
                        "kotest-assertions-core",
                        "kotest-property",
                        "kotest-extensions-spring",
                        "mockk",
                        "testcontainers-junit-jupiter",
                        "testcontainers-postgresql",
                        "testcontainers-keycloak"
                    )
                )

                DependencyHandlerScope_addAll(
                    "konsistTestImplementation",
                    listOf(
                        "kotest-runner-junit5",
                        "kotest-assertions-core",
                        "kotest-property",
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
            }

            val konsistTestTask = tasks.register<Test>("konsistTest") {
                description = "Runs Konsist architecture and coding standards checks."
                group = "verification"
                testClassesDirs = konsistTest.output.classesDirs
                classpath = konsistTest.runtimeClasspath
                shouldRunAfter(tasks.named("test"))
                useJUnitPlatform()
            }

            tasks.named("check") {
                dependsOn(integrationTestTask)
                dependsOn(konsistTestTask)
            }
        }
    }
}
