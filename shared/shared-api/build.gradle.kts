// Shared API Module - Axon commands, events, queries
plugins {
    id("eaf.kotlin-common")
}

dependencies {
    // Axon Framework API
    api(libs.axon.spring.boot.starter)

    // Arrow for domain error handling
    api(libs.arrow.core)
}
