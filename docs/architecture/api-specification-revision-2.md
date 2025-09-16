# API Specification (Revision 2)

Our API style is **REST / CQRS**. This requires an OpenAPI 3.0 specification.

### REST API Specification (OpenAPI 3.0) - MVP v0.1

```yaml
openapi: 3.0.1
info:
  title: "Enterprise Application Framework (EAF) v0.1 API"
  version: "0.1.0"
  description: |
    The official API for the EAF. 
    This API uses CQRS patterns (Commands/Queries) exposed via REST endpoints.
    All endpoints (except OIDC discovery) are secured by Keycloak (JWT Bearer Token) and are multi-tenant aware.
servers:
  - url: /api/v1
    description: EAF v1 API Root

security:
  - bearerAuth: []

paths:
  /widgets:
    post:
      summary: "Epic 2.2: Create Widget (Dispatches CreateWidgetCommand)"
      operationId: createWidget
      security:
        - bearerAuth: []
      requestBody:
        description: The CreateWidgetCommand payload
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                name:
                  type: string
      responses:
        '202':
          description: "Accepted. The command has been dispatched successfully."
          content:
            application/json:
              schema:
                type: object
                properties:
                  widgetId:
                    type: string
                    format: uuid
        '400':
          description: "Bad Request. Response uses RFC 7807."
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '401':
          description: "Unauthorized (Invalid/Missing JWT)."
        '403':
          description: "Forbidden (Valid JWT, insufficient permissions or Tenant error)."

  /widgets/{id}:
    get:
      summary: "Epic 2.4: Get Widget Projection (Dispatches FindWidgetQuery)"
      operationId: getWidgetById
      security:
        - bearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: "Success. Returns the Widget read model projection."
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/WidgetProjection'
        '404':
          description: "Not Found. Response uses RFC 7807."
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    WidgetProjection:
      type: object
      properties:
        widgetId: { type: string, format: uuid }
        tenantId: { type: string, format: uuid }
        name: { type: string }
        createdDate: { type: string, format: date-time }
        lastModifiedDate: { type: string, format: date-time }
    
    # (TenantProjection, ProductProjection, and LicenseProjection schemas also defined here)

    ProblemDetail:
      type: object
      properties:
        type: { type: string, format: uri, description: "A URI reference that identifies the problem type." }
        title: { type: string, description: "A short, human-readable summary of the problem type." }
        status: { type: number, format: int32, description: "The HTTP status code." }
        detail: { type: string, description: "A human-readable explanation specific to this occurrence of the problem." }
        instance: { type: string, format: uri, description: "A URI reference that identifies the specific occurrence of the problem." }
```

### Post-MVP Evolution: GraphQL Gateway

Per user requirement: The Post-MVP roadmap includes adding a GraphQL endpoint. This will be implemented as a **Gateway Layer** (using a tool capable of OpenAPI-to-GraphiQL conversion) that automatically provides GraphQL queries/mutations by consuming the versioned v0.1 OpenAPI (REST) specification defined above. This avoids logic duplication while providing a flexible API for future consumers.

-----
