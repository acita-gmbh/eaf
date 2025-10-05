import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

// Root project build configuration
// Repositories are managed in settings.gradle.kts

// Story 8.2: Pre-Commit Hook Infrastructure
// Tasks defined inline for root project only

// Kotest 6.0 Configuration removed from root - handled by convention plugins

// Removed gradle-versions plugin due to ConcurrentModificationException with Gradle 8.14

group = "com.axians.eaf"
version = "0.1.0-SNAPSHOT"

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
extra["eaf.libs"] = libs
extra["eaf.version.ktlint"] = libs.findVersion("ktlint").get().requiredVersion
extra["eaf.version.detekt"] = libs.findVersion("detekt").get().requiredVersion
extra["eaf.version.jacoco"] = libs.findVersion("jacoco").get().requiredVersion

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

subprojects {
    val catalog = rootProject.extra["eaf.libs"] as VersionCatalog

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

                "ch.qos.logback" to "logback-core" -> {
                    useVersion(catalog.findVersion("logback").get().requiredVersion)
                    because("Align with Logback serialization fix CVE-2024-9636/9637")
                }

                "ch.qos.logback" to "logback-classic" -> {
                    useVersion(catalog.findVersion("logback").get().requiredVersion)
                    because("Align with Logback serialization fix CVE-2024-9636/9637")
                }

                "org.springframework" to "spring-core" -> {
                    useVersion("6.2.7")
                    because("Ensure Spring Framework annotation security fix CVE-2025-24928")
                }
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
echo "✓ Commit message (Task 4 pending)"
exit 0
""")
            setExecutable(true)
        }

        logger.lifecycle("🎉 Git hooks installed! Bypass: git commit --no-verify")
    }
}

tasks.register("preCommitCheck") {
    group = "verification"
    description = "Run pre-commit validation (Story 8.2)"
    doLast {
        logger.lifecycle("🔍 Pre-commit validation (Tasks 2-5 pending)")
        logger.lifecycle("✅ Placeholder - full implementation in Tasks 2-6")
    }
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
