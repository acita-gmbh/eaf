package com.axians.eaf.tools.cli.generators

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

/**
 * Tests for UiResourceGenerator
 *
 * Story 7.4b: Create Product UI Module Generator
 * Focus: Field type mapping validation (Quinn CRITICAL-001 fix)
 *
 * Note: These tests validate the CRITICAL field type mapping fix that unblocked Epic 8.
 * The fix adds isString/isNumber/isBoolean/isDate flags enabling Mustache conditional rendering.
 */
@Suppress("MaxLineLength")
class UiResourceGeneratorTest :
    FunSpec({

        test("7.4b-UNIT-002: Product module validation should fail fast if module doesn't exist") {
            // Given: Non-existent product module
            // When: Generator attempts to validate
            // Then: Should return ProductModuleNotFound error

            val generator = UiResourceGenerator()
            val result =
                generator.generate(
                    resourceName = "Product",
                    moduleName = "non-existent-module-12345",
                    fields = "name:string",
                    apiBasePath = null,
                )

            // Should fail with ProductModuleNotFound error
            result.isLeft() shouldBe true
        }

        test("7.4b-UNIT-003: Field type mapping produces correct boolean flags for Mustache rendering") {
            // Given: Valid product module (widget-demo exists)
            // When: Generating UI resource with mixed field types
            // Then: Generated files should contain correct conditional rendering

            // This validates Quinn CRITICAL-001 fix
            // The fix adds isString/isNumber/isBoolean/isDate to field context

            val generator = UiResourceGenerator()

            // Generate with all 4 field types
            val result =
                generator.generate(
                    resourceName = "TestResource",
                    moduleName = "widget-demo",
                    fields = "name:string,price:number,active:boolean,createdAt:date",
                    apiBasePath = null,
                )

            // Should succeed
            result.isRight() shouldBe true

            // Verify generated List.tsx has conditional rendering for all types
            val listFile = File("products/widget-demo/ui-module/src/resources/testresource/List.tsx")
            listFile.exists() shouldBe true

            val listContent = listFile.readText()

            // Each field type should render its correct component
            listContent shouldContain "<TextField source=\"name\""  // string type
            listContent shouldContain "<NumberField source=\"price\""  // number type
            listContent shouldContain "<BooleanField source=\"active\""  // boolean type
            listContent shouldContain "<DateField source=\"createdAt\""  // date type

            // Cleanup
            File("products/widget-demo/ui-module").deleteRecursively()
        }
    })
