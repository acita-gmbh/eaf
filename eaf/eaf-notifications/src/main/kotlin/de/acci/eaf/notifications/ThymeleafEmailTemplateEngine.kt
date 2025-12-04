package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import io.github.oshai.kotlinlogging.KotlinLogging
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.exceptions.TemplateEngineException

private val logger = KotlinLogging.logger {}

/**
 * Thymeleaf-based template engine for rendering email templates.
 *
 * Templates are expected to be in `templates/email/` directory
 * with `.html` extension.
 */
public class ThymeleafEmailTemplateEngine(
    private val thymeleafEngine: ITemplateEngine
) : TemplateEngine {

    override fun render(
        templateName: String,
        context: Map<String, Any>
    ): Result<String, NotificationError.TemplateError> {
        val templatePath = "email/$templateName"
        return try {
            val thymeleafContext = Context().apply {
                setVariables(context)
            }
            val rendered = thymeleafEngine.process(templatePath, thymeleafContext)
            rendered.success()
        } catch (e: TemplateEngineException) {
            logger.warn(e) { "Failed to render template: $templatePath" }
            NotificationError.TemplateError(
                templateName = templateName,
                message = "Template rendering failed: ${e.message}"
            ).failure()
        }
    }
}
