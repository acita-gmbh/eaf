package conventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpringBootConventionPluginFunctionalTest {
    @Test
    fun `1_2-UNIT-005 - applies spring boot tasks`() {
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
            assertThat(result.output).contains("bootRun")
            assertThat(result.output).contains("bootJar")
        }
    }
}