package conventions

import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

/**
 * Convention plugin for quality gates in EAF.
 * Ensures ktlintCheck, detekt, konsistTest, pitest integrate into check lifecycle.
 */
class QualityGatesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("jacoco")
                apply("info.solidsoft.pitest")
            }

            val basePackage = (findProperty("eaf.basePackage") as? String)
                ?.takeIf { it.isNotBlank() }
                ?: group.toString().takeIf { it.isNotBlank() }
                ?: "com.axians.eaf"

            // Configure Jacoco
            configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
                toolVersion = catalog.version("jacoco")
            }

            tasks.named("test") {
                finalizedBy("jacocoTestReport")
            }

            tasks.named("jacocoTestReport", org.gradle.testing.jacoco.tasks.JacocoReport::class) {
                dependsOn("test")
                reports {
                    xml.required.set(true)
                    html.required.set(true)
                }
            }

            // Configure Pitest
            configure<PitestPluginExtension> {
                val pitestExtension = this
                junit5PluginVersion.set("1.2.1")
                avoidCallsTo.set(setOf("kotlin.jvm.internal", "kotlin.Result"))
                mutators.set(setOf("STRONGER"))
                targetClasses.set(setOf("$basePackage.*"))
                targetTests.set(setOf("$basePackage.*"))
                threads.set(Runtime.getRuntime().availableProcessors())
                outputFormats.set(setOf("XML", "HTML"))
                timestampedReports.set(false)
                mutationThreshold.set(80)
                coverageThreshold.set(85)

                failWhenNoMutations.set(false)

                project.afterEvaluate {
                    val hasSources = extensions
                        .getByType(SourceSetContainer::class.java)
                        .getByName("main")
                        .allSource
                        .files
                        .any { it.isFile }
                    pitestExtension.failWhenNoMutations.set(hasSources)
                }
            }

            // Wire quality gates into check task
            tasks.named("check") {
                dependsOn("ktlintCheck")
                dependsOn("detekt")
                dependsOn("jacocoTestReport")
                dependsOn("konsistTest")
                finalizedBy("pitest")
            }
        }
    }
}
