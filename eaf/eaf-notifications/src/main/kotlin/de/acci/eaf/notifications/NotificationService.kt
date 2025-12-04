package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId

/**
 * Service for sending notifications (emails).
 *
 * Implementations should be non-blocking and handle SMTP configuration
 * based on the provided [TenantId] (for future per-tenant SMTP support).
 *
 * For MVP, system-default SMTP configuration is used regardless of tenant.
 */
public interface NotificationService {
    /**
     * Send an email using a template.
     *
     * @param tenantId The tenant context (for future per-tenant SMTP config)
     * @param recipient The email recipient
     * @param subject The email subject line
     * @param templateName The template identifier (e.g., "vm-request-created")
     * @param context Template variables for rendering
     * @return Success with Unit, or Failure with [NotificationError]
     */
    public suspend fun sendEmail(
        tenantId: TenantId,
        recipient: EmailAddress,
        subject: String,
        templateName: String,
        context: Map<String, Any>
    ): Result<Unit, NotificationError>
}
