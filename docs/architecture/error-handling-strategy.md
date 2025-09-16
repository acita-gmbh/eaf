# Error Handling Strategy

(This is the "Arrow-Fold-Throw-ProblemDetails" pattern).

## Error Handling Flow

1. **Domain (Internal):** Returns `Either.Left(DomainError)`.
2. **Boundary (Controller):** "Folds" the Either, translates the `DomainError` into a specific `HttpException`.
3. **Framework (Advice):** A global `@ControllerAdvice` catches the `HttpException` and formats it as a standard **RFC 7807 ProblemDetail**.
4. **Frontend (Consumer):** The React Data Provider (WebSocket-based) parses the `application/problem+json` response to display errors.

## Error Response Format (RFC 7807)

All API errors return a JSON response body conforming to **RFC 7807 Problem Details** standard:

```json
{
  "type": "https://docs.eaf.com/errors/widget-not-found",
  "title": "Widget Not Found",
  "status": 404,
  "detail": "The requested widget with ID 'abc-123' could not be found.",
  "instance": "/widgets/abc-123",
  "traceId": "67890def-abcd-1234-5678-90abcdef1234",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Backend Error Handling Implementation

**Global Controller Advice:**

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DomainException::class)
    fun handleDomainException(
        ex: DomainException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            ex.httpStatus,
            ex.message ?: "An error occurred"
        )

        problemDetail.type = URI.create("https://docs.eaf.com/errors/${ex.errorCode}")
        problemDetail.title = ex.title
        problemDetail.instance = URI.create(request.requestURI)

        // Add correlation and tenant context
        problemDetail.setProperty("traceId", getCurrentTraceId())
        problemDetail.setProperty("tenantId", TenantContext.getCurrentTenant())

        return ResponseEntity.status(ex.httpStatus).body(problemDetail)
    }
}
```

## API Error Catalog

Standardized Problem Details `type` URIs, `title`, and `status` values with `traceId` and `tenantId` context:

**Authentication & Authorization Errors:**
* `/errors/unauthorized` (401) - Invalid or missing JWT token
* `/errors/forbidden` (403) - Valid token, insufficient permissions
* `/errors/tenant-access-denied` (403) - Cross-tenant access attempt

**Validation Errors:**
* `/errors/validation-failed` (400) - Request validation failure
* `/errors/invalid-input` (400) - Business rule validation failure
* `/errors/constraint-violation` (409) - Database constraint violation

**Resource Errors:**
* `/errors/not-found` (404) - Requested resource not found
* `/errors/conflict` (409) - Resource conflict (duplicate, version mismatch)
* `/errors/gone` (410) - Resource no longer available

**Rate Limiting & Capacity:**
* `/errors/rate-limit-exceeded` (429) - API rate limit exceeded
* `/errors/quota-exceeded` (429) - Tenant quota exceeded
* `/errors/capacity-exceeded` (503) - System capacity exceeded

**System Errors:**
* `/errors/internal` (500) - Internal server error
* `/errors/service-unavailable` (503) - Service temporarily unavailable
* `/errors/timeout` (504) - Request timeout

## Domain Error Types

**Functional Error Handling with Arrow:**

```kotlin
sealed class DomainError(
    val errorCode: String,
    val title: String,
    val httpStatus: HttpStatus,
    override val message: String?
) : Exception(message) {

    data class WidgetNotFound(val widgetId: String) : DomainError(
        errorCode = "widget-not-found",
        title = "Widget Not Found",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Widget with ID '$widgetId' not found"
    )

    data class ValidationError(val field: String, val violation: String) : DomainError(
        errorCode = "validation-failed",
        title = "Validation Failed",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Validation failed for field '$field': $violation"
    )

    data class TenantAccessDenied(val tenantId: String, val resourceType: String) : DomainError(
        errorCode = "tenant-access-denied",
        title = "Tenant Access Denied",
        httpStatus = HttpStatus.FORBIDDEN,
        message = "Access denied for tenant '$tenantId' to resource '$resourceType'"
    )
}
```

## Controller Error Mapping

**Arrow Either Pattern in Controllers:**

```kotlin
@RestController
class WidgetController(private val widgetService: WidgetService) {

    @PostMapping("/widgets")
    fun createWidget(@RequestBody request: CreateWidgetRequest): ResponseEntity<*> {
        return widgetService.createWidget(request)
            .fold(
                ifLeft = { error -> throw error }, // Global handler catches this
                ifRight = { widget -> ResponseEntity.accepted().body(widget) }
            )
    }
}
```

## Frontend Error Handling

**React Data Provider Integration:**

```typescript
const dataProvider = {
    create: (resource: string, params: any) => {
        return httpClient(url, requestOptions)
            .catch(error => {
                if (error.status === 400 && error.body?.type?.includes('validation-failed')) {
                    // Handle validation errors specifically
                    throw new ValidationError(error.body.detail, error.body.violations);
                }

                // Standard RFC 7807 error handling
                throw new HttpError(error.body.detail, error.status, error.body);
            });
    }
};
```

## Error Monitoring and Observability

**Structured Error Logging:**

```kotlin
@Component
class ErrorLogger {
    private val logger = LoggerFactory.getLogger(ErrorLogger::class.java)

    fun logError(error: DomainError, context: ErrorContext) {
        logger.error(
            "Domain error occurred",
            structuredArguments(
                kv("errorCode", error.errorCode),
                kv("httpStatus", error.httpStatus.value()),
                kv("tenantId", context.tenantId),
                kv("traceId", context.traceId),
                kv("userId", context.userId),
                kv("resource", context.resource)
            )
        )
    }
}
```

**Error Metrics Collection:**

```kotlin
@Component
class ErrorMetrics {
    private val errorCounter = Counter.builder("eaf.errors.total")
        .description("Total number of errors by type")
        .register(meterRegistry)

    fun recordError(error: DomainError, tenantId: String) {
        errorCounter.increment(
            Tags.of(
                Tag.of("error_code", error.errorCode),
                Tag.of("http_status", error.httpStatus.value().toString()),
                Tag.of("tenant_id", tenantId)
            )
        )
    }
}
```

## Client Error Handling Guidelines

**Error Response Processing:**

Clients must:
1. Parse RFC 7807 Problem Details format
2. Display `detail` message to users
3. Include `traceId` in support requests
4. Handle specific error types appropriately
5. Respect retry strategies for 5xx errors
6. Log error context for debugging

**Example Error Display:**

```typescript
function displayError(error: ProblemDetail) {
    const message = error.detail || 'An unexpected error occurred';
    const traceId = error.traceId;

    showNotification({
        type: 'error',
        message: message,
        action: traceId ? `Report issue (Trace: ${traceId})` : undefined
    });
}
```

-----
