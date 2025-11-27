import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    kotlin("jvm")
}

// Access version catalog
val libs = versionCatalogs.named("libs")

kotlin {
    // K2 compiler is default in Kotlin 2.2+
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)

        // Explicit API mode: all public declarations require explicit visibility
        explicitApi()

        // Strict null safety and context parameters (Kotlin 2.2+)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xcontext-parameters"
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Coroutines support
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${libs.findVersion("coroutines").get()}"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Logging - kotlin-logging wraps SLF4J with Kotlin-idiomatic API
    implementation("io.github.oshai:kotlin-logging-jvm:${libs.findVersion("kotlinLogging").get()}")
}
