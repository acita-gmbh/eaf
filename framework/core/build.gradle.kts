plugins {
    id("eaf.kotlin-common")
}

description = "EAF Core Framework - Domain patterns and utilities"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.10")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.10")
    implementation("io.arrow-kt:arrow-core:1.2.4")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

