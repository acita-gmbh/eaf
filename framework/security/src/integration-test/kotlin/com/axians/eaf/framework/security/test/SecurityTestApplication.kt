package com.axians.eaf.framework.security.test

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

/**
 * Test application for Security Framework integration tests.
 *
 * Loads Security module configuration for testing:
 * - SecurityConfiguration (OAuth2 Resource Server)
 * - SecurityModule (Spring Modulith boundary)
 *
 * Story 3.1: Spring Security OAuth2 Resource Server Foundation
 */
@SpringBootApplication
@ComponentScan(basePackages = ["com.axians.eaf.framework.security"])
class SecurityTestApplication
