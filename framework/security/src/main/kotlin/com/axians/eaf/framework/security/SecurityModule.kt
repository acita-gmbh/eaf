package com.axians.eaf.framework.security

import org.springframework.modulith.ApplicationModule

/**
 * Spring Modulith module metadata for EAF Security Module.
 *
 * Purpose: Programmatic Spring Modulith boundary enforcement
 * Validates: framework/security can only depend on framework/core
 * Enforcement: Compile-time via Konsist + Spring Modulith verification
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@ApplicationModule(
    displayName = "EAF Security Module",
    allowedDependencies = ["core"],
)
class SecurityModule
