package conventions

import java.nio.file.Files
import kotlin.io.path.readText
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

class PreCommitHooksConventionPluginFunctionalTest {
    @Test
    fun `1_10-UNIT-001 - installGitHooks copies templates into git hooks`() {
        val project = createTestProject()
        bootstrapSampleProject(project, includeAppModule = false)
        project.write("build.gradle.kts", """
            plugins {
                id("eaf.pre-commit-hooks")
            }
        """)

        Files.createDirectories(project.path.resolve(".git/hooks"))
        project.copyFile(repositoryRoot.resolve(".git-hooks/pre-commit"), ".git-hooks/pre-commit")
        project.copyFile(repositoryRoot.resolve(".git-hooks/pre-push"), ".git-hooks/pre-push")

        val result = project.gradle("installGitHooks").build()
        assertThat(result.task(":installGitHooks")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val preCommit = project.path.resolve(".git/hooks/pre-commit")
        assertThat(preCommit).exists()
        assertThat(preCommit.toFile().canExecute()).isTrue()
        assertThat(preCommit.readText()).contains("ktlintCheck")

        val prePush = project.path.resolve(".git/hooks/pre-push")
        assertThat(prePush).exists()
        assertThat(prePush.toFile().canExecute()).isTrue()
        assertThat(prePush.readText()).contains("detekt --quiet")
        assertThat(prePush.readText()).contains("test --quiet")
    }
}
