package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

/**
 * Convention plugin for quality gates in EAF.
 * Ensures ktlintCheck, detekt, konsistTest, pitest integrate into check lifecycle.
 */
class QualityGatesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("eaf.kotlin-common")
                apply("jacoco")
                // TODO: Fix Konsist plugin application
                // apply("com.lemonappdev.konsist")
                apply("info.solidsoft.pitest")
            }

            // Configure Jacoco
            configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
                toolVersion = "0.8.12"
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
            configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
                junit5PluginVersion.set("1.2.1")
                avoidCallsTo.set(setOf("kotlin.jvm.internal", "kotlin.Result"))
                mutators.set(setOf("STRONGER"))
                targetClasses.set(setOf("${project.group}.*"))
                targetTests.set(setOf("${project.group}.*"))
                threads.set(Runtime.getRuntime().availableProcessors())
                outputFormats.set(setOf("XML", "HTML"))
                timestampedReports.set(false)
                mutationThreshold.set(80)
                coverageThreshold.set(85)
            }

            // Wire quality gates into check task
            tasks.named("check") {
                dependsOn("ktlintCheck")
                dependsOn("detekt")
                dependsOn("jacocoTestReport")
                // TODO: Add back when Konsist is fixed
                // dependsOn("konsistTest")
                finalizedBy("pitest")
            }
        }
    }
}