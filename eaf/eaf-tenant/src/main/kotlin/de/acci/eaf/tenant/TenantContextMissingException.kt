package de.acci.eaf.tenant

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Fail-closed error when tenant context is absent. Produces HTTP 403 in WebFlux. */
public class TenantContextMissingException(
    message: String = "Tenant context is missing. All requests must include tenant_id."
) : ResponseStatusException(HttpStatus.FORBIDDEN, message)
