package com.axians.eaf.products.widget.api.auth

import com.axians.eaf.framework.security.revocation.TokenRevocationStore
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Administrative API for revoking JWT tokens (Layer 7).
 */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val revocationStore: TokenRevocationStore,
    private val jwtDecoder: JwtDecoder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeToken(
        @Valid @RequestBody request: RevokeTokenRequest,
    ) {
        val jwt =
            runCatching { jwtDecoder.decode(request.token.trim()) }
                .onFailure { logger.debug("Failed to decode JWT", it) }
                .getOrElse {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to decode JWT")
                }

        val jti =
            jwt.id?.takeIf { it.isNotBlank() }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT missing jti claim")

        revocationStore.revoke(jti, jwt.expiresAt)
        logger.debug("Token revoked for subject={} (jti={})", jwt.subject, jti)
        logger.info("Token revoked (jti={})", jti)
    }
}

data class RevokeTokenRequest(
    @field:NotBlank
    val token: String,
)
