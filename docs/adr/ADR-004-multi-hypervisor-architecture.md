# ADR-004: Multi-Hypervisor Architecture (Post-MVP)

**Status:** Accepted (Design Only)
**Date:** 2025-12-09
**Author:** DCM Team
**Deciders:** Wall-E, John (PM)
**Related:** [Research Document](../research/multi-hypervisor-support.md), [Epic 6](../epics.md#epic-6-multi-hypervisor-support-post-mvp)

---

## Context

The Broadcom VMware acquisition (November 2023) has triggered massive market disruption:
- 98% of VMware customers evaluating alternatives
- Price increases of 150-1500%
- Proxmox VE experiencing 650% growth (1.5M+ hosts)

See `docs/research/multi-hypervisor-support.md` for comprehensive market and technical analysis.

### Current State

DCM MVP uses VMware vSphere as the sole hypervisor, with the `HypervisorPort` interface (formerly `VspherePort`) abstracting hypervisor operations. The interface currently uses VMware-specific types (`VcenterConnectionParams`, `VsphereError`) which will be generalized in Epic 6.

## Decision

DCM will support multiple hypervisors through a **port/adapter abstraction layer**, implemented **post-MVP**.

**MVP:** VMware vSphere only (current implementation via `HypervisorPort` with VMware-specific types)
**Post-MVP:** Extensible `HypervisorPort` interface with generic types supporting Proxmox VE, Hyper-V, PowerVM

## Architecture Pattern

### Interface Abstraction (Post-MVP Target)

```kotlin
// dcm-application/src/main/kotlin/.../hypervisor/HypervisorPort.kt
public interface HypervisorPort {
    suspend fun testConnection(config: HypervisorConfig, credentials: Credentials): Result<ConnectionInfo, ConnectionError>
    suspend fun listResources(): Result<HypervisorResources, HypervisorError>
    suspend fun createVm(spec: VmProvisionSpec): Result<VmProvisioningResult, HypervisorError>
    suspend fun getVm(vmId: VmIdentifier): Result<VmInfo, HypervisorError>
    suspend fun startVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun stopVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
    suspend fun deleteVm(vmId: VmIdentifier): Result<Unit, HypervisorError>
}
```

### Adapter Implementations

```
dcm-infrastructure/
└── hypervisor/
    ├── vsphere/
    │   └── VsphereAdapter.kt      # MVP (existing VcenterAdapter)
    ├── proxmox/
    │   └── ProxmoxAdapter.kt      # Post-MVP Phase 1
    ├── hyperv/
    │   └── HyperVAdapter.kt       # Post-MVP Phase 2
    └── powervm/
        └── PowerVmAdapter.kt      # Post-MVP Phase 3
```

### Adapter Factory Pattern

```kotlin
@Component
class HypervisorAdapterFactory(
    private val vsphereAdapter: VsphereAdapter,
    private val proxmoxAdapter: ProxmoxAdapter?,  // Optional, loaded if available
    private val hypervAdapter: HyperVAdapter?,
    private val powerVmAdapter: PowerVmAdapter?,
    private val configRepository: TenantHypervisorConfigRepository
) {
    fun getAdapter(tenantId: TenantId): HypervisorPort {
        val config = configRepository.findByTenantId(tenantId)
            ?: throw NoHypervisorConfiguredException(tenantId)

        return when (config.hypervisorType) {
            HypervisorType.VSPHERE -> vsphereAdapter.withConfig(config)
            HypervisorType.PROXMOX -> proxmoxAdapter?.withConfig(config)
                ?: throw UnsupportedHypervisorException(HypervisorType.PROXMOX)
            HypervisorType.HYPERV -> hypervAdapter?.withConfig(config)
                ?: throw UnsupportedHypervisorException(HypervisorType.HYPERV)
            HypervisorType.POWERVM -> powerVmAdapter?.withConfig(config)
                ?: throw UnsupportedHypervisorException(HypervisorType.POWERVM)
        }
    }
}
```

### Tenant Configuration

```kotlin
data class TenantHypervisorConfig(
    val tenantId: TenantId,
    val hypervisorType: HypervisorType,
    val connectionConfig: HypervisorConnectionConfig,
    val resourceMappings: HypervisorResourceMappings
)

enum class HypervisorType {
    VSPHERE,   // MVP
    PROXMOX,   // Post-MVP Phase 1
    HYPERV,    // Post-MVP Phase 2
    POWERVM    // Post-MVP Phase 3 (enterprise only)
}
```

## SDK Landscape

| Hypervisor | SDK | Maven Central | Effort |
|------------|-----|---------------|--------|
| VMware vSphere | VCF SDK 9.0 | Yes | Complete |
| Proxmox VE | cv4pve-api-java 9.0.0 | Yes | Low |
| Hyper-V | winrm4j / MetricsHub winrm-java | Yes | High |
| PowerVM | Custom (no Java SDK) | N/A | Very High |

## Migration Path

1. **MVP (Current):** `HypervisorPort` interface with VMware-specific types
2. **Story 6.1:** Generalize types (`VcenterConnectionParams` → `HypervisorConnectionParams` sealed class)
3. **Story 6.2+:** Add new adapter implementations

## Consequences

### Positive

- Clean separation between interface and implementation (already exists)
- Tenant-level hypervisor selection without code changes
- Low MVP impact (design only, interface already renamed)
- Market opportunity capture for VMware refugees

### Negative

- Each hypervisor has unique resource models requiring mapping
- Test infrastructure needed per hypervisor
- Support complexity increases with each hypervisor

### Neutral

- Domain events remain hypervisor-agnostic
- All existing workflows work identically regardless of hypervisor

## Type Evolution Plan

The following VMware-specific types will be generalized in Epic 6:

| Current (MVP) | Future (Epic 6) | Notes |
|---------------|-----------------|-------|
| `VcenterConnectionParams` | `HypervisorConnectionParams` | Sealed class with per-hypervisor variants |
| `VsphereError` | `HypervisorError` | Generic error hierarchy |
| `Datacenter`, `Cluster` | `ResourceHierarchy` | Abstract resource tree |
| `VmwareVmId` | `HypervisorVmId` | Wrapper with hypervisor type |

## References

- Research Document: `docs/research/multi-hypervisor-support.md`
- Epic 6: Multi-Hypervisor Support (Post-MVP) in `docs/epics.md`
- FR-HYPERVISOR-001 through FR-HYPERVISOR-005 in `docs/prd.md`
- `HypervisorPort` interface: `dcm-application/.../vmware/HypervisorPort.kt`
