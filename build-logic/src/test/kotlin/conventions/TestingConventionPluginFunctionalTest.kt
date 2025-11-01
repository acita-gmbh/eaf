package conventions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.TaskOutcome

class TestingConventionPluginFunctionalTest : FunSpec({
    test("1.2-UNIT-001: testing convention registers integration and konsist tasks") {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project)

        val result = project.gradle(":app:check").build()

        result.task(":app:integrationTest")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":app:konsistTest")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    test("1.2-UNIT-002: dependency version drift fails fast") {
        val project = createTestProject()
        bootstrapSampleProject(project)
        writeTestingOnlySources(project, extraAppBuildContent = """
            dependencies {
                // Try to use wrong version of kotest-framework-engine-jvm
                testImplementation("io.kotest:kotest-framework-engine-jvm:5.8.0")
            }
        """)

        val result = project.gradle(":app:check").buildAndFail()
        result.output.shouldContain("Version drift detected")
    }
})