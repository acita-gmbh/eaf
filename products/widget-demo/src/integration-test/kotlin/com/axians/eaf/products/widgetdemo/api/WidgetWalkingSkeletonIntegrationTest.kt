package com.axians.eaf.products.widgetdemo.api

import com.axians.eaf.api.widget.dto.WidgetResponse
import com.axians.eaf.products.widgetdemo.WidgetDemoApplication
import com.axians.eaf.products.widgetdemo.test.NullableJwtDecoder
import com.axians.eaf.testing.containers.TestContainers
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.TimeUnit

@SpringBootTest(
    classes = [
        com.axians.eaf.products.widgetdemo.WidgetDemoApplication::class,
        com.axians.eaf.products.widgetdemo.test.WidgetDemoTestApplication::class,
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "otel.java.global-autoconfigure.enabled=false",
        "otel.sdk.disabled=true",
        "otel.traces.exporter=none",
        "otel.metrics.exporter=none",
        "otel.logs.exporter=none",
        "spring.jpa.hibernate.ddl-auto=update",
        "otel.instrumentation.spring-boot-starter.enabled=false",
        "otel.instrumentation.common.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "eaf.security.enable-oidc-decoder=false",
    ],
)
class WidgetWalkingSkeletonIntegrationTest : FunSpec() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    init {
        extension(SpringExtension())

        test("8.4-E2E-001: Complete Walking Skeleton flow - POST command → projection → GET query") {
            // Given: Widget creation request data
            val startTime = System.currentTimeMillis()
            val widgetRequest =
                mapOf(
                    "name" to "Walking Skeleton Widget",
                    "description" to "Epic 2 completion test widget",
                    "value" to 999.99,
                    "category" to "VALIDATION",
                    "metadata" to
                        mapOf(
                            "testType" to "walkingSkeleton",
                            "epic" to "2",
                            "story" to "2.4",
                        ),
                )

            // When: POST command to create widget
            val validToken = NullableJwtDecoder.validTokenValue
            val createResponse =
                mockMvc
                    .perform(
                        post("/widgets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer $validToken")
                            .content(objectMapper.writeValueAsString(widgetRequest)),
                    ).andExpect(status().isCreated)
                    .andReturn()

            val createResponseBody =
                objectMapper.readValue<Map<String, Any>>(
                    createResponse.response.contentAsString,
                )
            val widgetId = createResponseBody["id"] as String
            widgetId shouldNotBe null

            // Wait for projection to be updated (eventual consistency)
            var attempts = 0
            var widgetResponse: WidgetResponse? = null
            val maxAttempts = 20 // 2 seconds with 100ms intervals

            while (attempts < maxAttempts && widgetResponse == null) {
                TimeUnit.MILLISECONDS.sleep(100)
                attempts++

                try {
                    val queryResponse =
                        mockMvc
                            .perform(
                                get("/widgets/$widgetId")
                                    .header("Authorization", "Bearer $validToken"),
                            ).andReturn()

                    if (queryResponse.response.status == 200) {
                        widgetResponse =
                            objectMapper.readValue<WidgetResponse>(
                                queryResponse.response.contentAsString,
                            )
                    }
                } catch (e: Exception) {
                    // Continue waiting for projection
                }
            }

            // Then: Validate complete Walking Skeleton flow
            widgetResponse shouldNotBe null

            widgetResponse!!.id shouldBe widgetId
            widgetResponse.name shouldBe "Walking Skeleton Widget"
            widgetResponse.value shouldBe BigDecimal("999.99")

            val endTime = System.currentTimeMillis()
            val totalDuration = Duration.ofMillis(endTime - startTime)
            // Allow up to 2 seconds for eventual consistency (matching maxAttempts * 100ms polling)
            totalDuration.toMillis() shouldBeLessThan 2000L
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            TestContainers.postgres.start()
            TestContainers.redis.start()

            registry.add("spring.datasource.url", TestContainers.postgres::getJdbcUrl)
            registry.add("spring.datasource.username", TestContainers.postgres::getUsername)
            registry.add("spring.datasource.password", TestContainers.postgres::getPassword)
        }
    }
}
