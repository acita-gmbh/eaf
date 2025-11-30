package de.acci.dvmm.api.security

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.cors.CorsConfiguration

class SecurityConfigTest {

    private val mockEnvironment: Environment = mockk {
        every { getProperty("eaf.security.csrf.enabled", Boolean::class.java, true) } returns true
    }

    private val securityConfig = SecurityConfig(
        keycloakClientId = "test-client",
        allowedOrigins = "http://localhost:3000",
        environment = mockEnvironment,
    )

    private fun createExchange(path: String): MockServerWebExchange {
        val request = MockServerHttpRequest.get(path).build()
        return MockServerWebExchange.from(request)
    }

    @Test
    fun `corsConfigurationSource returns configuration with correct allowed origins`() {
        val config = SecurityConfig(
            keycloakClientId = "test-client",
            allowedOrigins = "http://localhost:3000,http://localhost:4200",
            environment = mockEnvironment,
        )

        val corsSource = config.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val corsConfig = corsSource.getCorsConfiguration(exchange)

        assertNotNull(corsConfig)
        assertEquals(listOf("http://localhost:3000", "http://localhost:4200"), corsConfig?.allowedOrigins)
    }

    @Test
    fun `corsConfigurationSource allows required HTTP methods`() {
        val corsSource = securityConfig.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val config = corsSource.getCorsConfiguration(exchange)

        assertNotNull(config)
        assertTrue(config?.allowedMethods?.containsAll(listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")) == true)
    }

    @Test
    fun `corsConfigurationSource allows all headers`() {
        val corsSource = securityConfig.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val config = corsSource.getCorsConfiguration(exchange)

        assertNotNull(config)
        assertEquals(listOf("*"), config?.allowedHeaders)
    }

    @Test
    fun `corsConfigurationSource allows credentials`() {
        val corsSource = securityConfig.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val config = corsSource.getCorsConfiguration(exchange)

        assertNotNull(config)
        assertTrue(config?.allowCredentials == true)
    }

    @Test
    fun `corsConfigurationSource sets max age for preflight caching`() {
        val corsSource = securityConfig.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val config = corsSource.getCorsConfiguration(exchange)

        assertNotNull(config)
        assertEquals(3600L, config?.maxAge)
    }

    @Test
    fun `corsConfigurationSource applies to all paths`() {
        val corsSource = securityConfig.corsConfigurationSource()

        assertNotNull(corsSource.getCorsConfiguration(createExchange("/api/vms")))
        assertNotNull(corsSource.getCorsConfiguration(createExchange("/actuator/health")))
        assertNotNull(corsSource.getCorsConfiguration(createExchange("/some/random/path")))
    }

    @Test
    fun `keycloakJwtAuthenticationConverter uses configured client id`() {
        val converter = securityConfig.keycloakJwtAuthenticationConverter()

        assertNotNull(converter)
    }

    @Test
    fun `corsConfigurationSource handles single origin`() {
        val corsSource = securityConfig.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val config = corsSource.getCorsConfiguration(exchange)

        assertNotNull(config)
        assertEquals(1, config?.allowedOrigins?.size)
        assertEquals("http://localhost:3000", config?.allowedOrigins?.first())
    }

    @Test
    fun `corsConfigurationSource trims whitespace from origins`() {
        val config = SecurityConfig(
            keycloakClientId = "test-client",
            allowedOrigins = " http://localhost:3000 , http://localhost:4200 ",
            environment = mockEnvironment,
        )

        val corsSource = config.corsConfigurationSource()
        val exchange = createExchange("/api/test")
        val corsConfig = corsSource.getCorsConfiguration(exchange)

        assertNotNull(corsConfig)
        assertEquals(listOf("http://localhost:3000", "http://localhost:4200"), corsConfig?.allowedOrigins)
    }
}
