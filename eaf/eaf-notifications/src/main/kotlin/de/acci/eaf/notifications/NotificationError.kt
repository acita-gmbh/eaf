package de.acci.eaf.notifications

/**
 * Notification errors that can occur during email sending.
 * Used with [de.acci.eaf.core.result.Result] for explicit error handling.
 */
public sealed interface NotificationError {
    /** The underlying cause message, if available. */
    public val message: String

    /**
     * Template not found or failed to render.
     */
    public data class TemplateError(
        public val templateName: String,
        override val message: String
    ) : NotificationError

    /**
     * SMTP connection or authentication failure.
     */
    public data class ConnectionError(
        override val message: String
    ) : NotificationError

    /**
     * Email address rejected by the mail server.
     */
    public data class InvalidRecipient(
        public val recipient: EmailAddress,
        override val message: String
    ) : NotificationError

    /**
     * Generic send failure (e.g., timeout, server error).
     */
    public data class SendFailure(
        override val message: String
    ) : NotificationError

    /**
     * No SMTP configuration available for the tenant.
     */
    public data class ConfigurationMissing(
        override val message: String = "No SMTP configuration available"
    ) : NotificationError
}
