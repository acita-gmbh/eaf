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
 *
 * Story 6.2: Added @Profile("!test") to prevent loading in test environments.
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
        private const val ROLE_PREFIX = "ROLE_"
        private const val MAX_ROLE_LENGTH = 256
        private val ALLOWED_ROLE_PATTERN =
            java.util.regex.Pattern
                .compile("^[\\p{L}\\p{N}_\\-\\.]+$")

        /**
         * Normalizes role name with comprehensive validation and prefix handling.
         *
         * Production-grade normalization that:
         * - Strips ALL leading "ROLE_" prefixes (case-insensitive, handles ROLE_ROLE_X)
         * - Validates character whitelist (Unicode letters/digits, underscore, hyphen, dot)
         * - Enforces fail-closed design (throws on null, empty, invalid chars, too long)
         * - Guarantees idempotence: normalize(normalize(x)) == normalize(x)
         * - Prevents injection attacks (LDAP, SQL, shell via character whitelist)
         *
         * Security requirements (Story 5.2 Security Review):
         * - Prevents authorization bypass from Keycloak misconfigurations
         * - Handles multiple ROLE_ prefixes (e.g., "ROLE_ROLE_eaf-admin" misconfiguration)
         * - Rejects empty/blank role names that would create invalid authorities
         * - Protects against injection attempts via character whitelist
         * - Fails fast on invalid input (fail-closed design)
         *
         * Example transformations:
         * - "eaf-admin" → "ROLE_eaf-admin"
         * - "ROLE_eaf-admin" → "ROLE_eaf-admin" (idempotent)
         * - "ROLE_ROLE_admin" → "ROLE_admin" (handles misconfiguration)
         * - "" → throws IllegalArgumentException (fail-closed)
         * - "admin;" → throws IllegalArgumentException (injection blocked)
         *
         * @param roleName Role name from JWT (never null in Kotlin, but defensively checked)
         * @return SimpleGrantedAuthority with canonical ROLE_ prefix
         * @throws IllegalArgumentException if input is null, empty, contains forbidden chars, or too long
         */
        fun normalizeRoleAuthority(roleName: String?): SimpleGrantedAuthority {
            // Defensive null-safety for Java interop
            val role = roleName ?: throw IllegalArgumentException("Role name must not be null")

            // Trim all whitespace (leading, trailing, handles tabs/newlines)
            var body = role.trim()
            require(body.isNotEmpty()) { "Role name must contain non-whitespace characters" }

            // Strip ALL leading "ROLE_" prefixes (case-insensitive)
            // Handles: ROLE_admin, ROLE_ROLE_admin, role_admin, RoLe_admin
            // Using startsWith(ignoreCase=true) avoids repeated uppercase conversions
            while (body.startsWith(ROLE_PREFIX, ignoreCase = true)) {
                body = body.substring(ROLE_PREFIX.length).trim()
            }

            // Verify we didn't strip everything (pure prefix inputs like "ROLE_" or "ROLE_ROLE_")
            require(body.isNotEmpty()) { "Role name is only prefixes (e.g., \"ROLE_\", \"ROLE_ROLE_\")" }

            // Permission-style authorities (e.g., widget:create, resource:action)
            // Support colon-delimited format but validate each segment for security
            val containsPermissionSeparator = ':' in body

            if (containsPermissionSeparator) {
                // Validate permission-style authority segments
                val segments = body.split(':')

                // Prevent empty segments (e.g., "widget:", ":create", "widget::create")
                require(segments.none(String::isBlank)) {
                    "Permission-style authority has empty segment: '$role'"
                }

                // Each segment must pass character whitelist validation
                require(segments.all { ALLOWED_ROLE_PATTERN.matcher(it).matches() }) {
                    "Permission-style authority contains prohibited characters. " +
                        "Only Unicode letters, digits, underscore (_), hyphen (-), and dot (.) allowed. " +
                        "Received: '$role'"
                }
            } else {
                // Enforce character whitelist: Unicode letters, digits, underscore, hyphen, dot only
                // Prevents injection attacks (SQL, LDAP, shell) and ensures predictable behavior
                require(ALLOWED_ROLE_PATTERN.matcher(body).matches()) {
                    "Role contains prohibited characters. " +
                        "Only Unicode letters, digits, underscore (_), hyphen (-), and dot (.) allowed. " +
                        "Received: '$role' (normalized body: '$body')"
                }
            }

            // Prevent DoS via extremely long role/permission names (applies to both forms)
            require(body.length <= MAX_ROLE_LENGTH) {
                "Role name too long (${body.length} > $MAX_ROLE_LENGTH). Received: '$role'"
            }

            return if (containsPermissionSeparator) {
                // Permission-style: use as-is without ROLE_ prefix
                SimpleGrantedAuthority(body)
            } else {
                // Traditional role: add ROLE_ prefix
                SimpleGrantedAuthority("$ROLE_PREFIX$body")
            }
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            log.debug("JwtValidationFilter processing request: {}", request.requestURI)
            val token = extractToken(request)

            if (token != null) {
                log.debug("JwtValidationFilter extracted token")
                // Apply 10-layer validation
                tenLayerValidator.validateTenLayers(token).fold(
                    ifLeft = { error ->
                        log.warn("JWT validation failed: {}", error)
                        handleValidationError(response, error)
                        return
                    },
                    ifRight = { validationResult ->
                        // Set authentication context for Spring Security
                        log.debug("JWT validation succeeded for user {}", validationResult.user.id)
                        val jwt = jwtDecoder.decode(token)
                        val authorities =
                            validationResult.roles
                                .map { role -> normalizeRoleAuthority(role.name) }
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
