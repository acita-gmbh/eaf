package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin that centralizes observability dependencies (Micrometer/Prometheus, OpenTelemetry)
 * across modules requiring metrics and distributed tracing instrumentation.
 */
class ObservabilityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        target.dependencies {
            // Micrometer Prometheus metrics (Story 5.2)
            listOf("micrometer-core", "micrometer-registry-prometheus").forEach { alias ->
                val library = catalog.library(alias)
                add("implementation", "${library.module}:${library.version}")
            }

            // OpenTelemetry distributed tracing (Story 5.3)
            listOf(
                "opentelemetry-api",
                "opentelemetry-sdk",
                "opentelemetry-exporter-otlp",
                "opentelemetry-semconv",
                "opentelemetry-instrumentation-spring-boot"
            ).forEach { alias ->
                val library = catalog.library(alias)
                add("implementation", "${library.module}:${library.version}")
            }
        }
    }
}
