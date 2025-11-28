package de.acci.dvmm

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

/**
 * Smoke test that verifies the Spring Boot application context loads correctly.
 *
 * This test ensures that:
 * - All Spring components are correctly wired
 * - Configuration is valid
 * - No circular dependencies exist
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DvmmApplicationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `application context loads successfully`() {
        // Verify context is loaded and application bean exists
        assertNotNull(applicationContext)
        assertNotNull(applicationContext.getBean(DvmmApplication::class.java))
    }
}
