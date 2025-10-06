package com.axians.eaf.tools.cli.templates

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class TemplateEngineTest :
    FunSpec({

        context("TemplateEngine rendering") {

            // 7.1-UNIT-005: Basic template rendering
            test("7.1-UNIT-005: should render test template with simple context") {
                // Given
                val engine = TemplateEngine()
                val context =
                    mapOf(
                        "name" to "EAF Developer",
                        "version" to "0.1.0",
                    )

                // When
                val result = engine.render("test-template.mustache", context)

                // Then
                result shouldContain "Hello, EAF Developer!"
                result shouldContain "Version: 0.1.0"
            }

            // 7.1-UNIT-006: Missing template error handling
            test("7.1-UNIT-006: should provide descriptive error when template file not found") {
                // Given
                val engine = TemplateEngine()

                // When/Then
                val exception =
                    shouldThrow<TemplateNotFoundException> {
                        engine.render("missing-template.mustache", emptyMap())
                    }

                exception.message shouldContain "Template not found"
                exception.message shouldContain "missing-template.mustache"
            }

            // 7.1-UNIT-007: SECURITY - Template injection protection (SEC-001 mitigation)
            test("7.1-UNIT-007: should handle empty and malicious context safely") {
                // Given
                val engine = TemplateEngine()

                // Scenario 1: Empty context map
                val emptyResult = engine.render("test-template.mustache", emptyMap())
                emptyResult shouldContain "Hello, !" // Placeholders render as empty
                emptyResult shouldContain "Version: "

                // Scenario 2: Special characters (XSS attempt)
                val xssContext =
                    mapOf(
                        "name" to "<script>alert('xss')</script>",
                        "version" to "0.1.0",
                    )
                val xssResult = engine.render("test-template.mustache", xssContext)
                // Mustache HTML-escapes by default - GOOD for security!
                // This validates Mustache's built-in XSS protection
                xssResult shouldContain "&lt;script&gt;"
                xssResult shouldNotContain "<script>" // Confirms escaping occurred

                // Scenario 3: Path traversal attempt
                val pathContext =
                    mapOf(
                        "name" to "../../etc/passwd",
                        "version" to "0.1.0",
                    )
                val pathResult = engine.render("test-template.mustache", pathContext)
                // Mustache renders string literally - no file system access
                pathResult shouldContain "../../etc/passwd"

                // Scenario 4: Null values
                val nullContext =
                    mapOf<String, Any?>(
                        "name" to null,
                        "version" to "0.1.0",
                    )
                val nullResult = engine.render("test-template.mustache", nullContext)
                // Mustache renders null as empty string
                nullResult shouldContain "Hello, !"
                nullResult shouldContain "Version: 0.1.0"

                // Scenario 5: Numeric types
                val numericContext =
                    mapOf(
                        "name" to 42,
                        "version" to 19.99,
                    )
                val numericResult = engine.render("test-template.mustache", numericContext)
                numericResult shouldContain "Hello, 42!"
                numericResult shouldContain "Version: 19.99"
            }
        }
    })
