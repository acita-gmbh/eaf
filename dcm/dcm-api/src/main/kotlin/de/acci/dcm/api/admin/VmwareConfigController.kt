package de.acci.dcm.api.admin

import de.acci.dcm.application.vmware.CheckVmwareConfigExistsHandler
import de.acci.dcm.application.vmware.CheckVmwareConfigExistsQuery
import de.acci.dcm.application.vmware.CreateVmwareConfigCommand
import de.acci.dcm.application.vmware.CreateVmwareConfigError
import de.acci.dcm.application.vmware.CreateVmwareConfigHandler
import de.acci.dcm.application.vmware.GetVmwareConfigError
import de.acci.dcm.application.vmware.GetVmwareConfigHandler
import de.acci.dcm.application.vmware.GetVmwareConfigQuery
import de.acci.dcm.application.vmware.TestVmwareConnectionCommand
import de.acci.dcm.application.vmware.TestVmwareConnectionError
import de.acci.dcm.application.vmware.TestVmwareConnectionHandler
import de.acci.dcm.application.vmware.UpdateVmwareConfigCommand
import de.acci.dcm.application.vmware.UpdateVmwareConfigError
import de.acci.dcm.application.vmware.UpdateVmwareConfigHandler
import de.acci.dcm.domain.vmware.VmwareConfiguration
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContext
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * REST controller for VMware vCenter configuration management.
 *
 * Story 3.1: VMware Connection Configuration
 *
 * All endpoints require ADMIN role via @PreAuthorize.
 * Tenant isolation is handled by PostgreSQL RLS.
 *
 * ## Endpoints
 *
 * - `GET /api/admin/vmware-config` - Get current configuration (password excluded)
 * - `PUT /api/admin/vmware-config` - Create or update configuration
 * - `POST /api/admin/vmware-config/test` - Test connection to vCenter
 * - `GET /api/admin/vmware-config/exists` - Check if configuration exists (lightweight)
 *
 * ## Error Handling
 *
 * - **401 Unauthorized**: Missing or invalid JWT
 * - **403 Forbidden**: User does not have admin role
 * - **404 Not Found**: Configuration not found
 * - **409 Conflict**: Concurrent modification (optimistic locking)
 * - **422 Unprocessable Entity**: Validation or connection test failure
 * - **500 Internal Server Error**: Database or encryption failure
 */
@RestController
@RequestMapping("/api/admin/vmware-config")
@PreAuthorize("hasRole('admin')")
public class VmwareConfigController(
    private val getVmwareConfigHandler: GetVmwareConfigHandler,
    private val createVmwareConfigHandler: CreateVmwareConfigHandler,
    private val updateVmwareConfigHandler: UpdateVmwareConfigHandler,
    private val testVmwareConnectionHandler: TestVmwareConnectionHandler,
    private val checkVmwareConfigExistsHandler: CheckVmwareConfigExistsHandler
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get current VMware configuration for the tenant.
     *
     * AC-3.1.1: Returns all configuration fields except the password.
     * Password is indicated as set via `hasPassword: true` but never exposed.
     *
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with configuration, or 404 if not configured
     */
    @GetMapping
    public suspend fun getConfiguration(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val adminId = jwt.subject

        logger.debug {
            "Admin fetching VMware configuration: " +
                "adminId=$adminId, " +
                "tenantId=${tenantId.value}"
        }

        val query = GetVmwareConfigQuery(tenantId = tenantId)

        return when (val result = getVmwareConfigHandler.handle(query)) {
            is Result.Success -> {
                ResponseEntity.ok(VmwareConfigApiResponse.fromDomain(result.value))
            }
            is Result.Failure -> handleGetConfigError(result.error, tenantId)
        }
    }

    private fun handleGetConfigError(error: GetVmwareConfigError, tenantId: TenantId): ResponseEntity<Any> {
        return when (error) {
            is GetVmwareConfigError.NotFound -> {
                logger.debug { "VMware configuration not found for tenant: tenantId=${tenantId.value}" }
                ResponseEntity.notFound().build()
            }
            is GetVmwareConfigError.Forbidden -> {
                // SECURITY: Return 404 to prevent tenant enumeration
                logger.warn { "Forbidden access to VMware configuration: tenantId=${tenantId.value}" }
                ResponseEntity.notFound().build()
            }
            is GetVmwareConfigError.QueryFailure -> {
                logger.error { "Failed to retrieve VMware configuration: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to retrieve configuration"
                    ).apply {
                        type = URI("/errors/internal-error")
                    }
                )
            }
        }
    }

    /**
     * Create or update VMware configuration.
     *
     * AC-3.1.1: Tenant admin can save vCenter settings.
     * AC-3.1.4: Password encrypted before storage.
     *
     * ## Create vs Update
     *
     * - `version: null` → Create new configuration
     * - `version: <number>` → Update existing with optimistic locking
     *
     * @param body Configuration data
     * @param jwt The authenticated admin's JWT
     * @return 200 OK on success, or appropriate error response
     */
    @PutMapping
    public suspend fun saveConfiguration(
        @Valid @RequestBody body: SaveVmwareConfigRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        logger.info {
            "Admin saving VMware configuration: " +
                "adminId=${userId.value}, " +
                "tenantId=${tenantId.value}, " +
                "vcenterUrl=${body.vcenterUrl}, " +
                "isUpdate=${body.version != null}"
        }

        return if (body.version == null) {
            // Create new configuration
            if (body.password.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Password is required when creating configuration"
                    ).apply {
                        type = URI("/errors/validation-error")
                    }
                )
            }

            val command = CreateVmwareConfigCommand(
                tenantId = tenantId,
                userId = userId,
                vcenterUrl = body.vcenterUrl,
                username = body.username,
                password = body.password,
                datacenterName = body.datacenterName,
                clusterName = body.clusterName,
                datastoreName = body.datastoreName,
                networkName = body.networkName,
                templateName = body.templateName ?: VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
                folderPath = body.folderPath
            )

            when (val result = createVmwareConfigHandler.handle(command)) {
                is Result.Success -> {
                    ResponseEntity.status(HttpStatus.CREATED).body(
                        SaveVmwareConfigApiResponse(
                            id = result.value.configurationId.value.toString(),
                            version = 1L,
                            message = "VMware configuration created successfully"
                        )
                    )
                }
                is Result.Failure -> handleCreateConfigError(result.error)
            }
        } else {
            // Update existing configuration
            val command = UpdateVmwareConfigCommand(
                tenantId = tenantId,
                userId = userId,
                expectedVersion = body.version,
                vcenterUrl = body.vcenterUrl,
                username = body.username,
                password = body.password, // Null = keep existing
                datacenterName = body.datacenterName,
                clusterName = body.clusterName,
                datastoreName = body.datastoreName,
                networkName = body.networkName,
                templateName = body.templateName,
                folderPath = body.folderPath,
                clearFolderPath = body.clearFolderPath
            )

            when (val result = updateVmwareConfigHandler.handle(command)) {
                is Result.Success -> {
                    ResponseEntity.ok(
                        SaveVmwareConfigApiResponse(
                            id = "", // Not returned by update - client already knows ID
                            version = result.value.newVersion,
                            message = "VMware configuration updated successfully"
                        )
                    )
                }
                is Result.Failure -> handleUpdateConfigError(result.error)
            }
        }
    }

    private fun handleCreateConfigError(error: CreateVmwareConfigError): ResponseEntity<Any> {
        return when (error) {
            is CreateVmwareConfigError.ConfigurationAlreadyExists -> {
                logger.info { "VMware configuration already exists for tenant" }
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "VMware configuration already exists. Use update (provide version) instead."
                    ).apply {
                        type = URI("/errors/already-exists")
                    }
                )
            }
            is CreateVmwareConfigError.EncryptionFailed -> {
                logger.error { "Password encryption failed: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to secure credentials"
                    ).apply {
                        type = URI("/errors/encryption-error")
                    }
                )
            }
            is CreateVmwareConfigError.PersistenceFailure -> {
                logger.error { "Failed to save VMware configuration: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to save configuration"
                    ).apply {
                        type = URI("/errors/internal-error")
                    }
                )
            }
        }
    }

    private fun handleUpdateConfigError(error: UpdateVmwareConfigError): ResponseEntity<Any> {
        return when (error) {
            is UpdateVmwareConfigError.NotFound -> {
                logger.info { "VMware configuration not found for update" }
                ResponseEntity.notFound().build()
            }
            is UpdateVmwareConfigError.ConcurrencyConflict -> {
                logger.info {
                    "Concurrency conflict: expected version ${error.expectedVersion}, " +
                        "actual ${error.actualVersion}"
                }
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "Configuration was modified by another admin. Please refresh and retry."
                    ).apply {
                        type = URI("/errors/conflict")
                    }
                )
            }
            is UpdateVmwareConfigError.EncryptionFailed -> {
                logger.error { "Password encryption failed: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to secure credentials"
                    ).apply {
                        type = URI("/errors/encryption-error")
                    }
                )
            }
            is UpdateVmwareConfigError.PersistenceFailure -> {
                logger.error { "Failed to update VMware configuration: ${error.message}" }
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to save configuration"
                    ).apply {
                        type = URI("/errors/internal-error")
                    }
                )
            }
        }
    }

    /**
     * Test connection to vCenter with provided credentials.
     *
     * AC-3.1.2: "Test Connection" button validates connectivity.
     * AC-3.1.3: Real-time feedback on validation errors.
     *
     * Validates:
     * 1. Network connectivity to vCenter URL
     * 2. SSL certificate (if HTTPS)
     * 3. Authentication with credentials
     * 4. Existence of datacenter, cluster, datastore, network
     *
     * @param body Connection test parameters
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with connection info, or 422 with error details
     */
    @PostMapping("/test")
    public suspend fun testConnection(
        @Valid @RequestBody body: TestVmwareConnectionRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Any> {
        val tenantId = TenantContext.current()
        val userId = UserId.fromString(jwt.subject)

        logger.info {
            "Admin testing VMware connection: " +
                "adminId=${userId.value}, " +
                "tenantId=${tenantId.value}, " +
                "vcenterUrl=${body.vcenterUrl}"
        }

        val command = TestVmwareConnectionCommand(
            tenantId = tenantId,
            userId = userId,
            vcenterUrl = body.vcenterUrl,
            username = body.username,
            password = body.password,
            datacenterName = body.datacenterName,
            clusterName = body.clusterName,
            datastoreName = body.datastoreName,
            networkName = body.networkName,
            templateName = body.templateName ?: VmwareConfiguration.DEFAULT_TEMPLATE_NAME,
            updateVerifiedAt = body.updateVerifiedAt
        )

        return when (val result = testVmwareConnectionHandler.handle(command)) {
            is Result.Success -> {
                val info = result.value
                ResponseEntity.ok(
                    TestVmwareConnectionApiResponse(
                        success = true,
                        vcenterVersion = info.vcenterVersion,
                        clusterName = info.clusterName,
                        clusterHosts = info.clusterHosts,
                        datastoreFreeGb = info.datastoreFreeGb,
                        message = info.message,
                        verifiedAtUpdated = info.verifiedAtUpdated
                    )
                )
            }
            is Result.Failure -> handleTestConnectionError(result.error)
        }
    }

    private fun handleTestConnectionError(error: TestVmwareConnectionError): ResponseEntity<Any> {
        val (errorType, message) = when (error) {
            is TestVmwareConnectionError.ConnectionRefused -> {
                "CONNECTION_REFUSED" to "Connection refused: ${error.message}"
            }
            is TestVmwareConnectionError.SslCertificateError -> {
                "SSL_ERROR" to "SSL certificate error: ${error.message}"
            }
            is TestVmwareConnectionError.AuthenticationFailed -> {
                "AUTH_FAILED" to "Authentication failed: ${error.message}"
            }
            is TestVmwareConnectionError.DatacenterNotFound -> {
                "DATACENTER_NOT_FOUND" to "Datacenter not found: ${error.datacenterName}"
            }
            is TestVmwareConnectionError.ClusterNotFound -> {
                "CLUSTER_NOT_FOUND" to "Cluster not found: ${error.clusterName}"
            }
            is TestVmwareConnectionError.DatastoreNotFound -> {
                "DATASTORE_NOT_FOUND" to "Datastore not found: ${error.datastoreName}"
            }
            is TestVmwareConnectionError.NetworkNotFound -> {
                "NETWORK_NOT_FOUND" to "Network not found: ${error.networkName}"
            }
            is TestVmwareConnectionError.TemplateNotFound -> {
                "TEMPLATE_NOT_FOUND" to "Template not found: ${error.templateName}"
            }
            is TestVmwareConnectionError.ApiError -> {
                "API_ERROR" to "vCenter API error: ${error.message}"
            }
            is TestVmwareConnectionError.PasswordRequired -> {
                "PASSWORD_REQUIRED" to error.message
            }
            is TestVmwareConnectionError.DecryptionFailed -> {
                "DECRYPTION_FAILED" to "Failed to decrypt stored password: ${error.message}"
            }
        }

        logger.warn { "VMware connection test failed: $errorType - $message" }

        return ResponseEntity.unprocessableEntity().body(
            mapOf(
                "success" to false,
                "error" to errorType,
                "message" to message
            )
        )
    }

    /**
     * Check if VMware configuration exists for the tenant.
     *
     * AC-3.1.5: Lightweight check for "VMware not configured" warning.
     * Returns only boolean existence status, not full configuration.
     *
     * @param jwt The authenticated admin's JWT
     * @return 200 OK with existence status
     */
    @GetMapping("/exists")
    public suspend fun checkConfigExists(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<VmwareConfigExistsResponse> {
        val tenantId = TenantContext.current()

        logger.debug {
            "Checking VMware configuration existence: " +
                "tenantId=${tenantId.value}"
        }

        val query = CheckVmwareConfigExistsQuery(tenantId = tenantId)
        val exists = checkVmwareConfigExistsHandler.handle(query)

        // If config exists, also get verifiedAt for richer response
        val verifiedAt = if (exists) {
            try {
                val config = getVmwareConfigHandler.handle(GetVmwareConfigQuery(tenantId))
                (config as? Result.Success)?.value?.verifiedAt
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Failed to fetch VMware config verifiedAt for tenant=${tenantId.value}" }
                null
            }
        } else {
            null
        }

        return ResponseEntity.ok(
            VmwareConfigExistsResponse(
                exists = exists,
                verifiedAt = verifiedAt
            )
        )
    }
}
