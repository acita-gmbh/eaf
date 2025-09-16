# Epic 3: Authentication (Core) (Revision 3)

**Epic Goal:** This epic delivers the complete, production-grade authentication (AuthN) layer for the EAF. It implements the prototype's validated 10-layer JWT standard, integrates with Keycloak, and builds the mandatory, **provisioned Keycloak Testcontainer** required for 100% accurate integration testing.

### Story 3.1: Create Keycloak Testcontainer Configuration
* **As a** Core Developer, **I want** a configurable Keycloak Testcontainer build that is pre-provisioned with correct data, **so that** all integration tests run against a realistic, standardized identity provider that matches our production setup.
* **AC 1:** A configuration export (e.g., realm JSON file) is created that defines the EAF's required test realm, OIDC clients, and default test users/roles.
* **AC 2:** The Testcontainers configuration (defined in the convention plugins from Epic 1) is updated to automatically build and run Keycloak using this realm configuration on startup.
* **AC 3:** A utility is created within the test infrastructure to easily retrieve authentication tokens for specific test users (e.g., "admin-user," "basic-user") from the running container.

### Story 3.2: Configure Spring Security & Keycloak OIDC Integration
* **As a** Core Developer, **I want** Spring Security configured to delegate authentication to Keycloak via the OIDC protocol, **so that** the EAF relies on our enterprise standard identity provider.
* **AC 1:** Required Spring Security dependencies (like `spring-boot-starter-oauth2-resource-server`) are added via the convention plugins.
* **AC 2:** Application configuration is added to connect to the OIDC discovery endpoint of the Keycloak instance (running via Docker Compose (Story 1.3) locally, or the Testcontainer (Story 3.1) in tests).
* **AC 3:** A new, basic "secured" endpoint (e.g., `/api/secure/hello`) is created.
* **AC 4:** An integration test (using Kotest) confirms: 1) A request without a token is rejected (401), and 2) A request using a valid token from the **provisioned Keycloak Testcontainer (Story 3.1)** is accepted (200).

### Story 3.3: Implement 10-Layer JWT Validation Standard
* **As a** Core Developer, **I want** to implement the prototype's validated 10-layer JWT validation standard as a core framework component, **so that** all incoming API requests are robustly secured, meeting our ASVS goals.
* **AC 1:** A custom validation filter or Spring Security converter/validator chain is implemented.
* **AC 2:** The validation chain successfully performs all 10 validation layers (Format, Algorithm (RS256-only), Signature, Claims Schema, Time (exp/iat), Issuer/Audience, Token Blacklist (stubbed), Tenant ID presence, Rate Limiting (stubbed), and Audit Logging).
* **AC 3:** Integration tests (using the **provisioned Keycloak Testcontainer (3.1)**) confirm that valid tokens pass all layers, and tokens with invalid signatures, expired timestamps, missing tenant claims, or wrong algorithms are correctly rejected (401).

### Story 3.4: Secure the Walking Skeleton (Widget) API
* **As a** Core Developer, **I want** to apply the new security policies (from 3.3) to the Widget API (from Epic 2), **so that** our "Walking Skeleton" is fully secured end-to-end.
* **AC 1:** The `POST /widgets` (Story 2.2) and `GET /widgets/{id}` (Story 2.4) endpoints are now secured and require a valid, authenticated token.
* **AC 2:** The end-to-end integration test from Story 2.4 (AC4) is updated.
* **AC 3:** The updated test now uses the test utility (from 3.1) to fetch a valid JWT from the **provisioned Keycloak Testcontainer** before executing the test, confirming the full CQRS flow works when secured.
* **AC 4:** A new integration test confirms that requests to the Widget API *without* a token fail (401).

---
