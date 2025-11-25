plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.kotlin.jvm.get().pluginId.let { "org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}" })
    implementation(libs.plugins.kotlin.spring.get().pluginId.let { "org.jetbrains.kotlin:kotlin-allopen:${libs.versions.kotlin.get()}" })
    implementation(libs.plugins.spring.boot.get().pluginId.let { "org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.spring.boot.get()}" })
    implementation(libs.plugins.spring.dependency.management.get().pluginId.let { "io.spring.gradle:dependency-management-plugin:${libs.versions.spring.dependency.management.get()}" })
}
