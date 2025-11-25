import info.solidsoft.gradle.pitest.PitestPluginExtension

plugins {
    id("info.solidsoft.pitest")
}

// Access version catalog
val libs = versionCatalogs.named("libs")

// Pitest mutation testing configuration
configure<PitestPluginExtension> {
    pitestVersion.set(libs.findVersion("pitest").get().toString())
    junit5PluginVersion.set(libs.findVersion("pitest-junit5-plugin").get().toString())
    targetClasses.set(listOf("ch.acita.eaf.*", "ch.acita.dvmm.*"))
    targetTests.set(listOf("ch.acita.eaf.*", "ch.acita.dvmm.*"))
    threads.set(Runtime.getRuntime().availableProcessors())
    outputFormats.set(listOf("HTML", "XML"))
    mutationThreshold.set(70) // 70% mutation score threshold
    timestampedReports.set(false)
}
