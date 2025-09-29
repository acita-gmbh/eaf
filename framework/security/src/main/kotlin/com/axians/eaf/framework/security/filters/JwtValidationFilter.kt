package com.axians.eaf.framework.security.filters

import com.axians.eaf.framework.security.errors.SecurityError
import com.axians.eaf.framework.security.jwt.TenLayerJwtValidator
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Spring Security filter that applies 10-layer JWT validation.
 * Integrates TenLayerJwtValidator with Spring Security filter chain.
 */
@Component
class JwtValidationFilter(
    private val tenLayerValidator: TenLayerJwtValidator,
    private val jwtDecoder: JwtDecoder,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    companion object {
        private val log = LoggerFactory.getLogger(JwtValidationFilter::class.java)
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val token = extractToken(request)

            if (token != null) {
                // Apply 10-layer validation
                tenLayerValidator.validateTenLayers(token).fold(
                    ifLeft = { error ->
                        handleValidationError(response, error)
                        return
                    },
                    ifRight = { validationResult ->
                        // Set authentication context for Spring Security
                        val jwt = jwtDecoder.decode(token)
                        val authorities =
                            validationResult.roles
                                .map { role -> SimpleGrantedAuthority("ROLE_${role.name}") }
                        val authentication =
                            JwtAuthenticationToken(
                                jwt,
                                authorities,
                                validationResult.user.id,
                            )
                        authentication.details =
                            mapOf(
                                "tenantId" to validationResult.tenantId,
                                "sessionId" to validationResult.sessionId,
                            )
                        SecurityContextHolder.getContext().authentication = authentication

                        log.debug(
                            "JWT validation successful for user: {}, tenant: {}",
                            validationResult.user.id,
                            validationResult.tenantId,
                        )
                    },
                )
            }

            filterChain.doFilter(request, response)
        } catch (e: IllegalArgumentException) {
            log.error("Invalid argument in JWT validation filter", e)
            handleUnexpectedError(response)
        } catch (e: SecurityException) {
            log.error("Security exception in JWT validation filter", e)
            handleUnexpectedError(response)
        }
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER)
        return if (authHeader?.startsWith(BEARER_PREFIX) == true) {
            authHeader.substring(BEARER_PREFIX.length)
        } else {
            null
        }
    }

    private fun handleValidationError(
        response: HttpServletResponse,
        error: SecurityError,
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse =
            mapOf(
                "error" to "unauthorized",
                "message" to error.message,
                "type" to error::class.simpleName,
                "timestamp" to System.currentTimeMillis(),
            )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))

        log.warn("JWT validation failed: {} - {}", error::class.simpleName, error.message)
    }

    private fun handleUnexpectedError(response: HttpServletResponse) {
        response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val errorResponse =
            mapOf(
                "error" to "internal_server_error",
                "message" to "Authentication processing error",
                "timestamp" to System.currentTimeMillis(),
            )

        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
