package conventions

import java.nio.file.Path
import kotlin.io.path.Path

internal val repositoryRoot: Path = Path("..").normalize().toAbsolutePath()
internal val buildLogicRoot: Path = Path(System.getProperty("user.dir")).normalize().toAbsolutePath()

internal fun bootstrapSampleProject(project: TestProject, includeAppModule: Boolean = true) {
    project.write("settings.gradle.kts", """
        pluginManagement {
            repositories {
                mavenCentral()
                gradlePluginPortal()
            }
            includeBuild("${buildLogicRoot.toString().replace("\\", "\\\\")}")
        }
        dependencyResolutionManagement {
            repositories {
                mavenCentral()
            }
        }
        rootProject.name = "sample"
        ${if (includeAppModule) "include(\":app\")" else ""}
    """)

    project.write("build.gradle.kts", """
        allprojects {
            repositories {
                mavenCentral()
            }
        }
    """)

    project.copyFile(repositoryRoot.resolve("gradle/libs.versions.toml"), "gradle/libs.versions.toml")
    project.copyFile(repositoryRoot.resolve("config/detekt/detekt.yml"), "config/detekt/detekt.yml")
}

internal fun writeSampleSources(project: TestProject, extraAppBuildContent: String = "") {
    val extraContent = extraAppBuildContent.trimIndent()
    val sampleBuildScript = buildString {
        appendLine("plugins {")
        appendLine("    id(\"eaf.testing\")")
        appendLine("    id(\"eaf.quality-gates\")")
        appendLine("}")
        appendLine()
        appendLine("group = \"com.axians.eaf.sample\"")
        appendLine("version = \"1.0.0\"")
        appendLine()
        appendLine("dependencyCheck {")
        appendLine("    skip = true")
        appendLine("}")
        if (extraContent.isNotBlank()) {
            appendLine()
            appendLine(extraContent)
        }
    }
    project.write("app/build.gradle.kts", sampleBuildScript)

    project.write("app/src/main/kotlin/com/axians/eaf/sample/SampleService.kt", """
        package com.axians.eaf.sample

        class SampleService {
            fun greet(name: String): String =
                if (name.isBlank()) {
                    "Hello, stranger!"
                } else {
                    "Hello, ${'$'}name!"
                }
        }
    """)

    project.write("app/src/main/java/com/axians/eaf/sample/JavaService.java", """
        package com.axians.eaf.sample;

        public class JavaService {
            public String parityLabel(int value) {
                return value % 2 == 0 ? "even" : "odd";
            }
        }
    """)

    project.write("app/src/test/kotlin/com/axians/eaf/sample/SampleServiceSpec.kt", """
        package com.axians.eaf.sample

        import io.kotest.core.spec.style.FunSpec
        import io.kotest.matchers.shouldBe

        class SampleServiceSpec :
            FunSpec({
                test("greets named users") {
                    SampleService().greet("World") shouldBe "Hello, World!"
                }

                test("greets anonymous users") {
                    SampleService().greet(" ") shouldBe "Hello, stranger!"
                }
            })
    """)

    project.write("app/src/test/kotlin/com/axians/eaf/sample/SampleServiceJvmTest.kt", """
        package com.axians.eaf.sample

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class SampleServiceJvmTest {
            private val service = SampleService()
            private val javaService = JavaService()

            @Test
            fun `greets named`() {
                assertEquals("Hello, World!", service.greet("World"))
            }

            @Test
            fun `falls back to stranger`() {
                assertEquals("Hello, stranger!", service.greet(""))
            }

            @Test
            fun `reports parity`() {
                assertEquals("even", javaService.parityLabel(2))
                assertEquals("odd", javaService.parityLabel(3))
            }
        }
    """)

    project.write("app/src/integrationTest/kotlin/com/axians/eaf/sample/SampleIntegrationTest.kt", """
        package com.axians.eaf.sample

        import io.kotest.core.spec.style.FunSpec
        import io.kotest.matchers.shouldBe

        class SampleIntegrationTest :
            FunSpec({
                val service = SampleService()

                test("integration covers greeting variations") {
                    service.greet("World") shouldBe "Hello, World!"
                    service.greet("") shouldBe "Hello, stranger!"
                }
            })
    """)

    project.write("app/src/konsistTest/kotlin/com/axians/eaf/sample/KonsistSmokeTest.kt", """
        package com.axians.eaf.sample

        import org.junit.jupiter.api.Assertions.assertTrue
        import org.junit.jupiter.api.Test

        class KonsistSmokeTest {
            @Test
            fun `konsist task executes`() {
                assertTrue(true)
            }
        }
    """)
}

internal fun writeTestingOnlySources(project: TestProject, extraAppBuildContent: String = "") {
    val testingBuildScript = buildString {
        appendLine("plugins {")
        appendLine("    id(\"eaf.testing\")")
        appendLine("}")
        appendLine()
        appendLine("group = \"com.axians.eaf.sample\"")
        appendLine("version = \"1.0.0\"")
        val trimmed = extraAppBuildContent.trimIndent()
        if (trimmed.isNotBlank()) {
            appendLine()
            appendLine(trimmed)
        }
    }
    project.write("app/build.gradle.kts", testingBuildScript)

    project.write("app/src/main/kotlin/com/axians/eaf/sample/Answer.kt", """
        package com.axians.eaf.sample

        fun ultimateAnswer(seed: Int): Int =
            if (seed % 2 == 0) {
                42
            } else {
                41
            }
    """)

    project.write("app/src/test/kotlin/com/axians/eaf/sample/AnswerSpec.kt", """
        package com.axians.eaf.sample

        import io.kotest.core.spec.style.FunSpec
        import io.kotest.matchers.shouldBe

        class AnswerSpec :
            FunSpec({
                test("answer is 42") {
                    ultimateAnswer(10) shouldBe 42
                }

                test("odd seeds fallback") {
                    ultimateAnswer(9) shouldBe 41
                }
            })
    """)

    project.write("app/src/integrationTest/kotlin/com/axians/eaf/sample/AnswerIntegrationTest.kt", """
        package com.axians.eaf.sample

        import io.kotest.core.spec.style.FunSpec
        import io.kotest.matchers.shouldBe

        class AnswerIntegrationTest :
            FunSpec({
                test("integration still knows answer") {
                    ultimateAnswer(6) shouldBe 42
                    ultimateAnswer(3) shouldBe 41
                }
            })
    """)

    project.write("app/src/konsistTest/kotlin/com/axians/eaf/sample/KonsistSmokeTest.kt", """
        package com.axians.eaf.sample

        import org.junit.jupiter.api.Assertions.assertTrue
        import org.junit.jupiter.api.Test

        class KonsistSmokeTest {
            @Test
            fun `konsist task executes`() {
                assertTrue(true)
            }
        }
    """)
}
