// Access version catalog
val libs = versionCatalogs.named("libs")

dependencies {
    // kotlin-logging wraps SLF4J with Kotlin-idiomatic API
    // Provides: lazy evaluation, extension functions, structured logging support
    "implementation"("io.github.oshai:kotlin-logging-jvm:${libs.findVersion("kotlinLogging").get()}")
}
