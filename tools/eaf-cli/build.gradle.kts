plugins {
    alias(libs.plugins.kotlin.jvm)
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

    // Testing
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotest.runner.junit5.jvm) // Required for JUnit Platform discovery
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
