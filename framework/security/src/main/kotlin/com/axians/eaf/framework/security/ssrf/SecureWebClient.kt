package com.axians.eaf.framework.security.ssrf

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono

/**
 * Secure WebClient wrapper with SSRF protection.
 *
 * Automatically validates all outbound HTTP requests before execution.
 *
 * Usage:
 * ```kotlin
 * @Autowired
 * private lateinit var secureWebClient: SecureWebClient
 *
 * val response = secureWebClient.get("https://api.example.com/data")
 *     .bodyToMono(String::class.java)
 *     .block()
 * ```
 *
 * OWASP A01:2025 - Broken Access Control
 *
 * Reference: docs/security/owasp-top-10-2025-compliance.md
 *
 * @since 1.0.0
 */
@Component
@ConditionalOnClass(WebClient::class)
class SecureWebClient(
    private val ssrfProtection: SsrfProtection,
    private val webClientBuilder: WebClient.Builder
) {

    /**
     * Create a GET request with SSRF protection.
     *
     * @param url The URL to request
     * @return WebClient.RequestHeadersSpec for further configuration
     * @throws SsrfException if the URL is blocked by SSRF protection
     */
    fun get(url: String): WebClient.RequestHeadersSpec<*> {
        validateUrl(url)
        return webClientBuilder.build()
            .get()
            .uri(url)
    }

    /**
     * Create a POST request with SSRF protection.
     *
     * @param url The URL to request
     * @return WebClient.RequestBodySpec for further configuration
     * @throws SsrfException if the URL is blocked by SSRF protection
     */
    fun post(url: String): WebClient.RequestBodySpec {
        validateUrl(url)
        return webClientBuilder.build()
            .post()
            .uri(url)
    }

    /**
     * Create a PUT request with SSRF protection.
     *
     * @param url The URL to request
     * @return WebClient.RequestBodySpec for further configuration
     * @throws SsrfException if the URL is blocked by SSRF protection
     */
    fun put(url: String): WebClient.RequestBodySpec {
        validateUrl(url)
        return webClientBuilder.build()
            .put()
            .uri(url)
    }

    /**
     * Create a DELETE request with SSRF protection.
     *
     * @param url The URL to request
     * @return WebClient.RequestHeadersSpec for further configuration
     * @throws SsrfException if the URL is blocked by SSRF protection
     */
    fun delete(url: String): WebClient.RequestHeadersSpec<*> {
        validateUrl(url)
        return webClientBuilder.build()
            .delete()
            .uri(url)
    }

    /**
     * Create a PATCH request with SSRF protection.
     *
     * @param url The URL to request
     * @return WebClient.RequestBodySpec for further configuration
     * @throws SsrfException if the URL is blocked by SSRF protection
     */
    fun patch(url: String): WebClient.RequestBodySpec {
        validateUrl(url)
        return webClientBuilder.build()
            .patch()
            .uri(url)
    }

    private fun validateUrl(url: String) {
        try {
            ssrfProtection.validateUrl(url)
        } catch (ex: SsrfException) {
            // Re-throw as Mono.error for reactive compatibility
            throw WebClientRequestException(ex, null, null, null)
        }
    }
}

/**
 * Auto-configuration for SSRF protection.
 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@org.springframework.boot.context.properties.EnableConfigurationProperties(SsrfProtectionProperties::class)
class SsrfProtectionAutoConfiguration {

    @org.springframework.context.annotation.Bean
    fun ssrfProtection(properties: SsrfProtectionProperties): SsrfProtection {
        return SsrfProtection(properties)
    }
}
