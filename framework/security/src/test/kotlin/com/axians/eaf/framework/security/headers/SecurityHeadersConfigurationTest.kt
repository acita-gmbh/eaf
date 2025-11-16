package com.axians.eaf.framework.security.headers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Unit tests for SecurityHeadersConfiguration.
 *
 * Tests:
 * - HSTS header configuration
 * - CSP header configuration
 * - X-Frame-Options header
 * - Security headers enablement/disablement
 * - Custom policy configuration
 *
 * Uses Spring's MockHttpServletRequest/Response (real test doubles, not mocks)
 * per EAF "No Mocks" policy.
 *
 * OWASP A02:2025 - Security Misconfiguration
 *
 * @since 1.0.0
 */
class SecurityHeadersConfigurationTest :
    FunSpec({

        context("HSTS Header") {
            test("should add HSTS header with default configuration") {
                // Given: Default HSTS configuration
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        hsts =
                            SecurityHeadersProperties.HstsProperties(
                                enabled = true,
                                maxAge = 31536000,
                                includeSubdomains = true,
                                preload = false,
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: HSTS header is set correctly
                response.getHeader("Strict-Transport-Security") shouldBe "max-age=31536000; includeSubDomains"
            }

            test("should add HSTS header with preload") {
                // Given: HSTS with preload enabled
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        hsts =
                            SecurityHeadersProperties.HstsProperties(
                                enabled = true,
                                maxAge = 31536000,
                                includeSubdomains = true,
                                preload = true,
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: HSTS header includes preload
                response.getHeader("Strict-Transport-Security") shouldBe "max-age=31536000; includeSubDomains; preload"
            }

            test("should not add HSTS header when disabled") {
                // Given: HSTS disabled
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        hsts =
                            SecurityHeadersProperties.HstsProperties(
                                enabled = false,
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: No HSTS header
                response.getHeader("Strict-Transport-Security") shouldBe null
            }
        }

        context("Content-Security-Policy Header") {
            test("should add CSP header with default policy") {
                // Given: Default CSP configuration
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        csp =
                            SecurityHeadersProperties.CspProperties(
                                enabled = true,
                                policy =
                                    "default-src 'self'; script-src 'self'; style-src 'self'; " +
                                        "img-src 'self' data:; font-src 'self'; " +
                                        "connect-src 'self'; frame-ancestors 'none'",
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: CSP header is set
                val cspHeader = response.getHeader("Content-Security-Policy")
                cspHeader shouldContain "default-src 'self'"
                cspHeader shouldContain "frame-ancestors 'none'"
            }

            test("should add CSP report-only header when report-only mode") {
                // Given: CSP in report-only mode
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        csp =
                            SecurityHeadersProperties.CspProperties(
                                enabled = true,
                                policy = "default-src 'self'",
                                reportOnly = true,
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: CSP report-only header is set
                response.getHeader("Content-Security-Policy-Report-Only") shouldContain "default-src 'self'"
                response.getHeader("Content-Security-Policy") shouldBe null
            }

            test("should support custom CSP policy") {
                // Given: Custom CSP policy
                val customPolicy = "default-src 'self' https://cdn.example.com; script-src 'self' 'unsafe-inline'"
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        csp =
                            SecurityHeadersProperties.CspProperties(
                                enabled = true,
                                policy = customPolicy,
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: Custom policy is set
                response.getHeader("Content-Security-Policy") shouldBe customPolicy
            }
        }

        context("X-Frame-Options Header") {
            test("should add X-Frame-Options header with DENY") {
                // Given: X-Frame-Options DENY
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        frameOptions =
                            SecurityHeadersProperties.FrameOptionsProperties(
                                enabled = true,
                                policy = "DENY",
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: X-Frame-Options is DENY
                response.getHeader("X-Frame-Options") shouldBe "DENY"
            }

            test("should add X-Frame-Options header with SAMEORIGIN") {
                // Given: X-Frame-Options SAMEORIGIN
                val properties =
                    SecurityHeadersProperties(
                        enabled = true,
                        frameOptions =
                            SecurityHeadersProperties.FrameOptionsProperties(
                                enabled = true,
                                policy = "SAMEORIGIN",
                            ),
                    )
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: X-Frame-Options is SAMEORIGIN
                response.getHeader("X-Frame-Options") shouldBe "SAMEORIGIN"
            }
        }

        context("Additional Security Headers") {
            test("should add X-Content-Type-Options header") {
                // Given: Default configuration
                val properties = SecurityHeadersProperties(enabled = true)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: X-Content-Type-Options is set
                response.getHeader("X-Content-Type-Options") shouldBe "nosniff"
            }

            test("should add X-XSS-Protection header") {
                // Given: Default configuration
                val properties = SecurityHeadersProperties(enabled = true)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: X-XSS-Protection is set
                response.getHeader("X-XSS-Protection") shouldBe "1; mode=block"
            }

            test("should add Referrer-Policy header") {
                // Given: Default configuration (uses default referrerPolicy)
                val properties = SecurityHeadersProperties(enabled = true)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: Referrer-Policy is set
                response.getHeader("Referrer-Policy") shouldBe "strict-origin-when-cross-origin"
            }

            test("should add Permissions-Policy header") {
                // Given: Default configuration (uses default permissionsPolicy)
                val properties = SecurityHeadersProperties(enabled = true)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: Permissions-Policy is set (default value from PermissionsPolicyProperties)
                response.getHeader("Permissions-Policy") shouldBe
                    "geolocation=(), microphone=(), camera=(), payment=(), usb=()"
            }

            test("should add Cache-Control header") {
                // Given: Default configuration (Cache-Control is always added)
                val properties = SecurityHeadersProperties(enabled = true)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: Cache-Control is set (hard-coded values from implementation)
                response.getHeader("Cache-Control") shouldBe "no-cache, no-store, must-revalidate"
                response.getHeader("Pragma") shouldBe "no-cache"
                response.getHeader("Expires") shouldBe "0"
            }
        }

        context("Global Enablement") {
            test("should not add any headers when globally disabled") {
                // Given: Security headers globally disabled
                val properties = SecurityHeadersProperties(enabled = false)
                val filter = SecurityHeadersFilter(properties)

                val request = MockHttpServletRequest()
                val response = MockHttpServletResponse()
                val filterChain = MockFilterChain()

                // When: Filter processes request
                filter.doFilter(request, response, filterChain)

                // Then: No security headers are set
                response.getHeader("Strict-Transport-Security") shouldBe null
                response.getHeader("Content-Security-Policy") shouldBe null
                response.getHeader("X-Frame-Options") shouldBe null
                response.getHeader("X-Content-Type-Options") shouldBe null
            }
        }
    })
