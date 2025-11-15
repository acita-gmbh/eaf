package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin that applies standardized structured logging configuration
 * to all EAF modules (framework and products).
 *
 * Provides:
 * - JSON logging encoder dependency (logstash-logback-encoder)
 * - Custom Logback configuration (logback-eaf.xml)
 * - MDC context field support for service_name, trace_id, tenant_id
 */
class LoggingConventionPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val catalog = loadCatalog(project.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(project) {
            // Add JSON logging encoder dependency
            dependencies {
                val logstashEncoder = catalog.library("logstash-logback-encoder")
                add("implementation", "${logstashEncoder.module}:${logstashEncoder.version}")
            }

            // Copy logback-eaf.xml to each module's resources for Spring Boot auto-discovery
            tasks.register("copyLoggingConfig") {
                // Configuration cache compatible: capture values at configuration time
                val sourceConfigPath = rootProject.file("build-logic/src/main/resources/logback-eaf.xml")
                val targetConfigPath = file("src/main/resources/logback-spring.xml")

                doLast {
                    // Ensure target directory exists
                    targetConfigPath.parentFile.mkdirs()

                    // Copy configuration file
                    sourceConfigPath.copyTo(targetConfigPath, overwrite = true)

                    logger.info("EAF Logging: Copied logback-eaf.xml to ${targetConfigPath.path}")
                }
            }

            // Ensure logging config is copied before compilation
            tasks.named("processResources") {
                dependsOn("copyLoggingConfig")
            }
        }
    }
}