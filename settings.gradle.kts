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
include(":eaf:eaf-notifications")

// DCM Product modules
include(":dcm:dcm-domain")
include(":dcm:dcm-application")
include(":dcm:dcm-api")
include(":dcm:dcm-infrastructure")
include(":dcm:dcm-app")
