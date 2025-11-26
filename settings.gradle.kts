rootProject.name = "eaf-monorepo"

// Build-logic composite build for convention plugins
pluginManagement {
    includeBuild("build-logic")
}

// Enable version catalog
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

// EAF Framework modules
include(":eaf:eaf-core")
include(":eaf:eaf-eventsourcing")
include(":eaf:eaf-tenant")
include(":eaf:eaf-auth")
include(":eaf:eaf-auth-keycloak")
include(":eaf:eaf-testing")

// DVMM Product modules
include(":dvmm:dvmm-domain")
include(":dvmm:dvmm-application")
include(":dvmm:dvmm-api")
include(":dvmm:dvmm-infrastructure")
include(":dvmm:dvmm-app")
