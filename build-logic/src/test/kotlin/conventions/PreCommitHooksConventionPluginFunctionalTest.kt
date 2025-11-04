package conventions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.readText
import org.gradle.testkit.runner.TaskOutcome

class PreCommitHooksConventionPluginFunctionalTest : FunSpec({
    test("1.10-UNIT-001: installGitHooks copies templates into .git/hooks") {
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
        result.task(":installGitHooks")!!.outcome shouldBe TaskOutcome.SUCCESS

        val preCommit = project.path.resolve(".git/hooks/pre-commit")
        preCommit.shouldExist()
        preCommit.toFile().canExecute().shouldBeTrue()
        preCommit.readText().shouldContain("ktlintCheck")

        val prePush = project.path.resolve(".git/hooks/pre-push")
        prePush.shouldExist()
        prePush.toFile().canExecute().shouldBeTrue()
        prePush.readText().shouldContain("detekt --quiet")
        prePush.readText().shouldContain("test --quiet")
    }
})
