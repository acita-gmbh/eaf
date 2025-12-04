package de.acci.eaf.notifications

import de.acci.eaf.core.result.Result
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import org.thymeleaf.exceptions.TemplateInputException

class ThymeleafEmailTemplateEngineTest {

    private val thymeleafEngine = mockk<ITemplateEngine>()
    private val templateEngine = ThymeleafEmailTemplateEngine(thymeleafEngine)

    @Test
    fun `render returns success with rendered content`() {
        val templateName = "test-template"
        val context = mapOf("name" to "World")
        val expectedHtml = "<html><body>Hello World</body></html>"

        every {
            thymeleafEngine.process(eq("email/$templateName"), any<Context>())
        } returns expectedHtml

        val result = templateEngine.render(templateName, context)

        assertTrue(result is Result.Success)
        assertEquals(expectedHtml, (result as Result.Success).value)
    }

    @Test
    fun `render passes context variables to thymeleaf`() {
        val templateName = "test-template"
        val context = mapOf("name" to "World", "count" to 42)

        every {
            thymeleafEngine.process(eq("email/$templateName"), any<Context>())
        } returns "<html></html>"

        templateEngine.render(templateName, context)

        verify {
            thymeleafEngine.process(
                eq("email/$templateName"),
                match<Context> { ctx ->
                    ctx.getVariable("name") == "World" &&
                    ctx.getVariable("count") == 42
                }
            )
        }
    }

    @Test
    fun `render returns failure when template not found`() {
        val templateName = "non-existent"

        every {
            thymeleafEngine.process(eq("email/$templateName"), any<Context>())
        } throws TemplateInputException("Template not found")

        val result = templateEngine.render(templateName, emptyMap())

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertEquals(templateName, error.templateName)
        assertTrue(error.message.contains("Template rendering failed"))
    }

    @Test
    fun `render uses email prefix for template path`() {
        val templateName = "vm-request-created"

        every {
            thymeleafEngine.process(any<String>(), any<Context>())
        } returns "<html></html>"

        templateEngine.render(templateName, emptyMap())

        verify {
            thymeleafEngine.process(eq("email/vm-request-created"), any<Context>())
        }
    }

    @Test
    fun `render returns failure when unexpected exception occurs`() {
        val templateName = "test-template"

        every {
            thymeleafEngine.process(eq("email/$templateName"), any<Context>())
        } throws RuntimeException("Unexpected database connection issue")

        val result = templateEngine.render(templateName, emptyMap())

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertEquals(templateName, error.templateName)
        assertTrue(error.message.contains("Unexpected template error"))
    }
}
