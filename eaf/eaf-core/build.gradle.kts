plugins {
    id("eaf.kotlin-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// eaf-core: Zero external dependencies except kotlin-stdlib
// This is the shared kernel for all EAF modules
dependencies {
    // Only kotlin-stdlib (implicit via kotlin plugin)
    // No external dependencies allowed in core
}

// Remove convention-added coroutines from this module to keep runtime dependency-free
afterEvaluate {
    configurations.named("implementation").configure {
        dependencies.removeAll { dep -> dep.group == "org.jetbrains.kotlinx" }
    }
}

pitest {
    targetClasses.set(listOf("de.acci.eaf.core.*"))
    excludedClasses.set(listOf("de.acci.eaf.core.types.*Companion"))
}
