# Multi-Hypervisor Abstraction

**Breaking free from vendor lock-in.**

The IT world is shifting. Broadcom's acquisition of VMware has made "single-vendor" strategies risky. DCM is designed to be hypervisor-agnostic, allowing customers to mix and match VMware, Proxmox, and Hyper-V within the same tenant.

---

## The "Hypervisor Port" Pattern

We don't sprinkle `if (isVmware)` logic throughout the codebase. Instead, we use the **Ports and Adapters** pattern.

### 1. The Generic Contract (Port)
In the Domain layer (`dcm-application`), we define a generic interface that describes *what* we need, not *how* it's done.

```kotlin
interface HypervisorPort {
    suspend fun listResources(): List<ResourcePool>
    suspend fun provisionVm(request: VmSpec): VmId
    suspend fun getVmStatus(id: VmId): VmStatus
    suspend fun powerCycle(id: VmId, action: PowerAction)
}
```

### 2. The Specific Adapters (Infrastructure)
In the Infrastructure layer (`dcm-infrastructure`), we implement this interface for each technology.

*   **VsphereAdapter:** Uses VCF SDK (SOAP) to talk to vCenter.
*   **ProxmoxAdapter:** Uses REST API to talk to Proxmox VE.
*   **HyperVAdapter:** Uses WinRM/PowerShell to talk to SCVMM.

### 3. The Factory
At runtime, we choose the correct adapter based on the **Project Configuration**.

```kotlin
class HypervisorFactory(
    private val vsphere: VsphereAdapter,
    private val proxmox: ProxmoxAdapter
) {
    fun getFor(project: Project): HypervisorPort {
        return when (project.platform) {
            Platform.VMWARE -> vsphere
            Platform.PROXMOX -> proxmox
        }
    }
}
```

---

## The Challenge: "Leaky Abstractions"

Every hypervisor is different. VMware has "Resource Pools," Proxmox has "Nodes." VMware uses "Datastores," Hyper-V uses "CSV."

### Strategy: Lowest Common Denominator + Extensions
1.  **Core:** Only abstract the things 99% of users need (CPU, RAM, Disk, Power, IP).
2.  **Extensions:** Use a key-value `metadata` map for platform-specific tweaks.
    *   *VMware:* `{"folder": "Datacenter/Vm"}`
    *   *Proxmox:* `{"storage": "local-zfs"}`

## Testing Strategy

We cannot easily mock Proxmox or Hyper-V in CI.

1.  **Unit Tests:** Mock the `HypervisorPort` interface. Domain logic doesn't care about the implementation.
2.  **Integration Tests:**
    *   **VMware:** Use `vcsim` (official simulator).
    *   **Proxmox:** Use `testcontainers-proxmox` (if available) or a lightweight mock server that mimics the REST API.
    *   **Generic:** Use standard Docker containers to simulate "provisioned VMs" for workflow testing.

## Future Proofing

This architecture allows us to add **AWS** or **Azure** support in the future simply by writing a new Adapter. The core business logic (Approvals, Quotas, Billing) remains the same.
