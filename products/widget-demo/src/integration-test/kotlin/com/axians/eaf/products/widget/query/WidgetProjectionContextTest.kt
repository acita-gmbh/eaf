package com.axians.eaf.products.widget.query

import com.axians.eaf.products.widget.WidgetDemoApplication
import com.axians.eaf.products.widget.test.config.AxonTestConfiguration
import com.axians.eaf.products.widget.test.config.TestAutoConfigurationOverrides
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Minimal context test to verify Spring Boot can start with Testcontainers.
 *
 * Uses Spring Boot 3.1+ @ServiceConnection for automatic datasource configuration.
 */
@Testcontainers
@SpringBootTest(
    classes = [WidgetDemoApplication::class],
    properties = [
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.defer-datasource-initialization=true",
        TestAutoConfigurationOverrides.DISABLE_MODULITH_JPA,
    ],
)
@Import(AxonTestConfiguration::class)
@Sql("/schema.sql")
@ActiveProfiles("test")
class WidgetProjectionContextTest : FunSpec() {
    @org.springframework.beans.factory.annotation.Autowired
    private lateinit var applicationContext: ApplicationContext

    init {
        extension(SpringExtension())

        test("Spring context loads successfully") {
            applicationContext shouldNotBe null
        }

        test("widget_projection table exists") {
            val dsl = applicationContext.getBean(DSLContext::class.java)
            val tableExists =
                dsl
                    .fetchOne(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'widget_projection')",
                    )?.get(0, Boolean::class.java)

            tableExists shouldBe true
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"))
                .withDatabaseName("eaf_test")
                .withUsername("test")
                .withPassword("test")
    }
}
