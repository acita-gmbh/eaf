package conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/**
 * Convention plugin for Kotlin modules in EAF.
 * Enforces JVM 21, strict compiler options, ktlint, and detekt.
 */
class KotlinCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val catalog = loadCatalog(target.rootProject.projectDir.resolve("gradle/libs.versions.toml").toPath())

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
            configure<KtlintExtension> {
                version.set(catalog.version("ktlint"))
                android.set(false)
                ignoreFailures.set(false)
                reporters {
                    reporter(ReporterType.PLAIN)
                    reporter(ReporterType.CHECKSTYLE)
                }
                filter {
                    exclude("**/generated/**")
                }
            }

            // Configure detekt
            configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
                toolVersion = catalog.version("detekt")
                config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                allRules = false
                ignoreFailures = false
            }
        }
    }
}
