package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result

/**
 * Engine for rendering notification templates.
 *
 * Abstracts the template technology (Thymeleaf, Mustache, etc.)
 * from the notification service.
 */
public interface TemplateEngine {
    /**
     * Render a template with the given context.
     *
     * @param templateName The template identifier (without extension)
     * @param context Variables available in the template
     * @return The rendered HTML content, or a [NotificationError.TemplateError]
     */
    public fun render(
        templateName: String,
        context: Map<String, Any>
    ): Result<String, NotificationError.TemplateError>
}
