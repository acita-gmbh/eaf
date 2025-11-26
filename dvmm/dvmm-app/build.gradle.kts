plugins {
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
    id("eaf.pitest-conventions")
}

// dvmm-app: Main application, assembles all modules
dependencies {
    implementation(project(":dvmm:dvmm-api"))
    implementation(project(":dvmm:dvmm-infrastructure"))
    implementation(project(":eaf:eaf-auth-keycloak"))
    implementation(libs.spring.boot.actuator)

    testImplementation(testFixtures(project(":eaf:eaf-testing")))
    testImplementation(libs.spring.boot.oauth2.resource.server)
    testImplementation(libs.spring.security.test)
}

springBoot {
    mainClass.set("de.acci.dvmm.DvmmApplicationKt")
}
