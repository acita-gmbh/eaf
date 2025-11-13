package com.axians.eaf.framework.security.filter

import com.axians.eaf.framework.security.InjectionDetector
import com.axians.eaf.framework.security.jwks.KeycloakJwksProvider
import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import com.axians.eaf.framework.security.role.RoleNormalizer
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.validation.JwtAlgorithmValidator
import com.axians.eaf.framework.security.validation.JwtAudienceValidator
import com.axians.eaf.framework.security.validation.JwtClaimSchemaValidator
import com.axians.eaf.framework.security.validation.JwtInjectionValidator
import com.axians.eaf.framework.security.validation.JwtIssuerValidator
import com.axians.eaf.framework.security.validation.JwtRevocationValidator
import com.axians.eaf.framework.security.validation.JwtTimeBasedValidator
import com.axians.eaf.framework.security.validation.JwtUserValidator
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import java.time.Instant

/**
 * JWT Validation Filter - Orchestrates all 10 layers of JWT validation.
 *
 * This filter implements comprehensive JWT validation with fail-fast behavior,
 * metrics emission, and Spring Security context population. All 10 validation
 * layers are executed in sequence with performance monitoring.
 *
 * **10-Layer JWT Validation (Architecture Section 16):**
 * 1. **Format Validation** - JWT structure and header extraction
 * 2. **Signature Validation** - RS256 cryptographic verification with JWKS
 * 3. **Algorithm Validation** - RS256 enforcement (reject HS256 algorithm confusion)
 * 4. **Claim Schema Validation** - Required claims presence (sub, iss, aud, exp, iat, tenant_id, roles)
 * 5. **Time-based Validation** - exp/iat/nbf with configurable 30s clock skew tolerance
 * 6. **Issuer/Audience Validation** - Trust boundary enforcement
 * 7. **Revocation Check** - Redis blacklist verification
 * 8. **Role Validation** - Role whitelist and privilege escalation detection
 * 9. **User Validation** - Optional user existence/active status check (configurable)
 * 10. **Injection Detection** - SQL/XSS/JNDI/Expression/Path Traversal pattern detection
 *
 * **Fail-Fast Behavior:** Validation stops at first failure with appropriate HTTP status.
 * **Metrics:** validation_layer_duration, validation_failures_by_layer counters.
 * **Performance Target:** <50ms total validation time (Architecture Decision #10).
 *
 * Story 3.9: Complete 10-Layer JWT Validation Integration
 */
@Suppress("LongParameterList") // 10-Layer validation requires comprehensive dependencies
class JwtValidationFilter(
    private val jwtDecoder: JwtDecoder,
    private val roleNormalizer: RoleNormalizer,
    private val revocationStore: TokenRevocationStore,
    private val userDirectory: UserDirectory,
    private val injectionDetector: InjectionDetector,
    private val meterRegistry: MeterRegistry,
    private val keycloakConfig: com.axians.eaf.framework.security.config.KeycloakOidcConfiguration,
    private val userValidationEnabled: Boolean = false
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtValidationFilter::class.java)

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    /**
     * Executes the complete 10-layer JWT validation sequence.
     *
     * Extracts JWT from Authorization header, validates through all 10 layers
     * with fail-fast behavior, emits metrics, and populates SecurityContext on success.
     */
    @Suppress("TooGenericExceptionCaught") // Security filter fail-closed: catch all unexpected errors
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.nanoTime()

        try {
            // Layer 1: Format validation - Extract and validate JWT
            val token = extractAndValidateTokenFormat(request)
                ?: return filterChain.doFilter(request, response)

            // Execute validation layers 2-10 with metrics and fail-fast
            val validatedJwt = validateAllLayers(token)

            // Success: Populate Spring Security context
            val authentication = buildAuthentication(validatedJwt)
            SecurityContextHolder.getContext().authentication = authentication

            // Record total validation time
            recordTotalValidationTime(Duration.ofNanos(System.nanoTime() - startTime))

            filterChain.doFilter(request, response)

        } catch (e: JwtValidationException) {
            // Fail-fast: Record failure metrics and return error
            recordValidationFailure(e.layer, e.cause)
            handleValidationFailure(response, e)
        } catch (e: Exception) {
            // Unexpected error - log and fail closed
            log.error("Unexpected error during JWT validation", e)
            recordValidationFailure("unknown", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed")
        }
    }

    /**
     * Layer 1: Format Validation - Extracts and validates JWT from Authorization header.
     *
     * @return JWT string if present and properly formatted, null if no auth required
     * @throws JwtValidationException if JWT format is invalid
     */
    private fun extractAndValidateTokenFormat(request: HttpServletRequest): String? {
        val authHeader = request.getHeader(AUTHORIZATION_HEADER) ?: return null

        return Timer.builder("jwt_validation_layer_duration")
            .tag("layer", "1")
            .tag("operation", "format_validation")
            .register(meterRegistry)
            .recordCallable {
                when {
                    !authHeader.startsWith(BEARER_PREFIX) ->
                        throw JwtValidationException(
                            layer = "1",
                            message = "Authorization header must start with 'Bearer '",
                            cause = IllegalArgumentException("Invalid authorization header format")
                        )

                    authHeader.length <= BEARER_PREFIX.length ->
                        throw JwtValidationException(
                            layer = "1",
                            message = "Authorization header missing JWT token",
                            cause = IllegalArgumentException("Empty JWT token")
                        )

                    else -> authHeader.substring(BEARER_PREFIX.length).trim()
                }
            }
    }

    /**
     * Executes validation layers 2-10 in sequence with fail-fast behavior.
     *
     * Each layer is timed individually and failures are recorded with metrics.
     *
     * @param tokenString Raw JWT string
     * @return Validated Jwt object
     * @throws JwtValidationException on first validation failure
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
    // Complexity is inherent: 10 sequential validation layers with individual error handling
    private fun validateAllLayers(tokenString: String): Jwt {
        // Layer 2: Signature validation (delegated to JwtDecoder)
        val jwt = validateLayer("2", "signature_validation") {
            jwtDecoder.decode(tokenString)
        }

        // Layer 3: Algorithm validation
        validateLayer("3", "algorithm_validation") {
            JwtAlgorithmValidator().validate(jwt).let { result ->
                if (result.hasErrors()) {
                    throw JwtValidationException(
                        layer = "3",
                        message = result.errors.first().description,
                        cause = IllegalArgumentException(result.errors.first().description)
                    )
                }
            }
        }

        // Layer 4: Claim schema validation
        validateLayer("4", "claim_schema_validation") {
            JwtClaimSchemaValidator().validate(jwt).let { result ->
                if (result.hasErrors()) {
                    throw JwtValidationException(
                        layer = "4",
                        message = result.errors.first().description,
                        cause = IllegalArgumentException(result.errors.first().description)
                    )
                }
            }
        }

        // Layer 5: Time-based validation
        validateLayer("5", "time_based_validation") {
            JwtTimeBasedValidator().validate(jwt).let { result ->
                if (result.hasErrors()) {
                    throw JwtValidationException(
                        layer = "5",
                        message = result.errors.first().description,
                        cause = IllegalArgumentException(result.errors.first().description)
                    )
                }
            }
        }

        // Layer 6: Issuer/Audience validation
        validateLayer("6", "issuer_audience_validation") {
            val issuerResult = JwtIssuerValidator(keycloakConfig.issuerUri).validate(jwt)
            if (issuerResult.hasErrors()) {
                throw JwtValidationException(
                    layer = "6",
                    message = issuerResult.errors.first().description,
                    cause = IllegalArgumentException(issuerResult.errors.first().description)
                )
            }

            val audienceResult = JwtAudienceValidator(keycloakConfig.audience).validate(jwt)
            if (audienceResult.hasErrors()) {
                throw JwtValidationException(
                    layer = "6",
                    message = audienceResult.errors.first().description,
                    cause = IllegalArgumentException(audienceResult.errors.first().description)
                )
            }
        }

        // Layer 7: Revocation check
        validateLayer("7", "revocation_check") {
            JwtRevocationValidator(revocationStore).validate(jwt).let { result ->
                if (result.hasErrors()) {
                    throw JwtValidationException(
                        layer = "7",
                        message = result.errors.first().description,
                        cause = IllegalArgumentException(result.errors.first().description)
                    )
                }
            }
        }

        // Layer 8: Role validation
        validateLayer("8", "role_validation") {
            validateRoles(jwt)
        }

        // Layer 9: User validation (optional)
        if (userValidationEnabled) {
            validateLayer("9", "user_validation") {
                JwtUserValidator(keycloakConfig, userDirectory).validate(jwt).let { result ->
                    if (result.hasErrors()) {
                        throw JwtValidationException(
                            layer = "9",
                            message = "User validation failed",
                            cause = IllegalArgumentException(result.errors.first().description)
                        )
                    }
                }
            }
        }

        // Layer 10: Injection detection
        validateLayer("10", "injection_detection") {
            JwtInjectionValidator(injectionDetector).validate(jwt).let { result ->
                if (result.hasErrors()) {
                    throw JwtValidationException(
                        layer = "10",
                        message = result.errors.first().description,
                        cause = IllegalArgumentException(result.errors.first().description)
                    )
                }
            }
        }

        return jwt
    }

    /**
     * Layer 8: Role Validation - Validates roles are present and properly formatted.
     *
     * Prevents privilege escalation by ensuring roles exist and are normalized.
     *
     * @throws JwtValidationException if role validation fails
     */
    @Suppress("ThrowsCount") // Multiple validation checks require distinct error conditions
    private fun validateRoles(jwt: Jwt) {
        val roles = jwt.getClaimAsStringList("roles")

        when {
            roles.isNullOrEmpty() ->
                throw JwtValidationException(
                    layer = "8",
                    message = "JWT missing roles claim",
                    cause = IllegalArgumentException("No roles present in token")
                )

            roles.any { it.isBlank() } ->
                throw JwtValidationException(
                    layer = "8",
                    message = "JWT contains empty role",
                    cause = IllegalArgumentException("Blank role detected")
                )

            else -> {
                // Validate roles are properly formatted (basic validation)
                roles.forEach { role ->
                    if (role.length > 100) { // Reasonable length limit
                        throw JwtValidationException(
                            layer = "8",
                            message = "Role too long: $role",
                            cause = IllegalArgumentException("Role exceeds maximum length")
                        )
                    }
                    if (!role.matches(Regex("^[a-zA-Z0-9:_-]+$"))) { // Alphanumeric, colon, underscore, dash
                        throw JwtValidationException(
                            layer = "8",
                            message = "Invalid role format: $role",
                            cause = IllegalArgumentException("Role contains invalid characters")
                        )
                    }
                }
            }
        }
    }

    /**
     * Executes a validation layer with timing and error handling.
     *
     * @param layer Layer number (2-10)
     * @param operation Human-readable operation name
     * @param block Validation logic to execute
     * @throws JwtValidationException if validation fails
     */
    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    // Infrastructure observability pattern: catch Exception for metrics, re-throw immediately
    private inline fun <T : Any> validateLayer(
        layer: String,
        operation: String,
        crossinline block: () -> T
    ): T {
        return Timer.builder("jwt_validation_layer_duration")
            .tag("layer", layer)
            .tag("operation", operation)
            .register(meterRegistry)
            .recordCallable {
                try {
                    block()
                } catch (e: JwtValidationException) {
                    throw e // Re-throw our custom exceptions
                } catch (e: Exception) {
                    // Wrap unexpected exceptions
                    throw JwtValidationException(
                        layer = layer,
                        message = "Validation failed for layer $layer",
                        cause = e
                    )
                }
            } ?: throw JwtValidationException(
                layer = layer,
                message = "Validation returned null for layer $layer"
            )
    }

    /**
     * Builds Spring Security Authentication from validated JWT.
     */
    private fun buildAuthentication(jwt: Jwt): Authentication {
        val normalizedAuthorities = roleNormalizer.normalize(jwt)

        return JwtAuthenticationToken(jwt, normalizedAuthorities)
    }

    /**
     * Records total validation time for performance monitoring.
     */
    private fun recordTotalValidationTime(duration: Duration) {
        Timer.builder("jwt_validation_total_duration")
            .register(meterRegistry)
            .record(duration)
    }

    /**
     * Records validation failures by layer for monitoring and alerting.
     */
    private fun recordValidationFailure(layer: String, cause: Throwable?) {
        meterRegistry.counter("jwt_validation_failures_total", "layer", layer).increment()
        log.warn("JWT validation failed at layer $layer: ${cause?.message}")
    }

    /**
     * Handles validation failures with appropriate HTTP responses.
     */
    private fun handleValidationFailure(response: HttpServletResponse, e: JwtValidationException) {
        val status = when (e.layer) {
            "1", "2", "3", "4", "5", "6" -> HttpServletResponse.SC_UNAUTHORIZED
            "7", "8", "9", "10" -> HttpServletResponse.SC_FORBIDDEN
            else -> HttpServletResponse.SC_BAD_REQUEST
        }

        response.sendError(status, "Authentication failed")
    }
}

/**
 * Custom exception for JWT validation failures with layer context.
 */
class JwtValidationException(
    val layer: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
