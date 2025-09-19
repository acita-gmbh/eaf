package conventions

import kotlin.test.Test
import kotlin.test.assertTrue

class QualityGatesConventionPluginFunctionalTest {
    @Test
    fun `wires quality gates into check lifecycle`() {
        withPluginTestProject(
            """
            plugins {
                id("eaf.testing")
                id("eaf.quality-gates")
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        ) {
            val tasksResult = runGradle("tasks", "--all")
            assertTrue(tasksResult.output.contains("jacocoTestReport"), "jacocoTestReport task should be available")
            assertTrue(tasksResult.output.contains("pitest"), "pitest task should be available")

            val dryRun = runGradle("check", "--dry-run")
            assertTrue(dryRun.output.contains(":konsistTest SKIPPED"), "check should invoke konsistTest")
            assertTrue(dryRun.output.contains(":pitest SKIPPED"), "check should finalize with pitest")
        }
    }
}
