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
}

// Dependency resolution management
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// Module inclusions
// Framework modules
include(":framework:core")
include(":framework:security")
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
