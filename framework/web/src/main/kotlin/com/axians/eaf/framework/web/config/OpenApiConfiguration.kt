package com.axians.eaf.framework.web.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI configuration for EAF REST APIs.
 * Documents security requirements for JWT authentication.
 */
@Configuration
open class OpenApiConfiguration {
    @Bean
    open fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("EAF REST API")
                    .description("Enterprise Application Framework REST API with JWT authentication")
                    .version("v0.1"),
            ).components(
                Components()
                    .addSecuritySchemes(
                        "BearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token obtained from Keycloak OIDC authentication"),
                    ),
            )
}
