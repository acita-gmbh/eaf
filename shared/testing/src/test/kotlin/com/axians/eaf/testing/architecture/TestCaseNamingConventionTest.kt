package com.axians.eaf.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FunSpec

/**
 * Architecture compliance tests that validate test case naming conventions
 * follow the standard defined in docs/architecture/test-strategy-and-standards-revision-3.md
 *
 * Epic 8 Story 8.1: All test cases must include story references for traceability.
 *
 * Standard Format: {EPIC}.{STORY}-{TYPE}-{SEQ}: {Description}
 * - TYPE: UNIT (business logic), INT (integration), E2E (end-to-end)
 * - Example: "2.4-UNIT-001: FindWidgetByIdQuery returns widget response when found"
 */
class TestCaseNamingConventionTest :
    FunSpec({

        context("8.1-UNIT-001: Test Case Naming Convention Validation") {
            test("8.1-UNIT-001a: All FunSpec test() calls must include story reference") {
                // Find all test files (exclude build artifacts and disabled tests)
                val testFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            (
                                it.path.contains("/src/test/kotlin/") ||
                                    it.path.contains("/src/integration-test/kotlin/") ||
                                    it.path.contains("/src/axon-integration-test/kotlin/")
                            ) &&
                                !it.path.contains("/kotlin-disabled/") &&
                                !it.path.contains("/build/") &&
                                !it.path.contains("/bin/") &&
                                (it.name.endsWith("Test.kt") || it.name.endsWith("Spec.kt"))
                        }

                if (testFiles.isNotEmpty()) {
                    testFiles.assertTrue { file ->
                        // Find all test("...") function calls in the file
                        // Convert to list to avoid sequence consumption issue (Copilot review fix)
                        val testCalls = Regex("""test\s*\(\s*"([^"]+)"\s*\)""").findAll(file.text).toList()

                        // If file has test() calls, ALL must have story references (STRICT MODE)
                        if (testCalls.isNotEmpty()) {
                            testCalls.all { match ->
                                val testName = match.groupValues[1]

                                // Validate format: {EPIC}.{STORY}-{TYPE}-{SEQ}: {Description}
                                // Example: "2.4-UNIT-001: Description" or "2.4-INT-003a: Description"
                                val storyReferencePattern = Regex("""^\d+\.\d+-[A-Z]+-\d+[a-z]?:.*""")

                                testName.matches(storyReferencePattern)
                            }
                        } else {
                            // File doesn't use test() calls (might be BehaviorSpec) - allow
                            true
                        }
                    }
                }
            }

            test("8.1-UNIT-001b: All BehaviorSpec files should have story reference comments") {
                val behaviorSpecFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            (
                                it.path.contains("/src/test/kotlin/") ||
                                    it.path.contains("/src/integration-test/kotlin/") ||
                                    it.path.contains("/src/axon-integration-test/kotlin/")
                            ) &&
                                !it.path.contains("/kotlin-disabled/") &&
                                !it.path.contains("/build/") &&
                                !it.path.contains("/bin/") &&
                                (it.name.endsWith("Test.kt") || it.name.endsWith("Spec.kt")) &&
                                it.text.contains("BehaviorSpec")
                        }

                if (behaviorSpecFiles.isNotEmpty()) {
                    behaviorSpecFiles.assertTrue { file ->
                        // BehaviorSpec should have story reference comments
                        // Pattern: // Story 2.1-UNIT-001: Description
                        // or: // 2.1-UNIT-001
                        val hasStoryReferenceComment =
                            file.text.contains(Regex("""//\s*Story\s+\d+\.\d+-[A-Z]+-\d+""")) ||
                                file.text.contains(Regex("""//\s*\d+\.\d+-[A-Z]+-\d+"""))

                        hasStoryReferenceComment
                    }
                }
            }

            test("8.1-UNIT-001c: Test TYPE values must be valid (UNIT, INT, E2E)") {
                val testFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            (
                                it.path.contains("/src/test/kotlin/") ||
                                    it.path.contains("/src/integration-test/kotlin/") ||
                                    it.path.contains("/src/axon-integration-test/kotlin/")
                            ) &&
                                !it.path.contains("/kotlin-disabled/") &&
                                !it.path.contains("/build/") &&
                                !it.path.contains("/bin/") &&
                                (it.name.endsWith("Test.kt") || it.name.endsWith("Spec.kt"))
                        }

                if (testFiles.isNotEmpty()) {
                    testFiles.assertTrue { file ->
                        // Extract all story references from the file
                        // Pattern: {EPIC}.{STORY}-{TYPE}-{SEQ}
                        // Convert to list to avoid sequence consumption issue (Copilot review fix)
                        val storyReferences = Regex("""\d+\.\d+-([A-Z]+)-\d+""").findAll(file.text).toList()

                        // Validate each TYPE is one of: UNIT, INT, E2E
                        val validTypes = setOf("UNIT", "INT", "E2E")

                        if (storyReferences.isNotEmpty()) {
                            storyReferences.all { match ->
                                val type = match.groupValues[1]
                                validTypes.contains(type)
                            }
                        } else {
                            // No story references found (might be a file without tests yet)
                            true
                        }
                    }
                }
            }

            test("8.1-UNIT-001d: Story references must use valid format (EPIC.STORY)") {
                val testFiles =
                    Konsist
                        .scopeFromProject()
                        .files
                        .filter {
                            (
                                it.path.contains("/src/test/kotlin/") ||
                                    it.path.contains("/src/integration-test/kotlin/") ||
                                    it.path.contains("/src/axon-integration-test/kotlin/")
                            ) &&
                                !it.path.contains("/kotlin-disabled/") &&
                                !it.path.contains("/build/") &&
                                !it.path.contains("/bin/") &&
                                (it.name.endsWith("Test.kt") || it.name.endsWith("Spec.kt"))
                        }

                if (testFiles.isNotEmpty()) {
                    testFiles.assertTrue { file ->
                        // Extract all story references
                        // Convert to list to avoid sequence consumption issue (Copilot review fix)
                        val storyReferences = Regex("""(\d+)\.(\d+)-[A-Z]+-\d+""").findAll(file.text).toList()

                        // Validate EPIC and STORY are numeric and in reasonable range
                        if (storyReferences.isNotEmpty()) {
                            storyReferences.all { match ->
                                val epic = match.groupValues[1].toIntOrNull()
                                val story = match.groupValues[2].toIntOrNull()

                                epic != null && story != null && epic in 1..99 && story in 1..99
                            }
                        } else {
                            // No story references found (might be a file without tests yet)
                            true
                        }
                    }
                }
            }
        }
    })
