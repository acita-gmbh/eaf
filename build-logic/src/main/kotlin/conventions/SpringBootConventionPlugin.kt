package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Spring Boot applications in EAF.
 * Configures Spring Boot + Modulith, Actuator, validation, and security starters.
 */
class SpringBootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                // TODO: Add Spring Boot plugin configuration when version catalog is resolved
                // apply("org.springframework.boot")
                // apply("io.spring.dependency-management")
                // apply("org.jetbrains.kotlin.plugin.spring")
                // apply("org.jetbrains.kotlin.plugin.jpa")
            }

            dependencies {
                // Note: Version catalog access in convention plugins requires direct dependency references
                // Core Spring Boot starters - placeholder for now, will be configured when version catalog is resolved
                // TODO: Add proper dependency configuration once version catalog conflict is resolved
            }
        }
    }
}