import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

// Root project build configuration
// Repositories are managed in settings.gradle.kts

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
