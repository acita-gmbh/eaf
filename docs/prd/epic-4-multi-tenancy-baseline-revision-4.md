# Epic 4: Multi-Tenancy Baseline (Revision 4)

**Epic Goal:** This epic delivers the complete data isolation architecture for the EAF. It implements the prototype's validated **3-layer tenant isolation model**, ensuring that all data access (Commands, Queries, and Projections) is programmatically isolated by tenant, fulfilling a critical security requirement. This epic also solves the async context propagation required by CQRS.

### Story 4.1: Implement Layer 1 (Request Layer): TenantContext Filter
* **As a** Core Developer, **I want** a request filter that extracts the validated tenant ID (from the JWT) and places it into a secure, request-scoped `TenantContext`, **so that** all downstream application logic and database sessions can reliably access the active tenant ID.
* **AC 1:** A `TenantContext` (likely using `ThreadLocal`) is created to hold the active `tenant_id`.
* **AC 2:** A Spring `Filter` is implemented that runs *after* the AuthN filter (from Epic 3).
* **AC 3:** The filter extracts the `tenant_id` claim (which was validated in Story 3.3) from the security principal and populates the `TenantContext`.
* **AC 4:** The filter implements a **"Fail-Closed" design**: If the JWT is valid but the `tenant_id` claim is missing, the request is immediately rejected (403 Forbidden).
* **AC 5:** The filter ensures the `TenantContext` is cleared (e.g., in a `finally` block) after the request completes to prevent context leakage.

### Story 4.2: Implement Layer 2 (Service Layer): Tenant Boundary Validation
* **As a** Core Developer, **I want** the Axon Command Handlers (Service Layer) to validate that the active tenant (from `TenantContext`) is authorized to operate on the requested aggregate, **so that** tenants cannot maliciously issue commands against another tenant's data (defense-in-depth).
* **AC 1:** All new aggregates (like `CreateWidgetCommand`) must be automatically assigned the `tenant_id` from the `TenantContext` when created.
* **AC 2:** When loading an existing aggregate (e.g., for an Update command), the framework must verify that the aggregate's stored tenant ID matches the `TenantContext` tenant ID.
* **AC 3:** If the tenant IDs do not match, a Security Exception is thrown, the transaction is rolled back, and the operation fails (e.g., 403 Forbidden or 404 Not Found).
* **AC 4:** An integration test (using the Keycloak Testcontainer from Epic 3) verifies that User A (Tenant A) *cannot* issue a valid command against an aggregate belonging to Tenant B.

### Story 4.3: Implement Layer 3 (Database Layer): PostgreSQL Row-Level Security (RLS)
* **As a** Core Developer, **I want** Row-Level Security (RLS) enabled on all tenant-owned tables (both event store and projections), **so that** data is isolated at the database layer itself as the final security guarantee.
* **AC 1:** A database interceptor (e.g., a JDBC interceptor or JPA listener) is created that sets the current PostgreSQL session variable (e.g., `SET app.current_tenant_id = ?`) using the ID from the `TenantContext` (from 4.1) at the start of every transaction.
* **AC 2:** RLS Policies are enabled via migration scripts on the `domain_event_entry` (Event Store) and `widget_projection` (Read Model) tables.
* **AC 3:** The RLS policies strictly enforce that all SELECT, INSERT, UPDATE, and DELETE operations can only apply to rows where the `tenant_id` column matches the `app.current_tenant_id` session variable.
* **AC 4:** Integration tests verify that even a direct SQL query (simulating an SQL injection vulnerability) attempting to read data *without* the session variable correctly set returns zero rows.

### Story 4.4: Implement Tenant Context Propagation for Async Processors
* **As a** Core Developer, **I want** the `TenantContext` to be reliably propagated to all asynchronous Axon Event Processors, **so that** the projection handlers (our read-side) can successfully write data to the RLS-protected database tables.
* **AC 1:** A mechanism (such as an Axon Message Interceptor or custom Unit of Work component) is implemented to read the `tenant_id` from the metadata of every event being processed by a Tracking Event Processor.
* **AC 2:** This mechanism correctly populates the `TenantContext` (from Story 4.1) *before* the asynchronous `@EventHandler` logic executes.
* **AC 3:** The database interceptor (from Story 4.3) successfully reads this propagated `TenantContext` and sets the PostgreSQL session variable (`app.current_tenant_id`) for the projection's transaction.
* **AC 4:** The end-to-end integration tests are updated to verify that the `widget_projection` table *is successfully written to* (passing the RLS check from 4.3) and that the data is correct.

---
