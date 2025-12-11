package de.acci.dvmm

import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.eaf.eventsourcing.EventStore
import io.mockk.mockk
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

/**
 * Smoke test that verifies the Spring Boot application context loads correctly.
 *
 * This test ensures that:
 * - All Spring components are correctly wired
 * - Configuration is valid
 * - No circular dependencies exist
 *
 * Note: Database-dependent beans are mocked since this test runs without Testcontainers.
 * For full integration tests with database, see VmRequestIntegrationTest.
 */
@SpringBootTest(
    classes = [DvmmApplication::class, DvmmApplicationTest.TestConfig::class, TestNotificationConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("test")
class DvmmApplicationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Configuration
    class TestConfig {
        // Mock the entire database dependency chain since this test runs without Testcontainers
        @Bean
        @Primary
        fun dataSource(): DataSource = mockk(relaxed = true)

        @Bean
        @Primary
        fun dslContext(): DSLContext = mockk(relaxed = true)

        @Bean
        @Primary
        fun eventStore(): EventStore = mockk(relaxed = true)

        @Bean
        @Primary
        fun createVmRequestHandler(): CreateVmRequestHandler = mockk(relaxed = true)
    }

    @Test
    fun `application context loads successfully`() {
        // Verify context is loaded and application bean exists
        assertNotNull(applicationContext)
        assertNotNull(applicationContext.getBean(DvmmApplication::class.java))
    }
}
