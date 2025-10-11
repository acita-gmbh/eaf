package com.axians.eaf.testing.tags

import io.kotest.core.Tag

/**
 * Kotest tag for Property-Based Tests.
 *
 * Story 8.6: Multi-stage testing pipeline separation.
 *
 * Property-based tests are designed for deep validation and finding unknown edge cases
 * through exhaustive random input generation. They are incompatible with mutation testing
 * due to exponential time complexity (iterations × mutations).
 *
 * **Usage:**
 * - Mark PBT test specs with this tag
 * - PBTs run in nightly/post-merge pipeline only
 * - PBTs are excluded from pitest mutation testing
 * - Regular tests run on every PR for fast TDD feedback
 *
 * **Pipeline Separation:**
 * - **PR/Commit (Fast)**: `./gradlew test pitest` (excludes @PBT)
 * - **Nightly (Deep)**: `./gradlew propertyTest` (includes @PBT only)
 *
 * **Example:**
 * ```kotlin
 * class RoleNormalizationPropertyTest : FunSpec() {
 *     override fun tags() = setOf(PbtTag)
 *
 *     init {
 *         test("should handle all valid roles") {
 *             checkAll(1000, validRoleArb()) { role ->
 *                 // ... property test logic
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @see <a href="https://kotest.io/docs/framework/tags.html">Kotest Tags Documentation</a>
 */
object PbtTag : Tag() {
    // CodeRabbit: Override name to match @PBT annotation tag
    override val name = "PBT"
}

/**
 * Type-safe annotation for property-based tests.
 * Maps to the "PBT" string tag for JUnit Platform integration.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@io.kotest.core.annotation.Tags("PBT")
annotation class PBT
