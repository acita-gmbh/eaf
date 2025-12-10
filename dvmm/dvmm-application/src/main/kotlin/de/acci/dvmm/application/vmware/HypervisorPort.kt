package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vmware.VcenterConnectionParams
import de.acci.eaf.core.result.Result

/**
 * Port for hypervisor API operations.
 *
 * This interface abstracts hypervisor connections and operations,
 * allowing different implementations for various hypervisors:
 * - VMware vSphere (via VcenterAdapter)
 * - Proxmox VE (Post-MVP, via ProxmoxAdapter)
 * - Microsoft Hyper-V (Post-MVP, via HyperVAdapter)
 * - IBM PowerVM (Post-MVP, via PowerVmAdapter)
 *
 * ## Adapter Pattern (AC-3.1.2, AC-3.1.3)
 *
 * Implementations are selected via Spring Profile or tenant configuration:
 * - `VcenterAdapter` (`@Profile("!vcsim")`) - Production vCenter API
 * - `VcsimAdapter` (`@Profile("vcsim")`) - VCSIM simulator for testing
 *
 * ## Multi-Hypervisor Architecture (ADR-004)
 *
 * This interface is designed to be hypervisor-agnostic. The method signatures
 * use generic concepts (testConnection, createVm, getVm) that apply to any
 * virtualization platform. Hypervisor-specific logic is encapsulated in adapters.
 *
 * See `docs/architecture.md#ADR-004` for the full multi-hypervisor architecture.
 *
 * ## Current State: VMware-Specific Types (MVP)
 *
 * **Note:** This interface currently uses VMware-specific types ([VcenterConnectionParams],
 * [VsphereError], etc.) because VMware vSphere is the only supported hypervisor in MVP.
 *
 * When Epic 6 (Multi-Hypervisor Support) is implemented, these will be replaced with
 * generic abstractions:
 * - `VcenterConnectionParams` → `HypervisorConnectionParams` (sealed class with per-hypervisor variants)
 * - `VsphereError` → `HypervisorError` (generic error hierarchy)
 * - VMware concepts (datacenter, cluster) → Generic resource hierarchy
 *
 * The interface is named `HypervisorPort` now to signal architectural intent and
 * simplify the future refactoring. See ADR-004 for the planned type hierarchy.
 *
 * ## Usage
 *
 * ```kotlin
 * @Autowired
 * lateinit var hypervisorPort: HypervisorPort
 *
 * suspend fun testConfiguration(params: VcenterConnectionParams, password: String) {
 *     val result = hypervisorPort.testConnection(params, password)
 *     result.fold(
 *         onSuccess = { info ->
 *             println("Connected to hypervisor ${info.vcenterVersion}")
 *         },
 *         onFailure = { error ->
 *             when (error) {
 *                 is ConnectionError.AuthenticationFailed ->
 *                     println("Invalid credentials")
 *                 is ConnectionError.NetworkError ->
 *                     println("Connection refused: ${error.message}")
 *                 // ...
 *             }
 *         }
 *     )
 * }
 * ```
 */
public interface HypervisorPort {

    /**
     * Test connection to vCenter using the provided parameters.
     *
     * Validates:
     * - Network connectivity to vCenter URL
     * - Authentication with provided credentials
     * - Existence of specified datacenter
     * - Existence of specified cluster
     * - Existence of specified datastore
     * - Existence of specified network
     * - Existence of specified VM template
     *
     * @param params Connection parameters (URL, username, infrastructure names)
     * @param password The plaintext password (caller must decrypt if using stored config)
     * @return Result containing connection info on success, or specific error on failure
     */
    public suspend fun testConnection(
        params: VcenterConnectionParams,
        password: String
    ): Result<ConnectionInfo, ConnectionError>

    public suspend fun listDatacenters(): Result<List<Datacenter>, VsphereError>
    public suspend fun listClusters(datacenter: Datacenter): Result<List<Cluster>, VsphereError>
    public suspend fun listDatastores(cluster: Cluster): Result<List<Datastore>, VsphereError>
    public suspend fun listNetworks(datacenter: Datacenter): Result<List<Network>, VsphereError>
    public suspend fun listResourcePools(cluster: Cluster): Result<List<ResourcePool>, VsphereError>

    /**
     * Create a VM from a template with full provisioning.
     *
     * This method performs the complete VM creation workflow:
     * 1. Clone the specified template
     * 2. Apply guest customization (hostname)
     * 3. Configure disk size if larger than template
     * 4. Power on the VM
     * 5. Wait for VMware Tools to become ready (with timeout)
     * 6. Detect IP address via VMware Tools
     *
     * @param spec VM specification (name, template, CPU, memory, disk)
     * @param onProgress Callback for tracking provisioning progress stages
     * @return Result containing VmProvisioningResult on success, or VsphereError on failure
     */
    public suspend fun createVm(
        spec: VmSpec,
        onProgress: suspend (VmProvisioningStage) -> Unit = {}
    ): Result<VmProvisioningResult, VsphereError>

    public suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError>
    public suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError>
}