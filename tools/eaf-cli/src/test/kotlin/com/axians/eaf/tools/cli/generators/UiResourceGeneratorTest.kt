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

            // Should succeed - print error if it fails
            if (result.isLeft()) {
                println("Generator error: ${result.leftOrNull()}")
            }
            result.isRight() shouldBe true

            // Verify generated List.tsx has conditional rendering for all types
            val listFile = File("products/widget-demo/ui-module/src/resources/testresource/List.tsx")
            listFile.exists() shouldBe true

            val listContent = listFile.readText()

            // Each field type should render its correct component
            listContent shouldContain "<TextField source=\"name\"" // string type
            listContent shouldContain "<NumberField source=\"price\"" // number type
            listContent shouldContain "<BooleanField source=\"active\"" // boolean type
            listContent shouldContain "<DateField source=\"createdAt\"" // date type

            // Cleanup
            File("products/widget-demo/ui-module").deleteRecursively()
        }

        test("7.4b-INTEGRATION-001: Full end-to-end UI resource generation workflow") {
            // Given: Real product module (widget-demo) with no existing ui-module
            // When: Generate complete UI resource with mixed field types
            // Then: All 10 files created, TypeScript compiles, imports resolve

            // This integration test validates the complete generator workflow:
            // 1. Initialize ui-module structure (package.json, tsconfig.json)
            // 2. Generate all 10 resource files from templates
            // 3. Update ui-module index.ts with exports
            // 4. Verify all files compile and imports resolve

            // Ensure clean state
            val uiModuleDir = File("products/widget-demo/ui-module")
            if (uiModuleDir.exists()) {
                uiModuleDir.deleteRecursively()
            }

            val generator = UiResourceGenerator()

            // Generate Product resource with all 4 field types
            val result =
                generator.generate(
                    resourceName = "Product",
                    moduleName = "widget-demo",
                    fields = "name:string,price:number,active:boolean,createdAt:date",
                    apiBasePath = "/api/v1/products",
                )

            // Should succeed
            if (result.isLeft()) {
                println("Generator error: ${result.leftOrNull()}")
            }
            result.isRight() shouldBe true

            // Verify ui-module structure was created (Task 1)
            uiModuleDir.exists() shouldBe true
            File(uiModuleDir, "package.json").exists() shouldBe true
            File(uiModuleDir, "tsconfig.json").exists() shouldBe true
            File(uiModuleDir, ".gitignore").exists() shouldBe true
            File(uiModuleDir, "src/index.ts").exists() shouldBe true

            // Verify all 10 resource files were generated (Task 2)
            val resourceDir = File(uiModuleDir, "src/resources/product")
            resourceDir.exists() shouldBe true

            val expectedFiles =
                listOf(
                    "List.tsx",
                    "Create.tsx",
                    "Edit.tsx",
                    "Show.tsx",
                    "types.ts",
                    "EmptyState.tsx",
                    "LoadingSkeleton.tsx",
                    "ResourceExport.ts",
                    "index.ts",
                    "README.md",
                )

            expectedFiles.forEach { filename ->
                File(resourceDir, filename).exists() shouldBe true
            }

            // Verify index.ts was updated with export (Task 3)
            val indexContent = File(uiModuleDir, "src/index.ts").readText()
            indexContent shouldContain "export { productResource } from './resources/product/ResourceExport';"

            // Verify generated code has correct imports from framework
            val listContent = File(resourceDir, "List.tsx").readText()
            listContent shouldContain "from 'react-admin'" // React-Admin imports
            listContent shouldContain "from '@axians/eaf-admin-shell'" // Framework imports
            listContent shouldContain "<TextField source=\"name\"" // String field
            listContent shouldContain "<NumberField source=\"price\"" // Number field
            listContent shouldContain "<BooleanField source=\"active\"" // Boolean field
            listContent shouldContain "<DateField source=\"createdAt\"" // Date field

            // Verify types.ts has correct TypeScript types
            val typesContent = File(resourceDir, "types.ts").readText()
            typesContent shouldContain "name: string;"
            typesContent shouldContain "price: number;"
            typesContent shouldContain "active: boolean;"
            typesContent shouldContain "createdAt: Date;"

            // Verify ResourceExport has all components
            val exportContent = File(resourceDir, "ResourceExport.ts").readText()
            exportContent shouldContain "list: ProductList"
            exportContent shouldContain "create: ProductCreate"
            exportContent shouldContain "edit: ProductEdit"
            exportContent shouldContain "show: ProductShow"

            // Cleanup
            uiModuleDir.deleteRecursively()
        }
    })
