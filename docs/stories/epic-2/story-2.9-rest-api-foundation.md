# Story 2.9: REST API Foundation with RFC 7807 Error Handling

**Story Context:** [2-9-rest-api-foundation.context.xml](2-9-rest-api-foundation.context.xml)

**Epic:** Epic 2 - Walking Skeleton - CQRS/Event Sourcing Core
**Status:** TODO
**Story Points:** TBD
**Related Requirements:** FR003 (Event Store), FR011 (Performance)

---

## User Story

As a framework developer,
I want REST API foundation with standardized error responses,
So that API consumers have consistent, machine-readable error information.

---

## Acceptance Criteria

1. ✅ framework/web module created with Spring Web MVC dependencies
2. ✅ ProblemDetailExceptionHandler.kt implements RFC 7807 Problem Details
3. ✅ Error responses include: type, title, status, detail, instance, traceId, tenantId
4. ✅ RestConfiguration.kt with CORS, Jackson ObjectMapper, response formatting
5. ✅ CursorPaginationSupport.kt utility for cursor-based pagination
6. ✅ Integration test validates error response format
7. ✅ All framework exceptions mapped to appropriate HTTP status codes

---

## Prerequisites

**Story 2.1** - Axon Framework Core Configuration

---

## Technical Notes

### RFC 7807 Problem Details

**framework/web/src/main/kotlin/com/axians/eaf/framework/web/rest/ProblemDetailExceptionHandler.kt:**
```kotlin
@RestControllerAdvice
class ProblemDetailExceptionHandler {

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Validation failed")

        problem.type = URI.create("https://eaf.axians.com/errors/validation-error")
        problem.title = "Validation Error"
        problem.setProperty("instance", request.requestURI)
        problem.setProperty("traceId", getTraceId())
        problem.setProperty("tenantId", getTenantId())
        problem.setProperty("timestamp", Instant.now())

        return ResponseEntity.badRequest().body(problem)
    }

    @ExceptionHandler(AggregateNotFoundException::class)
    fun handleNotFound(ex: AggregateNotFoundException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found")

        problem.type = URI.create("https://eaf.axians.com/errors/not-found")
        problem.title = "Not Found"
        problem.setProperty("instance", request.requestURI)
        problem.setProperty("traceId", getTraceId())
        problem.setProperty("tenantId", getTenantId())
        problem.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem)
    }

    @ExceptionHandler(TenantIsolationException::class)
    fun handleTenantIsolation(ex: TenantIsolationException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Tenant isolation violation")

        problem.type = URI.create("https://eaf.axians.com/errors/tenant-isolation")
        problem.title = "Forbidden"
        problem.setProperty("instance", request.requestURI)
        problem.setProperty("traceId", getTraceId())
        problem.setProperty("tenantId", getTenantId())
        problem.setProperty("timestamp", Instant.now())

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem)
    }

    private fun getTraceId(): String? = MDC.get("trace_id")
    private fun getTenantId(): String? = TenantContext.get()?.value
}
```

### REST Configuration

**RestConfiguration.kt:**
```kotlin
@Configuration
class RestConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper {
        return Jackson2ObjectMapperBuilder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .modules(JavaTimeModule(), KotlinModule.Builder().build())
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf("http://localhost:3000")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
```

### Cursor Pagination Utility

**CursorPaginationSupport.kt:**
```kotlin
object CursorPaginationSupport {

    fun encodeCursor(timestamp: Instant, id: UUID): String {
        val json = """{"timestamp":"$timestamp","id":"$id"}"""
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }

    fun decodeCursor(cursor: String): Cursor {
        val json = String(Base64.getDecoder().decode(cursor))
        // Parse JSON and return Cursor
        return Cursor(/* parsed timestamp */, /* parsed id */)
    }

    data class Cursor(val timestamp: Instant, val id: UUID)
}
```

---

## Implementation Checklist

- [ ] Create framework/web module
- [ ] Add Spring Web MVC dependencies
- [ ] Create ProblemDetailExceptionHandler.kt with RFC 7807 handlers
- [ ] Map all framework exceptions (Validation, NotFound, TenantIsolation)
- [ ] Create RestConfiguration.kt (Jackson, CORS)
- [ ] Create CursorPaginationSupport.kt utility
- [ ] Write integration test for error responses
- [ ] Verify RFC 7807 format (type, title, status, detail, instance, traceId, tenantId)
- [ ] Test all HTTP status codes (400, 403, 404, 500)
- [ ] Commit: "Add REST API foundation with RFC 7807 error handling"

---

## Test Evidence

- [ ] ValidationException → 400 Bad Request with RFC 7807 format
- [ ] AggregateNotFoundException → 404 Not Found
- [ ] TenantIsolationException → 403 Forbidden
- [ ] Error responses include traceId and tenantId
- [ ] CORS configured correctly
- [ ] Jackson ObjectMapper serializes dates as ISO-8601

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] All exceptions mapped to HTTP status codes
- [ ] RFC 7807 format validated
- [ ] Integration tests pass
- [ ] Story marked as DONE in workflow status

---

## Related Stories

**Previous Story:** Story 2.8 - Widget Query Handler
**Next Story:** Story 2.10 - Widget REST API Controller

---

## References

- PRD: FR003, FR011
- Architecture: Section 15 (API Contracts - Error Format RFC 7807)
- Tech Spec: Section 5.3 (Error Response Format)
