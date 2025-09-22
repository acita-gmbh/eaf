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
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
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
                apply("jacoco")
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

            // Configure Jacoco
            configure<org.gradle.testing.jacoco.plugins.JacocoPluginExtension> {
                toolVersion = catalog.version("jacoco")
            }

            configure<DependencyCheckExtension> {
                failBuildOnCVSS = 7.0f
                formats = mutableListOf(
                    ReportGenerator.Format.HTML.name,
                    ReportGenerator.Format.JSON.name
                )
                analyzers.apply {
                    assemblyEnabled = false
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
                pitestVersion.set(catalog.version("pitest"))
                junit5PluginVersion.set(catalog.version("pitest-junit5"))
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

            val sourceSets = extensions.findByType(SourceSetContainer::class.java)

            val jacocoVerification = try {
                tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java)
            } catch (ignored: UnknownTaskException) {
                tasks.register("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java)
            }

            jacocoVerification.configure {
                dependsOn("test")
                executionData.setFrom(
                    layout.buildDirectory.dir("jacoco").map { dir ->
                        dir.asFileTree.matching {
                            include("test.exec", "test-*.exec")
                        }
                    }
                )
                onlyIf {
                    val resolvedExec = executionData.files.filter { it.exists() }
                    if (resolvedExec.isEmpty()) {
                        logger.lifecycle("Skipping Jacoco coverage verification for ${'$'}path because no execution data was generated.")
                    }
                    resolvedExec.isNotEmpty()
                }
                sourceSets?.findByName("main")?.let { mainSourceSet ->
                    sourceDirectories.setFrom(mainSourceSet.allSource.srcDirs)
                    classDirectories.setFrom(
                        mainSourceSet.output.classesDirs.asFileTree.matching {
                            exclude("**/*Application*")
                        }
                    )
                }

                violationRules.apply {
                    rule {
                        limit {
                            counter = "LINE"
                            value = "COVEREDRATIO"
                            minimum = "0.80".toBigDecimal()
                        }
                        limit {
                            counter = "BRANCH"
                            value = "COVEREDRATIO"
                            minimum = "0.80".toBigDecimal()
                        }
                    }
                }
            }

            this@with.tasks.findByName("compileIntegrationTestKotlin")?.let { task ->
                jacocoVerification.configure { dependsOn(task) }
            }
            this@with.tasks.findByName("compileIntegrationTestJava")?.let { task ->
                jacocoVerification.configure { dependsOn(task) }
            }
            this@with.tasks.findByName("integrationTest")?.let { task ->
                jacocoVerification.configure { dependsOn(task) }
            }

            // Wire quality gates into check task
            tasks.named("check") {
                dependsOn("test")
                dependsOn("ktlintCheck")
                dependsOn("detekt")
                dependsOn("jacocoTestReport")
                dependsOn(jacocoVerification)
                dependsOn("dependencyCheckAnalyze")

                listOf("konsistTest", "integrationTest", "pitest").forEach { taskName ->
                    if (this@with.tasks.findByName(taskName) != null) {
                        dependsOn(taskName)
                    }
                }

                doFirst {
                    logger.lifecycle("\n🔴 RED → prepare failing tests | 🟡 GREEN → satisfy Constitutional TDD | 🔵 REFACTOR → keep quality gates clean")
                }

                doLast {
                    logger.lifecycle("✅ Constitutional TDD stack complete (ktlint → detekt → test → integrationTest → konsistTest → pitest).")
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
