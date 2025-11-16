package com.axians.eaf.framework.security.ssrf

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for SSRF protection.
 *
 * Tests:
 * - Blocking private IP ranges (RFC 1918)
 * - Blocking loopback addresses
 * - Blocking link-local addresses
 * - Blocking cloud metadata services
 * - Scheme validation
 * - Host whitelist/blacklist
 * - IP range allowlist
 *
 * OWASP A01:2025 - Broken Access Control
 *
 * @since 1.0.0
 */
class SsrfProtectionTest :
    FunSpec({

        context("Scheme Validation") {
            test("should allow HTTP and HTTPS by default") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: HTTP and HTTPS should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("http://example.com")
                }
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("https://example.com")
                }
            }

            test("should block non-HTTP schemes") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: FTP should be blocked
                val exception =
                    shouldThrow<SsrfException> {
                        ssrf.validateUrl("ftp://example.com")
                    }
                exception.message shouldContain "not allowed"
            }

            test("should allow custom schemes") {
                // Given: SSRF protection allowing FTP
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(allowedSchemes = setOf("ftp")),
                    )

                // When/Then: FTP should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("ftp://example.com")
                }
            }
        }

        context("Private IP Blocking") {
            test("should block loopback addresses") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: Loopback IPs should be blocked
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://127.0.0.1")
                }
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://localhost")
                }
            }

            test("should block RFC 1918 private IP ranges") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: Private IPs should be blocked
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://10.0.0.1")
                }
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://172.16.0.1")
                }
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://192.168.1.1")
                }
            }

            test("should block link-local addresses") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: Link-local IP should be blocked
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://169.254.169.254")
                }
            }
        }

        context("Metadata Service Blocking") {
            test("should block AWS metadata service") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: AWS metadata service should be blocked
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://169.254.169.254/latest/meta-data/")
                }
            }

            test("should block GCP metadata service") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: GCP metadata service should be blocked
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://metadata.google.internal/computeMetadata/v1/")
                }
            }
        }

        context("Host Whitelist") {
            test("should allow only whitelisted hosts with public IPs") {
                // Given: SSRF protection with host whitelist
                // Note: Host whitelist performs hostname check, then IP validation (defense-in-depth)
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(
                            allowedHosts = setOf("www.google.com"), // Public IP
                        ),
                    )

                // When/Then: Whitelisted host with public IP should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("https://www.google.com")
                }

                // And: Non-whitelisted host should be blocked
                val exception =
                    shouldThrow<SsrfException> {
                        ssrf.validateUrl("https://other.com/data")
                    }
                exception.message shouldContain "not in allowed hosts list"
            }

            test("should support wildcard hosts with public IPs") {
                // Given: SSRF protection with wildcard whitelist
                // Note: Wildcard matching is done on hostname, then IP is validated
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(
                            allowedHosts = setOf("*.google.com"), // Public IPs
                        ),
                    )

                // When/Then: Wildcard subdomain with public IP should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("https://www.google.com")
                }
            }
        }

        context("Host Blacklist") {
            test("should block explicitly blocked hosts") {
                // Given: SSRF protection with host blacklist
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(
                            blockedHosts = setOf("localhost", "internal.company.com"),
                        ),
                    )

                // When/Then: Blocked host should be rejected
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://internal.company.com/api")
                }
            }
        }

        context("IP Range Allowlist") {
            test("should block IPs not in allowed ranges") {
                // Given: SSRF protection with limited IP allowlist
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(
                            allowedIpRanges = setOf("203.0.113.0/24"),
                        ),
                    )

                // When/Then: IP outside allowed range should be blocked
                // Note: Implementation validates ALL IPs against private ranges first
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://192.168.1.1/api") // Private IP
                }
            }
        }

        context("Disabled Protection") {
            test("should allow all URLs when disabled") {
                // Given: Disabled SSRF protection
                val ssrf =
                    SsrfProtection(
                        SsrfProtectionProperties(enabled = false),
                    )

                // When/Then: All URLs should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("http://127.0.0.1")
                }
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("http://169.254.169.254")
                }
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("ftp://example.com")
                }
            }
        }

        context("Public URLs") {
            test("should allow public internet URLs") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: Public URLs should be allowed
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("https://api.github.com/repos")
                }
                shouldNotThrow<SsrfException> {
                    ssrf.validateUrl("https://www.google.com")
                }
            }
        }

        context("Error Handling") {
            test("should handle invalid URLs") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: Invalid URL should throw exception
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("not-a-valid-url")
                }
            }

            test("should handle URLs without host") {
                // Given: Default SSRF protection
                val ssrf = SsrfProtection(SsrfProtectionProperties())

                // When/Then: URL without host should throw exception
                shouldThrow<SsrfException> {
                    ssrf.validateUrl("http://")
                }
            }
        }
    })
