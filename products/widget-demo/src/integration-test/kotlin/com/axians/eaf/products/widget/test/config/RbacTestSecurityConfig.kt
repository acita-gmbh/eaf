package com.axians.eaf.products.widget.test.config

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.time.Instant

/**
 * RBAC Test Security Configuration (Story 3.10).
 *
 * Enables method security (@PreAuthorize) for testing role-based access control.
 * Uses "rbac-test" profile to isolate from regular "test" profile.
 *
 * **CRITICAL ARCHITECTURAL INSIGHT (from 5 AI agents analysis):**
 * - `authenticated()` activates ExceptionTranslationFilter in the filter chain
 * - AccessDeniedException from @PreAuthorize is caught by ExceptionTranslationFilter
 * - ExceptionTranslationFilter delegates to AccessDeniedHandler → 403 Forbidden
 * - @ControllerAdvice does NOT work for Security exceptions (Filter Stack != MVC Stack)
 *
 * **CRITICAL:** Uses @Configuration (NOT @TestConfiguration) to match working TestSecurityConfig pattern.
 * @TestConfiguration may cause early initialization issues with Testcontainers lifecycle.
 *
 * Configuration:
 * - Method security enabled (@PreAuthorize support)
 * - HTTP security requires authentication (activates ExceptionTranslationFilter)
 * - Custom AccessDeniedHandler returns RFC 7807 ProblemDetail with 403
 * - Mock JwtDecoder for Spring Security Test JWT tokens
 * - Stateless session management
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("rbac-test")
open class RbacTestSecurityConfig {
    private val objectMapper = ObjectMapper()

    /**
     * Custom AccessDeniedHandler for RBAC tests.
     *
     * Returns RFC 7807 ProblemDetail with 403 Forbidden status when @PreAuthorize denies access.
     * This handler is registered in the SecurityFilterChain and invoked by ExceptionTranslationFilter.
     */
    private fun rbacTestAccessDeniedHandler() =
        AccessDeniedHandler { request, response, ex ->
            val problemDetail =
                ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN,
                    ex.message ?: "Access Denied",
                )
            problemDetail.type = URI.create("https://eaf.axians.com/errors/access-denied")
            problemDetail.instance = URI.create(request.requestURI)

            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.writer.write(objectMapper.writeValueAsString(problemDetail))
        }

    @Bean
    @Primary
    fun rbacTestSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // CRITICAL: authenticated() enables ExceptionTranslationFilter
                // This is required for proper 403 responses from @PreAuthorize
                auth.anyRequest().authenticated()
            }.oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }.exceptionHandling { eh ->
                // Register custom AccessDeniedHandler for 403 responses
                // ExceptionTranslationFilter will delegate AccessDeniedException here
                eh.accessDeniedHandler(rbacTestAccessDeniedHandler())
            }.build()

    /**
     * Mock JwtDecoder for testing @PreAuthorize with Spring Security Test mock JWTs.
     */
    @Bean
    @Primary
    open fun rbacTestJwtDecoder(): JwtDecoder {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKey = keyPair.public as RSAPublicKey

        return NimbusJwtDecoder.withPublicKey(publicKey).build()
    }

    @Bean
    @Primary
    open fun tokenRevocationStore(): TokenRevocationStore =
        object : TokenRevocationStore {
            override fun isRevoked(jti: String): Boolean = false

            override fun revoke(
                jti: String,
                expiresAt: Instant?,
            ) {
                // no-op for tests
            }
        }
}
