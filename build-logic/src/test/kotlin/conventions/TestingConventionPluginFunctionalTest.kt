package conventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class TestingConventionPluginFunctionalTest {
    @Test
    fun `1_2-UNIT-001 - testing convention registers integration and konsist tasks`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project)

        val result = project.gradle(":app:check").build()

        assertThat(result.task(":app:integrationTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":app:konsistTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `1_2-UNIT-002 - dependency version drift fails fast`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project, extraAppBuildContent = """
            dependencies {
                // Try to use wrong version of kotest-framework-engine-jvm
                testImplementation("io.kotest:kotest-framework-engine-jvm:5.8.0")
            }
        """)

        val result = project.gradle(":app:check").buildAndFail()
        assertThat(result.output).contains("Version drift detected")
    }
}