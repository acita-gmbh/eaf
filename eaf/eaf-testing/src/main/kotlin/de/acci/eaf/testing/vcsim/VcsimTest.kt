package de.acci.eaf.testing.vcsim

import org.junit.jupiter.api.extension.ExtendWith
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Meta-annotation for VCSIM integration tests.
 *
 * Combines Testcontainers support with VCSIM lifecycle management.
 * Test classes annotated with `@VcsimTest` automatically get:
 *
 * - VCSIM container started before test execution
 * - [VcsimTestFixture] available for parameter injection
 * - [VcsimContainer] available for parameter injection
 * - State reset between test classes for isolation
 * - Container reuse within test class for performance
 *
 * ## Usage
 * ```kotlin
 * @VcsimTest
 * class MyVmwareIntegrationTest {
 *
 *     @Test
 *     fun `should test VM creation`(fixture: VcsimTestFixture) {
 *         val vmRef = fixture.createVm(VmSpec(name = "test-vm"))
 *         assertThat(vmRef.name).isEqualTo("test-vm")
 *     }
 *
 *     @Test
 *     fun `should access container directly`(container: VcsimContainer) {
 *         assertThat(container.isRunning).isTrue()
 *         assertThat(container.getSdkUrl()).contains("/sdk")
 *     }
 * }
 * ```
 *
 * ## Spring Integration
 * For Spring Boot tests, use `@DynamicPropertySource` to inject
 * connection properties:
 *
 * ```kotlin
 * @VcsimTest
 * @SpringBootTest
 * class MySpringVmwareTest {
 *
 *     companion object {
 *         @JvmStatic
 *         @DynamicPropertySource
 *         fun configureProperties(registry: DynamicPropertyRegistry) {
 *             val container = TestContainers.vcsim
 *             registry.add("vsphere.url") { container.getSdkUrl() }
 *             registry.add("vsphere.username") { container.getUsername() }
 *             registry.add("vsphere.password") { container.getPassword() }
 *         }
 *     }
 * }
 * ```
 *
 * ## Container Lifecycle
 * The VCSIM container is started lazily via [TestContainers.vcsim] singleton
 * and reused across all test classes. This minimizes startup overhead while
 * maintaining test isolation through fixture state reset.
 *
 * @see VcsimExtension
 * @see VcsimTestFixture
 * @see VcsimContainer
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Testcontainers
@ExtendWith(VcsimExtension::class)
public annotation class VcsimTest
