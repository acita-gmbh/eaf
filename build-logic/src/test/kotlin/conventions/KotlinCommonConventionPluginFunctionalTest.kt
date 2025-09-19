package conventions

import kotlin.test.Test
import kotlin.test.assertTrue

class KotlinCommonConventionPluginFunctionalTest {
    @Test
    fun `applies ktlint and detekt tasks`() {
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
            assertTrue(result.output.contains("ktlintCheck"), "ktlintCheck task should be available")
            assertTrue(result.output.contains("detekt"), "detekt task should be available")
        }
    }
}
