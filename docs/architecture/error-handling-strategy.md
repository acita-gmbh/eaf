# Error Handling Strategy

## Overview

The EAF error handling strategy implements the **Arrow-Fold-Throw-ProblemDetails Pattern**, providing comprehensive error management from domain logic through API responses. This approach ensures consistent error handling, proper context enrichment, and excellent developer experience while maintaining security and operational visibility.

## Error Handling Architecture

```mermaid
graph TD
    DOMAIN[Domain Logic] --> EITHER[Either<DomainError, Success>]
    EITHER --> CONTROLLER[Controller Layer]
    CONTROLLER --> FOLD[Either.fold()]
    FOLD --> SUCCESS[Success Response]
    FOLD --> EXCEPTION[HttpException]
    EXCEPTION --> GLOBAL[Global Exception Handler]
    GLOBAL --> PROBLEM[RFC 7807 ProblemDetail]
    PROBLEM --> CLIENT[Client Response]

    subgraph "Error Enrichment"
        CONTEXT[Context Addition]
        LOGGING[Structured Logging]
        METRICS[Error Metrics]
        AUDIT[Audit Trail]
    end

    GLOBAL --> CONTEXT
    GLOBAL --> LOGGING
    GLOBAL --> METRICS
    GLOBAL --> AUDIT
```

## Arrow-Fold-Throw-ProblemDetails Pattern

### 1. Domain Layer: Either<DomainError, Success>

Domain operations return `Either` types for explicit error handling without exceptions:

```kotlin
// Domain error hierarchy
sealed class DomainError {
    abstract val message: String
    abstract val code: String
    abstract val context: Map<String, Any>

    data class ValidationError(
        val field: String,
        val constraint: String,
        val invalidValue: Any?,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Validation failed for field '$field': $constraint"
        override val code: String = "VALIDATION_ERROR"
    }

    data class BusinessRuleViolation(
        val rule: String,
        val reason: String,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Business rule violated: $rule - $reason"
        override val code: String = "BUSINESS_RULE_VIOLATION"
    }

    data class ResourceNotFound(
        val resourceType: String,
        val resourceId: String,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "$resourceType with ID '$resourceId' not found"
        override val code: String = "RESOURCE_NOT_FOUND"
    }

    data class ConcurrencyConflict(
        val aggregateId: String,
        val expectedVersion: Long,
        val actualVersion: Long,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Concurrency conflict for aggregate $aggregateId: expected version $expectedVersion, found $actualVersion"
        override val code: String = "CONCURRENCY_CONFLICT"
    }

    data class InsufficientPermissions(
        val action: String,
        val resource: String,
        val requiredPermissions: Set<String>,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Insufficient permissions for action '$action' on resource '$resource'"
        override val code: String = "INSUFFICIENT_PERMISSIONS"
    }

    data class TenantIsolationViolation(
        val requestedTenant: String,
        val actualTenant: String,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Tenant isolation violation: requested $requestedTenant, authenticated as $actualTenant"
        override val code: String = "TENANT_ISOLATION_VIOLATION"
    }

    data class ExternalServiceError(
        val service: String,
        val operation: String,
        val underlyingError: String,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "External service error: $service/$operation - $underlyingError"
        override val code: String = "EXTERNAL_SERVICE_ERROR"
    }

    data class RateLimitExceeded(
        val limit: Int,
        val window: String,
        val remainingTime: Duration,
        override val context: Map<String, Any> = emptyMap()
    ) : DomainError() {
        override val message: String = "Rate limit exceeded: $limit requests per $window, retry in ${remainingTime.toSeconds()}s"
        override val code: String = "RATE_LIMIT_EXCEEDED"
    }
}

// Domain service example
@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val eventBus: EventBus,
    private val permissionService: PermissionService
) {

    fun createProduct(command: CreateProductCommand): Either<DomainError, Product> = either {
        // Permission check
        val hasPermission = permissionService.hasPermission(
            command.userId,
            "product:create",
            command.tenantId
        ).bind()

        ensure(hasPermission) {
            DomainError.InsufficientPermissions(
                action = "product:create",
                resource = "product",
                requiredPermissions = setOf("product:create"),
                context = mapOf(
                    "userId" to command.userId,
                    "tenantId" to command.tenantId
                )
            )
        }

        // Business validation
        val existingProduct = productRepository.findBySku(command.sku, command.tenantId).bind()
        ensure(existingProduct == null) {
            DomainError.BusinessRuleViolation(
                rule = "product.sku.unique",
                reason = "Product with SKU '${command.sku}' already exists",
                context = mapOf(
                    "sku" to command.sku,
                    "tenantId" to command.tenantId,
                    "existingProductId" to existingProduct?.productId
                )
            )
        }

        // Input validation
        ensure(command.name.isNotBlank()) {
            DomainError.ValidationError(
                field = "name",
                constraint = "not_blank",
                invalidValue = command.name,
                context = mapOf("productId" to command.productId)
            )
        }

        ensure(command.sku.matches(SKU_PATTERN)) {
            DomainError.ValidationError(
                field = "sku",
                constraint = "pattern",
                invalidValue = command.sku,
                context = mapOf(
                    "expectedPattern" to SKU_PATTERN.pattern,
                    "productId" to command.productId
                )
            )
        }

        // Create product
        val product = Product.create(command).bind()
        val savedProduct = productRepository.save(product).bind()

        // Publish domain event
        eventBus.publish(ProductCreatedEvent(
            productId = savedProduct.productId,
            tenantId = savedProduct.tenantId,
            name = savedProduct.name,
            sku = savedProduct.sku
        )).bind()

        savedProduct
    }

    companion object {
        private val SKU_PATTERN = Regex("^[A-Z]{3}-[0-9]{6}$")
    }
}
```

### 2. Controller Layer: Either.fold()

Controllers use `fold()` to handle Either results and convert domain errors to HTTP exceptions:

```kotlin
// HTTP exception hierarchy
sealed class HttpException(
    val status: HttpStatus,
    message: String,
    val errorCode: String,
    val context: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    class BadRequestException(
        message: String,
        errorCode: String = "BAD_REQUEST",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.BAD_REQUEST, message, errorCode, context, cause)

    class UnauthorizedException(
        message: String = "Authentication required",
        errorCode: String = "UNAUTHORIZED",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.UNAUTHORIZED, message, errorCode, context, cause)

    class ForbiddenException(
        message: String,
        errorCode: String = "FORBIDDEN",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.FORBIDDEN, message, errorCode, context, cause)

    class NotFoundException(
        message: String,
        errorCode: String = "NOT_FOUND",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.NOT_FOUND, message, errorCode, context, cause)

    class ConflictException(
        message: String,
        errorCode: String = "CONFLICT",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.CONFLICT, message, errorCode, context, cause)

    class InternalServerErrorException(
        message: String = "Internal server error",
        errorCode: String = "INTERNAL_SERVER_ERROR",
        context: Map<String, Any> = emptyMap(),
        cause: Throwable? = null
    ) : HttpException(HttpStatus.INTERNAL_SERVER_ERROR, message, errorCode, context, cause)
}

// Domain error to HTTP exception mapping
fun DomainError.toHttpException(): HttpException {
    return when (this) {
        is DomainError.ValidationError -> HttpException.BadRequestException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "field" to this.field,
                "constraint" to this.constraint,
                "invalidValue" to this.invalidValue
            )
        )

        is DomainError.BusinessRuleViolation -> HttpException.BadRequestException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "rule" to this.rule,
                "reason" to this.reason
            )
        )

        is DomainError.ResourceNotFound -> HttpException.NotFoundException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "resourceType" to this.resourceType,
                "resourceId" to this.resourceId
            )
        )

        is DomainError.ConcurrencyConflict -> HttpException.ConflictException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "aggregateId" to this.aggregateId,
                "expectedVersion" to this.expectedVersion,
                "actualVersion" to this.actualVersion
            )
        )

        is DomainError.InsufficientPermissions -> HttpException.ForbiddenException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "action" to this.action,
                "resource" to this.resource,
                "requiredPermissions" to this.requiredPermissions
            )
        )

        is DomainError.TenantIsolationViolation -> HttpException.ForbiddenException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "requestedTenant" to this.requestedTenant,
                "actualTenant" to this.actualTenant
            )
        )

        is DomainError.ExternalServiceError -> HttpException.InternalServerErrorException(
            message = "Service temporarily unavailable",
            errorCode = this.code,
            context = this.context + mapOf(
                "service" to this.service,
                "operation" to this.operation
            )
        )

        is DomainError.RateLimitExceeded -> HttpException.TooManyRequestsException(
            message = this.message,
            errorCode = this.code,
            context = this.context + mapOf(
                "limit" to this.limit,
                "window" to this.window,
                "retryAfter" to this.remainingTime.toSeconds()
            )
        )
    }
}

// Controller implementation
@RestController
@RequestMapping("/api/v1/products")
@Validated
class ProductController(
    private val productService: ProductService,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    fun createProduct(
        @RequestBody @Valid request: CreateProductRequest,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<ProductResponse> {
        val command = CreateProductCommand(
            productId = UUID.randomUUID().toString(),
            tenantId = authentication.tenantId,
            userId = authentication.userId,
            name = request.name,
            sku = request.sku,
            description = request.description,
            price = request.price
        )

        return productService.createProduct(command).fold(
            ifLeft = { error ->
                // Convert domain error to HTTP exception
                throw error.toHttpException()
            },
            ifRight = { product ->
                // Success case
                ResponseEntity
                    .created(URI.create("/api/v1/products/${product.productId}"))
                    .body(product.toResponse())
            }
        )
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: String,
        authentication: JwtAuthenticationToken
    ): ProductResponse {
        val query = FindProductByIdQuery(productId, authentication.tenantId)

        return queryGateway.query(query, ProductProjection::class.java)
            .join()
            ?.toResponse()
            ?: throw HttpException.NotFoundException(
                message = "Product not found",
                context = mapOf(
                    "productId" to productId,
                    "tenantId" to authentication.tenantId
                )
            )
    }
}
```

### 3. Global Exception Handler: RFC 7807 ProblemDetail

The global exception handler converts HTTP exceptions to RFC 7807 Problem Details format:

```kotlin
// Global exception handler
@RestControllerAdvice
class GlobalExceptionHandler(
    private val tracer: Tracer,
    private val meterRegistry: MeterRegistry,
    private val auditLogger: AuditLogger
) {

    @ExceptionHandler(HttpException::class)
    fun handleHttpException(
        exception: HttpException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = createProblemDetail(exception, request)
        enrichWithContext(problemDetail, request)
        recordMetrics(exception)
        logError(exception, request)

        return ResponseEntity
            .status(exception.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .headers(createErrorHeaders(exception))
            .body(problemDetail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationException(
        exception: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Request validation failed"
        ).apply {
            type = URI.create("https://api.axians.com/problems/validation-error")
            title = "Validation Error"
            instance = URI.create(request.requestURI)

            setProperty("violations", exception.constraintViolations.map { violation ->
                mapOf(
                    "field" to violation.propertyPath.toString(),
                    "constraint" to violation.constraintDescriptor.annotation.annotationClass.simpleName,
                    "invalidValue" to violation.invalidValue,
                    "message" to violation.message
                )
            })
        }

        enrichWithContext(problemDetail, request)
        recordValidationError(exception)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        exception: AccessDeniedException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "Access denied"
        ).apply {
            type = URI.create("https://api.axians.com/problems/access-denied")
            title = "Access Denied"
            instance = URI.create(request.requestURI)
        }

        enrichWithContext(problemDetail, request)
        recordSecurityEvent("ACCESS_DENIED", request)

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        exception: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        ).apply {
            type = URI.create("https://api.axians.com/problems/internal-server-error")
            title = "Internal Server Error"
            instance = URI.create(request.requestURI)
        }

        enrichWithContext(problemDetail, request)
        recordInternalError(exception)
        logInternalError(exception, request)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    private fun createProblemDetail(
        exception: HttpException,
        request: HttpServletRequest
    ): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            exception.status,
            exception.message
        ).apply {
            type = URI.create("https://api.axians.com/problems/${exception.errorCode.lowercase().replace('_', '-')}")
            title = formatTitle(exception.errorCode)
            instance = URI.create(request.requestURI)

            // Add context properties
            exception.context.forEach { (key, value) ->
                setProperty(key, value)
            }

            // Add error-specific properties
            setProperty("errorCode", exception.errorCode)
        }
    }

    private fun enrichWithContext(
        problemDetail: ProblemDetail,
        request: HttpServletRequest
    ) {
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val tenantId = TenantContext.current()?.tenantId
        val userId = getCurrentUserId()

        problemDetail.setProperty("traceId", traceId)
        problemDetail.setProperty("tenantId", tenantId)
        problemDetail.setProperty("userId", userId)
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.requestURI)
        problemDetail.setProperty("method", request.method)
        problemDetail.setProperty("userAgent", request.getHeader("User-Agent"))
        problemDetail.setProperty("correlationId", request.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString())
    }

    private fun createErrorHeaders(exception: HttpException): HttpHeaders {
        val headers = HttpHeaders()

        // Add retry-after header for rate limiting
        if (exception is HttpException.TooManyRequestsException) {
            val retryAfter = exception.context["retryAfter"] as? Long ?: 60
            headers.add("Retry-After", retryAfter.toString())
        }

        // Add correlation ID
        headers.add("X-Correlation-ID", getCurrentCorrelationId())

        return headers
    }

    private fun recordMetrics(exception: HttpException) {
        meterRegistry.counter(
            "api.errors.total",
            "status", exception.status.value().toString(),
            "error_code", exception.errorCode,
            "tenant_id", TenantContext.current()?.tenantId?.toString() ?: "unknown"
        ).increment()
    }

    private fun logError(exception: HttpException, request: HttpServletRequest) {
        val logLevel = when (exception.status.series()) {
            HttpStatus.Series.CLIENT_ERROR -> if (exception.status == HttpStatus.NOT_FOUND) "DEBUG" else "WARN"
            HttpStatus.Series.SERVER_ERROR -> "ERROR"
            else -> "INFO"
        }

        val logMessage = buildString {
            append("HTTP Error: ")
            append("${exception.status.value()} ${exception.status.reasonPhrase} - ")
            append("${request.method} ${request.requestURI} - ")
            append(exception.message)
        }

        when (logLevel) {
            "ERROR" -> logger.error(logMessage, exception)
            "WARN" -> logger.warn(logMessage)
            "DEBUG" -> logger.debug(logMessage)
            else -> logger.info(logMessage)
        }
    }

    private fun formatTitle(errorCode: String): String {
        return errorCode.split('_')
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
```

## Error Context and Enrichment

### Audit Trail Integration

```kotlin
// Audit event for errors
@Component
class ErrorAuditLogger(
    private val auditEventRepository: AuditEventRepository
) {

    fun logErrorEvent(
        exception: HttpException,
        request: HttpServletRequest,
        tenantId: String?,
        userId: String?
    ) {
        val auditEvent = AuditEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = "ERROR_OCCURRED",
            severity = mapSeverity(exception.status),
            tenantId = tenantId,
            userId = userId,
            resourceType = extractResourceType(request.requestURI),
            resourceId = extractResourceId(request.requestURI),
            action = request.method,
            outcome = "FAILURE",
            details = mapOf(
                "httpStatus" to exception.status.value(),
                "errorCode" to exception.errorCode,
                "errorMessage" to exception.message,
                "requestUri" to request.requestURI,
                "userAgent" to request.getHeader("User-Agent"),
                "clientIp" to getClientIpAddress(request),
                "correlationId" to getCorrelationId(request),
                "traceId" to getCurrentTraceId(),
                "errorContext" to exception.context
            ),
            timestamp = Instant.now()
        )

        auditEventRepository.save(auditEvent)
    }

    private fun mapSeverity(status: HttpStatus): AuditSeverity {
        return when (status.series()) {
            HttpStatus.Series.CLIENT_ERROR -> when (status) {
                HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> AuditSeverity.HIGH
                HttpStatus.NOT_FOUND -> AuditSeverity.LOW
                else -> AuditSeverity.MEDIUM
            }
            HttpStatus.Series.SERVER_ERROR -> AuditSeverity.HIGH
            else -> AuditSeverity.LOW
        }
    }
}
```

### Structured Logging

```kotlin
// Structured error logging
@Component
class StructuredErrorLogger {

    fun logError(
        exception: HttpException,
        request: HttpServletRequest,
        context: Map<String, Any> = emptyMap()
    ) {
        val logEvent = ErrorLogEvent(
            timestamp = Instant.now(),
            level = mapLogLevel(exception.status),
            message = exception.message,
            errorCode = exception.errorCode,
            httpStatus = exception.status.value(),
            httpMethod = request.method,
            requestUri = request.requestURI,
            tenantId = TenantContext.current()?.tenantId,
            userId = getCurrentUserId(),
            traceId = getCurrentTraceId(),
            correlationId = getCorrelationId(request),
            userAgent = request.getHeader("User-Agent"),
            clientIp = getClientIpAddress(request),
            stackTrace = if (exception.status.is5xxServerError) exception.stackTraceToString() else null,
            errorContext = exception.context + context
        )

        // Log as structured JSON
        logger.info(objectMapper.writeValueAsString(logEvent))

        // Also send to external logging service if configured
        if (shouldSendToExternalService(exception.status)) {
            externalLoggingService.sendErrorLog(logEvent)
        }
    }

    data class ErrorLogEvent(
        val timestamp: Instant,
        val level: String,
        val message: String,
        val errorCode: String,
        val httpStatus: Int,
        val httpMethod: String,
        val requestUri: String,
        val tenantId: String?,
        val userId: String?,
        val traceId: String?,
        val correlationId: String?,
        val userAgent: String?,
        val clientIp: String?,
        val stackTrace: String?,
        val errorContext: Map<String, Any>
    )

    companion object {
        private val logger = LoggerFactory.getLogger("eaf.errors")
    }
}
```

## Error Recovery and Resilience

### Circuit Breaker Integration

```kotlin
// Resilience patterns for external services
@Component
class ResilientExternalService(
    private val restTemplate: RestTemplate,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val retryRegistry: RetryRegistry
) {

    fun callExternalService(request: ExternalServiceRequest): Either<DomainError, ExternalServiceResponse> {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("external-service")
        val retry = retryRegistry.retry("external-service")

        return try {
            val supplier = CircuitBreaker.decorateSupplier(circuitBreaker) {
                Retry.decorateSupplier(retry) {
                    makeHttpCall(request)
                }.get()
            }

            supplier.get().right()
        } catch (e: CircuitBreakerOpenException) {
            DomainError.ExternalServiceError(
                service = "external-service",
                operation = request.operation,
                underlyingError = "Circuit breaker is open",
                context = mapOf(
                    "circuitBreakerState" to circuitBreaker.state.name,
                    "failureRate" to circuitBreaker.metrics.failureRate
                )
            ).left()
        } catch (e: Exception) {
            DomainError.ExternalServiceError(
                service = "external-service",
                operation = request.operation,
                underlyingError = e.message ?: "Unknown error",
                context = mapOf("exceptionType" to e::class.simpleName)
            ).left()
        }
    }
}
```

### Retry Configuration

```yaml
# application.yml - Resilience configuration
resilience4j:
  circuitbreaker:
    instances:
      external-service:
        failure-rate-threshold: 60
        minimum-number-of-calls: 10
        sliding-window-size: 10
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 3

  retry:
    instances:
      external-service:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
        ignore-exceptions:
          - com.axians.eaf.errors.HttpException.BadRequestException
```

## Frontend Error Integration

### Error Response Format

```typescript
// Frontend error types
interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  traceId?: string;
  tenantId?: string;
  correlationId?: string;
  timestamp?: string;
  violations?: ValidationViolation[];
  [key: string]: any;
}

interface ValidationViolation {
  field: string;
  constraint: string;
  invalidValue: any;
  message: string;
}

// Error handling in React Admin
class ApiClient {
  async request<T>(url: string, options: RequestInit = {}): Promise<T> {
    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${getToken()}`,
          'X-Correlation-ID': generateCorrelationId(),
          ...options.headers,
        },
      });

      if (!response.ok) {
        throw await this.createErrorFromResponse(response);
      }

      return await response.json();
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      throw new ApiError('NETWORK_ERROR', 'Network request failed', 0, {
        originalError: error.message,
      });
    }
  }

  private async createErrorFromResponse(response: Response): Promise<ApiError> {
    const contentType = response.headers.get('content-type');

    if (contentType?.includes('application/problem+json')) {
      const problemDetail: ProblemDetail = await response.json();
      return new ApiError(
        problemDetail.errorCode || 'HTTP_ERROR',
        problemDetail.detail || problemDetail.title,
        problemDetail.status,
        problemDetail
      );
    }

    return new ApiError(
      'HTTP_ERROR',
      `HTTP ${response.status}: ${response.statusText}`,
      response.status
    );
  }
}

class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly context: Record<string, any> = {}
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isValidationError(): boolean {
    return this.code === 'VALIDATION_ERROR' && this.context.violations;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }
}
```

## Error Monitoring and Alerting

### Error Metrics

```kotlin
// Error metrics collection
@Component
class ErrorMetricsCollector(
    private val meterRegistry: MeterRegistry
) {

    fun recordError(
        exception: HttpException,
        request: HttpServletRequest,
        processingTime: Duration
    ) {
        // Error count by type
        meterRegistry.counter(
            "api.errors.total",
            "status", exception.status.value().toString(),
            "error_code", exception.errorCode,
            "method", request.method,
            "endpoint", normalizeEndpoint(request.requestURI),
            "tenant_id", TenantContext.current()?.tenantId?.toString() ?: "unknown"
        ).increment()

        // Error processing time
        meterRegistry.timer(
            "api.errors.processing_time",
            "status", exception.status.value().toString(),
            "error_code", exception.errorCode
        ).record(processingTime)

        // Error rate by endpoint
        meterRegistry.counter(
            "api.endpoint.errors",
            "endpoint", normalizeEndpoint(request.requestURI),
            "method", request.method
        ).increment()

        // Business error tracking
        if (exception.status.is4xxClientError) {
            meterRegistry.counter(
                "business.errors",
                "error_code", exception.errorCode,
                "tenant_id", TenantContext.current()?.tenantId?.toString() ?: "unknown"
            ).increment()
        }
    }

    private fun normalizeEndpoint(uri: String): String {
        // Replace IDs with placeholders for consistent metrics
        return uri.replace(Regex("/[0-9a-f-]{36}"), "/{id}")
                  .replace(Regex("/\\d+"), "/{id}")
    }
}
```

### Alerting Rules

```yaml
# prometheus/alerts.yml
groups:
  - name: eaf-errors
    rules:
      - alert: HighErrorRate
        expr: rate(api_errors_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High API error rate detected"
          description: "Error rate is {{ $value }} errors/second"

      - alert: CriticalErrorSpike
        expr: rate(api_errors_total{status=~"5.."}[1m]) > 0.05
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Critical server error spike"
          description: "Server error rate is {{ $value }} errors/second"

      - alert: TenantIsolationViolation
        expr: increase(api_errors_total{error_code="TENANT_ISOLATION_VIOLATION"}[5m]) > 0
        for: 0m
        labels:
          severity: critical
        annotations:
          summary: "Tenant isolation violation detected"
          description: "Potential security breach - tenant isolation violated"
```

## Related Documentation

- **[API Specification](api-specification-revision-2.md)** - RFC 7807 Problem Details implementation
- **[Security Architecture](security.md)** - Security error handling and audit trail
- **[System Components](components.md)** - Global exception handler implementation
- **[Testing Strategy](test-strategy-and-standards-revision-3.md)** - Error handling testing patterns
- **[Monitoring & Observability](monitoring-and-observability.md)** - Error monitoring and alerting

---

**Next Steps**: Review [API Specification](api-specification-revision-2.md) for RFC 7807 implementation details, then proceed to [Testing Strategy](test-strategy-and-standards-revision-3.md) for error handling test patterns.