package de.acci.eaf.testing.vcsim

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * Testcontainers wrapper for VMware vCenter Simulator (VCSIM).
 *
 * VCSIM provides a vSphere API-compatible `/sdk` endpoint for integration testing
 * without requiring real VMware infrastructure. It simulates vCenter Server
 * with configurable clusters, hosts, VMs, and other inventory objects.
 *
 * ## Default Configuration
 * - Image: `vmware/vcsim:v0.47.0`
 * - Port: 8989 (HTTPS `/sdk` endpoint)
 * - 2 clusters, 4 hosts per cluster, 10 VMs per host (80 total VMs)
 * - Default credentials: user/pass
 *
 * ## Usage
 * ```kotlin
 * // Via TestContainers singleton (recommended)
 * val vcsim = TestContainers.vcsim
 * val sdkUrl = vcsim.getSdkUrl()
 *
 * // Or direct instantiation
 * val vcsim = VcsimContainer()
 * vcsim.start()
 * ```
 *
 * ## Environment Variables
 * Configure inventory size via environment variables:
 * - `VCSIM_CLUSTER`: Number of clusters (default: 2)
 * - `VCSIM_HOST`: Hosts per cluster (default: 4)
 * - `VCSIM_VM`: VMs per host (default: 10)
 * - `VCSIM_POOL`: Resource pools per cluster (default: 2)
 * - `VCSIM_FOLDER`: VM folders (default: 3)
 *
 * @see <a href="https://github.com/vmware/govmomi/blob/main/vcsim/README.md">VCSIM Documentation</a>
 */
public class VcsimContainer(
    dockerImageName: DockerImageName = DockerImageName.parse(DEFAULT_IMAGE)
) : GenericContainer<VcsimContainer>(dockerImageName) {

    public companion object {
        /** Default VCSIM Docker image (pinned for reproducibility) */
        public const val DEFAULT_IMAGE: String = "vmware/vcsim:v0.47.0"

        /** Default VCSIM SDK port */
        public const val DEFAULT_PORT: Int = 8989

        /** Default VCSIM username */
        public const val DEFAULT_USERNAME: String = "user"

        /** Default VCSIM password */
        public const val DEFAULT_PASSWORD: String = "pass"

        /** Default number of clusters */
        public const val DEFAULT_CLUSTERS: Int = 2

        /** Default number of hosts per cluster */
        public const val DEFAULT_HOSTS_PER_CLUSTER: Int = 4

        /** Default number of VMs per host */
        public const val DEFAULT_VMS_PER_HOST: Int = 10

        /** Default number of resource pools per cluster */
        public const val DEFAULT_POOLS: Int = 2

        /** Default number of VM folders */
        public const val DEFAULT_FOLDERS: Int = 3
    }

    init {
        withExposedPorts(DEFAULT_PORT)
        withEnv("VCSIM_CLUSTER", DEFAULT_CLUSTERS.toString())
        withEnv("VCSIM_HOST", DEFAULT_HOSTS_PER_CLUSTER.toString())
        withEnv("VCSIM_VM", DEFAULT_VMS_PER_HOST.toString())
        withEnv("VCSIM_POOL", DEFAULT_POOLS.toString())
        withEnv("VCSIM_FOLDER", DEFAULT_FOLDERS.toString())
        // Use log message wait strategy since VCSIM uses HTTPS with self-signed cert
        // VCSIM logs "export GOVC_URL" when ready to accept connections
        waitingFor(
            Wait.forLogMessage(".*GOVC_URL.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2))
        )
        withReuse(true)
    }

    /**
     * Returns the full vSphere SDK URL for connecting to VCSIM.
     *
     * The URL points to the `/sdk` endpoint which provides SOAP API
     * compatibility with govmomi, pyvmomi, and other vSphere clients.
     *
     * @return SDK URL in format `https://host:port/sdk`
     */
    public fun getSdkUrl(): String = "https://$host:${getMappedPort(DEFAULT_PORT)}/sdk"

    /**
     * Returns the base URL without the `/sdk` path.
     *
     * Useful for accessing other VCSIM endpoints like `/about`.
     *
     * @return Base URL in format `https://host:port`
     */
    public fun getBaseUrl(): String = "https://$host:${getMappedPort(DEFAULT_PORT)}"

    /**
     * Returns the default username for VCSIM authentication.
     *
     * VCSIM accepts any username, but "user" is the documented default.
     *
     * @return Username string ("user")
     */
    public fun getUsername(): String = DEFAULT_USERNAME

    /**
     * Returns the default password for VCSIM authentication.
     *
     * VCSIM accepts any password, but "pass" is the documented default.
     *
     * @return Password string ("pass")
     */
    public fun getPassword(): String = DEFAULT_PASSWORD

    /**
     * Returns the mapped SDK port on the host machine.
     *
     * @return Mapped port number
     */
    public fun getSdkPort(): Int = getMappedPort(DEFAULT_PORT)

    /**
     * Configures the number of clusters in the simulated environment.
     *
     * @param count Number of clusters (must be positive)
     * @return This container for method chaining
     */
    public fun withClusters(count: Int): VcsimContainer {
        require(count > 0) { "Cluster count must be positive, got $count" }
        withEnv("VCSIM_CLUSTER", count.toString())
        return this
    }

    /**
     * Configures the number of hosts per cluster.
     *
     * @param count Number of hosts per cluster (must be positive)
     * @return This container for method chaining
     */
    public fun withHostsPerCluster(count: Int): VcsimContainer {
        require(count > 0) { "Host count must be positive, got $count" }
        withEnv("VCSIM_HOST", count.toString())
        return this
    }

    /**
     * Configures the number of VMs per host.
     *
     * @param count Number of VMs per host (must be non-negative)
     * @return This container for method chaining
     */
    public fun withVmsPerHost(count: Int): VcsimContainer {
        require(count >= 0) { "VM count must be non-negative, got $count" }
        withEnv("VCSIM_VM", count.toString())
        return this
    }

    /**
     * Configures the number of resource pools per cluster.
     *
     * @param count Number of resource pools (must be non-negative)
     * @return This container for method chaining
     */
    public fun withResourcePools(count: Int): VcsimContainer {
        require(count >= 0) { "Resource pool count must be non-negative, got $count" }
        withEnv("VCSIM_POOL", count.toString())
        return this
    }

    /**
     * Configures the number of VM folders.
     *
     * @param count Number of folders (must be non-negative)
     * @return This container for method chaining
     */
    public fun withFolders(count: Int): VcsimContainer {
        require(count >= 0) { "Folder count must be non-negative, got $count" }
        withEnv("VCSIM_FOLDER", count.toString())
        return this
    }
}
