package conventions

import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class TestingConventionPluginFunctionalTest {
    @Test
    fun `testing convention registers integration and konsist tasks`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project)

        val result = project.gradle(":app:check").build()

        assertTrue(result.task(":app:integrationTest")?.outcome == TaskOutcome.SUCCESS)
        assertTrue(result.task(":app:konsistTest")?.outcome == TaskOutcome.SUCCESS)
    }

    @Test
    fun `dependency version drift fails fast`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project, extraAppBuildContent = """
            dependencies {
                testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
            }
        """)

        val result = project.gradle(":app:check").buildAndFail()
        val output = result.output
        assertTrue(output.contains("Version drift detected"), "Expected version drift message in build output but was:\n$output")
    }
}
