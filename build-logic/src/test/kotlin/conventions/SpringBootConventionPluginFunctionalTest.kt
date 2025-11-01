package conventions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class SpringBootConventionPluginFunctionalTest : FunSpec({
    test("1.2-UNIT-005: applies spring boot tasks") {
        withPluginTestProject(
            """
            plugins {
                id("eaf.spring-boot")
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        ) {
            val result = runGradle("tasks", "--all")
            result.output.shouldContain("bootRun")
            result.output.shouldContain("bootJar")
        }
    }
})