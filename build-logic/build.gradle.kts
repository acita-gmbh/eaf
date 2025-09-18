plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Core plugins for convention plugins
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.10")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.3.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.6")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    // TODO: Fix Konsist coordinates
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.15.0")
}

gradlePlugin {
    plugins {
        register("eaf.kotlin-common") {
            id = "eaf.kotlin-common"
            implementationClass = "conventions.KotlinCommonConventionPlugin"
        }
        register("eaf.spring-boot") {
            id = "eaf.spring-boot"
            implementationClass = "conventions.SpringBootConventionPlugin"
        }
        register("eaf.testing") {
            id = "eaf.testing"
            implementationClass = "conventions.TestingConventionPlugin"
        }
        register("eaf.quality-gates") {
            id = "eaf.quality-gates"
            implementationClass = "conventions.QualityGatesConventionPlugin"
        }
    }
}