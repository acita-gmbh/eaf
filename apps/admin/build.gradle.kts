plugins {
    base
}

group = "com.axians.eaf.apps"
version = "0.1.0-SNAPSHOT"

/**
 * Gradle integration for React-Admin consumer application
 * Story 9.1 - Task 8: Gradle Build Integration (AC 13-15, 38-41)
 */

val npmInstall = tasks.register<Exec>("npmInstall") {
    group = "build"
    description = "Install pnpm dependencies for React-Admin portal"
    workingDir = file(".")
    commandLine("pnpm", "install")

    inputs.file("package.json")
    inputs.file("../../pnpm-lock.yaml")
    outputs.dir("node_modules")
}

val npmBuild = tasks.register<Exec>("npmBuild") {
    group = "build"
    description = "Build React-Admin portal for production"
    workingDir = file(".")
    dependsOn(npmInstall)
    commandLine("pnpm", "run", "build")

    inputs.dir("src")
    inputs.file("package.json")
    inputs.file("vite.config.ts")
    inputs.file("tsconfig.json")
    outputs.dir("dist")
}

val npmDev = tasks.register<Exec>("npmDev") {
    group = "application"
    description = "Run development server for React-Admin portal"
    workingDir = file(".")
    dependsOn(npmInstall)
    commandLine("pnpm", "run", "dev")
}

val npmClean = tasks.register<Delete>("npmClean") {
    group = "build"
    description = "Clean npm build artifacts"
    delete("dist", "node_modules")
}

// Integrate with Gradle lifecycle tasks
tasks.matching { it.name == "assemble" || it.name == "build" }.configureEach {
    dependsOn(npmBuild)
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(npmInstall)
}

tasks.matching { it.name == "clean" }.configureEach {
    dependsOn(npmClean)
}
