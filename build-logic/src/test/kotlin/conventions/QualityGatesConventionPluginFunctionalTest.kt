package conventions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class QualityGatesConventionPluginFunctionalTest : FunSpec({
    test("check runs constitutional stack successfully") {
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
        checkTask.shouldNotBeNull()
        checkTask.outcome shouldBe TaskOutcome.SUCCESS
        result.output.shouldContain("Constitutional TDD stack complete")
    }

    test("detekt version drift is rejected") {
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
        result.output.shouldContain("detekt version drift detected")
    }
})