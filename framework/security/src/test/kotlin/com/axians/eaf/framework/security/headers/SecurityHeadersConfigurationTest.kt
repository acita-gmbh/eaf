package com.axians.eaf.framework.security.headers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
 * OWASP A02:2025 - Security Misconfiguration
 *
 * @since 1.0.0
 */
class SecurityHeadersConfigurationTest : FunSpec({

    context("HSTS Header") {
        test("should add HSTS header with default configuration") {
            // Given: Default HSTS configuration
            val properties = SecurityHeadersProperties(
                enabled = true,
                hsts = SecurityHeadersProperties.HstsProperties(
                    enabled = true,
                    maxAge = 31536000,
                    includeSubdomains = true,
                    preload = false
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: HSTS header is set correctly
            verify(response).setHeader(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains"
            )
        }

        test("should add HSTS header with preload") {
            // Given: HSTS configuration with preload enabled
            val properties = SecurityHeadersProperties(
                hsts = SecurityHeadersProperties.HstsProperties(
                    maxAge = 63072000, // 2 years for preload
                    includeSubdomains = true,
                    preload = true
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: HSTS header includes preload directive
            verify(response).setHeader(
                "Strict-Transport-Security",
                "max-age=63072000; includeSubDomains; preload"
            )
        }

        test("should not add HSTS header when disabled") {
            // Given: HSTS disabled
            val properties = SecurityHeadersProperties(
                hsts = SecurityHeadersProperties.HstsProperties(enabled = false)
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: HSTS header is not set
            verify(response, org.mockito.kotlin.never()).setHeader(
                org.mockito.kotlin.eq("Strict-Transport-Security"),
                org.mockito.kotlin.any()
            )
        }
    }

    context("Content-Security-Policy Header") {
        test("should add CSP header with default restrictive policy") {
            // Given: Default CSP configuration
            val properties = SecurityHeadersProperties(
                csp = SecurityHeadersProperties.CspProperties(
                    enabled = true,
                    reportOnly = false
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: CSP header is set with restrictive policy
            val captor = org.mockito.kotlin.argumentCaptor<String>()
            verify(response).setHeader(org.mockito.kotlin.eq("Content-Security-Policy"), captor.capture())

            val cspPolicy = captor.firstValue
            cspPolicy shouldContain "default-src 'self'"
            cspPolicy shouldContain "script-src 'self'"
            cspPolicy shouldContain "frame-ancestors 'none'"
        }

        test("should add CSP header in report-only mode") {
            // Given: CSP in report-only mode
            val properties = SecurityHeadersProperties(
                csp = SecurityHeadersProperties.CspProperties(
                    enabled = true,
                    reportOnly = true,
                    policy = "default-src 'self'"
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: CSP header is set in report-only mode
            verify(response).setHeader(
                "Content-Security-Policy-Report-Only",
                "default-src 'self'"
            )
        }

        test("should support custom CSP policy") {
            // Given: Custom CSP policy
            val customPolicy = "default-src 'self'; script-src 'self' cdn.example.com; style-src 'self' 'unsafe-inline'"
            val properties = SecurityHeadersProperties(
                csp = SecurityHeadersProperties.CspProperties(
                    policy = customPolicy
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Custom policy is set
            verify(response).setHeader("Content-Security-Policy", customPolicy)
        }
    }

    context("X-Frame-Options Header") {
        test("should add X-Frame-Options with DENY") {
            // Given: Default frame options (DENY)
            val properties = SecurityHeadersProperties(
                frameOptions = SecurityHeadersProperties.FrameOptionsProperties(
                    policy = "DENY"
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: X-Frame-Options is set to DENY
            verify(response).setHeader("X-Frame-Options", "DENY")
        }

        test("should support SAMEORIGIN policy") {
            // Given: SAMEORIGIN frame options
            val properties = SecurityHeadersProperties(
                frameOptions = SecurityHeadersProperties.FrameOptionsProperties(
                    policy = "SAMEORIGIN"
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: X-Frame-Options is set to SAMEORIGIN
            verify(response).setHeader("X-Frame-Options", "SAMEORIGIN")
        }
    }

    context("Additional Security Headers") {
        test("should add X-Content-Type-Options header") {
            // Given: Security headers enabled
            val properties = SecurityHeadersProperties(enabled = true)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: X-Content-Type-Options is set to nosniff
            verify(response).setHeader("X-Content-Type-Options", "nosniff")
        }

        test("should add X-XSS-Protection header") {
            // Given: Security headers enabled
            val properties = SecurityHeadersProperties(enabled = true)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: X-XSS-Protection is set
            verify(response).setHeader("X-XSS-Protection", "1; mode=block")
        }

        test("should add Referrer-Policy header") {
            // Given: Default referrer policy
            val properties = SecurityHeadersProperties(
                referrerPolicy = SecurityHeadersProperties.ReferrerPolicyProperties(
                    policy = "strict-origin-when-cross-origin"
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Referrer-Policy is set
            verify(response).setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        }

        test("should add Permissions-Policy header") {
            // Given: Default permissions policy
            val properties = SecurityHeadersProperties(
                permissionsPolicy = SecurityHeadersProperties.PermissionsPolicyProperties(
                    policy = "geolocation=(), microphone=(), camera=()"
                )
            )
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Permissions-Policy is set
            verify(response).setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        }

        test("should add Cache-Control headers for sensitive content") {
            // Given: Security headers enabled
            val properties = SecurityHeadersProperties(enabled = true)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            whenever(response.containsHeader("Cache-Control")).thenReturn(false)

            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Cache-Control headers are set
            verify(response).setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            verify(response).setHeader("Pragma", "no-cache")
            verify(response).setHeader("Expires", "0")
        }

        test("should not override existing Cache-Control header") {
            // Given: Response already has Cache-Control
            val properties = SecurityHeadersProperties(enabled = true)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            whenever(response.containsHeader("Cache-Control")).thenReturn(true)

            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Cache-Control is not overridden
            verify(response, org.mockito.kotlin.never()).setHeader(
                org.mockito.kotlin.eq("Cache-Control"),
                org.mockito.kotlin.any()
            )
        }
    }

    context("Global Enablement") {
        test("should not add any headers when globally disabled") {
            // Given: Security headers globally disabled
            val properties = SecurityHeadersProperties(enabled = false)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: No security headers are set
            verify(response, org.mockito.kotlin.never()).setHeader(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.any()
            )
        }

        test("should still call filter chain when disabled") {
            // Given: Security headers disabled
            val properties = SecurityHeadersProperties(enabled = false)
            val filter = SecurityHeadersFilter(properties)

            val request = mock<HttpServletRequest>()
            val response = mock<HttpServletResponse>()
            val filterChain = mock<FilterChain>()

            // When: Filter processes request
            filter.doFilterInternal(request, response, filterChain)

            // Then: Filter chain is still called
            verify(filterChain).doFilter(request, response)
        }
    }

    context("Property Defaults") {
        test("should use sensible defaults") {
            // Given: Default properties
            val properties = SecurityHeadersProperties()

            // Then: Defaults are secure
            properties.enabled shouldBe true
            properties.hsts.enabled shouldBe true
            properties.hsts.maxAge shouldBe 31536000 // 1 year
            properties.hsts.includeSubdomains shouldBe true
            properties.csp.enabled shouldBe true
            properties.csp.reportOnly shouldBe false
            properties.frameOptions.enabled shouldBe true
            properties.frameOptions.policy shouldBe "DENY"
            properties.referrerPolicy.enabled shouldBe true
            properties.permissionsPolicy.enabled shouldBe true
        }
    }
})
