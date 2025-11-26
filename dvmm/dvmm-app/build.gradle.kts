plugins {
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// dvmm-app: Main application, assembles all modules
dependencies {
    implementation(project(":dvmm:dvmm-api"))
    implementation(project(":dvmm:dvmm-infrastructure"))
    testImplementation(testFixtures(project(":eaf:eaf-testing")))
}

springBoot {
    mainClass.set("de.acci.dvmm.DvmmApplicationKt")
}
