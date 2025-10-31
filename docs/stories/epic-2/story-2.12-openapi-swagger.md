# Story 2.12: OpenAPI Documentation and Swagger UI

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR002 (Developer Tooling), FR015 (Documentation)

---

## User Story

As a framework developer,
I want automatic OpenAPI 3.0 documentation generation,
So that API consumers have up-to-date, interactive API documentation.

---

## Acceptance Criteria

1. ✅ Springdoc OpenAPI 2.6.0 dependency added
2. ✅ OpenApiConfiguration.kt with API metadata (title, version, description)
3. ✅ Security scheme configured (Bearer JWT)
4. ✅ Swagger UI accessible at /swagger-ui.html
5. ✅ Widget API fully documented with request/response schemas
6. ✅ "Try it out" functionality works in Swagger UI (with test JWT)
7. ✅ OpenAPI JSON spec available at /v3/api-docs
8. ✅ API documentation includes examples and descriptions

---

## Prerequisites

**Story 2.10** - Widget REST API Controller

---

## Technical Notes

### OpenAPI Configuration

**framework/web/src/main/kotlin/com/axians/eaf/framework/web/openapi/OpenApiConfiguration.kt:**
```kotlin
@Configuration
class OpenApiConfiguration {

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("EAF v1.0 API")
                    .version("1.0.0")
                    .description("Enterprise Application Framework REST API")
                    .contact(
                        Contact()
                            .name("Axians EAF Team")
                            .email("eaf-team@axians.com")
                    )
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearer-jwt",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT Bearer token from Keycloak")
                    )
            )
            .security(listOf(SecurityRequirement().addList("bearer-jwt")))
    }
}
```

### Enhanced Controller Annotations

```kotlin
@RestController
@RequestMapping("/api/v1/widgets")
@Tag(name = "Widgets", description = "Widget management API")
class WidgetController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create new widget",
        description = "Creates a new widget aggregate with the specified name",
        responses = [
            ApiResponse(responseCode = "201", description = "Widget created successfully"),
            ApiResponse(responseCode = "400", description = "Validation error",
                content = [Content(schema = Schema(implementation = ProblemDetail::class))])
        ]
    )
    fun createWidget(
        @Parameter(description = "Widget creation request", required = true)
        @Valid @RequestBody request: CreateWidgetRequest
    ): WidgetResponse {
        // Implementation
    }
}
```

---

## Implementation Checklist

- [ ] Add Springdoc OpenAPI 2.6.0 to version catalog
- [ ] Add dependency to framework/web
- [ ] Create OpenApiConfiguration.kt
- [ ] Configure API metadata (title, version, description)
- [ ] Configure Bearer JWT security scheme
- [ ] Enhance WidgetController with @Operation, @Tag annotations
- [ ] Add @Parameter annotations to request parameters
- [ ] Add @ApiResponse annotations for status codes
- [ ] Start application and access /swagger-ui.html
- [ ] Verify Widget API documented
- [ ] Test "Try it out" with test JWT
- [ ] Verify /v3/api-docs returns OpenAPI spec
- [ ] Commit: "Add OpenAPI 3.0 documentation with Swagger UI"

---

## Test Evidence

- [ ] Swagger UI accessible at /swagger-ui.html
- [ ] Widget API endpoints visible in Swagger UI
- [ ] Request/Response schemas documented
- [ ] Security scheme (Bearer JWT) shown
- [ ] "Try it out" functionality works
- [ ] OpenAPI JSON at /v3/api-docs validates

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Swagger UI functional
- [ ] All endpoints documented
- [ ] Security scheme configured
- [ ] API documentation complete
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.11 - End-to-End Integration Test
**Next Story:** Story 2.13 - Performance Baseline and Monitoring

---

## References

- PRD: FR002 (Developer Tooling), FR015 (Documentation)
- Architecture: Section 15 (API Contracts)
- Tech Spec: Section 2.4 (Springdoc OpenAPI 2.6.0)
