package com.axians.eaf.framework.web.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI 3.0 configuration for EAF REST API documentation.
 *
 * Configures:
 * - API metadata (title, version, description, contact)
 * - Bearer JWT security scheme (placeholder for future security implementation)
 * - Swagger UI accessible at /swagger-ui.html
 * - OpenAPI JSON spec at /v3/api-docs
 *
 * Story 2.12: OpenAPI Documentation and Swagger UI
 */
@Configuration
open class OpenApiConfiguration {
    @Bean
    open fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("EAF v1.0 API")
                    .version("1.0.0")
                    .description("Enterprise Application Framework REST API")
                    .contact(
                        Contact()
                            .name("Axians EAF Team")
                            .email("eaf-team@axians.com"),
                    ),
            ).components(
                Components()
                    .addSecuritySchemes(
                        "bearer-jwt",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT Bearer token from Keycloak"),
                    ),
            ).security(listOf(SecurityRequirement().addList("bearer-jwt")))
}
