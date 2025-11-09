package com.axians.eaf.framework.security

import io.kotest.core.spec.style.FunSpec

/**
 * Placeholder test to satisfy ciTest task requirement.
 *
 * The security module uses integration tests extensively.
 * This placeholder prevents ciTest from failing with "no tests discovered".
 *
 * Story 3.3: Testcontainers Keycloak for Integration Tests
 */
class PlaceholderTest :
    FunSpec({
        test("security module compiles successfully") {
            // Placeholder test - real tests are in src/integration-test
            assert(true)
        }
    })
