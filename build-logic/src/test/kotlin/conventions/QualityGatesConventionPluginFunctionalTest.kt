package conventions

import kotlin.test.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class QualityGatesConventionPluginFunctionalTest {
    @Test
    fun `check runs constitutional stack successfully`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeSampleSources(
            project,
            extraAppBuildContent = """
                tasks.named<info.solidsoft.gradle.pitest.PitestTask>("pitest") {
                    failWhenNoMutations.set(false)
                }
            """
        )
        println("Project dir: ${project.path}")

        val result = project.gradle(":app:check").build()

        val checkTask = result.task(":app:check")
        assertTrue(checkTask != null && checkTask.outcome == TaskOutcome.SUCCESS, "check task should succeed")
        assertTrue(result.output.contains("Constitutional TDD stack complete"))
    }

    @Test
    fun `detekt version drift is rejected`() {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeSampleSources(project, extraAppBuildContent = """
            extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class) {
                toolVersion = "1.23.5"
            }
            tasks.named<info.solidsoft.gradle.pitest.PitestTask>("pitest") {
                failWhenNoMutations.set(false)
            }
        """)
        println("Project dir (drift test): ${project.path}")

        val result = project.gradle(":app:check").buildAndFail()
        val output = result.output
        assertTrue(output.contains("detekt version drift detected"), "Expected detekt drift message in build output but was:\n$output")
    }
}
