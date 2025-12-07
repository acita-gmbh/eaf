package de.acci.dvmm.application.vmware

import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vmware.VcenterConnectionParams
import de.acci.eaf.core.result.Result

/**
 * Port for vSphere API operations.
 *
 * This interface abstracts the vCenter/ESXi connection and operations,
 * allowing different implementations for production (vCenter API) and
 * testing (VCSIM simulator).
 *
 * ## Adapter Pattern (AC-3.1.2, AC-3.1.3)
 *
 * Two implementations are provided, selected via Spring Profile:
 * - `VcenterAdapter` (`@Profile("!vcsim")`) - Production vCenter API
 * - `VcsimAdapter` (`@Profile("vcsim")`) - VCSIM simulator for testing
 *
 * ## Usage
 *
 * ```kotlin
 * @Autowired
 * lateinit var vspherePort: VspherePort
 *
 * suspend fun testConfiguration(params: VcenterConnectionParams, password: String) {
 *     val result = vspherePort.testConnection(params, password)
 *     result.fold(
 *         onSuccess = { info ->
 *             println("Connected to vCenter ${info.vcenterVersion}")
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
public interface VspherePort {

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
     * @return Result containing VmProvisioningResult on success, or VsphereError on failure
     */
    public suspend fun createVm(spec: VmSpec): Result<VmProvisioningResult, VsphereError>

    public suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError>
    public suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError>
}