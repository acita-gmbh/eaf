import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("info.solidsoft.pitest")
}

// Access version catalog
val libs = versionCatalogs.named("libs")

// Pitest mutation testing configuration with Arcmutate Kotlin & Spring support
configure<PitestPluginExtension> {
    pitestVersion.set(libs.findVersion("pitest").get().toString())
    junit5PluginVersion.set(libs.findVersion("pitest-junit5-plugin").get().toString())
    targetClasses.set(listOf("de.acci.eaf.*", "de.acci.dvmm.*"))
    targetTests.set(listOf("de.acci.eaf.*", "de.acci.dvmm.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    mutationThreshold.set(70) // 70% mutation score threshold
    timestampedReports.set(false)

    // Arcmutate Kotlin plugin - filters junk mutations from Kotlin compiler-generated code
    // Requires valid arcmutate-licence.txt at project root
    pluginConfiguration.set(mapOf(
        "KOTLIN" to "", // Enable Kotlin support
        "KOTLIN_NO_NULLS" to "", // Filter null-check intrinsics mutations
        "KOTLIN_EXTRA" to "" // Additional junk mutation filters
    ))

    // Enable Kotlin-specific and Spring mutators
    mutators.set(listOf(
        "DEFAULTS",
        "KOTLIN_RETURNS", // Kotlin-specific return value mutations
        "KOTLIN_REMOVE_DISTINCT", // Remove .distinct() calls
        "KOTLIN_REMOVE_SORTED", // Remove .sorted() calls
        "SPRING" // Spring annotation mutations (validation, security, response)
    ))

    // Exclude Kotlin compiler-generated constructs that cause false positives
    excludedClasses.set(listOf(
        "*\$WhenMappings", // Kotlin when statement internals
        "*\$\$special\$\$inlined\$*" // Inline function bytecode
    ))

    // Avoid calls to Kotlin intrinsics (reduces unkillable mutations)
    avoidCallsTo.set(listOf(
        "kotlin.jvm.internal",
        "kotlinx.coroutines"
    ))

    // Exclude data class auto-generated methods
    excludedMethods.set(listOf(
        "hashCode",
        "equals",
        "toString",
        "copy",
        "component1",
        "component2",
        "component3",
        "component4",
        "component5"
    ))
}

// Add Arcmutate plugins to pitest classpath
dependencies {
    "pitest"(libs.findLibrary("arcmutate-kotlin").get())
    "pitest"(libs.findLibrary("arcmutate-spring").get())
}

// Validate license file exists before running pitest
tasks.named("pitest") {
    doFirst {
        val licenseFile = rootProject.file("arcmutate-licence.txt")
        if (!licenseFile.exists()) {
            val envLicense = System.getenv("ARCMUTATE_LICENSE")
            if (envLicense.isNullOrBlank()) {
                throw GradleException(
                    """
                    |Arcmutate license not found!
                    |
                    |Either:
                    |  1. Place arcmutate-licence.txt at project root
                    |  2. Set ARCMUTATE_LICENSE environment variable with license content
                    |
                    |For local development, contact team lead for license file.
                    |In CI, the license is injected from GitHub Secrets.
                    """.trimMargin()
                )
            } else {
                // Write license from environment variable
                licenseFile.writeText(envLicense)
                logger.lifecycle("Arcmutate license written from ARCMUTATE_LICENSE environment variable")
            }
        } else {
            logger.lifecycle("Arcmutate license found at: ${licenseFile.absolutePath}")
        }
    }
}
