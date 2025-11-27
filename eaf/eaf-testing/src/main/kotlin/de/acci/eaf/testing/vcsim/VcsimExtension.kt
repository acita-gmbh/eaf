package de.acci.eaf.testing.vcsim

import de.acci.eaf.testing.TestContainers
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * JUnit 5 extension for VCSIM integration tests.
 *
 * This extension manages the VCSIM container lifecycle and provides
 * test fixtures for VMware vSphere API testing.
 *
 * ## Features
 * - Ensures VCSIM container is started before test class execution
 * - Reuses singleton container instance for performance (via [TestContainers.vcsim])
 * - Resets fixture state between test classes for isolation
 * - Provides [VcsimTestFixture] parameter injection
 * - Provides [VcsimContainer] parameter injection
 *
 * ## Usage
 * ```kotlin
 * @VcsimTest
 * class MyVmwareTest {
 *
 *     @Test
 *     fun `should test VMware operations`(fixture: VcsimTestFixture) {
 *         val vm = fixture.createVm(VmSpec(name = "test-vm"))
 *         assertThat(vm.name).isEqualTo("test-vm")
 *     }
 * }
 * ```
 *
 * ## Container Lifecycle
 * The VCSIM container is started lazily on first access via [TestContainers.vcsim]
 * and reused across all test classes for performance. State reset is handled
 * by [VcsimTestFixture.resetState] at the class level.
 *
 * @see VcsimTest
 * @see VcsimTestFixture
 */
public class VcsimExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private companion object {
        private val NAMESPACE: Namespace = Namespace.create(VcsimExtension::class.java)
        private const val FIXTURE_KEY = "vcsimFixture"
    }

    /**
     * Ensures VCSIM container is running and creates fixture before tests.
     *
     * Called once before all tests in the annotated class.
     */
    override fun beforeAll(context: ExtensionContext) {
        // Trigger container start via lazy initialization
        val container = TestContainers.vcsim

        // Create and store fixture for this test class
        val fixture = VcsimTestFixture(container)

        // Store in extension context for parameter resolution
        getStore(context).put(FIXTURE_KEY, fixture)
    }

    /**
     * Resets fixture state after all tests in the class complete.
     *
     * This ensures isolation between test classes while reusing the
     * container instance for performance.
     */
    override fun afterAll(context: ExtensionContext) {
        val fixture = getStore(context).get(FIXTURE_KEY) as? VcsimTestFixture
        fixture?.resetState()
    }

    /**
     * Determines if this extension can resolve the given parameter.
     *
     * Supports injection of:
     * - [VcsimTestFixture] - Test helper methods
     * - [VcsimContainer] - Direct container access
     */
    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Boolean {
        val type = parameterContext.parameter.type
        return type == VcsimTestFixture::class.java || type == VcsimContainer::class.java
    }

    /**
     * Resolves the parameter value for injection.
     */
    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext
    ): Any {
        val type = parameterContext.parameter.type
        return when (type) {
            VcsimTestFixture::class.java -> {
                getStore(extensionContext).get(FIXTURE_KEY) as VcsimTestFixture
            }
            VcsimContainer::class.java -> {
                TestContainers.vcsim
            }
            else -> throw IllegalArgumentException("Unsupported parameter type: $type")
        }
    }

    private fun getStore(context: ExtensionContext): ExtensionContext.Store {
        return context.getStore(NAMESPACE)
    }
}
