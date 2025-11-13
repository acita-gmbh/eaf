package com.axians.eaf.framework.security.validation

import com.axians.eaf.framework.security.config.KeycloakOidcConfiguration
import com.axians.eaf.framework.security.user.UserDirectory
import com.axians.eaf.framework.security.user.UserValidationException
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/** Layer 9 validator ensuring JWT subjects map to active users. */
@Component
class JwtUserValidator(
    private val keycloakConfig: KeycloakOidcConfiguration,
    private val userDirectory: UserDirectory,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        run {
            if (!keycloakConfig.validateUser) {
                return@run OAuth2TokenValidatorResult.success()
            }

            val subject = token.subject
            if (subject.isNullOrBlank()) {
                return@run failure("JWT missing subject (sub) claim required for user validation.")
            }

            try {
                val record = userDirectory.findById(subject)
                when {
                    record == null || !record.active -> failure("JWT subject user is invalid (Layer 9)")
                    else -> OAuth2TokenValidatorResult.success()
                }
            } catch (ex: UserValidationException) {
                log.warn("Layer 9 user validation failed for subject {}", subject, ex)
                failure("Unable to validate JWT subject user at this time. Please retry later.")
            }
        }

    private fun failure(description: String): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))

    companion object {
        private val log = LoggerFactory.getLogger(JwtUserValidator::class.java)
    }
}
