package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin that centralizes Micrometer/Prometheus dependencies across modules
 * requiring observability instrumentation.
 */
class ObservabilityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        target.dependencies {
            listOf("micrometer-core", "micrometer-registry-prometheus").forEach { alias ->
                val library = catalog.library(alias)
                add("implementation", "${library.module}:${library.version}")
            }
        }
    }
}
