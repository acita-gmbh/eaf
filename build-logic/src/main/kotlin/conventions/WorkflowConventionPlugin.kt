package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin that centralizes Flowable BPMN workflow engine dependencies
 * across modules requiring workflow orchestration capabilities (Story 6.1).
 */
class WorkflowConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
            }

            dependencies {
                // Flowable Spring Boot Starter (Story 6.1)
                val library = catalog.library("flowable-spring-boot-starter")
                add("implementation", library.toDependencyNotation())
            }
        }
    }
}