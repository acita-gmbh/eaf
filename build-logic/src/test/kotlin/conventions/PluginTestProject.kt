package conventions

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

internal class PluginTestProject private constructor(
    private val directory: Path,
    private val repoRoot: Path
) {
    companion object {
        fun create(): PluginTestProject {
            val workingDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
            val repoRoot = workingDir.parent
                ?: error("Unable to locate repository root from $workingDir")
            val projectDir = createTempDirectory("eaf-plugin-test-")
            projectDir.resolve("settings.gradle.kts").writeText(
                """
                rootProject.name = "plugin-under-test"
                """.trimIndent()
            )

            val gradleDir = projectDir.resolve("gradle")
            gradleDir.createDirectories()
            Files.copy(
                repoRoot.resolve("gradle/libs.versions.toml"),
                gradleDir.resolve("libs.versions.toml"),
                StandardCopyOption.REPLACE_EXISTING
            )

            val detektDir = projectDir.resolve("config/detekt")
            detektDir.createDirectories()
            Files.copy(
                repoRoot.resolve("config/detekt/detekt.yml"),
                detektDir.resolve("detekt.yml"),
                StandardCopyOption.REPLACE_EXISTING
            )

            return PluginTestProject(projectDir, repoRoot)
        }
    }

    val projectDir: File = directory.toFile()

    fun writeBuildFile(content: String) {
        directory.resolve("build.gradle.kts").writeText(content)
    }

    fun writeFile(relativePath: String, content: String) {
        val target = directory.resolve(relativePath)
        target.parent?.createDirectories()
        target.writeText(content)
    }

    fun runGradle(vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments)
            .forwardOutput()
            .build()

    fun cleanup() {
        projectDir.deleteRecursively()
    }
}

internal inline fun <T> withPluginTestProject(
    buildScript: String,
    block: PluginTestProject.() -> T
): T {
    val project = PluginTestProject.create()
    return try {
        project.writeBuildFile(buildScript)
        project.block()
    } finally {
        project.cleanup()
    }
}
