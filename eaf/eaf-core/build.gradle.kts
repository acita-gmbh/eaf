plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
}

// eaf-core: Zero external dependencies except kotlin-stdlib
// This is the shared kernel for all EAF modules
dependencies {
    // Only kotlin-stdlib (implicit via kotlin plugin)
    // No external dependencies allowed in core
}
