package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Convention plugin for testing in EAF.
 * Configures Kotest, Nullable pattern helpers, and Testcontainers integration tests.
 */
class TestingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
            }

            // Testing configuration placeholder
            // TODO: Implement full testing configuration once version catalog is resolved

            // Configure test tasks to use JUnit Platform for Kotest
            tasks.named("test", Test::class) {
                useJUnitPlatform()
            }
        }
    }
}