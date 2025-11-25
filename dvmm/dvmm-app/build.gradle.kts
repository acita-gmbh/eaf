plugins {
    id("eaf.spring-conventions")
    id("eaf.test-conventions")
}

// dvmm-app: Main application, assembles all modules
dependencies {
    implementation(project(":dvmm:dvmm-api"))
    implementation(project(":dvmm:dvmm-infrastructure"))
    implementation(project(":eaf:eaf-testing"))
}

springBoot {
    mainClass.set("com.acita.dvmm.DvmmApplicationKt")
}
