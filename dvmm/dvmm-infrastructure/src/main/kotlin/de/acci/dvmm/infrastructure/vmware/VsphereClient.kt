package de.acci.dvmm.infrastructure.vmware

import com.vmware.sdk.vsphere.utils.VimClient.getVimServiceInstanceRef
import com.vmware.vim25.InvalidLoginFaultMsg
import com.vmware.vim25.ManagedObjectReference
import com.vmware.vim25.ObjectSpec
import com.vmware.vim25.PropertyFilterSpec
import com.vmware.vim25.PropertySpec
import com.vmware.vim25.RetrieveOptions
import com.vmware.vim25.TaskInfo
import com.vmware.vim25.TaskInfoState
import com.vmware.vim25.TraversalSpec
import com.vmware.vim25.VimService
import com.vmware.vim25.VirtualMachineCloneSpec
import com.vmware.vim25.VirtualMachineConfigSpec
import com.vmware.vim25.VirtualMachineRelocateSpec
import de.acci.dvmm.application.vmware.Cluster
import de.acci.dvmm.application.vmware.CredentialEncryptor
import de.acci.dvmm.application.vmware.Datacenter
import de.acci.dvmm.application.vmware.Datastore
import de.acci.dvmm.application.vmware.Network
import de.acci.dvmm.application.vmware.ResourcePool
import de.acci.dvmm.application.vmware.VmId
import de.acci.dvmm.application.vmware.VmInfo
import de.acci.dvmm.application.vmware.VmSpec
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VsphereError
import de.acci.dvmm.domain.vm.VmProvisioningResult
import de.acci.dvmm.domain.vm.VmProvisioningStage
import de.acci.dvmm.domain.vm.VmwareVmId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import jakarta.annotation.PreDestroy
import jakarta.xml.ws.BindingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.minutes

@Component
public class VsphereClient(
    private val sessionManager: VsphereSessionManager,
    private val configPort: VmwareConfigurationPort,
    private val credentialEncryptor: CredentialEncryptor,
    @Value("\${dvmm.vcenter.ignore-cert:false}")
    private val ignoreCert: Boolean = false,
    @Value("\${dvmm.vsphere.timeout-ms:60000}")
    private val timeoutMs: Long = 60000
) {
    companion object {
        init {
            // Force CXF to use legacy HttpURLConnection transport which respects JVM SSL defaults.
            // Must be set before any CXF classes are loaded.
            System.setProperty("org.apache.cxf.transport.http.forceURLConnection", "true")
        }
    }

    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val circuitBreaker = CircuitBreaker.of("vsphere", CircuitBreakerConfig.custom()
        .slidingWindowSize(5)
        .minimumNumberOfCalls(5)
        .failureRateThreshold(100.0f)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .permittedNumberOfCallsInHalfOpenState(2)
        .recordResult { result -> result is Result.Failure<*> }
        .build())

    /**
     * Cleans up resources when the Spring bean is destroyed.
     * Cancels all keepalive coroutines to prevent resource leaks during shutdown.
     */
    @PreDestroy
    public fun cleanup() {
        logger.info { "Shutting down VsphereClient, cancelling keepalive jobs" }
        scope.cancel()
    }

    private suspend fun <T> executeResilient(
        name: String,
        operationTimeoutMs: Long = timeoutMs,
        block: suspend () -> Result<T, VsphereError>
    ): Result<T, VsphereError> {
        return try {
            logger.debug { "Starting vSphere operation: $name (timeout: ${operationTimeoutMs}ms)" }
            withTimeout(operationTimeoutMs) {
                circuitBreaker.executeSuspendFunction {
                    block()
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "Operation $name timed out" }
            VsphereError.Timeout("Operation $name timed out after ${operationTimeoutMs}ms").failure()
        } catch (e: io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            logger.warn { "Operation $name blocked by circuit breaker" }
            VsphereError.ConnectionError("Circuit breaker open - vCenter is unstable").failure()
        } catch (e: Exception) {
            logger.error(e) { "Operation $name failed unexpectedly" }
            VsphereError.ApiError("Unexpected error in $name", e).failure()
        }
    }

    /**
     * Ensures a vSphere session exists for the given tenant, creating one if necessary.
     *
     * ## VCF SDK 9.0 Port 443 Limitation Workaround
     *
     * VcenterClientFactory only supports port 443. For VCSIM (dynamic ports) or
     * non-standard vCenter deployments, we bypass the factory and configure JAX-WS directly:
     * 1. Force CXF to use legacy HttpURLConnection transport (supports SSL config)
     * 2. Configure SSL context via JVM defaults
     * 3. Create VimService and get VimPortType
     * 4. Set endpoint URL via BindingProvider (supports any port)
     * 5. Login via SOAP API
     *
     * @return VsphereSession on success, VsphereError on failure
     */
    private suspend fun ensureSession(tenantId: TenantId): Result<VsphereSession, VsphereError> {
        sessionManager.getSession(tenantId)?.let { return it.success() }

        val config = configPort.findByTenantId(tenantId)
            ?: return VsphereError.ConnectionError("No VMware configuration found for tenant $tenantId").failure()

        return try {
            val password = credentialEncryptor.decrypt(config.passwordEncrypted)

            // Trust-all manager for ignoring certificate validation (used by both JVM and CXF)
            val trustAllCerts = if (ignoreCert) {
                arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = null
                    override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                })
            } else null

            // Create VimService and get port
            val vimService = VimService()
            val vimPort = vimService.vimPort

            // Configure endpoint URL via BindingProvider (supports custom ports like VCSIM)
            val bindingProvider = vimPort as BindingProvider
            bindingProvider.requestContext[BindingProvider.ENDPOINT_ADDRESS_PROPERTY] = config.vcenterUrl
            // Enable session management - required for maintaining login session
            bindingProvider.requestContext[BindingProvider.SESSION_MAINTAIN_PROPERTY] = true

            // Configure CXF HTTPConduit with SSL settings (per-connection, not global JVM defaults)
            val client = org.apache.cxf.frontend.ClientProxy.getClient(vimPort)
            val httpConduit = client.conduit as org.apache.cxf.transport.http.HTTPConduit
            if (ignoreCert && trustAllCerts != null) {
                // Trust-all for VCSIM/testing - only affects this CXF connection
                httpConduit.tlsClientParameters = org.apache.cxf.configuration.jsse.TLSClientParameters().apply {
                    isDisableCNCheck = true
                    trustManagers = trustAllCerts
                }
            } else {
                // Production: use system default truststore (JVM cacerts)
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                httpConduit.tlsClientParameters = org.apache.cxf.configuration.jsse.TLSClientParameters().apply {
                    trustManagers = tmf.trustManagers
                }
            }

            // Login to vCenter
            val serviceContent = vimPort.retrieveServiceContent(getVimServiceInstanceRef())
            vimPort.login(
                serviceContent.sessionManager,
                config.username,
                password,
                null
            )

            // Start keepalive coroutine
            val job = scope.launch {
                while (isActive) {
                    delay(15.minutes)
                    try {
                        vimPort.currentTime(getVimServiceInstanceRef())
                        sessionManager.touchSession(tenantId)
                        logger.debug { "Keepalive successful for tenant $tenantId" }
                    } catch (e: Exception) {
                        logger.warn(e) { "Keepalive failed for tenant $tenantId" }
                        if (e is InvalidLoginFaultMsg || e.message?.contains("session") == true) {
                            sessionManager.removeSession(tenantId)
                            cancel()
                        }
                    }
                }
            }

            val session = VsphereSession(
                vimPort = vimPort,
                serviceContent = serviceContent,
                keepAliveJob = job
            )
            sessionManager.registerSession(tenantId, session)
            logger.info { "Established new vSphere session for tenant $tenantId" }

            session.success()
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to vSphere for tenant $tenantId" }
            VsphereError.ConnectionError("Failed to connect to vSphere", e).failure()
        }
    }

    // ... helpers ...
    private fun moRef(type: String, value: String) = ManagedObjectReference().apply {
        this.type = type
        this.value = value
    }

    private suspend fun <T> retrieveObjects(
        session: VsphereSession,
        type: String,
        properties: List<String>,
        root: ManagedObjectReference? = null,
        mapper: (ManagedObjectReference, Map<String, Any?>) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        val vimPort = session.vimPort
        val serviceContent = session.serviceContent
        val propertyCollector = serviceContent.propertyCollector
        val viewManager = serviceContent.viewManager

        val rootRef = root ?: serviceContent.rootFolder
        val containerView = vimPort.createContainerView(viewManager, rootRef, listOf(type), true)

        try {
            val propSpec = PropertySpec().apply {
                this.type = type
                this.pathSet.addAll(properties)
            }

            val traversalSpec = TraversalSpec().apply {
                this.name = "traverseEntities"
                this.type = "ContainerView"
                this.path = "view"
                this.isSkip = false
            }

            val objSpec = ObjectSpec().apply {
                this.obj = containerView
                this.isSkip = true
                this.selectSet.add(traversalSpec)
            }

            val filterSpec = PropertyFilterSpec().apply {
                this.propSet.add(propSpec)
                this.objectSet.add(objSpec)
            }

            val options = RetrieveOptions().apply {
                this.maxObjects = 1000
            }

            val results = mutableListOf<T>()
            var token: String? = null

            do {
                val result = if (token == null) {
                    vimPort.retrievePropertiesEx(propertyCollector, listOf(filterSpec), options)
                } else {
                    vimPort.continueRetrievePropertiesEx(propertyCollector, token)
                }

                if (result != null) {
                    token = result.token
                    result.objects.forEach { objContent ->
                        val props = objContent.propSet.associate { it.name to it.`val` }
                        results.add(mapper(objContent.obj, props))
                    }
                } else {
                    token = null
                }
            } while (token != null)

            results
        } finally {
            try {
                vimPort.destroyView(containerView)
            } catch (e: Exception) {
                logger.warn { "Failed to destroy ContainerView" }
            }
        }
    }

    private suspend fun getProperty(session: VsphereSession, obj: ManagedObjectReference, prop: String): Any? = withContext(Dispatchers.IO) {
        val vimPort = session.vimPort
        val serviceContent = session.serviceContent
        
        val propSpec = PropertySpec().apply {
            this.type = obj.type
            this.pathSet.add(prop)
        }
        
        val objSpec = ObjectSpec().apply {
            this.obj = obj
            this.isSkip = false
        }
        
        val filterSpec = PropertyFilterSpec().apply {
            this.propSet.add(propSpec)
            this.objectSet.add(objSpec)
        }
        
        val result = vimPort.retrievePropertiesEx(serviceContent.propertyCollector, listOf(filterSpec), RetrieveOptions())
        
        result?.objects?.firstOrNull()?.propSet?.firstOrNull { it.name == prop }?.`val`
    }

    private suspend fun waitForTask(
        session: VsphereSession,
        task: ManagedObjectReference,
        timeoutMs: Long = 300_000 // 5 minutes default timeout
    ) = withContext(Dispatchers.IO) {
        val vimPort = session.vimPort
        val serviceContent = session.serviceContent
        val startTime = System.currentTimeMillis()

        val infoSpec = PropertySpec().apply {
            this.type = "Task"
            this.pathSet.add("info.state")
            this.pathSet.add("info.error")
        }
        val filterSpec = PropertyFilterSpec().apply {
            this.propSet.add(infoSpec)
            this.objectSet.add(ObjectSpec().apply { this.obj = task })
        }

        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > timeoutMs) {
                throw RuntimeException("Task timed out after ${elapsed}ms (limit: ${timeoutMs}ms)")
            }

            val result = vimPort.retrievePropertiesEx(serviceContent.propertyCollector, listOf(filterSpec), RetrieveOptions())
            val props = result?.objects?.firstOrNull()?.propSet?.associate { it.name to it.`val` }

            val state = props?.get("info.state") as? TaskInfoState
            val error = props?.get("info.error")

            if (state == TaskInfoState.SUCCESS) return@withContext
            if (state == TaskInfoState.ERROR) {
                throw RuntimeException("Task failed: $error")
            }

            delay(500)
        }
    }
    
    private suspend fun getTaskInfo(session: VsphereSession, task: ManagedObjectReference): TaskInfo = withContext(Dispatchers.IO) {
        val result = getProperty(session, task, "info") as? TaskInfo
            ?: throw RuntimeException("Failed to get task info")
        result
    }

    public suspend fun listDatacenters(): Result<List<Datacenter>, VsphereError> = executeResilient("listDatacenters") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }

        runCatching {
            retrieveObjects(session, "Datacenter", listOf("name")) { obj, props ->
                Datacenter(obj.value, props["name"] as? String ?: "Unknown")
            }
        }.fold(
            onSuccess = { it.success() },
            onFailure = { e ->
                logger.error(e) { "Failed to list datacenters: ${e.message}" }
                VsphereError.ApiError("Failed to list datacenters", e).failure()
            }
        )
    }

    public suspend fun listClusters(datacenter: Datacenter): Result<List<Cluster>, VsphereError> = executeResilient("listClusters") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }
        runCatching {
            retrieveObjects(session, "ClusterComputeResource", listOf("name"), moRef("Datacenter", datacenter.id)) { obj, props ->
                Cluster(obj.value, props["name"] as? String ?: "Unknown")
            }
        }.fold(
            onSuccess = { it.success() },
            onFailure = { VsphereError.ApiError("Failed to list clusters", it).failure() }
        )
    }

    public suspend fun listNetworks(datacenter: Datacenter): Result<List<Network>, VsphereError> = executeResilient("listNetworks") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }
        runCatching {
            retrieveObjects(session, "Network", listOf("name"), moRef("Datacenter", datacenter.id)) { obj, props ->
                Network(obj.value, props["name"] as? String ?: "Unknown")
            }
        }.fold(
            onSuccess = { it.success() },
            onFailure = { VsphereError.ApiError("Failed to list networks", it).failure() }
        )
    }

    public suspend fun listResourcePools(cluster: Cluster): Result<List<ResourcePool>, VsphereError> = executeResilient("listResourcePools") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }
        runCatching {
            retrieveObjects(session, "ResourcePool", listOf("name"), moRef("ClusterComputeResource", cluster.id)) { obj, props ->
                ResourcePool(obj.value, props["name"] as? String ?: "Unknown")
            }
        }.fold(
            onSuccess = { it.success() },
            onFailure = { VsphereError.ApiError("Failed to list resource pools", it).failure() }
        )
    }

    public suspend fun listDatastores(cluster: Cluster): Result<List<Datastore>, VsphereError> = executeResilient("listDatastores") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }
        runCatching {
            withContext(Dispatchers.IO) {
                val vimPort = session.vimPort
                val serviceContent = session.serviceContent
                
                val clusterRef = moRef("ClusterComputeResource", cluster.id)
                val datastores = getProperty(session, clusterRef, "datastore") as? List<ManagedObjectReference>
                    ?: emptyList()
                    
                if (datastores.isEmpty()) return@withContext emptyList<Datastore>()
                
                val dsPropSpec = PropertySpec().apply { type = "Datastore"; pathSet.add("name") }
                val dsObjSpecs = datastores.map { dsRef -> ObjectSpec().apply { obj = dsRef; isSkip = false } }
                val dsFilterSpec = PropertyFilterSpec().apply { propSet.add(dsPropSpec); objectSet.addAll(dsObjSpecs) }
                
                val dsResults = vimPort.retrievePropertiesEx(serviceContent.propertyCollector, listOf(dsFilterSpec), RetrieveOptions())
                
                val dsList = mutableListOf<Datastore>()
                dsResults?.objects?.forEach { objContent ->
                    val name = objContent.propSet.firstOrNull { it.name == "name" }?.`val` as? String ?: "Unknown"
                    dsList.add(Datastore(objContent.obj.value, name))
                }
                
                var token = dsResults?.token
                while (token != null) {
                    val next = vimPort.continueRetrievePropertiesEx(serviceContent.propertyCollector, token)
                    if (next != null) {
                        token = next.token
                        next.objects.forEach { objContent ->
                            val name = objContent.propSet.firstOrNull { it.name == "name" }?.`val` as? String ?: "Unknown"
                            dsList.add(Datastore(objContent.obj.value, name))
                        }
                    } else {
                        token = null
                    }
                }
                dsList
            }
        }.fold(
            onSuccess = { it.success() },
            onFailure = { VsphereError.ApiError("Failed to list datastores", it).failure() }
        )
    }

    /**
     * VMware Tools IP detection timeout in milliseconds.
     * After cloning and power-on, we wait for VMware Tools to report the IP address.
     * Must be less than createVmTimeoutMs to avoid outer timeout interruption.
     */
    private val vmwareToolsTimeoutMs: Long = 120_000 // 2 minutes

    /**
     * Total timeout for VM creation including clone + IP detection.
     * Must be greater than inner timeouts: waitForTask (5 min max) + IP detection (2 min).
     * Using 8 minutes to allow buffer for both operations plus safety margin.
     */
    private val createVmTimeoutMs: Long = 480_000 // 8 minutes

    public suspend fun createVm(
        spec: VmSpec,
        onProgress: suspend (VmProvisioningStage) -> Unit = {}
    ): Result<VmProvisioningResult, VsphereError> =
        executeResilient(name = "createVm", operationTimeoutMs = createVmTimeoutMs) {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ProvisioningError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient VsphereError.ProvisioningError("Connection failed", sessionResult.error).failure()
        }

        val config = configPort.findByTenantId(tenantId) ?: return@executeResilient VsphereError.ProvisioningError("Configuration not found").failure()

        withContext(Dispatchers.IO) {
            try {
                val vimPort = session.vimPort
                val searchIndex = session.serviceContent.searchIndex

                val dcPath = config.datacenterName
                val templateName = spec.template.ifBlank { config.templateName }
                val templatePath = "$dcPath/vm/$templateName"

                val templateRef = vimPort.findByInventoryPath(searchIndex, templatePath)
                    ?: return@withContext VsphereError.ProvisioningError("Template not found: $templatePath").failure()

                val dsPath = "$dcPath/datastore/${config.datastoreName}"
                val dsRef = vimPort.findByInventoryPath(searchIndex, dsPath)
                    ?: return@withContext VsphereError.ProvisioningError("Datastore not found: ${config.datastoreName}").failure()

                val clusterPath = "$dcPath/host/${config.clusterName}"
                val clusterRef = vimPort.findByInventoryPath(searchIndex, clusterPath)
                    ?: return@withContext VsphereError.ProvisioningError("Cluster not found: ${config.clusterName}").failure()

                val rpRef = getProperty(session, clusterRef, "resourcePool") as? ManagedObjectReference
                    ?: return@withContext VsphereError.ProvisioningError("Cluster has no resource pool").failure()

                val relocateSpec = VirtualMachineRelocateSpec().apply {
                    this.datastore = dsRef
                    this.pool = rpRef
                }

                val configSpec = VirtualMachineConfigSpec().apply {
                    this.numCPUs = spec.cpu
                    this.memoryMB = (spec.memoryGb * 1024).toLong()
                }

                val cloneSpec = VirtualMachineCloneSpec().apply {
                    this.location = relocateSpec
                    this.config = configSpec
                    this.isPowerOn = true
                    this.isTemplate = false
                }

                val folderRef = vimPort.findByInventoryPath(searchIndex, "$dcPath/vm")
                    ?: return@withContext VsphereError.ProvisioningError("VM folder not found").failure()

                logger.info { "Cloning VM '${spec.name}' from template '$templateName'" }
                onProgress(VmProvisioningStage.CLONING)
                val task = vimPort.cloneVMTask(templateRef, folderRef, spec.name, cloneSpec)
                waitForTask(session, task)

                val info = getTaskInfo(session, task)
                val vmRef = info.result as ManagedObjectReference
                val vmwareVmId = VmwareVmId.of(vmRef.value)
                logger.info { "Clone task completed, VM created: ${vmRef.value}" }

                onProgress(VmProvisioningStage.CONFIGURING)
                onProgress(VmProvisioningStage.POWERING_ON)
                onProgress(VmProvisioningStage.WAITING_FOR_NETWORK)

                // Wait for VMware Tools to report IP address
                val ipDetectionResult = waitForVmwareToolsIp(session, vmRef)

                // Signal provisioning complete (matches VcsimAdapter contract)
                onProgress(VmProvisioningStage.READY)

                VmProvisioningResult(
                    vmwareVmId = vmwareVmId,
                    ipAddress = ipDetectionResult.ipAddress,
                    hostname = spec.name,
                    warningMessage = ipDetectionResult.warningMessage
                ).success()

            } catch (e: CancellationException) {
                throw e  // Allow proper coroutine cancellation
            } catch (e: Exception) {
                logger.error(e) { "Clone failed: ${e.message}" }
                VsphereError.ProvisioningError("Clone failed", e).failure()
            }
        }
    }

    /**
     * Wait for VMware Tools to report the VM's IP address.
     *
     * Returns the IP address if detected within timeout, or null with a warning message.
     * If the coroutine is cancelled, this throws CancellationException to allow proper
     * structured concurrency - callers should handle this appropriately.
     */
    private suspend fun waitForVmwareToolsIp(
        session: VsphereSession,
        vmRef: ManagedObjectReference
    ): IpDetectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > vmwareToolsTimeoutMs) {
                logger.warn { "VMware Tools timeout after ${elapsed}ms waiting for IP on ${vmRef.value}" }
                return@withContext IpDetectionResult(
                    ipAddress = null,
                    warningMessage = "VMware Tools timeout - IP detection pending"
                )
            }

            // Check guest.ipAddress via PropertyCollector
            val ipAddress = getProperty(session, vmRef, "guest.ipAddress") as? String
            if (!ipAddress.isNullOrBlank()) {
                logger.info { "IP address detected for ${vmRef.value}: $ipAddress" }
                return@withContext IpDetectionResult(ipAddress = ipAddress, warningMessage = null)
            }

            // Also check if VMware Tools is running
            val toolsStatus = getProperty(session, vmRef, "guest.toolsRunningStatus") as? String
            logger.debug { "Waiting for VMware Tools IP on ${vmRef.value}, toolsStatus=$toolsStatus, elapsed=${elapsed}ms" }

            delay(2000) // Poll every 2 seconds
        }

        // If we get here, the coroutine was cancelled - throw to propagate cancellation properly
        throw kotlinx.coroutines.CancellationException("IP detection cancelled for ${vmRef.value}")
    }

    private data class IpDetectionResult(
        val ipAddress: String?,
        val warningMessage: String?
    )

    public suspend fun getVm(vmId: VmId): Result<VmInfo, VsphereError> = executeResilient("getVm") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.ConnectionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient sessionResult.error.failure()
        }

        withContext(Dispatchers.IO) {
            try {
                val vmRef = moRef("VirtualMachine", vmId.value)
                val name = getProperty(session, vmRef, "name") as? String ?: throw RuntimeException("Name not found")
                VmInfo(vmId.value, name).success()
            } catch (e: Exception) {
                VsphereError.NotFound("VM not found: ${vmId.value}").failure()
            }
        }
    }

    public suspend fun deleteVm(vmId: VmId): Result<Unit, VsphereError> = executeResilient("deleteVm") {
        val tenantId = try { TenantContext.current() } catch (e: Exception) { return@executeResilient VsphereError.DeletionError("No tenant context", e).failure() }
        val sessionResult = ensureSession(tenantId)
        val session = when (sessionResult) {
            is Result.Success -> sessionResult.value
            is Result.Failure -> return@executeResilient VsphereError.DeletionError("Connection failed", sessionResult.error).failure()
        }

        withContext(Dispatchers.IO) {
            try {
                val vmRef = moRef("VirtualMachine", vmId.value)
                val task = session.vimPort.destroyTask(vmRef)
                waitForTask(session, task)
                Unit.success()
            } catch (e: Exception) {
                VsphereError.DeletionError("Delete failed", e).failure()
            }
        }
    }
}