# Story 3.1: VMware Connection Configuration

Status: done

## Story

As a **tenant admin**,
I want to configure VMware vCenter connection settings,
So that DCM can provision VMs in my infrastructure.

## Acceptance Criteria

### AC-3.1.1: VMware Configuration Form
**Given** I am a tenant admin
**When** I navigate to Settings > VMware Configuration
**Then** I see a configuration form with fields for:
- vCenter URL (`https://vcenter.example.com/sdk`)
- Username (service account)
- Password (masked input, encrypted at rest)
- Datacenter name
- Cluster name
- Datastore name
- Network name (for default network)
- Template name (default: ubuntu-22.04-template)
- Folder path (optional, for VM organization)

### AC-3.1.2: Test Connection Success
**Given** I have entered valid VMware configuration
**When** I click "Test Connection"
**Then** I see success message: "Connected to vCenter 8.0, Cluster: {name}"
**And** the connection is validated against vCenter API

### AC-3.1.3: Test Connection Failure
**Given** I have entered invalid VMware configuration
**When** I click "Test Connection"
**Then** I see specific error messages:
- "Connection refused" (network/firewall issue)
- "Authentication failed" (invalid credentials)
- "Datacenter not found" (wrong datacenter name)
- "Cluster not found" (wrong cluster name)

### AC-3.1.4: Secure Credential Storage
**Given** I save VMware configuration
**When** the configuration is persisted
**Then** credentials are encrypted using AES-256 (Spring Security Crypto)
**And** configuration is tenant-isolated via RLS policy
**And** only admins of the same tenant can view/modify

### AC-3.1.5: Missing Configuration Warning
**Given** no VMware configuration exists for my tenant
**When** a user tries to submit a VM request
**Then** they see message: "VMware not configured - contact Admin"
**And** the VM request form submit button is disabled

## Tasks / Subtasks

### Task 1: Database Schema (AC: 3.1.4)
- [x] 1.1 Create Flyway migration `V008__vmware_configurations.sql`
  ```sql
  CREATE TABLE "VMWARE_CONFIGURATIONS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL UNIQUE,  -- One config per tenant
    "VCENTER_URL" VARCHAR(500) NOT NULL,
    "USERNAME" VARCHAR(255) NOT NULL,
    "PASSWORD_ENCRYPTED" BYTEA NOT NULL,  -- AES-256 encrypted
    "DATACENTER_NAME" VARCHAR(255) NOT NULL,
    "CLUSTER_NAME" VARCHAR(255) NOT NULL,
    "DATASTORE_NAME" VARCHAR(255) NOT NULL,
    "NETWORK_NAME" VARCHAR(255) NOT NULL,
    "TEMPLATE_NAME" VARCHAR(255) NOT NULL DEFAULT 'ubuntu-22.04-template',
    "FOLDER_PATH" VARCHAR(500),
    "VERIFIED_AT" TIMESTAMPTZ,  -- Last successful connection test
    "CREATED_AT" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "UPDATED_AT" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "CREATED_BY" UUID NOT NULL,
    "UPDATED_BY" UUID NOT NULL,
    "VERSION" BIGINT NOT NULL DEFAULT 0  -- Optimistic locking
  );
  CREATE INDEX "IDX_VMWARE_CONFIGS_TENANT" ON "VMWARE_CONFIGURATIONS"("TENANT_ID");
  -- RLS Policy (CRITICAL: Include WITH CHECK)
  ALTER TABLE "VMWARE_CONFIGURATIONS" ENABLE ROW LEVEL SECURITY;
  CREATE POLICY tenant_isolation ON "VMWARE_CONFIGURATIONS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
  ALTER TABLE "VMWARE_CONFIGURATIONS" FORCE ROW LEVEL SECURITY;
  GRANT SELECT, INSERT, UPDATE, DELETE ON "VMWARE_CONFIGURATIONS" TO eaf_app;
  ```
- [x] 1.2 Update `dcm-infrastructure/src/main/resources/db/jooq-init.sql` with H2-compatible DDL
- [x] 1.3 Regenerate jOOQ code: `./gradlew :dcm:dcm-infrastructure:generateJooq`
- [x] 1.4 Verify RLS policy has both USING and WITH CHECK clauses

### Task 2: Backend Domain & Application (AC: 3.1.1, 3.1.4)
- [x] 2.1 Create `VmwareConfiguration` data class in `dcm-domain` (configuration entity, not value object - has ID and lifecycle)
- [x] 2.2 Create `VmwareConfigurationPort` interface in `dcm-application/ports`
- [x] 2.3 Create `CreateVmwareConfigCommand` and handler (use `Result<T,E>` pattern)
- [x] 2.4 Create `UpdateVmwareConfigCommand` and handler (include VERSION for optimistic locking)
- [x] 2.5 Create `GetVmwareConfigQuery` and handler
- [x] 2.6 Implement `CredentialEncryptor` using Spring Security Crypto (AES-256)

### Task 3: Backend Infrastructure (AC: 3.1.1, 3.1.4)
- [x] 3.1 Create `VmwareConfigurationRepository` using jOOQ
  - Use sealed `ProjectionColumns` pattern (see `VmRequestProjectionRepository.kt` for reference)
  - Include VERSION check for optimistic locking on UPDATE operations
- [x] 3.2 Implement `EncryptedCredentialConverter` for jOOQ type mapping (BYTEA ↔ String)
- [x] 3.3 Create `VmwareConfigurationProjection` for read queries

### Task 4: vSphere Connection Testing (AC: 3.1.2, 3.1.3)
- [x] 4.1 Create `VspherePort` interface with `testConnection()` method
- [x] 4.2 Create `VcsimAdapter` implementation (for tests)
- [x] 4.3 Create `VcenterAdapter` implementation (for production, uses yavijava library)
- [x] 4.4 Configure Spring Profile switching (`@Profile("vcsim")` / `@Profile("!vcsim")`)
- [x] 4.5 Wrap yavijava blocking calls in `withContext(Dispatchers.IO)`:
  ```kotlin
  suspend fun testConnection(
      params: VcenterConnectionParams,
      password: String
  ): Result<ConnectionInfo, ConnectionError> =
      withContext(Dispatchers.IO) {
          // yavijava SOAP calls are blocking - must run on IO dispatcher
          val serviceInstance = ServiceInstance(URL(params.vcenterUrl), params.username, password, true)
          // ... connection logic
      }
  ```

### Task 5: REST API (AC: 3.1.1, 3.1.2, 3.1.3)
- [x] 5.1 Create `VmwareConfigController` with endpoints:
  - `GET /api/admin/vmware-config` - Get current config (masked password)
  - `PUT /api/admin/vmware-config` - Create/update config
  - `POST /api/admin/vmware-config/test` - Test connection
- [x] 5.2 Create `VmwareConfigRequest` DTO with Zod-like validation
- [x] 5.3 Create `VmwareConfigResponse` DTO (password masked)
- [x] 5.4 Create `ConnectionTestResponse` DTO
- [x] 5.5 Add `@PreAuthorize("hasRole('admin')")` security

### Task 6: Frontend Settings Form (AC: 3.1.1, 3.1.2, 3.1.3)
- [x] 6.1 Create `VmwareConfigurationForm.tsx` component
- [x] 6.2 Add form validation with React Hook Form + Zod
- [x] 6.3 Implement masked password input with show/hide toggle
- [x] 6.4 Implement "Test Connection" button with loading state
- [x] 6.5 Display connection test results (success/error messages)
- [x] 6.6 Add Settings page route and navigation

### Task 7: Missing Config Warning (AC: 3.1.5)
- [x] 7.1 Create `useVmwareConfigExists` hook
- [x] 7.2 Update VM Request form to check config existence
- [x] 7.3 Display warning banner when config missing
- [x] 7.4 Disable submit button with tooltip explanation

### Task 8: Testing (All ACs)
- [x] 8.1 Unit tests for command handlers (MockK)
- [x] 8.2 Unit tests for credential encryption/decryption
- [x] 8.3 Integration tests for VmwareConfigController
- [x] 8.4 RLS isolation tests (TC-002 pattern) - verify cross-tenant access fails
- [x] 8.5 VCSIM integration tests for VcenterAdapter (stub implementation - real VCSIM deferred to Story 3.2)
- [x] 8.6 Frontend component tests for VmwareConfigurationForm
- [x] 8.7 Achieve 80% code coverage, 70% mutation score (verified 2025-12-05)

## Dev Notes

### Epic 3 Context: VM Provisioning
**Goal:** Transform DCM from a workflow tool into a real infrastructure automation system by implementing actual VM provisioning on VMware ESXi.
**User Value:** "My VM is actually created - not just a ticket."
**Critical Risk:** VMware API complexity and infrastructure dependency.
**Mitigation Strategy:** Use VCSIM for all integration tests, implement idempotent provisioning, and use circuit breakers.

### Story Context: 3.1 VMware Connection Configuration
This story is the **VMware Tracer Bullet**, validating the complete VMware integration stack by establishing the connection configuration.

**Key Objective:** Create a robust, secure, and tenant-isolated mechanism for admins to configure vCenter connections.
**Impact:** Enables all Epic 3 provisioning functionality.
**Dependencies:** RLS (Story 1.6), Auth (Story 2.1).

### Epic 2 Learnings (APPLY THESE!)

1. **MockK Default Parameters Pattern:**
   When stubbing Kotlin functions with default parameters, use `any()` for ALL parameters:
   ```kotlin
   // CORRECT - Explicitly match ALL parameters
   coEvery { handler.handle(any(), any()) } returns result.success()

   // WRONG - MockK evaluates defaults at setup time
   coEvery { handler.handle(any()) } returns result.success()
   ```
   [Source: CLAUDE.md - MockK Unit Testing Patterns]

2. **Fire-and-Forget Pattern for Async Operations:**
   Use `void` operator for intentional fire-and-forget (TanStack Query invalidation, navigation):
   ```tsx
   void queryClient.invalidateQueries({ queryKey: ['vmware-config'] })
   ```
   [Source: Story 2.12 Email Notifications pattern]

3. **RLS Test Pattern (TC-002):**
   Always verify cross-tenant access returns 0 rows, not an error:
   ```kotlin
   // Tenant B queries should NOT see Tenant A's config
   val result = repository.findByTenantId(tenantB)
   assertThat(result).isEmpty()
   ```

4. **Frontend Readonly Props:**
   All React props must use `Readonly<Props>`:
   ```tsx
   export function VmwareConfigForm({ config, onSave }: Readonly<Props>) { ... }
   ```

### Technical Requirements

- **Backend:**
  - **API:** `GET/PUT /api/admin/vmware-config`, `POST /api/admin/vmware-config/test`
  - **Command Pattern:** Commands in `dcm-application`, handlers with `Result<T,E>`
  - **Security:** AES-256 encryption for passwords (Spring Security Crypto)
  - **Multi-tenancy:** `tenant_id` column with RLS enforcement (USING + WITH CHECK)
  - **Context:** Propagate `TenantContext` via coroutine context element

- **Frontend:**
  - **Settings View:** New "VMware Configuration" form under Settings
  - **Fields:** URL, User, Password (masked), Datacenter, Cluster, Datastore, Network
  - **Action:** "Test Connection" with immediate feedback
  - **User Check:** VM Request form must show "VMware not configured" if missing

### Architecture Compliance

- **Module Boundaries (ADR-001):**
  - `dcm-api`: Controllers, DTOs
  - `dcm-application`: Commands, Queries, Handlers, Ports (interfaces)
  - `dcm-infrastructure`: Adapters, Repositories, External clients
  - **NO** direct EAF -> DCM dependencies (Konsist enforced)

- **Adapter Pattern (CRITICAL):**
  ```kotlin
  // Port in dcm-application
  interface VspherePort {
      suspend fun testConnection(config: VmwareConfiguration): Result<ConnectionInfo, ConnectionError>
  }

  // Adapters in dcm-infrastructure - switched via Spring Profile
  @Profile("vcsim")
  class VcsimAdapter : VspherePort { ... }

  @Profile("!vcsim")
  class VcenterAdapter : VspherePort { ... }
  ```
  **NO** `if (isVcsim)` conditionals in service layer!

### Library & Frameworks (with versions)

| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | 2.2+ | Language with context parameters |
| Spring Boot | 3.5+ | WebFlux, Security Crypto |
| yavijava | 6.0.x | vSphere SOAP SDK (**DEPRECATED** - see Story 3.1.1 for migration to official SDK) |
| resilience4j | 2.2.x | Circuit breaker for vSphere calls |
| PostgreSQL | 16+ | RLS support |
| React | 19.2+ | Frontend with React Compiler |
| React Hook Form | 7.x | Form management |
| Zod | 3.x | Schema validation |

**Note:** Story 3.1 was implemented with `yavijava`. **Story 3.1.1 migrates to the official vSphere Automation SDK** due to yavijava deprecation (last release May 2017).

### File Structure Targets

**Backend:**
- `dcm-infrastructure/src/main/resources/db/migration/V008__vmware_configurations.sql`
- `dcm-infrastructure/src/main/resources/db/jooq-init.sql` (UPDATE)
- `dcm-api/src/main/kotlin/.../VmwareConfigurationController.kt`
- `dcm-api/src/main/kotlin/.../dto/VmwareConfigurationDto.kt`
- `dcm-application/src/main/kotlin/.../vmware/CreateVmwareConfigCommand.kt`
- `dcm-application/src/main/kotlin/.../vmware/UpdateVmwareConfigCommand.kt`
- `dcm-application/src/main/kotlin/.../vmware/GetVmwareConfigQuery.kt`
- `dcm-application/src/main/kotlin/.../ports/VspherePort.kt`
- `dcm-infrastructure/src/main/kotlin/.../vmware/VmwareConfigurationRepository.kt`
- `dcm-infrastructure/src/main/kotlin/.../vmware/VcenterAdapter.kt`
- `dcm-infrastructure/src/main/kotlin/.../vmware/VcsimAdapter.kt`
- `dcm-infrastructure/src/main/kotlin/.../crypto/CredentialEncryptor.kt`

**Frontend:**
- `dcm-web/src/pages/settings/VmwareConfigurationPage.tsx`
- `dcm-web/src/components/settings/VmwareConfigurationForm.tsx`
- `dcm-web/src/api/vmware-config.ts`
- `dcm-web/src/hooks/useVmwareConfig.ts`

**Tests:**
- `dcm-application/src/test/.../CreateVmwareConfigCommandHandlerTest.kt`
- `dcm-application/src/test/.../UpdateVmwareConfigCommandHandlerTest.kt`
- `dcm-infrastructure/src/test/.../VmwareConfigurationRepositoryIntegrationTest.kt`
- `dcm-infrastructure/src/test/.../VcenterAdapterVcsimTest.kt`
- `dcm-infrastructure/src/test/.../CredentialEncryptorTest.kt`
- `dcm-api/src/test/.../VmwareConfigurationControllerIntegrationTest.kt`
- `dcm-web/src/components/settings/__tests__/VmwareConfigurationForm.test.tsx`

### Testing Strategy

| Type | Scope | Key Validations |
|------|-------|-----------------|
| Unit | Command handlers, encryption | Business logic, error handling |
| Integration | API + Repository | Full request flow, RLS enforcement |
| VCSIM | VcenterAdapter | Real vSphere API calls against simulator |
| Security | RLS (TC-002) | Cross-tenant access MUST fail |
| Frontend | Component | Form validation, test connection UX |

**Coverage Requirements:**
- 80% line coverage per module (Kover)
- 70% mutation score (Pitest)

### References

- [Epic 3.1 Requirements: docs/epics.md#Story-3.1]
- [Tech Spec Epic 3: docs/sprint-artifacts/tech-spec-epic-3.md]
- [Architecture ADR-001: docs/architecture.md#ADR-001]
- [Security Crypto: docs/security-architecture.md]
- [RLS Pattern TC-002: CLAUDE.md#PostgreSQL-RLS-Policies]
- [MockK Pattern: CLAUDE.md#MockK-Unit-Testing-Patterns]

## Dev Agent Record

### Context Reference

This story context was regenerated with comprehensive validation fixes including:
- Proper user story statement from epics.md
- All 5 acceptance criteria from tech-spec-epic-3.md
- Specific implementation tasks with AC references
- Database schema with RLS requirements
- Epic 2 learnings to prevent common mistakes
- Complete file structure and library versions

### Agent Model Used

Claude Opus 4.5 (SM Agent Bob)

### Validation Applied

- Validated against create-story checklist
- All 6 critical issues resolved
- All 2 enhancement opportunities applied

### Completion Notes

- Story regenerated on 2025-12-04 with comprehensive improvements
- All acceptance criteria mapped to specific tasks
- Ready for implementation by Dev Agent

## Story Completion Status

**Status:** in-progress

**Validation Score:** 12/12 passed (100%)

**Next Steps:**
1. Run `dev-story` workflow to implement
2. Run `code-review` when complete
3. Update sprint-status.yaml to `done` after review passes
