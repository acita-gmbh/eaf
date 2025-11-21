package conventions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinCommonConventionPluginFunctionalTest {
    @Test
    fun `1_2-UNIT-006 - applies ktlint and detekt tasks`() {
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
            assertThat(result.output).contains("ktlintCheck")
            assertThat(result.output).contains("detekt")
        }
    }
}