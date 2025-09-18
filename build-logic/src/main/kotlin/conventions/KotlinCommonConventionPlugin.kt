package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention plugin for Kotlin modules in EAF.
 * Enforces JVM 21, strict compiler options, ktlint, and detekt.
 */
class KotlinCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
                apply("org.jlleitschuh.gradle.ktlint")
                apply("io.gitlab.arturbosch.detekt")
            }

            // Configure Kotlin JVM toolchain
            configure<KotlinJvmProjectExtension> {
                jvmToolchain(21)
            }

            // Configure Kotlin compilation
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
                    freeCompilerArgs.addAll(listOf(
                        "-Xjsr305=strict",
                        "-opt-in=kotlin.RequiresOptIn"
                    ))
                    allWarningsAsErrors.set(true)
                }
            }

            // Configure Java compilation (for mixed projects)
            tasks.withType<JavaCompile>().configureEach {
                options.release.set(21)
                options.encoding = "UTF-8"
                options.compilerArgs.addAll(listOf(
                    "-Xlint:all",
                    "-Werror"
                ))
            }

            // Configure ktlint
            configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
                version.set("1.3.1")
                android.set(false)
                ignoreFailures.set(false)
                reporters {
                    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
                    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
                }
                filter {
                    exclude("**/generated/**")
                }
            }

            // Configure detekt
            configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
                toolVersion = "1.23.7"
                config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                allRules = false
                ignoreFailures = false
            }
        }
    }
}