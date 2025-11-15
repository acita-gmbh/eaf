import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPlugin

// Root project build configuration
// Repositories are managed in settings.gradle.kts

// Story 8.2: Pre-Commit Hook Infrastructure
// Tasks defined inline for root project only

// Kotest 6.0 Configuration removed from root - handled by convention plugins

// Removed gradle-versions plugin due to ConcurrentModificationException with Gradle 8.14

// SBOM Generation (CycloneDX) - Week 2 Enhancement
plugins {
    alias(libs.plugins.cyclonedx) apply true

    // Kotlin plugins declared centrally to prevent "loaded multiple times" warning
    // These are applied by convention plugins (eaf.kotlin-common, eaf.spring-boot)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
}

group = "com.axians.eaf"
version = "0.1.0-SNAPSHOT"

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
extra["eaf.libs"] = libs
extra["eaf.version.ktlint"] = libs.findVersion("ktlint").get().requiredVersion
extra["eaf.version.detekt"] = libs.findVersion("detekt").get().requiredVersion
extra["eaf.version.kover"] = libs.findVersion("kover").get().requiredVersion // Story 8.6: Replaced jacoco

val dependencyCheckAggregate = tasks.register("dependencyCheckAnalyze") {
    group = "verification"
    description = "Runs OWASP Dependency Check across all modules with quality gates enabled."
}

gradle.projectsEvaluated {
    val analyzeDependencies = subprojects
        .filter { it.pluginManager.hasPlugin("org.owasp.dependencycheck") }
        .map { "${it.path}:dependencyCheckAnalyze" }

    if (analyzeDependencies.isNotEmpty()) {
        tasks.named("dependencyCheckAnalyze") {
            dependsOn(analyzeDependencies)
        }
    }
}

// SBOM Generation Configuration (CycloneDX 3.0)
// In 3.0+: cyclonedxBom aggregates BOMs from all subprojects
// API changed in 3.0: use task configuration instead of extension block
tasks.cyclonedxBom {
    // Output location: JSON only (standard for supply chain tools)
    jsonOutput = file("build/reports/bom.json")
    // XML output disabled (only JSON is generated when xmlOutput is not set)

    // License information
    includeLicenseText = false
}

subprojects {
    val catalog = rootProject.extra["eaf.libs"] as VersionCatalog

    // Dependency Locking (improves build performance and reproducibility)
    configurations.configureEach {
        resolutionStrategy.activateDependencyLocking()
    }

    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when (requested.group to requested.name) {
                "org.yaml" to "snakeyaml" -> {
                    useVersion(catalog.findVersion("snakeyaml").get().requiredVersion)
                    because("Apply SnakeYAML RCE fix CVE-2024-56324")
                }

                "commons-io" to "commons-io" -> {
                    useVersion(catalog.findVersion("commons-io").get().requiredVersion)
                    because("Mitigate Apache Commons IO XML DoS CVE-2024-47554")
                }

                "org.apache.commons" to "commons-lang3" -> {
                    useVersion(catalog.findVersion("commons-lang3").get().requiredVersion)
                    because("Story 1.9: Fix CVE-2025-48924 uncontrolled recursion")
                }

                "ch.qos.logback" to "logback-core" -> {
                    useVersion(catalog.findVersion("logback").get().requiredVersion)
                    because("Align with Logback serialization fix CVE-2024-9636/9637")
                }

                "ch.qos.logback" to "logback-classic" -> {
                    useVersion(catalog.findVersion("logback").get().requiredVersion)
                    because("Align with Logback serialization fix CVE-2024-9636/9637")
                }

                "org.springframework" to "spring-core" -> {
                    useVersion(catalog.findVersion("spring-framework").get().requiredVersion)
                    because("Ensure Spring Framework annotation security fix CVE-2025-24928")
                }

                "io.netty" to "netty-buffer",
                "io.netty" to "netty-codec",
                "io.netty" to "netty-common",
                "io.netty" to "netty-handler",
                "io.netty" to "netty-resolver",
                "io.netty" to "netty-transport",
                "io.netty" to "netty-transport-native-unix-common" -> {
                    useVersion(catalog.findVersion("netty").get().requiredVersion)
                    because("Story 1.9: Fix CVE-2025-55163, CVE-2025-58056, CVE-2025-58057")
                }

                "io.grpc" to "grpc-netty-shaded" -> {
                    useVersion(catalog.findVersion("grpc-netty-shaded").get().requiredVersion)
                    because("Fix CVE-2025-55163 in grpc-netty-shaded (requires >= 1.75.0)")
                }
            }
        }
    }

    // FIX: Force consistent serialization version to fix Kotest XML report error
    plugins.withType<JavaPlugin> {
        dependencies {
            constraints {
                val serializationVersion = catalog.findVersion("kotlinx-serialization").get().requiredVersion
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$serializationVersion")
                add("testRuntimeOnly", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$serializationVersion")
            }
        }
    }
}

// Story 8.2: Git Hook Installation Tasks (Task 1.5-1.6)
tasks.register("installGitHooks") {
    group = "verification"
    description = "Install pre-commit and commit-msg hooks (Story 8.2)"

    doLast {
        val hooksDir = file(".git/hooks")
        if (!hooksDir.exists()) {
            logger.warn("⚠️ .git/hooks not found")
            return@doLast
        }

        file(".git/hooks/pre-commit").apply {
            writeText("""#!/bin/sh
echo "🔍 EAF pre-commit validation..."
./gradlew preCommitCheck --daemon --quiet || exit 1
exit 0
""")
            setExecutable(true)
        }

        file(".git/hooks/commit-msg").apply {
            writeText("""#!/bin/sh
# EAF Commit Message Validation (Story 8.2 Task 4)
./scripts/git/validate-commit-msg.sh "$1"
""")
            setExecutable(true)
        }

        logger.lifecycle("🎉 Git hooks installed! Bypass: git commit --no-verify")
    }
}

tasks.register<Exec>("preCommitCheck") {
    group = "verification"
    description = "Run pre-commit validation (Story 8.2)"

    workingDir = rootDir
    commandLine("sh", "scripts/git/pre-commit-validate.sh")
}

tasks.register("uninstallGitHooks") {
    group = "verification"
    description = "Remove git hooks (Story 8.2)"
    doLast {
        file(".git/hooks/pre-commit").delete()
        file(".git/hooks/commit-msg").delete()
        logger.lifecycle("✅ Hooks uninstalled")
    }
}