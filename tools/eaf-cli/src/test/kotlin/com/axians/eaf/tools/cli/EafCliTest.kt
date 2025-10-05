package com.axians.eaf.tools.cli

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

class EafCliTest :
    FunSpec({

        context("EAF CLI command execution") {

            // 7.1-UNIT-001: Version command
            test("7.1-UNIT-001: should display version when --version flag provided") {
                // Given
                val cli = EafCli()
                val outputStream = ByteArrayOutputStream()
                val commandLine =
                    CommandLine(cli).apply {
                        out = PrintWriter(outputStream, true)
                    }

                // When
                val exitCode = commandLine.execute("--version")

                // Then
                exitCode shouldBe 0
                val output = outputStream.toString()
                output shouldContain "EAF CLI 0.1.0"
            }

            // 7.1-UNIT-002: Help command
            test("7.1-UNIT-002: should display help text when --help flag provided") {
                // Given
                val cli = EafCli()
                val outputStream = ByteArrayOutputStream()
                val commandLine =
                    CommandLine(cli).apply {
                        out = PrintWriter(outputStream, true)
                    }

                // When
                val exitCode = commandLine.execute("--help")

                // Then
                exitCode shouldBe 0
                val output = outputStream.toString()
                output shouldContain "Usage: eaf"
                output shouldContain "Enterprise Application Framework Scaffolding CLI"
                output shouldContain "scaffold"
            }

            // 7.1-UNIT-003: Scaffold subcommand help
            test("7.1-UNIT-003: should display scaffold subcommand help") {
                // Given
                val cli = EafCli()
                val outputStream = ByteArrayOutputStream()
                val commandLine =
                    CommandLine(cli).apply {
                        out = PrintWriter(outputStream, true)
                    }

                // When
                val exitCode = commandLine.execute("scaffold", "--help")

                // Then
                exitCode shouldBe 0
                val output = outputStream.toString()
                output shouldContain "Usage: eaf scaffold"
                output shouldContain "Generate EAF code scaffolds"
            }

            // 7.1-UNIT-004: Invalid command error handling
            test("7.1-UNIT-004: should handle invalid command gracefully with error message") {
                // Given
                val cli = EafCli()
                val errorStream = ByteArrayOutputStream()
                val commandLine =
                    CommandLine(cli).apply {
                        err = PrintWriter(errorStream, true)
                    }

                // When
                val exitCode = commandLine.execute("invalid-command")

                // Then
                exitCode shouldBeGreaterThan 0 // Non-zero exit code indicates error
                val error = errorStream.toString()
                error shouldContain "Unmatched argument"
            }
        }
    })
