# Epic 3: VM Provisioning - Technical Specification

**Version:** 1.0
**Created:** 2025-12-04
**Author:** SM Agent (Bob)
**Status:** Draft

---

## 1. Overview

### 1.1 Epic Summary

| Attribute | Value |
|-----------|-------|
| **Epic ID** | Epic 3 |
| **Title** | VM Provisioning |
| **Goal** | Implement actual VM provisioning on VMware ESXi via vSphere API |
| **Stories** | 10 |
| **Risk Level** | **CRITICAL** (VMware API complexity, infrastructure dependency) |
| **FRs Covered** | FR30, FR34-FR40, FR47, FR71, FR77-FR79 (11 FRs) |

### 1.2 User Value Statement

> "My VM is actually created - not just a ticket" - From approved request to running VM without manual intervention.

### 1.3 Critical Risk Factors

Epic 3 transforms DCM from a workflow tool into real infrastructure automation. The risk profile is fundamentally different from Epic 2:

| Risk Factor | Impact | Mitigation |
|-------------|--------|------------|
| **External dependency** | vSphere API can fail, timeout, return partial results | Circuit breaker pattern, comprehensive error handling |
| **Idempotency critical** | Duplicate VMs from retry scenarios | Correlation IDs in VM annotations, idempotent provisioning |
| **Async operations** | Provisioning takes minutes, not milliseconds | Saga pattern, progress tracking projection |
| **Resource cleanup** | Failed provisions create orphaned resources | Saga compensation, cleanup automation |
| **API variance** | VCSIM behavior may differ from real vCenter | Contract test suite (Story 3.9) |

### 1.4 Two-Phase Development Strategy

**Phase 1 (VCSIM):** Stories 3.1-3.8 - Development with vCenter Simulator
- All integration tests run against VCSIM (Story 1.10 foundation)
- No dependency on real vCenter infrastructure
- Safe to iterate rapidly on design

**Phase 2 (Real vCenter):** Story 3.9 - Validation with real infrastructure
- Contract tests validate VCSIM assumptions
- Requires a dedicated vCenter test instance (currently BLOCKED)
- Go-live gate: Phase 1 smoke tests must pass

---

## 2. Objectives & Scope

### 2.1 In Scope

1. **VMware Configuration** (Story 3.1)
   - Tenant-specific vCenter connection settings
   - Encrypted credential storage (AES-256)
   - Connection test functionality
   - Missing config warning for users

2. **vSphere API Client** (Story 3.2)
   - Robust client with official vSphere Automation SDK (post Story 3.1.1)
   - Circuit breaker pattern (resilience4j)
   - Connection pooling and session management
   - Full CRUD operations for VMs

3. **Provisioning Workflow** (Stories 3.3-3.4)
   - Automatic trigger on approval
   - Template-based VM creation (CloneVM_Task)
   - Guest customization (hostname)
   - Wait for VMware Tools ready

4. **Progress Tracking** (Story 3.5)
   - Real-time provisioning stages
   - SSE/polling updates
   - Estimated completion time

5. **Error Handling** (Story 3.6)
   - Exponential backoff retry (5 attempts)
   - Transient vs permanent error classification
   - Saga compensation for partial failures
   - User-friendly error messages

6. **VM Details & Notifications** (Stories 3.7-3.8)
   - Live VM status from VMware
   - Connection instructions (SSH/RDP)
   - Email notifications on ready/failure

### 2.2 Out of Scope

- Real vCenter integration tests (Story 3.9 BLOCKED)
- Multi-datacenter support
- VM lifecycle management (stop/restart/delete)
- Custom template selection (Linux default for MVP)
- Windows guest customization
- IP address allocation (DHCP only)

### 2.3 Technical Debt from Epic 2 to Address

| Item | Source | Action in Epic 3 |
|------|--------|------------------|
| E2E tests with full auth | Epic 2 Retro | Enable in Story 3.1 |
| Project name resolution | Story 2.12 | Create `ProjectDirectory` interface |
| Email templates | Story 2.12 | Add `vm-ready.html`, `vm-failed.html` |

---

## 3. Architecture Alignment

### 3.1 Ports & Adapters Pattern

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                            dcm-application                                  │
│  ┌─────────────────────┐   ┌─────────────────────────────────────────────┐  │
│  │ ProvisionVmHandler  │──▶│              VspherePort                    │  │
│  │                     │   │  - createVm(spec): Result<VmId, Error>      │  │
│  │ (Saga Orchestrator) │   │  - getVm(vmId): Result<VmInfo, Error>       │  │
│  └─────────────────────┘   │  - deleteVm(vmId): Result<Unit, Error>      │  │
│                            │  - testConnection(): Result<VersionInfo, E> │  │
│                            └─────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                         │
                  ┌──────────────────────┼──────────────────────┐
                  │                      │                      │
                  ▼                      ▼                      ▼
┌─────────────────────────────┐  ┌──────────────┐  ┌──────────────────────┐
│   VcsimAdapter              │  │ VcenterAdapter│  │    (Future)          │
│   (Spring Profile: vcsim)   │  │ (Profile:real)│  │  CloudAdapter        │
│                             │  │               │  │  (Azure/AWS)         │
│   Uses VCSIM /sdk endpoint  │  │ Official SDK  │  │                      │
└─────────────────────────────┘  └──────────────┘  └──────────────────────┘
```

**CRITICAL:** No conditional logic in service layer. Profile-based adapter injection only.

```kotlin
// ✅ CORRECT: Clean abstraction
interface VspherePort {
    suspend fun createVm(spec: VmSpec): Result<VmId, ProvisioningError>
    suspend fun getVm(vmId: VmId): Result<VmInfo, NotFoundError>
    suspend fun deleteVm(vmId: VmId): Result<Unit, DeletionError>
    suspend fun testConnection(): Result<VcenterInfo, ConnectionError>
}

// ❌ WRONG: Do NOT do this!
class VmProvisioningService {
    fun provision(request: VmRequest) {
        if (isVcsim) { ... } else { ... }  // NO!
    }
}
```

### 3.2 Module Dependencies

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                          dcm-app (Spring Boot)                              │
│   ┌─────────────┐   ┌──────────────────┐   ┌────────────────┐              │
│   │  dcm-api   │──▶│ dcm-application │──▶│  dcm-domain   │              │
│   │  (REST)     │   │ (Sagas/Handlers) │   │  (Aggregates)  │              │
│   └─────────────┘   └──────────────────┘   └────────────────┘              │
│         │                    │                      ▲                       │
│         ▼                    ▼                      │                       │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                    dcm-infrastructure                               │  │
│   │   ┌───────────────────┐   ┌────────────────────────────────────┐    │  │
│   │   │ VcenterAdapter    │   │ VcsimAdapter                       │    │  │
│   │   │ (Official SDK)    │   │ (VCSIM Testcontainer)              │    │  │
│   │   └───────────────────┘   └────────────────────────────────────┘    │  │
│   │   ┌───────────────────┐   ┌────────────────────────────────────┐    │  │
│   │   │ ProvisioningRepo  │   │ VmwareConfigRepository             │    │  │
│   │   │ (Progress jOOQ)   │   │ (Encrypted credentials)            │    │  │
│   │   └───────────────────┘   └────────────────────────────────────┘    │  │
│   └─────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EAF Framework                                   │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────┐  ┌──────────────┐  │
│  │  eaf-core   │  │ eaf-eventsourcing│  │ eaf-tenant  │  │ eaf-testing  │  │
│  └─────────────┘  └──────────────────┘  └─────────────┘  │ (VCSIM)      │  │
│                                                          └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Architecture Constraints (Konsist Enforced)

| Rule | Enforcement |
|------|-------------|
| EAF modules MUST NOT import `de.acci.dcm.*` | Konsist `ArchitectureTest` |
| `dcm-domain` MUST NOT import `org.springframework.*` | Konsist `ArchitectureTest` |
| `VspherePort` in `dcm-application` (port layer) | Package convention |
| `VcenterAdapter`/`VcsimAdapter` in `dcm-infrastructure` | Package convention |
| VMware credentials encrypted at rest | Security requirement |

---

## 4. Detailed Design

### 4.1 Domain Model Extensions

#### 4.1.1 New Domain Events

```kotlin
// dcm-domain/src/main/kotlin/de/acci/dcm/domain/vmrequest/events/

// Triggered when provisioning starts
data class VmProvisioningStarted(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val vmSpec: VmProvisioningSpec,
    override val occurredAt: Instant
) : DomainEvent

// Progress update during provisioning
data class VmProvisioningProgressUpdated(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val stage: ProvisioningStage,
    val progress: Int,  // 0-100
    override val occurredAt: Instant
) : DomainEvent

// Successful completion
data class VmProvisioned(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val vmId: VmwareVmId,        // VMware MoRef
    val hostname: String,
    val ipAddress: String?,      // May be null initially
    val provisionedAt: Instant,
    val durationSeconds: Long,
    override val occurredAt: Instant
) : DomainEvent

// Failure after all retries exhausted
data class VmProvisioningFailed(
    override val aggregateId: VmRequestId,
    override val tenantId: TenantId,
    val errorCode: ProvisioningErrorCode,
    val errorMessage: String,
    val retryCount: Int,
    val lastAttemptAt: Instant,
    override val occurredAt: Instant
) : DomainEvent
```

#### 4.1.2 New Value Objects

```kotlin
// VMware Managed Object Reference (MoRef) identifier
// Note: VMware API returns "vm-<number>" format (e.g., "vm-123")
// Official SDK returns standard format; "VirtualMachine:" prefix accepted for legacy compatibility
// Verify actual format in Story 3.2 VCSIM tests
@JvmInline
value class VmwareVmId(val value: String) {
    init {
        require(value.startsWith("vm-") || value.startsWith("VirtualMachine:")) {
            "Invalid VMware VM MoRef format: expected 'vm-*' or 'VirtualMachine:*'"
        }
    }
}

// Provisioning specification
data class VmProvisioningSpec(
    val vmName: VmName,
    val size: VmSize,
    val templateName: String,
    val datastoreName: String,
    val networkName: String,
    val folderPath: String,
    val correlationId: CorrelationId  // For idempotency
)

// Provisioning stages
enum class ProvisioningStage(val displayName: String, val progress: Int) {
    QUEUED("Created", 0),
    CLONING("Cloning from template...", 20),
    CONFIGURING("Applying configuration", 50),
    STARTING("Starting VM", 70),
    WAITING_FOR_NETWORK("Waiting for network", 85),
    READY("Ready", 100)
}

// Error codes for user-friendly messages
enum class ProvisioningErrorCode(val userMessage: String) {
    INSUFFICIENT_RESOURCES("Insufficient resources in cluster"),
    DATASTORE_NOT_AVAILABLE("Datastore not available"),
    TEMPLATE_NOT_FOUND("VM template not found"),
    NETWORK_CONFIG_FAILED("Network configuration failed"),
    VMWARE_TOOLS_TIMEOUT("VM tools did not start in time"),
    CONNECTION_FAILED("Cannot connect to VMware"),
    UNKNOWN("Provisioning failed - contact administrator")
}
```

#### 4.1.3 VmRequestAggregate Extensions

```kotlin
// Add to VmRequestAggregate
class VmRequestAggregate {
    // ... existing fields ...
    private var vmwareVmId: VmwareVmId? = null
    private var hostname: String? = null
    private var ipAddress: String? = null
    private var provisioningStage: ProvisioningStage? = null
    private var provisioningStartedAt: Instant? = null
    private var failureReason: String? = null

    fun startProvisioning(spec: VmProvisioningSpec, clock: Clock) {
        require(status == VmRequestStatus.APPROVED) {
            "Can only start provisioning for approved requests"
        }
        status = VmRequestStatus.PROVISIONING
        provisioningStage = ProvisioningStage.QUEUED
        provisioningStartedAt = clock.instant()
        recordEvent(
            VmProvisioningStarted(
                aggregateId = id,
                tenantId = tenantId,
                vmSpec = spec,
                occurredAt = clock.instant()
            )
        )
    }

    fun updateProvisioningProgress(stage: ProvisioningStage, clock: Clock) {
        require(status == VmRequestStatus.PROVISIONING) {
            "Can only update progress during provisioning"
        }
        provisioningStage = stage
        recordEvent(
            VmProvisioningProgressUpdated(
                aggregateId = id,
                tenantId = tenantId,
                stage = stage,
                progress = stage.progress,
                occurredAt = clock.instant()
            )
        )
    }

    fun markProvisioned(vmId: VmwareVmId, hostname: String, ipAddress: String?, clock: Clock) {
        require(status == VmRequestStatus.PROVISIONING) {
            "Can only mark provisioned during provisioning"
        }
        status = VmRequestStatus.READY
        vmwareVmId = vmId
        this.hostname = hostname
        this.ipAddress = ipAddress
        provisioningStage = ProvisioningStage.READY
        val duration = Duration.between(provisioningStartedAt, clock.instant()).seconds
        recordEvent(
            VmProvisioned(
                aggregateId = id,
                tenantId = tenantId,
                vmId = vmId,
                hostname = hostname,
                ipAddress = ipAddress,
                provisionedAt = clock.instant(),
                durationSeconds = duration,
                occurredAt = clock.instant()
            )
        )
    }

    fun markProvisioningFailed(
        errorCode: ProvisioningErrorCode,
        errorMessage: String,
        retryCount: Int,
        clock: Clock
    ) {
        require(status == VmRequestStatus.PROVISIONING) {
            "Can only mark failed during provisioning"
        }
        status = VmRequestStatus.FAILED
        failureReason = errorMessage
        recordEvent(
            VmProvisioningFailed(
                aggregateId = id,
                tenantId = tenantId,
                errorCode = errorCode,
                errorMessage = errorMessage,
                retryCount = retryCount,
                lastAttemptAt = clock.instant(),
                occurredAt = clock.instant()
            )
        )
    }
}
```

### 4.2 VspherePort Interface

```kotlin
// dcm-application/src/main/kotlin/de/acci/dcm/application/ports/VspherePort.kt

interface VspherePort {
    /**
     * Create a VM from template.
     * @param tenantId Tenant context for credential lookup
     * @param spec VM specification
     * @return VmId on success, ProvisioningError on failure
     */
    suspend fun createVm(
        tenantId: TenantId,
        spec: VmProvisioningSpec
    ): Result<VmwareVmId, ProvisioningError>

    /**
     * Get VM status and details.
     */
    suspend fun getVm(
        tenantId: TenantId,
        vmId: VmwareVmId
    ): Result<VmInfo, VsphereError>

    /**
     * Delete a VM (for cleanup on failure).
     */
    suspend fun deleteVm(
        tenantId: TenantId,
        vmId: VmwareVmId
    ): Result<Unit, VsphereError>

    /**
     * Test connection and return vCenter version info.
     */
    suspend fun testConnection(
        tenantId: TenantId
    ): Result<VcenterInfo, ConnectionError>

    /**
     * Wait for VM to reach ready state (VMware Tools running).
     * @param timeout Maximum wait time
     */
    suspend fun waitForReady(
        tenantId: TenantId,
        vmId: VmwareVmId,
        timeout: Duration = Duration.ofMinutes(5)
    ): Result<VmInfo, VsphereError>
}

data class VmInfo(
    val vmId: VmwareVmId,
    val name: String,
    val powerState: PowerState,
    val ipAddress: String?,
    val hostname: String?,
    val cpuCount: Int,
    val memoryMb: Int,
    val diskGb: Int,
    val guestToolsStatus: GuestToolsStatus,
    val createdAt: Instant
)

data class VcenterInfo(
    val version: String,          // e.g., "8.0.2"
    val build: String,            // e.g., "22617221"
    val datacenterName: String,
    val clusterName: String
)

enum class PowerState { POWERED_ON, POWERED_OFF, SUSPENDED }
enum class GuestToolsStatus { RUNNING, NOT_RUNNING, NOT_INSTALLED }
```

### 4.3 Saga Pattern for Provisioning

```kotlin
// dcm-application/src/main/kotlin/de/acci/dcm/application/sagas/ProvisionVmSaga.kt

/**
 * Saga orchestrator for VM provisioning.
 * Handles the multi-step provisioning process with compensation on failure.
 */
class ProvisionVmSaga(
    private val eventStore: EventStore,
    private val vspherePort: VspherePort,
    private val vmwareConfigRepository: VmwareConfigRepository,
    private val clock: Clock
) {
    // Note: withMaxRetries(4) = 4 retries after initial = 5 total attempts
    private val retryPolicy = RetryPolicy.builder<VmwareVmId>()
        .handle(TransientVsphereException::class.java)
        .withMaxRetries(4)  // 5 total attempts (initial + 4 retries)
        .withBackoff(Duration.ofSeconds(10), Duration.ofSeconds(120), 2.0)
        .build()

    suspend fun execute(command: ProvisionVmCommand): Result<VmwareVmId, ProvisioningError> {
        // 1. Load aggregate and validate state
        val aggregate = eventStore.load(VmRequestAggregate::class, command.requestId)
            ?: return Result.failure(AggregateNotFound(command.requestId))

        // 2. Check VMware configuration exists
        val config = vmwareConfigRepository.findByTenantId(command.tenantId)
            ?: return Result.failure(VmwareNotConfigured(command.tenantId))

        // 3. Start provisioning
        aggregate.startProvisioning(command.spec, clock)
        eventStore.save(aggregate)

        // 4. Execute with retry (using coroutine-compatible retry)
        return try {
            val vmId = withRetry(retryPolicy) {
                executeProvisioningSteps(aggregate, command.spec)
            }
            Result.success(vmId)
        } catch (e: ProvisioningException) {
            // Compensation: cleanup partial VM if created
            e.partialVmId?.let { vmId ->
                runCatching { vspherePort.deleteVm(command.tenantId, vmId) }
                    .onFailure { cleanupError ->
                        logger.warn(cleanupError) {
                            "Failed to cleanup partial VM $vmId for request ${command.requestId}. " +
                            "Manual cleanup may be required. Correlation: ${command.spec.correlationId}"
                        }
                        // Consider: emit DeadLetter event for manual reconciliation
                    }
            }
            aggregate.markProvisioningFailed(
                errorCode = e.errorCode,
                errorMessage = e.message ?: "Unknown error",
                retryCount = e.retryCount,
                clock = clock
            )
            eventStore.save(aggregate)
            Result.failure(e.toProvisioningError())
        }
    }

    private suspend fun executeProvisioningSteps(
        aggregate: VmRequestAggregate,
        spec: VmProvisioningSpec
    ): VmwareVmId {
        // Step 1: Clone VM from template
        updateProgress(aggregate, ProvisioningStage.CLONING)
        val vmId = vspherePort.createVm(aggregate.tenantId, spec)
            .getOrElse { throw ProvisioningException(it.errorCode, it.message, 0) }

        try {
            // Step 2: Apply configuration
            updateProgress(aggregate, ProvisioningStage.CONFIGURING)
            // Guest customization happens during clone

            // Step 3: Power on
            updateProgress(aggregate, ProvisioningStage.STARTING)
            // VM powers on automatically with clone settings

            // Step 4: Wait for VMware Tools
            updateProgress(aggregate, ProvisioningStage.WAITING_FOR_NETWORK)
            val vmInfo = vspherePort.waitForReady(aggregate.tenantId, vmId)
                .getOrElse { throw ProvisioningException(ProvisioningErrorCode.VMWARE_TOOLS_TIMEOUT, it.message, 0, vmId) }

            // Step 5: Mark complete
            aggregate.markProvisioned(
                vmId = vmId,
                hostname = vmInfo.hostname ?: spec.vmName.value,
                ipAddress = vmInfo.ipAddress,
                clock = clock
            )
            eventStore.save(aggregate)

            return vmId
        } catch (e: Exception) {
            // Attach partial VM ID for cleanup
            throw ProvisioningException(
                errorCode = mapException(e),
                message = e.message,
                retryCount = 0,
                partialVmId = vmId
            )
        }
    }

    private suspend fun updateProgress(aggregate: VmRequestAggregate, stage: ProvisioningStage) {
        aggregate.updateProvisioningProgress(stage, clock)
        eventStore.save(aggregate)
    }
}
```

### 4.4 Database Schema

#### 4.4.1 VMware Configuration Table

```sql
-- Tenant-specific VMware configuration (encrypted credentials)
CREATE TABLE "VMWARE_CONFIGURATIONS" (
    "ID" UUID PRIMARY KEY,
    "TENANT_ID" UUID NOT NULL UNIQUE,
    "VCENTER_URL" VARCHAR(500) NOT NULL,
    "USERNAME" VARCHAR(255) NOT NULL,
    "PASSWORD_ENCRYPTED" BYTEA NOT NULL,        -- AES-256 encrypted
    "DATACENTER_NAME" VARCHAR(255) NOT NULL,
    "CLUSTER_NAME" VARCHAR(255) NOT NULL,
    "DATASTORE_NAME" VARCHAR(255) NOT NULL,
    "NETWORK_NAME" VARCHAR(255) NOT NULL,
    "TEMPLATE_NAME" VARCHAR(255) NOT NULL DEFAULT 'ubuntu-22.04-template',
    "FOLDER_PATH" VARCHAR(500),
    "VERIFIED_AT" TIMESTAMP WITH TIME ZONE,     -- Last successful connection test
    "CREATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "VERSION" BIGINT NOT NULL DEFAULT 0
);

-- [jooq ignore start]
ALTER TABLE "VMWARE_CONFIGURATIONS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "VMWARE_CONFIGURATIONS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "VMWARE_CONFIGURATIONS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "VMWARE_CONFIGURATIONS" TO eaf_app;
-- [jooq ignore stop]

CREATE INDEX idx_vmware_config_tenant ON "VMWARE_CONFIGURATIONS"("TENANT_ID");
```

#### 4.4.2 Provisioning Progress Table

```sql
-- Track provisioning progress for real-time updates
CREATE TABLE "PROVISIONING_PROGRESS" (
    "ID" UUID PRIMARY KEY,
    "REQUEST_ID" UUID NOT NULL REFERENCES "VM_REQUESTS"("ID"),
    "TENANT_ID" UUID NOT NULL,
    "STAGE" VARCHAR(50) NOT NULL,
    "PROGRESS" INT NOT NULL DEFAULT 0,
    "VMWARE_VM_ID" VARCHAR(255),                -- Populated after clone starts
    "ERROR_CODE" VARCHAR(50),
    "ERROR_MESSAGE" TEXT,
    "RETRY_COUNT" INT NOT NULL DEFAULT 0,
    "STARTED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "UPDATED_AT" TIMESTAMP WITH TIME ZONE NOT NULL,
    "COMPLETED_AT" TIMESTAMP WITH TIME ZONE
);

-- [jooq ignore start]
ALTER TABLE "PROVISIONING_PROGRESS" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "PROVISIONING_PROGRESS" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON "PROVISIONING_PROGRESS"
    FOR ALL
    USING ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK ("TENANT_ID" = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON "PROVISIONING_PROGRESS" TO eaf_app;
-- [jooq ignore stop]

CREATE INDEX idx_provisioning_request ON "PROVISIONING_PROGRESS"("REQUEST_ID");
CREATE INDEX idx_provisioning_tenant_status ON "PROVISIONING_PROGRESS"("TENANT_ID", "STAGE");
```

#### 4.4.3 VM_REQUESTS Table Extensions

```sql
-- Add provisioning columns to existing VM_REQUESTS table
ALTER TABLE "VM_REQUESTS"
    ADD COLUMN "VMWARE_VM_ID" VARCHAR(255),
    ADD COLUMN "HOSTNAME" VARCHAR(255),
    ADD COLUMN "IP_ADDRESS" VARCHAR(45),
    ADD COLUMN "PROVISIONING_STARTED_AT" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "PROVISIONING_COMPLETED_AT" TIMESTAMP WITH TIME ZONE,
    ADD COLUMN "PROVISIONING_DURATION_SECONDS" BIGINT;
```

### 4.5 API Extensions

#### 4.5.1 New REST Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/admin/vmware-config` | Get tenant VMware config | Admin |
| PUT | `/api/admin/vmware-config` | Update VMware config | Admin |
| POST | `/api/admin/vmware-config/test` | Test VMware connection | Admin |
| GET | `/api/requests/{id}/provisioning` | Get provisioning progress | User (owner) |
| GET | `/api/requests/{id}/vm` | Get provisioned VM details | User (owner) |

#### 4.5.2 New DTOs

```kotlin
// VMware Configuration
// Note: Add @Valid JSR-380 validation constraints during implementation
// - vcenterUrl: @NotBlank @URL (valid https URL)
// - username: @NotBlank @Length(min = 3)
// - password: @NotBlank @Size(min = 8) to discourage weak credentials
// - Other fields: @NotBlank
data class VmwareConfigRequest(
    val vcenterUrl: String,
    val username: String,
    val password: String,       // Will be encrypted before storage
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String = "ubuntu-22.04-template"
)

data class VmwareConfigResponse(
    val vcenterUrl: String,
    val username: String,
    val datacenterName: String,
    val clusterName: String,
    val datastoreName: String,
    val networkName: String,
    val templateName: String,
    val verified: Boolean,
    val verifiedAt: Instant?
)

data class ConnectionTestResponse(
    val success: Boolean,
    val message: String,
    val vcenterVersion: String?,
    val datacenterFound: Boolean,
    val clusterFound: Boolean,
    val datastoreFound: Boolean,
    val templateFound: Boolean
)

// Provisioning Progress
data class ProvisioningProgressResponse(
    val stage: String,
    val stageName: String,
    val progress: Int,
    val stages: List<ProvisioningStageResponse>,
    val startedAt: Instant,
    val estimatedCompletionAt: Instant?,
    val error: ProvisioningErrorResponse?
)

data class ProvisioningStageResponse(
    val name: String,
    val status: String,  // "completed", "in_progress", "pending"
    val completedAt: Instant?
)

data class ProvisioningErrorResponse(
    val code: String,
    val message: String,
    val retryCount: Int
)

// Provisioned VM Details
data class VmDetailsResponse(
    val vmId: String,
    val hostname: String,
    val ipAddress: String?,
    val powerState: String,
    val cpuCores: Int,
    val memoryGb: Int,
    val diskGb: Int,
    val createdAt: Instant,
    val uptime: String?,
    val connectionInstructions: ConnectionInstructionsResponse
)

data class ConnectionInstructionsResponse(
    val ssh: String?,     // "ssh user@192.168.1.x"
    val rdp: String?,     // "mstsc /v:192.168.1.x"
    val console: String?  // vSphere console URL
)
```

### 4.6 VCSIM Integration

#### 4.6.1 Existing Infrastructure (Story 1.10)

The following components are already available in `eaf-testing`:

```kotlin
// VcsimContainer - Testcontainer for VCSIM
class VcsimContainer : GenericContainer<VcsimContainer>("vmware/vcsim:v0.47.0") {
    fun getSdkUrl(): String = "https://${host}:${getMappedPort(8989)}/sdk"
    fun getUsername(): String = "user"
    fun getPassword(): String = "pass"
}

// VcsimTestFixture - Helper methods
class VcsimTestFixture(private val container: VcsimContainer) {
    fun createVm(spec: VmSpec): VmRef
    fun createNetwork(name: String): NetworkRef
    fun createDatastore(name: String): DatastoreRef
    fun simulateProvisioning(vmRef: VmRef): Unit
    fun resetState(): Unit
}

// @VcsimTest - Annotation for VCSIM-enabled tests
@Target(AnnotationTarget.CLASS)
@ExtendWith(VcsimExtension::class)
@Testcontainers
annotation class VcsimTest
```

#### 4.6.2 Epic 3 VCSIM Enhancement

Story 3.2 extends `VcsimTestFixture` to make actual SOAP API calls:

```kotlin
// Enhanced VcsimTestFixture for Epic 3
class VcsimTestFixture(private val container: VcsimContainer) {
    // Existing methods (local simulation)...

    // NEW: Real VCSIM API calls for realistic testing
    suspend fun createVmViaSoapApi(spec: VmSpec): VmRef {
        // Actually call VCSIM /sdk endpoint
        // CloneVM_Task SOAP call
    }

    suspend fun getVmViaSoapApi(vmRef: VmRef): VmInfo {
        // RetrieveProperties SOAP call
    }

    suspend fun deleteVmViaSoapApi(vmRef: VmRef): Unit {
        // Destroy_Task SOAP call
    }
}
```

#### VCSIM Feasibility Verification (Story 3.2 Pre-requisite)

> **IMPORTANT:** Before committing to VCSIM SOAP API approach, Story 3.2 must verify:

1. **VCSIM v0.47.0 SOAP support**: Confirm CloneVM_Task, RetrieveProperties, Destroy_Task work
2. **Official SDK compatibility**: Test vSphere Automation SDK calls against VCSIM container
3. **Fallback plan**: If VCSIM SOAP is incomplete, pivot to mock-based adapters

This is critical for Phase 1 timeline. If VCSIM SOAP limitations are discovered,
document gaps and adjust approach before Story 3.3.

---

## 5. Story Breakdown

### 5.1 Story Dependency Graph

```text
                                    ┌──────────────────────────┐
                                    │ Story 3.1: VMware Config │
                                    │ (VMware Tracer Bullet)   │
                                    └───────────┬──────────────┘
                                                │
                                    ┌───────────▼──────────────┐
                                    │ Story 3.1.1: SDK Migrate │
                                    │ (yavijava → Official SDK)│
                                    └───────────┬──────────────┘
                                                │
                                    ┌───────────▼──────────────┐
                                    │ Story 3.2: vSphere Client│
                                    │ (Official SDK + resil4j) │
                                    └───────────┬──────────────┘
                                                │
                                    ┌───────────▼──────────────┐
                                    │ Story 3.3: Trigger on    │
                                    │ Approval (Saga start)    │
                                    └───────────┬──────────────┘
                                                │
                          ┌─────────────────────┼─────────────────────┐
                          │                     │                     │
              ┌───────────▼───────────┐ ┌───────▼───────┐ ┌───────────▼───────────┐
              │ Story 3.4: Execute    │ │ Story 3.5:    │ │ Story 3.6: Error     │
              │ Provisioning          │ │ Progress      │ │ Handling             │
              │ (CloneVM_Task)        │ │ Tracking      │ │ (Retry + Cleanup)    │
              └───────────┬───────────┘ └───────┬───────┘ └───────────┬───────────┘
                          │                     │                     │
                          └─────────────────────┼─────────────────────┘
                                                │
                          ┌─────────────────────┼─────────────────────┐
                          │                                           │
              ┌───────────▼───────────┐               ┌───────────────▼───────────┐
              │ Story 3.7: VM Details │               │ Story 3.8: Notifications  │
              │ (Live status)         │               │ (vm-ready, vm-failed)     │
              └───────────────────────┘               └───────────────────────────┘

                                    ┌──────────────────────────┐
                                    │ Story 3.9: Contract Tests│
                                    │ (BLOCKED - needs vCenter)│
                                    └──────────────────────────┘
```

### 5.2 Story Summary

| ID | Title | Prerequisites | FRs | Risk |
|----|-------|---------------|-----|------|
| 3.1 | VMware Connection Configuration | 1.6, 2.1 | FR71 | HIGH |
| 3.1.1 | Migrate to Official vSphere SDK | 3.1 | Tech debt | MEDIUM |
| 3.2 | vSphere API Client | 3.1.1, 1.2 | FR34 (partial) | HIGH |
| 3.3 | Provisioning Trigger on Approval | 2.11, 3.2 | FR30 | MEDIUM |
| 3.4 | VM Provisioning Execution | 3.3, 3.2 | FR34-37 | **CRITICAL** |
| 3.5 | Provisioning Progress Tracking | 3.4, 2.8 | FR37 | MEDIUM |
| 3.6 | Provisioning Error Handling | 3.4 | FR38-39, FR77-79 | **CRITICAL** |
| 3.7 | Provisioned VM Details | 3.4, 2.7 | FR40 | LOW |
| 3.8 | VM Ready Notification | 3.6, 2.12 | FR47 | LOW |
| 3.9 | vCenter Contract Test Suite | 3.1-3.8 | FR34, FR77-78 | HIGH (BLOCKED) |

### 5.3 Key Acceptance Criteria (Critical Stories)

#### Story 3.1: VMware Connection Configuration (HIGH)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-3.1.1 | I am tenant admin | Navigate to Settings → VMware | See config form (URL, creds, datacenter, cluster, datastore, network) |
| AC-3.1.2 | Valid config entered | Click "Test Connection" | Success: "Connected to vCenter 8.0, Cluster: {name}" |
| AC-3.1.3 | Invalid config | Click "Test Connection" | Specific error: "Authentication failed" / "Datacenter not found" |
| AC-3.1.4 | Config saved | - | Credentials encrypted (AES-256), RLS tenant isolation |
| AC-3.1.5 | No VMware config | User submits VM request | Message: "VMware not configured - contact Admin" |

#### Story 3.4: VM Provisioning Execution (CRITICAL)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-3.4.1 | `ProvisionVmCommand` received | Provisioning executes | VM created with correct name, CPU, RAM, disk, network |
| AC-3.4.2 | Provisioning starts | - | VM cloned from template (not from scratch) |
| AC-3.4.3 | Clone completes | - | Guest customization applies hostname |
| AC-3.4.4 | VM created | - | VM powered on, waits for VMware Tools (timeout: 5 min) |
| AC-3.4.5 | Provisioning succeeds | VM ready | `VmProvisioned` event with vmId, ipAddress, hostname |
| AC-3.4.6 | Correlation tracking | - | Correlation ID in VM annotation for traceability |

#### Story 3.6: Provisioning Error Handling (CRITICAL)

| AC | Given | When | Then |
|----|-------|------|------|
| AC-3.6.1 | Transient error (timeout) | Error occurs | Retry with exponential backoff: 10s→20s→40s→80s (5 attempts = 4 delays, 2x multiplier, cap 120s) |
| AC-3.6.2 | All retries fail | Max retries exceeded | `VmProvisioningFailed` event, status → FAILED |
| AC-3.6.3 | Permanent error | Non-retryable error | Fail immediately, user-friendly message |
| AC-3.6.4 | Partial completion | VM created, network failed | Cleanup partial VM, log with correlation ID |
| AC-3.6.5 | Failure notification | - | User email (summary), Admin email (full details) |

---

## 6. Non-Functional Requirements

### 6.1 Performance

| Metric | Target | Measurement |
|--------|--------|-------------|
| Provisioning Start | < 5s from approval | Event timing |
| Clone Completion | < 3 min (typical) | VMware task time |
| Total Provisioning | < 10 min (p95) | End-to-end |
| Progress Update Latency | < 2s | SSE/polling |
| Concurrent Provisioning | 10 per tenant | k6 load test |

### 6.2 Reliability

| Aspect | Implementation |
|--------|----------------|
| Idempotency | Correlation ID in VM annotation prevents duplicates |
| Retry Logic | Exponential backoff: 10s→20s→40s→80s (5 attempts = 4 delays, 2x multiplier, cap 120s) |
| Circuit Breaker | resilience4j, trips after 5 consecutive failures |
| Saga Compensation | Cleanup partial VMs on failure |
| Dead Letter | Failed provisions logged for manual review |

### 6.3 Security

| Requirement | Implementation |
|-------------|----------------|
| Credential Storage | AES-256 encryption (Spring Security Crypto) |
| Credential Access | Never logged, never exposed in API |
| RLS Isolation | VMware config per tenant |
| Connection Security | TLS to vCenter, certificate validation |
| Service Account | Minimum required vSphere permissions |

### 6.4 Testability

| Test Type | Coverage | Notes |
|-----------|----------|-------|
| Unit Tests | ≥80% | Saga logic, error handling |
| Integration Tests | VCSIM | All vSphere operations |
| Contract Tests | Story 3.9 | Real vCenter (when available) |
| E2E Tests | Full flow | Playwright with VCSIM backend |

---

## 7. Dependencies

### 7.1 Epic 1/2 Dependencies

| Dependency | Story | Status |
|------------|-------|--------|
| Event Store | 1.3 | DONE |
| Aggregate Base | 1.4 | DONE |
| PostgreSQL RLS | 1.6 | DONE |
| Keycloak Auth | 1.7 | DONE |
| jOOQ Projections | 1.8 | DONE |
| **VCSIM Integration** | **1.10** | **DONE** |
| Approve/Reject | 2.11 | DONE |
| Email Notifications | 2.12 | DONE |

### 7.2 New Libraries

| Library | Version | Module | Purpose |
|---------|---------|--------|---------|
| VCF SDK Java | 9.0.0.0 | dcm-infrastructure | Official VCF SDK (Maven Central) - Includes vSphere Automation & VIM APIs |
| resilience4j | 2.2.x | dcm-application | Circuit breaker, retry |
| spring-security-crypto | 6.x | dcm-infrastructure | Credential encryption |

> **Note:** Story 3.1 was initially implemented with yavijava. Story 3.1.1 migrates to the official **VCF SDK** due to yavijava deprecation.

### 7.3 External Dependencies

| Dependency | Story | Status |
|------------|-------|--------|
| VCSIM (Testcontainer) | All | Available |
| Real vCenter | 3.9 | **BLOCKED** |

---

## 8. Lessons from Epic 2

### 8.1 Patterns to Apply

| Pattern | Source | Application in Epic 3 |
|---------|--------|----------------------|
| Fire-and-forget email | Story 2.12 | Extend for vm-ready/vm-failed notifications |
| Timeline projection | Story 2.8 | Reuse for provisioning progress |
| Status badge component | Story 2.7 | Extend with PROVISIONING, READY, FAILED states |
| Toast notifications | Story 2.6 | Provisioning status updates |
| MockK default params | Project Context | Apply to all handler tests |

### 8.2 Technical Debt to Avoid

| Issue | Prevention |
|-------|------------|
| E2E tests without full auth | Enable auth from Story 3.1 |
| Project name as UUID | Add `ProjectDirectory` interface |
| Email as PII in events | Already documented, address in Epic 5 |

### 8.3 Action Items from Epic 2 Retro

| Action | Owner | Target Story |
|--------|-------|--------------|
| Enable remaining E2E tests | Dev | 3.1 |
| Add ProjectDirectory interface | Dev | 3.7 |
| Review VCSIM test coverage | Dev | 3.2 |
| Create Epic 3 tech context | SM | This document |

---

## 9. Definition of Done

### 9.1 Story Level

- [ ] All acceptance criteria implemented
- [ ] Unit tests ≥80% coverage
- [ ] Mutation score ≥70%
- [ ] Integration tests with VCSIM
- [ ] Code review approved
- [ ] CI pipeline passes
- [ ] ProjectDirectory interface created (if story involves project name resolution)
- [ ] Email templates added if applicable (vm-ready.html, vm-failed.html)

### 9.2 Epic Level

- [ ] All 9 stories completed (Story 3.9 may remain BLOCKED pending vCenter access)
- [ ] VCSIM integration tests comprehensive (≥90% coverage of ProvisionVmSaga)
- [ ] Error handling tested (all error codes)
- [ ] Saga compensation tested:
  - [ ] VM cleanup verified when network config fails (AC-3.6.4)
  - [ ] Correlation ID honored in retry scenarios (no duplicate VMs)
  - [ ] Dead-letter logging populated for unrecoverable errors
- [ ] Email notifications (vm-ready, vm-failed)
- [ ] Performance targets met (k6)
- [ ] Security review passed

### 9.3 Go-Live Gate (Story 3.9)

- [ ] vCenter test instance provisioned (org responsibility, not dev)
- [ ] Phase 1 Smoke Tests pass on real vCenter
- [ ] Contract tests pass against real vCenter (all error scenarios)
- [ ] At least 1 successful VM provisioning
- [ ] Pilot tenant identified for staged rollout
- [ ] Runbook documented for rollback (delete partial VMs, reverse saga steps)

---

## 10. References

- [Epic 3 Definition](../epics.md#epic-3-vm-provisioning)
- [Architecture](../architecture.md)
- [PRD](../prd.md)
- [Test Design System](../test-design-system.md)
- [Epic 2 Tech Spec](tech-spec-epic-2.md)
- [Epic 2 Retrospective](epic-2-retro-2025-12-04.md)
- [Story 1.10: VCSIM Integration](1-10-vcsim-integration.md)
- [VMware vSphere API Reference](https://developer.vmware.com/apis/1720/)
- [VCF SDK Java](https://mvnrepository.com/artifact/com.vmware.vcf/vcf-sdk-java)
- [resilience4j Documentation](https://resilience4j.readme.io/)

---

*Generated by SM Agent (Bob) via BMAD epic-tech-context workflow*
*Model: claude-opus-4-5-20251101*
