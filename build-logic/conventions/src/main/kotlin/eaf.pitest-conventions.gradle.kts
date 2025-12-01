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
    "pitest"(libs.findLibrary("arcmutate-kotlin").orElseThrow {
        GradleException("Library 'arcmutate-kotlin' not found in libs.versions.toml - add it to [libraries] section")
    })
    "pitest"(libs.findLibrary("arcmutate-spring").orElseThrow {
        GradleException("Library 'arcmutate-spring' not found in libs.versions.toml - add it to [libraries] section")
    })
}

// Validate license file exists before running pitest
tasks.named("pitest") {
    doFirst {
        val licenseFile = rootProject.file("arcmutate-licence.txt")
        if (!licenseFile.exists()) {
            val envLicense = System.getenv("ARCMUTATE_LICENSE")?.trim()
            if (envLicense.isNullOrBlank()) {
                throw GradleException(
                    """
                    |Arcmutate license not found!
                    |
                    |Either:
                    |  1. Place arcmutate-licence.txt at project root
                    |  2. Set ARCMUTATE_LICENSE environment variable with license content
                    |
                    |For local development, see docs/research/mutant-kraken-integration-research.md for license setup instructions.
                    |In CI, the license is injected from GitHub Secrets.
                    """.trimMargin()
                )
            }

            // Validate license content appears reasonable - short content usually means truncated or invalid
            if (envLicense.length < 50) {
                throw GradleException(
                    """
                    |ARCMUTATE_LICENSE appears unusually short (${envLicense.length} chars).
                    |
                    |This typically indicates a truncated or invalid license.
                    |Expected format is the full license file content, not just the subscription UUID.
                    |
                    |Download full license from: https://subscriptions.arcmutate.com/<your-subscription-id>/arcmutate-licence.txt
                    """.trimMargin()
                )
            }

            // Write license from environment variable with proper error handling
            try {
                licenseFile.writeText(envLicense)

                // Verify file was written correctly
                if (!licenseFile.exists()) {
                    throw GradleException("License file write appeared to succeed but file does not exist: ${licenseFile.absolutePath}")
                }
                val writtenContent = licenseFile.readText()
                // Normalize line endings for cross-platform comparison (Windows CRLF vs Unix LF)
                val normalizedWritten = writtenContent.replace("\r\n", "\n").replace("\r", "\n")
                val normalizedExpected = envLicense.replace("\r\n", "\n").replace("\r", "\n")
                if (normalizedWritten != normalizedExpected) {
                    throw GradleException("License file content verification failed - file may be corrupted")
                }

                logger.lifecycle("Arcmutate license written and verified from ARCMUTATE_LICENSE environment variable")
            } catch (e: GradleException) {
                throw e
            } catch (e: Exception) {
                throw GradleException(
                    """
                    |Failed to write Arcmutate license file to: ${licenseFile.absolutePath}
                    |
                    |Error: ${e.message}
                    |
                    |Possible causes:
                    |  - No write permission to project root directory
                    |  - Disk full
                    |  - Read-only file system
                    |
                    |Workaround: Manually create the file with license content.
                    """.trimMargin(),
                    e
                )
            }
        } else {
            logger.lifecycle("Arcmutate license found at: ${licenseFile.absolutePath}")
        }
    }
}
