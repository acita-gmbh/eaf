package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.spring.SpringExtension

/**
 * Global Kotest project configuration.
 *
 * This class is automatically detected by Kotest on the classpath via the
 * `io.kotest.provided` package naming convention.
 *
 * By registering SpringExtension globally, we enable:
 * 1. Spring test context support for all @SpringBootTest specs
 * 2. SpringAutowireConstructorExtension (automatic with kotest-extensions-spring)
 * 3. Clean constructor injection pattern (no lateinit var or init block needed)
 *
 * Story 1.1: Testing Infrastructure Modernization Epic
 * Related: AC1-AC4
 *
 * @see <a href="https://kotest.io/docs/extensions/spring.html">Kotest Spring Extension Docs</a>
 */
class ProjectConfig : AbstractProjectConfig() {
    /**
     * Globally registers SpringExtension for all test specs.
     *
     * This enables constructor injection in @SpringBootTest classes:
     *
     * Before (old pattern):
     * ```
     * @SpringBootTest
     * class MyTest : FunSpec() {
     *     @Autowired
     *     private lateinit var mockMvc: MockMvc
     *
     *     init {
     *         extension(SpringExtension())  // Manual registration
     *         test("...") { }
     *     }
     * }
     * ```
     *
     * After (new pattern):
     * ```
     * @SpringBootTest
     * class MyTest(
     *     private val mockMvc: MockMvc  // Constructor injection!
     * ) : FunSpec({
     *     test("...") { }
     * })
     * ```
     */
    override val extensions: List<Extension> = listOf(SpringExtension())
}
