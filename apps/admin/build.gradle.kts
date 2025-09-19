plugins {
    base
}

group = "com.axians.eaf.apps"
version = "0.1.0-SNAPSHOT"

val npmInstall = tasks.register("npmInstall") {
    group = "build"
    description = "Placeholder for installing npm dependencies until the React Admin build is wired."
}

val npmBuild = tasks.register("npmBuild") {
    group = "build"
    description = "Placeholder for building the React Admin UI; replace with actual frontend tooling in Story 3.x."
    dependsOn(npmInstall)
}

// Ensure lifecycle tasks remain available even before the real frontend build exists.
tasks.matching { it.name == "assemble" || it.name == "build" }.configureEach {
    dependsOn(npmBuild)
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(npmInstall)
}
