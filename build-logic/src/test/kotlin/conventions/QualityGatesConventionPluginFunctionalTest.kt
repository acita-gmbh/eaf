package conventions

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class QualityGatesConventionPluginFunctionalTest {
    @Test
    fun `1_2-UNIT-003 - check runs constitutional stack successfully`() {
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
        assertThat(checkTask).isNotNull()
        assertThat(checkTask!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("Constitutional TDD stack complete")
    }

    @Test
    fun `1_2-UNIT-004 - detekt version drift is rejected`() {
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
        assertThat(result.output).contains("detekt version drift detected")
    }
}