# ADR-004a: Multi-Hypervisor Implementation Details (Addendum)

**Status:** Proposed
**Date:** 2025-12-09
**Author:** Winston (Architect)
**Deciders:** Wall-E, John (PM)
**Related:** [ADR-004](./ADR-004-multi-hypervisor-architecture.md), [Epic 6](../epics.md#epic-6-multi-hypervisor-support-post-mvp)

---

## Purpose

Use this addendum when implementing Epic 6 (Multi-Hypervisor Support). While ADR-004 established the strategic direction, this document provides the tactical details needed to:

1. Implement capability detection to prevent runtime failures
2. Map domain concepts to hypervisor-specific resources
3. Handle errors consistently across different hypervisors
4. Migrate existing VMware types to generic abstractions
5. Test each hypervisor adapter systematically
6. Plan migration phases with backward compatibility

---

## 1. Feature Parity Matrix

Different hypervisors support different feature sets. The `HypervisorPort` interface must expose capabilities to avoid runtime failures.

### 1.1 Capability Detection

```kotlin
// dvmm-application/.../hypervisor/HypervisorCapabilities.kt
data class HypervisorCapabilities(
    val supportsLiveMigration: Boolean,
    val supportsSnapshots: Boolean,
    val supportsHighAvailability: Boolean,
    val supportsResourcePools: Boolean,
    val supportsHotAdd: HotAddCapabilities,
    val supportedNetworkTypes: Set<NetworkType>,
    val maxCpuCores: Int?,
    val maxMemoryGb: Int?
)

data class HotAddCapabilities(
    val cpu: Boolean,
    val memory: Boolean,
    val disk: Boolean
)

enum class NetworkType {
    BRIDGED,
    NAT,
    ISOLATED,
    DISTRIBUTED_SWITCH,  // VMware only
    OVS_BRIDGE           // Proxmox only
}
```

### 1.2 Updated HypervisorPort Interface

```kotlin
public interface HypervisorPort {
    // Capability discovery
    fun capabilities(): HypervisorCapabilities
    
    // Existing operations
    suspend fun testConnection(config: HypervisorConfig, credentials: Credentials): Result<ConnectionInfo, ConnectionError>
    suspend fun listResources(): Result<HypervisorResources, HypervisorError>
    suspend fun createVm(spec: VmProvisionSpec): Result<VmProvisioningResult, HypervisorError>
    suspend fun getVm(vmId: VmIdentifier): Result<VmInfo, HypervisorError>
    suspend fun startVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun stopVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun deleteVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    
    // Optional advanced operations (default implementations throw NotSupportedError)
    suspend fun snapshotVm(vmId: VmIdentifier, name: String): Result<SnapshotId, HypervisorError> =
        NotSupportedError("Snapshot not supported by this hypervisor").failure()
    
    suspend fun migrateVm(vmId: VmIdentifier, targetHost: HostIdentifier): Result<Unit, HypervisorError> =
        NotSupportedError("Live migration not supported by this hypervisor").failure()
}
```

### 1.3 Capability Matrix

The following table compares feature support across all four hypervisors:

| Feature | VMware vSphere | Proxmox VE | Hyper-V | PowerVM |
|---------|----------------|------------|---------|---------|
| Live Migration | ✅ vMotion | ⚠️ Limited (offline migration only) | ✅ Live Migration | ✅ LPM |
| Snapshots | ✅ Full | ✅ Full | ✅ Checkpoints | ❌ None |
| HA | ✅ vSphere HA | ✅ Corosync/Pacemaker | ✅ Failover Clustering | ✅ PowerHA |
| Resource Pools | ✅ Native | ❌ None | ❌ None | ✅ Shared Processor Pools |
| Hot-Add CPU | ✅ Yes | ✅ Yes | ✅ Yes | ❌ No |
| Hot-Add Memory | ✅ Yes | ⚠️ Limited | ✅ Yes | ❌ No |
| Distributed Switching | ✅ vDS | ❌ Uses OVS | ❌ Uses Hyper-V Switch | ❌ Uses SEA/VLAN |
| Max CPUs | 768 vCPUs | 512 vCPUs | 240 vCPUs | 1024 vCPUs |
| Max Memory | 24 TB | 4 TB | 12 TB | 16 TB |

**Legend:**
- ✅ Full support
- ⚠️ Partial/limited support
- ❌ Not supported

---

## 2. Resource Hierarchy Mapping

Each hypervisor has a different resource organization model. We need a translation layer.

### 2.1 Resource Hierarchy Models

**VMware vSphere:**
```text
Datacenter
└── Cluster
    └── Host
        └── ResourcePool (optional)
            └── VM
```

**Proxmox VE:**
```text
Datacenter (logical only)
└── Node (physical host)
    └── VM
```

**Hyper-V:**
```text
Cluster (optional)
└── Host
    └── VM
```

**PowerVM:**
```text
CEC (Central Electronics Complex)
└── Frame
    └── LPAR (Logical Partition)
```

### 2.2 Resource Mapper Interface

```kotlin
// dvmm-application/.../hypervisor/ResourceMapper.kt
interface ResourceMapper {
    suspend fun mapProvisionSpec(
        domainSpec: VmProvisionSpec,
        tenantMapping: HypervisorResourceMapping
    ): Result<HypervisorNativeSpec, MappingError>
    
    suspend fun mapVmInfo(
        nativeVm: HypervisorNativeVm
    ): Result<VmInfo, MappingError>
    
    suspend fun discoverResources(): Result<HypervisorResourceTree, HypervisorError>
}

// Domain model for resource tree (hypervisor-agnostic)
data class HypervisorResourceTree(
    val computeUnits: List<ComputeUnit>,
    val storageUnits: List<StorageUnit>,
    val networkUnits: List<NetworkUnit>
)

data class ComputeUnit(
    val id: String,
    val name: String,
    val type: ComputeUnitType,
    val availableCpu: Int,
    val availableMemoryGb: Int,
    val children: List<ComputeUnit> = emptyList()
)

enum class ComputeUnitType {
    DATACENTER,
    CLUSTER,
    HOST,
    RESOURCE_POOL
}
```

### 2.3 Tenant Resource Mapping

```kotlin
// Stored per-tenant in database
data class HypervisorResourceMapping(
    val tenantId: TenantId,
    val hypervisorType: HypervisorType,
    val computeMapping: ComputeMapping,
    val storageMapping: StorageMapping,
    val networkMapping: NetworkMapping
)

data class ComputeMapping(
    val targetComputeUnit: String,  // e.g., "datacenter/cluster/prod-cluster-01"
    val defaultResourcePool: String? = null
)

data class StorageMapping(
    val defaultDatastore: String,
    val alternativeDatastores: List<String> = emptyList()
)

data class NetworkMapping(
    val networks: Map<String, String>  // domain network name -> hypervisor network ID
)
```

### 2.4 Example: VSphere Resource Mapper

```kotlin
class VsphereResourceMapper : ResourceMapper {
    override suspend fun mapProvisionSpec(
        domainSpec: VmProvisionSpec,
        tenantMapping: HypervisorResourceMapping
    ): Result<HypervisorNativeSpec, MappingError> {
        val compute = tenantMapping.computeMapping
        val storage = tenantMapping.storageMapping
        val network = tenantMapping.networkMapping
        
        return VsphereNativeSpec(
            datacenter = compute.targetComputeUnit.split("/")[0],
            cluster = compute.targetComputeUnit.split("/")[2],
            resourcePool = compute.defaultResourcePool,
            datastore = storage.defaultDatastore,
            network = network.networks[domainSpec.networkName] 
                ?: return MappingError.NetworkNotFound(domainSpec.networkName).failure(),
            vmName = domainSpec.vmName,
            cpuCores = domainSpec.cpuCores,
            memoryGb = domainSpec.memoryGb
        ).success()
    }
}
```

---

## 3. Error Taxonomy

### 3.1 Error Hierarchy

```kotlin
// dvmm-application/.../hypervisor/HypervisorError.kt
sealed class HypervisorError {
    abstract val message: String
    abstract val retriable: Boolean
    abstract val cause: Throwable?
    
    // Authentication/Authorization
    data class AuthenticationFailed(
        override val message: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = false
    }
    
    data class AuthorizationFailed(
        override val message: String,
        val requiredPermissions: List<String> = emptyList(),
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = false
    }
    
    // Resource Errors
    data class ResourceNotFound(
        val resourceType: String,
        val resourceId: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val message = "$resourceType not found: $resourceId"
        override val retriable = false
    }
    
    data class ResourceExhausted(
        val resourceType: String,
        val requested: Int,
        val available: Int,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val message = "Insufficient $resourceType: requested $requested, available $available"
        override val retriable = true  // Might succeed later
    }
    
    data class ResourceAlreadyExists(
        val resourceType: String,
        val resourceId: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val message = "$resourceType already exists: $resourceId"
        override val retriable = false
    }
    
    // Operation Errors
    data class OperationNotSupported(
        val operation: String,
        val hypervisorType: HypervisorType,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val message = "Operation '$operation' not supported by $hypervisorType"
        override val retriable = false
    }
    
    data class OperationFailed(
        val operation: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = true
    }
    
    data class OperationTimeout(
        val operation: String,
        val timeoutSeconds: Int,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val message = "Operation '$operation' timed out after ${timeoutSeconds}s"
        override val retriable = true
    }
    
    // Network Errors
    data class ConnectionFailed(
        override val message: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = true
    }
    
    data class NetworkError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = true
    }
    
    // Validation Errors
    data class InvalidConfiguration(
        override val message: String,
        val field: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = false
    }
    
    data class InvalidVmSpec(
        override val message: String,
        val violations: List<String> = emptyList(),
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = false
    }
    
    // Generic/Unknown
    data class UnknownError(
        override val message: String,
        override val cause: Throwable? = null
    ) : HypervisorError() {
        override val retriable = false
    }
}
```

### 3.2 Error Mapping Strategy

Each adapter must map hypervisor-specific errors to the domain error hierarchy:

```kotlin
// Example: VsphereAdapter error mapping
private fun mapVsphereException(e: Exception): HypervisorError {
    return when (e) {
        is VimFault -> when {
            e is InvalidLogin -> HypervisorError.AuthenticationFailed(
                message = "vCenter authentication failed: ${e.message}",
                cause = e
            )
            e is NotEnoughCpus -> HypervisorError.ResourceExhausted(
                resourceType = "CPU",
                requested = e.requested,
                available = e.available,
                cause = e
            )
            e is DuplicateName -> HypervisorError.ResourceAlreadyExists(
                resourceType = "VM",
                resourceId = e.name,
                cause = e
            )
            else -> HypervisorError.OperationFailed(
                operation = "unknown",
                message = e.message ?: "Unknown vSphere error",
                cause = e
            )
        }
        is SocketTimeoutException -> HypervisorError.OperationTimeout(
            operation = "network",
            timeoutSeconds = 30,
            cause = e
        )
        is ConnectException -> HypervisorError.ConnectionFailed(
            message = "Cannot connect to vCenter: ${e.message}",
            cause = e
        )
        else -> HypervisorError.UnknownError(
            message = e.message ?: "Unknown error",
            cause = e
        )
    }
}
```

---

## 4. Type Evolution with Backward Compatibility

### 4.1 Connection Parameters - Sealed Class Hierarchy

```kotlin
// dvmm-application/.../hypervisor/HypervisorConnectionParams.kt
sealed class HypervisorConnectionParams {
    abstract val hypervisorType: HypervisorType
    
    data class Vsphere(
        val vcenterUrl: String,
        val username: String,
        val passwordEncrypted: ByteArray,
        val port: Int = 443,
        val validateSsl: Boolean = true
    ) : HypervisorConnectionParams() {
        override val hypervisorType = HypervisorType.VSPHERE
    }
    
    data class Proxmox(
        val host: String,
        val port: Int = 8006,
        val tokenId: String,  // e.g., "user@pam!tokenid"
        val tokenSecretEncrypted: ByteArray,
        val validateSsl: Boolean = true
    ) : HypervisorConnectionParams() {
        override val hypervisorType = HypervisorType.PROXMOX
    }
    
    data class HyperV(
        val winrmEndpoint: String,  // e.g., "https://hyperv-host:5986/wsman"
        val domain: String,
        val username: String,
        val passwordEncrypted: ByteArray,
        val useHttps: Boolean = true,
        val skipCertValidation: Boolean = false
    ) : HypervisorConnectionParams() {
        override val hypervisorType = HypervisorType.HYPERV
    }
    
    data class PowerVm(
        val hmcEndpoint: String,  // HMC (Hardware Management Console) REST API
        val username: String,
        val passwordEncrypted: ByteArray,
        val managedSystem: String
    ) : HypervisorConnectionParams() {
        override val hypervisorType = HypervisorType.POWERVM
    }
}
```

### 4.2 Migration Strategy (Two-Phase)

#### Phase 1: Deprecation (Release N)

```kotlin
// Keep old type with deprecation warning
@Deprecated(
    message = "Use HypervisorConnectionParams.Vsphere instead",
    replaceWith = ReplaceWith(
        "HypervisorConnectionParams.Vsphere(vcenterUrl, username, passwordEncrypted, port, validateSsl)"
    ),
    level = DeprecationLevel.WARNING
)
typealias VcenterConnectionParams = HypervisorConnectionParams.Vsphere

@Deprecated(
    message = "Use HypervisorError instead",
    replaceWith = ReplaceWith("HypervisorError"),
    level = DeprecationLevel.WARNING
)
typealias VsphereError = HypervisorError
```

#### Phase 2: Removal (Release N+1)

- Remove deprecated type aliases
- Update all references to use new types
- Update tests

### 4.3 Database Schema Evolution

The `tenant_hypervisor_config` table stores connection params as encrypted JSON:

```sql
-- Migration V1__add_hypervisor_config.sql
CREATE TABLE tenant_hypervisor_config (
    tenant_id UUID PRIMARY KEY,
    hypervisor_type VARCHAR(50) NOT NULL,  -- 'VSPHERE', 'PROXMOX', 'HYPERV', 'POWERVM'
    connection_params_encrypted BYTEA NOT NULL,  -- JSON encrypted with tenant key
    resource_mappings JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

The `connection_params_encrypted` column stores type-specific JSON.

**vSphere example:**

```json
{
  "type": "VSPHERE",
  "vcenterUrl": "https://vcenter.example.com",
  "username": "dvmm@vsphere.local",
  "passwordEncrypted": "<base64-encrypted>",
  "port": 443,
  "validateSsl": true
}
```

**Proxmox example:**

```json
{
  "type": "PROXMOX",
  "host": "pve.example.com",
  "port": 8006,
  "tokenId": "dvmm@pam!api-token",
  "tokenSecretEncrypted": "<base64-encrypted>",
  "validateSsl": true
}
```

---

## 5. Testing Strategy

### 5.1 Test Infrastructure Matrix

| Hypervisor | Test Approach | Implementation |
|------------|---------------|----------------|
| VMware vSphere | Mock (vcsim) | Existing `VcsimAdapter` |
| Proxmox VE | HTTP Mock | WireMock with Proxmox API stubs |
| Hyper-V | Container-based | Docker Windows containers with WinRM |
| PowerVM | Custom Mock | MockWebServer with HMC REST API stubs |

### 5.2 VMware vSphere Testing (Existing)

```kotlin
// Already implemented
class VsphereAdapterTest {
    private lateinit var vcsim: VcsimContainer
    
    @BeforeEach
    fun setup() {
        vcsim = VcsimContainer().apply { start() }
    }
    
    @Test
    fun `createVm should provision VM successfully`() = runTest {
        // Test implementation using vcsim
    }
}
```

### 5.3 Proxmox VE Testing (New)

```kotlin
class ProxmoxAdapterTest {
    private lateinit var wireMock: WireMockServer
    
    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(8006)
        wireMock.start()
        
        // Stub Proxmox API endpoints
        wireMock.stubFor(
            post(urlEqualTo("/api2/json/access/ticket"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("""{"data": {"ticket": "PVE:token", "CSRFPreventionToken": "csrf"}}"""))
        )
        
        wireMock.stubFor(
            post(urlEqualTo("/api2/json/nodes/pve/qemu"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody("""{"data": "UPID:pve:00001234:..."}"""))
        )
    }
    
    @Test
    fun `createVm should provision VM via Proxmox API`() = runTest {
        val adapter = ProxmoxAdapter(
            httpClient = HttpClient.newHttpClient(),
            baseUrl = "http://localhost:8006"
        )
        
        val result = adapter.createVm(testVmSpec)
        
        assertTrue(result.isSuccess)
        wireMock.verify(postRequestedFor(urlEqualTo("/api2/json/nodes/pve/qemu")))
    }
}
```

### 5.4 Hyper-V Testing (New)

```kotlin
// Requires Docker Windows containers with WinRM enabled
@Testcontainers
class HyperVAdapterTest {
    @Container
    private val winrmContainer = GenericContainer<Nothing>("mcr.microsoft.com/windows/servercore:ltsc2022")
        .withExposedPorts(5986)
        .withCommand("powershell", "-Command", "Enable-PSRemoting -Force")
    
    @Test
    fun `createVm should provision VM via WinRM`() = runTest {
        val adapter = HyperVAdapter(
            winrmEndpoint = "https://localhost:${winrmContainer.getMappedPort(5986)}/wsman",
            domain = "WORKGROUP",
            username = "Administrator",
            password = "P@ssw0rd"
        )
        
        val result = adapter.createVm(testVmSpec)
        
        assertTrue(result.isSuccess)
    }
}
```

**Note:** Hyper-V testing requires Windows containers, which are complex. Consider using WireMock to stub WinRM SOAP responses instead:

```kotlin
class HyperVAdapterTest {
    private lateinit var wireMock: WireMockServer
    
    @BeforeEach
    fun setup() {
        wireMock = WireMockServer(5986)
        wireMock.start()
        
        // Stub WinRM SOAP envelope responses
        wireMock.stubFor(
            post(urlEqualTo("/wsman"))
                .withRequestBody(containing("New-VM"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withBody(winrmNewVmResponse))
        )
    }
}
```

### 5.5 PowerVM Testing (New)

```kotlin
class PowerVmAdapterTest {
    private lateinit var mockServer: MockWebServer
    
    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        
        // Stub HMC REST API responses
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(hmcLoginResponse)
        )
        
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(hmcCreateLparResponse)
        )
    }
    
    @Test
    fun `createVm should provision LPAR via HMC API`() = runTest {
        val adapter = PowerVmAdapter(
            hmcEndpoint = mockServer.url("/").toString(),
            username = "hscroot",
            password = "password",
            managedSystem = "9119-MHE*1234567"
        )
        
        val result = adapter.createVm(testVmSpec)
        
        assertTrue(result.isSuccess)
    }
}
```

### 5.6 Integration Test Strategy

Each hypervisor adapter should have:

1. **Unit tests** - Test error mapping, resource mapping logic
2. **Integration tests** - Test against mock/container infrastructure
3. **Contract tests** - Verify `HypervisorPort` interface compliance
4. **Capability tests** - Verify advertised capabilities match actual behavior

```kotlin
// Example: Contract test
abstract class HypervisorPortContractTest {
    abstract fun createAdapter(): HypervisorPort
    
    @Test
    fun `capabilities should not promise features adapter cannot deliver`() = runTest {
        val adapter = createAdapter()
        val capabilities = adapter.capabilities()
        
        if (!capabilities.supportsSnapshots) {
            val result = adapter.snapshotVm(testVmId, "test-snapshot")
            assertTrue(result.isFailure)
            assertTrue(result.error is HypervisorError.OperationNotSupported)
        }
    }
}

class VsphereAdapterContractTest : HypervisorPortContractTest() {
    override fun createAdapter() = VsphereAdapter(/* ... */)
}

class ProxmoxAdapterContractTest : HypervisorPortContractTest() {
    override fun createAdapter() = ProxmoxAdapter(/* ... */)
}
```

---

## 6. Migration Impact Analysis

### 6.1 Affected Components

| Component | Impact | Migration Effort |
|-----------|--------|------------------|
| `HypervisorPort` interface | Type parameter changes | Low (already generic) |
| `VcenterAdapter` | Rename to `VsphereAdapter`, implement new error mapping | Medium |
| Command handlers | Update error handling to use new hierarchy | Medium |
| Tests | Update type references, add new test infrastructure | High |
| Database | Add `tenant_hypervisor_config` table | Low |
| Configuration | Add hypervisor selection in admin UI | High (Epic 6 work) |

### 6.2 Breaking Changes

#### Story 6.1: Type Generalization

- `VcenterConnectionParams` → `HypervisorConnectionParams.Vsphere`
- `VsphereError` → `HypervisorError`
- `VcenterAdapter` → `VsphereAdapter`

**Mitigation:**
1. Add deprecated type aliases in release N
2. Update all internal references to new types
3. Keep deprecated aliases for one release cycle
4. Remove in release N+1

### 6.3 Test Migration Checklist

- [ ] Add `HypervisorPortContractTest` abstract class
- [ ] Migrate `VcenterAdapterTest` to `VsphereAdapterContractTest`
- [ ] Set up WireMock for Proxmox testing
- [ ] Create HMC REST API stubs for PowerVM testing
- [ ] Add capability verification tests for all adapters
- [ ] Update integration tests to use new error types

### 6.4 Rollout Strategy

#### Phase 1: Foundation (Story 6.1)

- Generalize types with deprecated aliases
- Add capability detection to `HypervisorPort`
- Implement error taxonomy
- Update existing VMware adapter

#### Phase 2: Proxmox Support (Story 6.2)

- Implement `ProxmoxAdapter`
- Add WireMock-based tests
- Create tenant configuration UI for Proxmox

#### Phase 3: Hyper-V Support (Story 6.3)

- Implement `HyperVAdapter`
- Add WinRM integration
- Create tenant configuration UI for Hyper-V

#### Phase 4: PowerVM Support (Story 6.4 - Enterprise Only)

- Implement `PowerVmAdapter`
- Add HMC REST API integration
- Create tenant configuration UI for PowerVM

---

## Decision

**Deciders:** Wall-E, John (PM), Winston (Architect)  
**Decision Date:** 2025-12-09

Accept this addendum as the **implementation blueprint** for ADR-004. All future stories in Epic 6 must reference this document for architectural decisions.

## Consequences

### Positive

- **Clear error semantics** - Retry logic can be implemented safely
- **Capability-driven UX** - Frontend can disable unsupported features dynamically
- **Testable adapters** - Each hypervisor has a concrete test strategy
- **Smooth migration** - Backward compatibility preserved during transition

### Negative

- **Increased complexity** - More types, more tests, more code
- **Test infrastructure burden** - WireMock, containers, mock servers per hypervisor
- **Documentation overhead** - Each adapter needs detailed capability documentation

### Neutral

- **Type safety improves** - Sealed classes prevent invalid states
- **Error handling becomes explicit** - No more silent failures

---

## References

- [ADR-004: Multi-Hypervisor Architecture](./ADR-004-multi-hypervisor-architecture.md)
- [Epic 6: Multi-Hypervisor Support](../epics.md#epic-6-multi-hypervisor-support-post-mvp)
- [Research: Multi-Hypervisor Support](../research/multi-hypervisor-support.md)
- [Proxmox API Documentation](https://pve.proxmox.com/pve-docs/api-viewer/)
- [Hyper-V WinRM Documentation](https://learn.microsoft.com/en-us/windows/win32/winrm/portal)
- [PowerVM HMC REST API](https://www.ibm.com/docs/en/power-systems/latest?topic=apis-hmc-rest-api)
