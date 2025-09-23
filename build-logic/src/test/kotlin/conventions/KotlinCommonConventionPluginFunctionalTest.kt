package conventions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

class KotlinCommonConventionPluginFunctionalTest : FunSpec({
    test("applies ktlint and detekt tasks") {
        withPluginTestProject(
            """
            plugins {
                id("eaf.kotlin-common")
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        ) {
            val result = runGradle("tasks", "--all")
            result.output.shouldContain("ktlintCheck")
            result.output.shouldContain("detekt")
        }
    }
})