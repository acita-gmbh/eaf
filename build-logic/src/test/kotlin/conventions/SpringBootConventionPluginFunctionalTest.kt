package conventions

import kotlin.test.Test
import kotlin.test.assertTrue

class SpringBootConventionPluginFunctionalTest {
    @Test
    fun `applies spring boot tasks`() {
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
            assertTrue(result.output.contains("bootRun"), "bootRun task should be available")
            assertTrue(result.output.contains("bootJar"), "bootJar task should be available")
        }
    }
}
