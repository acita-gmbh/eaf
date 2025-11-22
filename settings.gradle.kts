rootProject.name = "eaf-v1"

// Plugin management
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    includeBuild("build-logic")
}

// Settings plugins
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.2.2"
}

// Build Scan configuration (performance visibility and debugging)
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")

        // Publish scans in CI or on failure
        publishing {
            onlyIf {
                it.buildResult.failures.isNotEmpty() || System.getenv("CI") == "true"
            }
        }

        // Tag scans for easier filtering
        tag(if (System.getenv("CI") == "true") "CI" else "local")
    }
}

// Dependency resolution management
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Order matters: most frequently used first
        // Content filtering reduces HTTP requests by preventing lookups in wrong repositories
        mavenCentral() {
            content {
                // Exclude Gradle plugins (use Plugin Portal)
                excludeGroupByRegex("org\\.gradle.*")
                excludeGroup("com.gradle")
                excludeGroup("io.spring.gradle")
            }
        }

        // Spring Release Repository (fallback for new GA releases not yet synced to Maven Central)
        // Spring Boot 4.0.0 announced 2025-11-20 but may have sync delay to Maven Central
        maven {
            url = uri("https://repo.spring.io/release")
            content {
                includeGroup("org.springframework.boot")
                includeGroup("org.springframework")
                includeGroup("org.springframework.data")
                includeGroup("org.springframework.security")
                includeGroup("org.springframework.modulith")
            }
        }

        gradlePluginPortal() {
            content {
                // Only Gradle plugins and build tools
                includeGroupByRegex("org\\.gradle.*")
                includeGroupByRegex("com\\.gradle.*")
                includeGroup("org.jetbrains.kotlin")
                includeGroup("org.springframework.boot")
                includeGroup("io.spring.gradle")
                includeGroup("org.jlleitschuh.gradle")
                includeGroup("io.gitlab.arturbosch.detekt")
                includeGroup("info.solidsoft.gradle.pitest")
                includeGroup("org.jetbrains.kotlinx.kover")
                includeGroup("org.owasp")
                includeGroup("org.cyclonedx")
                includeGroup("org.jooq")
                includeGroup("io.gatling.gradle")
            }
        }
    }
}

// Module inclusions
// Framework modules
include(":framework:core")
include(":framework:security")
include(":framework:multi-tenancy")
include(":framework:cqrs")
include(":framework:observability")
include(":framework:workflow")
include(":framework:persistence")
include(":framework:web")

// Product modules
include(":products:widget-demo")  // Reference implementation for framework validation

// Shared modules
include(":shared:shared-api")
include(":shared:shared-types")
include(":shared:testing")

// Frontend applications
include(":apps:admin")

// Tools modules
include(":tools:eaf-cli")
