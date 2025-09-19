package conventions

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner

internal class TestProject(private val projectDir: Path) {
    val path: Path
        get() = projectDir
    fun write(relativePath: String, content: String) {
        val target = projectDir.resolve(relativePath)
        target.parent.createDirectories()
        val normalised = content.trimIndent()
        val withNewline = if (normalised.endsWith("\n")) normalised else normalised + "\n"
        target.writeText(withNewline)
    }

    fun copyFile(source: Path, relativeTarget: String) {
        val target = projectDir.resolve(relativeTarget)
        target.parent.createDirectories()
        Files.copy(source, target)
    }

    fun gradle(vararg arguments: String) =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(arguments.toList() + "--stacktrace")
            .withPluginClasspath()
}

internal fun createTestProject(): TestProject {
    val directory = Files.createTempDirectory("eaf-build-logic-test-")
    return TestProject(directory)
}
