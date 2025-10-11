package conventions

import info.solidsoft.gradle.pitest.PitestPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jlleitschuh.gradle.ktlint.KtlintExtension
// import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification // Story 8.6: Removed, replaced with Kover
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension
import org.owasp.dependencycheck.reporting.ReportGenerator

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
                apply("org.jetbrains.kotlinx.kover") // Story 8.6: Replaced JaCoCo with Kover
                apply("info.solidsoft.pitest")
                apply("org.owasp.dependencycheck")
            }

            // Workaround for detekt Kotlin version compatibility (issue #6198)
            configurations.matching { it.name == "detekt" }.all {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion("2.0.21") // Force detekt-compatible Kotlin version
                    }
                }
            }

            val basePackage = (findProperty("eaf.basePackage") as? String)
                ?.takeIf { it.isNotBlank() }
                ?: group.toString().takeIf { it.isNotBlank() }
                ?: "com.axians.eaf"

            configure<DependencyCheckExtension> {
                failBuildOnCVSS = 7.0f
                formats = mutableListOf(
                    ReportGenerator.Format.HTML.name,
                    ReportGenerator.Format.JSON.name
                )
                analyzers.apply {
                    assemblyEnabled = false
                    // Disable OSS Index to avoid rate limiting issues
                    ossIndexEnabled = false
                }
                data {
                    sequenceOf(
                        System.getenv("DEPENDENCY_CHECK_DATA_DIR"),
                        System.getenv("GRADLE_USER_HOME")?.let { "$it/dependency-check-data" }
                    ).firstOrNull { !it.isNullOrBlank() }
                        ?.let { directory = it }
                }
                nvd.apply {
                    val apiKey = sequenceOf(
                        System.getenv("NVD_API_KEY"),
                        System.getenv("DEPENDENCY_CHECK_NVD_API_KEY"),
                        this@with.findProperty("nvd.apiKey") as? String
                    ).firstOrNull { !it.isNullOrBlank() }?.trim()

                    apiKey?.let { this.apiKey = it }
                }
            }

            // Configure Pitest
            configure<PitestPluginExtension> {
                val pitestExtension = this
                pitestVersion.set(catalog.version("pitest-tool"))
                // No JUnit plugin needed - Kotest has its own Pitest extension
                testPlugin.set("kotest") // Use kotest plugin for Pitest (lowercase required)
                avoidCallsTo.set(setOf("kotlin.jvm.internal", "kotlin.Result"))
                mutators.set(setOf("STRONGER"))
                targetClasses.set(setOf("$basePackage.*"))
                targetTests.set(setOf("$basePackage.*"))
                threads.set(Runtime.getRuntime().availableProcessors())
                outputFormats.set(setOf("XML", "HTML"))
                timestampedReports.set(false)
                mutationThreshold.set(25) // Story 8.6: Baseline floor all modules meet (target: 50% by Epic 9)
                coverageThreshold.set(40) // Story 8.6: Baseline floor (target: 66% by Epic 9)

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

            // Kover is applied via pluginManager above - configuration happens via kover { } DSL in build files
            // Default Kover behavior: generates reports, no verification by default

            // Wire quality gates into check task
            tasks.named("check") {
                dependsOn("test")
                dependsOn("ktlintCheck")
                dependsOn("detekt")
                dependsOn("koverXmlReport") // Story 8.6: Kover replaces jacocoTestReport
                dependsOn("koverVerify")    // Story 8.6: Kover verification replaces jacocoTestCoverageVerification
                // TODO(Epic 8 Pre-Production): Re-enable dependencyCheckAnalyze before production deployment
                // TEMPORARILY DISABLED: CVE scanning disabled to improve build performance during development
                // SECURITY IMPACT: High/critical CVEs (CVSS ≥7.0) won't fail builds until re-enabled
                // TRACKING: CodeRabbit review https://github.com/acita-gmbh/eaf/pull/40#discussion_r2399149685
                // TARGET: Epic 8 pre-production hardening phase
                // dependsOn("dependencyCheckAnalyze")

                listOf("konsistTest", "integrationTest", "pitest").forEach { taskName ->
                    if (this@with.tasks.findByName(taskName) != null) {
                        dependsOn(taskName)
                    }
                }

                doFirst {
                    logger.lifecycle("\n🔴 RED → prepare failing tests | 🟡 GREEN → satisfy Constitutional TDD | 🔵 REFACTOR → keep quality gates clean")
                }

                doLast {
                    logger.lifecycle("✅ Constitutional TDD stack complete (ktlint → detekt → test → kover → integrationTest → konsistTest → pitest).")
                }
            }

            afterEvaluate {
                extensions.findByType(KtlintExtension::class.java)?.let { ktlintExtension ->
                    val configuredVersion = ktlintExtension.version.orNull
                    require(configuredVersion == null || configuredVersion == catalog.version("ktlint")) {
                        "ktlint version drift detected: $configuredVersion (expected ${catalog.version("ktlint")})."
                    }
                }

                extensions.findByType(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java)?.let { detektExtension ->
                    val toolVersion = detektExtension.toolVersion
                    require(toolVersion == catalog.version("detekt")) {
                        "detekt version drift detected: $toolVersion (expected ${catalog.version("detekt")})."
                    }
                }
            }
        }
    }
}
