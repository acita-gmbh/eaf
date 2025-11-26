package de.acci.eaf.tenant

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.acci.eaf.core.types.TenantId
import java.util.Base64

/**
 * Extracts tenant_id claim from JWT payload.
 *
 * NOTE: This extractor assumes the JWT has already been validated upstream.
 * Signature verification happens via Spring Security OAuth2 Resource Server
 * (Story 1.7 - Keycloak Integration). This component only extracts the claim
 * from pre-validated tokens.
 */
internal object JwtTenantClaimExtractor {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    fun extractTenantId(token: String): TenantId? {
        val parts = token.split('.')
        if (parts.size < 2) return null

        val payloadJson = runCatching {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            String(decoded)
        }.getOrNull() ?: return null

        val node = runCatching { mapper.readTree(payloadJson) }.getOrNull() ?: return null
        val tenantValue = node.get("tenant_id")?.asText() ?: return null

        return runCatching { TenantId.fromString(tenantValue) }.getOrNull()
    }
}
