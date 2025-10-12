plugins {
    alias(libs.plugins.application)
    alias(libs.plugins.kotest.plugin)
    id("eaf.kotlin-common")
}

application {
    mainClass.set("com.axians.eaf.tools.cli.EafCliKt")
    applicationName = "eaf"
}

dependencies {
    implementation(libs.picocli)
    implementation(libs.mustache)
    implementation(libs.kotlin.stdlib)

    // Functional programming - Arrow Either for error handling
    implementation(libs.bundles.arrow)

    // Testing
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotest.runner.junit5.jvm) // Required for JUnit Platform discovery
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Set working directory to root so tests can find framework/ and products/ directories
    workingDir(rootProject.projectDir)
}

// Configure jvmKotest task with same working directory (jvmKotest is JavaExec type)
tasks.named<JavaExec>("jvmKotest") {
    workingDir(rootProject.projectDir)
}
