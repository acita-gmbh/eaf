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
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("org.springframework.boot")
                apply("io.spring.dependency-management")
                apply("org.jetbrains.kotlin.plugin.spring")
                apply("org.jetbrains.kotlin.plugin.jpa")
            }

            dependencies {
                fun addAll(configuration: String, aliases: List<String>) {
                    aliases.forEach { alias ->
                        val library = catalog.library(alias)
                        add(configuration, "${library.module}:${library.version}")
                    }
                }

                addAll(
                    "implementation",
                    listOf(
                        "spring-boot-starter-web",
                        "spring-boot-starter-actuator",
                        "spring-boot-starter-validation",
                        "spring-boot-starter-security",
                        "spring-boot-starter-oauth2-resource-server",
                        "spring-security-oauth2-jose",
                        "spring-modulith-starter-core",
                        "spring-modulith-starter-jpa"
                    )
                )
            }
        }
    }
}
