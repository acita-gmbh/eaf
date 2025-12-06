package de.acci.eaf.testing.vcsim

import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Future

private val logger = KotlinLogging.logger {}

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
 * // Via TestContainers singleton (recommended - includes TLS certificates)
 * val vcsim = TestContainers.vcsim
 * val sdkUrl = vcsim.getSdkUrl()
 *
 * // Or direct instantiation with certificates
 * val bundle = VcsimCertificateGenerator.generate()
 * val vcsim = VcsimContainer()
 *     .withCertificates(bundle)
 * vcsim.start()
 *
 * // Secure HTTP client using the bundle
 * val sslContext = bundle.createSslContext()
 * val httpClient = HttpClient.newBuilder()
 *     .sslContext(sslContext)
 *     .build()
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
 * @see VcsimCertificateGenerator
 */
public class VcsimContainer : GenericContainer<VcsimContainer> {

    /**
     * Creates a VcsimContainer using a pre-built Docker image.
     *
     * @param dockerImageName Docker image name (default: vmware/vcsim:v0.47.0)
     */
    public constructor(
        dockerImageName: DockerImageName = DockerImageName.parse(DEFAULT_IMAGE)
    ) : super(dockerImageName)

    /**
     * Creates a VcsimContainer using an image built from a Dockerfile.
     *
     * Used internally by [create] for ARM64 architecture where we build
     * VCSIM from source since the official image is AMD64-only.
     *
     * @param imageFuture Future that resolves to the built image name
     */
    public constructor(imageFuture: Future<String>) : super(imageFuture)

    public companion object {
        /** Default VCSIM Docker image (pinned for reproducibility) */
        public const val DEFAULT_IMAGE: String = "vmware/vcsim:v0.47.0"

        /** VCSIM version (govmomi tag) for building from source */
        public const val VCSIM_VERSION: String = "v0.47.0"

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

        /** Container path for TLS certificate */
        private const val CONTAINER_CERT_PATH: String = "/tmp/vcsim/server.crt"

        /** Container path for TLS private key */
        private const val CONTAINER_KEY_PATH: String = "/tmp/vcsim/server.key"

        /** Image name for ARM64 builds */
        private const val ARM64_IMAGE_NAME: String = "eaf-vcsim:$VCSIM_VERSION"

        /**
         * Creates a VcsimContainer with architecture-appropriate image.
         *
         * ## Behavior by Architecture
         * - **AMD64 (x86_64)**: Uses official `vmware/vcsim` image (fast pull)
         * - **ARM64 (Apple Silicon)**: Builds native image on-demand
         *   - First run: ~60s (Go compile + Docker build)
         *   - Subsequent runs: <5s (uses cached image `eaf-vcsim:v0.47.0`)
         *
         * ## Example
         * ```kotlin
         * val vcsim = VcsimContainer.create()
         *     .withCertificates(VcsimCertificateGenerator.generate())
         * vcsim.start()
         * ```
         *
         * @return VcsimContainer configured for the host architecture
         */
        public fun create(): VcsimContainer {
            return if (isArm64()) {
                logger.info { "ARM64 detected - building VCSIM from source (first run ~60s, cached after)" }
                VcsimContainer(buildArm64Image())
            } else {
                logger.debug { "AMD64 detected - using official vmware/vcsim image" }
                VcsimContainer()
            }
        }

        /**
         * Detects if the current JVM is running on ARM64 architecture.
         *
         * @return true if running on ARM64 (aarch64/arm64), false otherwise
         */
        internal fun isArm64(): Boolean {
            val arch = System.getProperty("os.arch", "").lowercase()
            return arch.contains("aarch64") || arch.contains("arm64")
        }

        private fun buildArm64Image(): Future<String> {
            return ImageFromDockerfile(ARM64_IMAGE_NAME, false) // false = don't delete on exit
                .withFileFromClasspath("Dockerfile", "docker/vcsim/Dockerfile")
                .withBuildArg("VCSIM_VERSION", VCSIM_VERSION)
        }
    }

    private var certificateBundle: VcsimCertificateBundle? = null
    private var certDirectory: Path? = null

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
     * Configures VCSIM to use generated TLS certificates.
     *
     * When certificates are configured:
     * - Certificate files are written to a temporary directory
     * - Files are mounted into the container
     * - VCSIM is started with -tlscert and -tlskey flags
     *
     * Use [getCertificateBundle] to obtain an SSLContext for secure connections.
     *
     * @param bundle Certificate bundle from [VcsimCertificateGenerator.generate]
     * @return This container for method chaining
     */
    public fun withCertificates(bundle: VcsimCertificateBundle): VcsimContainer {
        this.certificateBundle = bundle
        return this
    }

    /**
     * Returns the certificate bundle if configured.
     *
     * Use this to create an SSLContext for HTTP clients:
     * ```kotlin
     * val sslContext = container.getCertificateBundle()?.createSslContext()
     * ```
     *
     * @return Certificate bundle, or null if not configured
     */
    public fun getCertificateBundle(): VcsimCertificateBundle? = certificateBundle

    override fun configure() {
        super.configure()

        certificateBundle?.let { bundle ->
            // Create temp directory for certificates
            val tempDir = Files.createTempDirectory("vcsim-certs")
            certDirectory = tempDir

            // Write certificate files
            val certFiles = bundle.writeTo(tempDir)

            // Mount certificate files into container (hostPath, containerPath, mode)
            withFileSystemBind(
                certFiles.serverCert.toAbsolutePath().toString(),
                CONTAINER_CERT_PATH,
                BindMode.READ_ONLY
            )
            withFileSystemBind(
                certFiles.serverKey.toAbsolutePath().toString(),
                CONTAINER_KEY_PATH,
                BindMode.READ_ONLY
            )

            // Configure VCSIM to use our certificates
            // Must include -l flag since withCommand replaces CMD (which has the default listen address)
            withCommand("-l", "0.0.0.0:$DEFAULT_PORT", "-tlscert=$CONTAINER_CERT_PATH", "-tlskey=$CONTAINER_KEY_PATH")
        }
    }

    override fun close() {
        super.close()
        // Clean up temporary certificate directory
        certDirectory?.let { dir ->
            try {
                Files.walk(dir).use { stream ->
                    stream.sorted(Comparator.reverseOrder())
                        .forEach(Files::delete)
                }
            } catch (e: Exception) {
                // Best-effort cleanup - temp directory will be cleaned by OS eventually
                logger.debug(e) { "Failed to clean up temp certificate directory: $dir" }
            }
        }
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
