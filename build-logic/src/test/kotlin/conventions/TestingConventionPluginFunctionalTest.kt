package conventions

import kotlin.test.Test
import kotlin.test.assertTrue

class TestingConventionPluginFunctionalTest {
    @Test
    fun `registers integration and konsist test tasks`() {
        withPluginTestProject(
            """
            plugins {
                id("eaf.testing")
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        ) {
            val result = runGradle("tasks", "--all")
            assertTrue(result.output.contains("integrationTest"), "integrationTest task should be available")
            assertTrue(result.output.contains("konsistTest"), "konsistTest task should be available")
        }
    }
}
