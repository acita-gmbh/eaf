# API Specification (Revision 2)

## Overview

The EAF API implements RESTful principles with OpenAPI 3.0 specification, comprehensive error handling using RFC 7807 Problem Details, and enterprise-grade security with JWT authentication. The API follows CQRS patterns with clear separation between command and query endpoints.

## API Design Principles

### 1. RESTful Design
- Resource-oriented URLs
- HTTP methods for semantic operations
- Stateless interactions
- HATEOAS for navigation (future enhancement)

### 2. Security First
- JWT Bearer token authentication
- Multi-tenant isolation via token claims
- Rate limiting and throttling
- Input validation and sanitization

### 3. Error Handling
- RFC 7807 Problem Details format
- Structured error responses
- Correlation IDs for tracing
- Contextual error information

### 4. Performance
- Pagination for list endpoints
- Conditional requests (ETags)
- Compression support
- Caching headers

## OpenAPI 3.0 Specification

### Base Configuration

```yaml
openapi: 3.0.3
info:
  title: Enterprise Application Framework API
  version: 0.1.0
  description: |
    Production-ready API for multi-tenant enterprise applications with CQRS/Event Sourcing architecture.

    ## Authentication
    All endpoints require JWT Bearer token authentication obtained from Keycloak OIDC provider.

    ## Multi-Tenancy
    Tenant isolation is enforced automatically based on JWT claims. All operations are scoped to the authenticated user's tenant.

    ## Error Handling
    Errors follow RFC 7807 Problem Details format with structured error information and correlation IDs for debugging.
  contact:
    name: EAF Architecture Team
    email: architecture@axians.com
  license:
    name: Proprietary
    url: https://axians.com/licenses/eaf

servers:
  - url: https://api.{environment}.axians.com/v1
    description: Environment-specific API endpoint
    variables:
      environment:
        default: prod
        enum: [dev, staging, prod]
        description: Deployment environment

security:
  - BearerAuth: []

components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
      description: |
        JWT token obtained from Keycloak OIDC. Include in Authorization header as "Bearer <token>".
        Token must contain valid tenant claims for multi-tenant isolation.
```

### Common Components

```yaml
components:
  schemas:
    # Error Handling (RFC 7807)
    ProblemDetail:
      type: object
      required: [type, title, status]
      properties:
        type:
          type: string
          format: uri
          description: URI identifying the problem type
          example: "https://api.axians.com/problems/validation-error"
        title:
          type: string
          description: Human-readable summary
          example: "Validation Error"
        status:
          type: integer
          description: HTTP status code
          example: 400
        detail:
          type: string
          description: Detailed error description
          example: "The 'sku' field must match pattern '^[A-Z]{3}-[0-9]{6}$'"
        instance:
          type: string
          format: uri
          description: URI of the specific problem occurrence
          example: "/api/v1/products/create"
        traceId:
          type: string
          description: Correlation ID for request tracing
          example: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        tenantId:
          type: string
          format: uuid
          description: Tenant context for the error
          example: "550e8400-e29b-41d4-a716-446655440000"
        violations:
          type: array
          description: Detailed validation violations
          items:
            $ref: '#/components/schemas/ValidationViolation'

    ValidationViolation:
      type: object
      properties:
        field:
          type: string
          description: Field that failed validation
          example: "sku"
        constraint:
          type: string
          description: Validation constraint that was violated
          example: "pattern"
        invalidValue:
          description: The invalid value that was provided
          example: "INVALID-SKU"
        message:
          type: string
          description: Human-readable validation message
          example: "SKU must match pattern '^[A-Z]{3}-[0-9]{6}$'"

    # Pagination
    PageRequest:
      type: object
      properties:
        page:
          type: integer
          minimum: 0
          default: 0
          description: Zero-based page number
        size:
          type: integer
          minimum: 1
          maximum: 100
          default: 20
          description: Page size (max 100)
        sort:
          type: array
          items:
            type: string
            pattern: '^[a-zA-Z][a-zA-Z0-9]*(\.(asc|desc))?$'
          description: Sort criteria (field.direction)
          example: ["name.asc", "createdAt.desc"]

    PageResponse:
      type: object
      properties:
        content:
          type: array
          description: Page content
        totalElements:
          type: integer
          format: int64
          description: Total number of elements
        totalPages:
          type: integer
          description: Total number of pages
        numberOfElements:
          type: integer
          description: Number of elements in current page
        size:
          type: integer
          description: Page size
        number:
          type: integer
          description: Current page number (zero-based)
        first:
          type: boolean
          description: Whether this is the first page
        last:
          type: boolean
          description: Whether this is the last page

  responses:
    BadRequest:
      description: Bad Request - Invalid input or validation error
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'
          examples:
            validation-error:
              summary: Validation Error
              value:
                type: "https://api.axians.com/problems/validation-error"
                title: "Validation Error"
                status: 400
                detail: "Request validation failed"
                instance: "/api/v1/products"
                traceId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                violations:
                  - field: "sku"
                    constraint: "pattern"
                    invalidValue: "invalid"
                    message: "SKU must match pattern '^[A-Z]{3}-[0-9]{6}$'"

    Unauthorized:
      description: Unauthorized - Missing or invalid authentication
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'

    Forbidden:
      description: Forbidden - Insufficient permissions or tenant access denied
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'

    NotFound:
      description: Not Found - Resource does not exist
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'

    Conflict:
      description: Conflict - Resource already exists or concurrency conflict
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'

    InternalServerError:
      description: Internal Server Error - Unexpected server error
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetail'
```

## Product API

### Product Schemas

```yaml
components:
  schemas:
    CreateProductRequest:
      type: object
      required: [name, sku, price]
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
          description: Product name
          example: "Enterprise Security Suite"
        sku:
          type: string
          pattern: '^[A-Z]{3}-[0-9]{6}$'
          description: Stock Keeping Unit (format: ABC-123456)
          example: "ESS-123456"
        description:
          type: string
          maxLength: 1000
          description: Product description
          example: "Comprehensive security solution for enterprise environments"
        price:
          type: number
          format: decimal
          minimum: 0
          multipleOf: 0.01
          description: Product price in base currency
          example: 999.99
        features:
          type: array
          items:
            $ref: '#/components/schemas/ProductFeature'
          description: Product features configuration
        metadata:
          type: object
          additionalProperties:
            type: string
          description: Additional product metadata
          example:
            category: "security"
            vendor: "axians"

    UpdateProductRequest:
      type: object
      properties:
        name:
          type: string
          minLength: 1
          maxLength: 255
        description:
          type: string
          maxLength: 1000
        price:
          type: number
          format: decimal
          minimum: 0
          multipleOf: 0.01
        features:
          type: array
          items:
            $ref: '#/components/schemas/ProductFeature'
        metadata:
          type: object
          additionalProperties:
            type: string

    ProductResponse:
      type: object
      properties:
        productId:
          type: string
          format: uuid
          description: Unique product identifier
        sku:
          type: string
          description: Stock Keeping Unit
        name:
          type: string
          description: Product name
        description:
          type: string
          description: Product description
        price:
          type: number
          format: decimal
          description: Product price
        status:
          type: string
          enum: [DRAFT, ACTIVE, DISCONTINUED]
          description: Product status
        features:
          type: array
          items:
            $ref: '#/components/schemas/ProductFeature'
        metadata:
          type: object
          additionalProperties:
            type: string
        createdAt:
          type: string
          format: date-time
          description: Creation timestamp
        updatedAt:
          type: string
          format: date-time
          description: Last update timestamp

    ProductFeature:
      type: object
      required: [name, enabled]
      properties:
        name:
          type: string
          description: Feature name
          example: "advanced-analytics"
        enabled:
          type: boolean
          description: Whether feature is enabled
          example: true
        configuration:
          type: object
          additionalProperties: true
          description: Feature-specific configuration
          example:
            retention_days: 90
            max_queries: 1000

    ProductSearchRequest:
      type: object
      properties:
        query:
          type: string
          description: Search term for name, SKU, or description
          example: "security"
        status:
          type: string
          enum: [DRAFT, ACTIVE, DISCONTINUED]
          description: Filter by product status
        priceMin:
          type: number
          format: decimal
          minimum: 0
          description: Minimum price filter
        priceMax:
          type: number
          format: decimal
          minimum: 0
          description: Maximum price filter
        features:
          type: array
          items:
            type: string
          description: Filter by required features
        metadata:
          type: object
          additionalProperties:
            type: string
          description: Filter by metadata key-value pairs
```

### Product Endpoints

```yaml
paths:
  /products:
    post:
      summary: Create a new product
      description: |
        Creates a new product in the authenticated user's tenant.
        The product will be created in ACTIVE status if all validations pass.
      operationId: createProduct
      tags: [Products]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateProductRequest'
            examples:
              basic-product:
                summary: Basic Product
                value:
                  name: "Enterprise Security Suite"
                  sku: "ESS-123456"
                  description: "Comprehensive security solution"
                  price: 999.99
              feature-rich-product:
                summary: Product with Features
                value:
                  name: "Advanced Analytics Platform"
                  sku: "AAP-789012"
                  price: 1999.99
                  features:
                    - name: "real-time-processing"
                      enabled: true
                      configuration:
                        max_throughput: 10000
                    - name: "machine-learning"
                      enabled: false
                  metadata:
                    category: "analytics"
                    tier: "enterprise"
      responses:
        '201':
          description: Product created successfully
          headers:
            Location:
              schema:
                type: string
                format: uri
              description: URI of the created product
              example: "/api/v1/products/550e8400-e29b-41d4-a716-446655440000"
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProductResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '409':
          description: Conflict - Product with SKU already exists
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

    get:
      summary: List products
      description: |
        Retrieves a paginated list of products for the authenticated user's tenant.
        Supports filtering, searching, and sorting.
      operationId: listProducts
      tags: [Products]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
        - name: sort
          in: query
          schema:
            type: array
            items:
              type: string
          style: form
          explode: false
          example: ["name.asc", "createdAt.desc"]
        - name: status
          in: query
          schema:
            type: string
            enum: [DRAFT, ACTIVE, DISCONTINUED]
        - name: search
          in: query
          schema:
            type: string
          description: Search in name, SKU, or description
      responses:
        '200':
          description: Products retrieved successfully
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/PageResponse'
                  - type: object
                    properties:
                      content:
                        type: array
                        items:
                          $ref: '#/components/schemas/ProductResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'

  /products/{productId}:
    get:
      summary: Get product by ID
      description: Retrieves a specific product by its unique identifier
      operationId: getProductById
      tags: [Products]
      parameters:
        - name: productId
          in: path
          required: true
          schema:
            type: string
            format: uuid
          description: Unique product identifier
      responses:
        '200':
          description: Product retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProductResponse'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'

    put:
      summary: Update product
      description: Updates an existing product with new information
      operationId: updateProduct
      tags: [Products]
      parameters:
        - name: productId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateProductRequest'
      responses:
        '200':
          description: Product updated successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProductResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          $ref: '#/components/responses/Conflict'

    delete:
      summary: Discontinue product
      description: |
        Marks a product as discontinued. The product will no longer be available
        for new license creation but existing licenses remain valid.
      operationId: discontinueProduct
      tags: [Products]
      parameters:
        - name: productId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [reason]
              properties:
                reason:
                  type: string
                  minLength: 1
                  maxLength: 500
                  description: Reason for discontinuation
                  example: "Product end-of-life"
      responses:
        '204':
          description: Product discontinued successfully
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          description: Product cannot be discontinued
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

  /products/search:
    post:
      summary: Advanced product search
      description: |
        Performs advanced search with complex filtering criteria.
        Supports full-text search, feature filtering, and metadata queries.
      operationId: searchProducts
      tags: [Products]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ProductSearchRequest'
      responses:
        '200':
          description: Search results
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/PageResponse'
                  - type: object
                    properties:
                      content:
                        type: array
                        items:
                          $ref: '#/components/schemas/ProductResponse'
```

## License API

### License Schemas

```yaml
components:
  schemas:
    CreateLicenseRequest:
      type: object
      required: [productId, customerId, seats]
      properties:
        productId:
          type: string
          format: uuid
          description: Product to license
        customerId:
          type: string
          format: uuid
          description: Customer receiving the license
        seats:
          type: integer
          minimum: 1
          maximum: 10000
          description: Number of license seats
          example: 50
        maxSeats:
          type: integer
          minimum: 1
          maximum: 10000
          description: Maximum seats (for future expansion)
          example: 100
        expiryDate:
          type: string
          format: date
          description: License expiry date (null for perpetual)
          example: "2025-12-31"
        features:
          type: array
          items:
            $ref: '#/components/schemas/LicenseFeature'
          description: Licensed features configuration

    IssueLicenseRequest:
      type: object
      required: [seats]
      properties:
        seats:
          type: integer
          minimum: 1
          description: Number of seats to issue
        expiryDate:
          type: string
          format: date
          description: License expiry date
        features:
          type: array
          items:
            $ref: '#/components/schemas/LicenseFeature'

    LicenseResponse:
      type: object
      properties:
        licenseId:
          type: string
          format: uuid
        productId:
          type: string
          format: uuid
        customerId:
          type: string
          format: uuid
        licenseKey:
          type: string
          description: License key (only returned after issuance)
          example: "ESS-A1B2C3D4E5F67890123456789012"
        seats:
          type: integer
        maxSeats:
          type: integer
        expiryDate:
          type: string
          format: date
          nullable: true
        status:
          type: string
          enum: [DRAFT, ACTIVE, EXPIRED, REVOKED, SUSPENDED]
        features:
          type: array
          items:
            $ref: '#/components/schemas/LicenseFeature'
        usageMetrics:
          type: object
          properties:
            currentUsage:
              type: integer
              description: Current seat usage
            lastValidated:
              type: string
              format: date-time
              description: Last validation timestamp
        createdAt:
          type: string
          format: date-time
        issuedAt:
          type: string
          format: date-time
          nullable: true
        updatedAt:
          type: string
          format: date-time

    LicenseFeature:
      type: object
      required: [name, enabled]
      properties:
        name:
          type: string
          description: Feature name
          example: "advanced-reporting"
        enabled:
          type: boolean
          description: Whether feature is enabled
        limits:
          type: object
          additionalProperties:
            type: integer
          description: Feature-specific limits
          example:
            max_reports: 100
            retention_days: 365

    ValidateLicenseRequest:
      type: object
      required: [licenseKey]
      properties:
        licenseKey:
          type: string
          description: License key to validate
        features:
          type: array
          items:
            type: string
          description: Features to validate access for

    ValidationResponse:
      type: object
      properties:
        valid:
          type: boolean
          description: Whether license is valid
        licenseId:
          type: string
          format: uuid
          description: License identifier (if valid)
        remainingSeats:
          type: integer
          description: Available seats remaining
        expiryDate:
          type: string
          format: date
          nullable: true
          description: License expiry date
        features:
          type: array
          items:
            $ref: '#/components/schemas/LicenseFeature'
          description: Available features
        validationId:
          type: string
          format: uuid
          description: Unique validation identifier for auditing
        validatedAt:
          type: string
          format: date-time
          description: Validation timestamp
```

### License Endpoints

```yaml
paths:
  /licenses:
    post:
      summary: Create a new license
      description: |
        Creates a new license in DRAFT status. The license must be explicitly
        issued before it becomes active and generates a license key.
      operationId: createLicense
      tags: [Licenses]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateLicenseRequest'
      responses:
        '201':
          description: License created successfully
          headers:
            Location:
              schema:
                type: string
                format: uri
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LicenseResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'

    get:
      summary: List licenses
      description: Retrieves a paginated list of licenses
      operationId: listLicenses
      tags: [Licenses]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            minimum: 0
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
        - name: status
          in: query
          schema:
            type: string
            enum: [DRAFT, ACTIVE, EXPIRED, REVOKED, SUSPENDED]
        - name: customerId
          in: query
          schema:
            type: string
            format: uuid
          description: Filter by customer
        - name: productId
          in: query
          schema:
            type: string
            format: uuid
          description: Filter by product
        - name: expiringInDays
          in: query
          schema:
            type: integer
            minimum: 0
            maximum: 365
          description: Filter licenses expiring within N days
      responses:
        '200':
          description: Licenses retrieved successfully
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/PageResponse'
                  - type: object
                    properties:
                      content:
                        type: array
                        items:
                          $ref: '#/components/schemas/LicenseResponse'

  /licenses/{licenseId}:
    get:
      summary: Get license by ID
      operationId: getLicenseById
      tags: [Licenses]
      parameters:
        - name: licenseId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: License retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LicenseResponse'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'

  /licenses/{licenseId}/issue:
    post:
      summary: Issue a license
      description: |
        Issues a DRAFT license, generating a license key and activating it.
        This operation is irreversible and transitions the license to ACTIVE status.
      operationId: issueLicense
      tags: [Licenses]
      parameters:
        - name: licenseId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/IssueLicenseRequest'
      responses:
        '200':
          description: License issued successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/LicenseResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          description: License already issued
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

  /licenses/{licenseId}/validate:
    post:
      summary: Validate a license
      description: |
        Validates a license for usage. This endpoint is typically called by
        client applications to verify license validity before granting access.
      operationId: validateLicense
      tags: [Licenses]
      parameters:
        - name: licenseId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ValidateLicenseRequest'
      responses:
        '200':
          description: License validation result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'

  /licenses/{licenseId}/revoke:
    post:
      summary: Revoke a license
      description: |
        Revokes an active license, immediately invalidating it.
        This operation is irreversible and should be used with caution.
      operationId: revokeLicense
      tags: [Licenses]
      parameters:
        - name: licenseId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [reason]
              properties:
                reason:
                  type: string
                  minLength: 1
                  maxLength: 500
                  description: Reason for revocation
                  example: "Customer contract terminated"
      responses:
        '204':
          description: License revoked successfully
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'
        '409':
          description: License cannot be revoked
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

  /licenses/validate-key:
    post:
      summary: Validate license by key
      description: |
        Validates a license using its license key. This is the primary
        endpoint used by client applications for license validation.
      operationId: validateLicenseByKey
      tags: [Licenses]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ValidateLicenseRequest'
      responses:
        '200':
          description: License validation result
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ValidationResponse'
        '400':
          $ref: '#/components/responses/BadRequest'
        '401':
          $ref: '#/components/responses/Unauthorized'
```

## Implementation Examples

### Spring Boot Controller

```kotlin
// products/licensing-server/src/main/kotlin/com/axians/eaf/products/licensing/web/ProductController.kt
@RestController
@RequestMapping("/api/v1/products")
@Validated
@SecurityRequirement(name = "BearerAuth")
class ProductController(
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(
        @RequestBody @Valid request: CreateProductRequest,
        authentication: JwtAuthenticationToken
    ): ResponseEntity<ProductResponse> {
        val command = CreateProductCommand(
            productId = UUID.randomUUID().toString(),
            tenantId = authentication.tenantId,
            name = request.name,
            sku = request.sku,
            description = request.description,
            price = request.price,
            features = request.features ?: emptySet(),
            metadata = request.metadata ?: emptyMap()
        )

        return either {
            val result = commandGateway.sendAndWait<Either<DomainError, String>>(command)
                .bind()

            val productId = result.bind()
            val product = queryGateway.query(
                FindProductByIdQuery(productId, authentication.tenantId),
                ProductProjection::class.java
            ).join() ?: throw ResourceNotFoundException("Product", productId)

            ResponseEntity
                .created(URI.create("/api/v1/products/$productId"))
                .body(product.toResponse())
        }.fold(
            ifLeft = { error -> throw error.toHttpException() },
            ifRight = { it }
        )
    }

    @GetMapping("/{productId}")
    fun getProduct(
        @PathVariable productId: String,
        authentication: JwtAuthenticationToken
    ): ProductResponse {
        val query = FindProductByIdQuery(productId, authentication.tenantId)
        val product = queryGateway.query(query, ProductProjection::class.java).join()
            ?: throw ResourceNotFoundException("Product", productId)

        return product.toResponse()
    }

    @GetMapping
    fun listProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "name.asc") sort: Array<String>,
        @RequestParam(required = false) status: ProductStatus?,
        @RequestParam(required = false) search: String?,
        authentication: JwtAuthenticationToken
    ): Page<ProductResponse> {
        val pageable = PageRequest.of(page, size, parseSort(sort))

        val query = if (search != null) {
            ProductSearchQuery(
                searchTerm = search,
                tenantId = authentication.tenantId,
                status = status,
                page = page,
                size = size
            )
        } else {
            FindProductsQuery(
                tenantId = authentication.tenantId,
                status = status,
                pageable = pageable
            )
        }

        val result = queryGateway.query(query, Page::class.java).join()
        return result.map { (it as ProductProjection).toResponse() }
    }
}
```

### Global Exception Handler

```kotlin
// framework/web/src/main/kotlin/com/axians/eaf/web/GlobalExceptionHandler.kt
@RestControllerAdvice
class GlobalExceptionHandler(
    private val tracer: Tracer,
    private val meterRegistry: MeterRegistry
) {

    @ExceptionHandler(DomainError::class)
    fun handleDomainError(
        error: DomainError,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = when (error) {
            is DomainError.ValidationError -> createValidationProblem(error, request)
            is DomainError.BusinessRuleViolation -> createBusinessProblem(error, request)
            is DomainError.ResourceNotFound -> createNotFoundProblem(error, request)
            is DomainError.ConcurrencyConflict -> createConflictProblem(error, request)
        }

        enrichWithContext(problemDetail, request)
        recordMetrics(problemDetail)

        return ResponseEntity
            .status(problemDetail.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationException(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Request validation failed"
        ).apply {
            type = URI.create("https://api.axians.com/problems/validation-error")
            title = "Validation Error"
            instance = URI.create(request.requestURI)

            setProperty("violations", ex.constraintViolations.map { violation ->
                mapOf(
                    "field" to violation.propertyPath.toString(),
                    "constraint" to violation.constraintDescriptor.annotation.annotationClass.simpleName,
                    "invalidValue" to violation.invalidValue,
                    "message" to violation.message
                )
            })
        }

        enrichWithContext(problemDetail, request)
        return ResponseEntity
            .status(problemDetail.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problemDetail)
    }

    private fun enrichWithContext(problemDetail: ProblemDetail, request: HttpServletRequest) {
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val tenantId = TenantContext.current()?.tenantId

        problemDetail.setProperty("traceId", traceId)
        problemDetail.setProperty("tenantId", tenantId)
        problemDetail.setProperty("timestamp", Instant.now())
        problemDetail.setProperty("path", request.requestURI)
        problemDetail.setProperty("method", request.method)
    }

    private fun recordMetrics(problemDetail: ProblemDetail) {
        meterRegistry.counter(
            "api.errors.total",
            "status", problemDetail.status.toString(),
            "type", problemDetail.type?.toString() ?: "unknown"
        ).increment()
    }
}
```

## Related Documentation

- **[Data Models](data-models.md)** - Domain aggregates and events used by these APIs
- **[Security Architecture](security.md)** - JWT authentication and authorization details
- **[Error Handling Strategy](error-handling-strategy.md)** - Comprehensive error handling patterns
- **[System Components](components.md)** - CQRS infrastructure supporting these APIs
- **[Testing Strategy](test-strategy-and-standards-revision-3.md)** - API testing patterns and examples

---

**Next Steps**: Review [Security Architecture](security.md) for JWT implementation details, then proceed to [Error Handling Strategy](error-handling-strategy.md) for comprehensive error management patterns.