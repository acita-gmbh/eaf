package de.acci.dcm.api.admin

import de.acci.dcm.application.vmware.CheckVmwareConfigExistsHandler
import de.acci.dcm.application.vmware.CheckVmwareConfigExistsQuery
import de.acci.dcm.application.vmware.CreateVmwareConfigError
import de.acci.dcm.application.vmware.CreateVmwareConfigHandler
import de.acci.dcm.application.vmware.CreateVmwareConfigResult
import de.acci.dcm.application.vmware.GetVmwareConfigError
import de.acci.dcm.application.vmware.GetVmwareConfigHandler
import de.acci.dcm.application.vmware.GetVmwareConfigQuery
import de.acci.dcm.application.vmware.TestVmwareConnectionError
import de.acci.dcm.application.vmware.TestVmwareConnectionHandler
import de.acci.dcm.application.vmware.TestVmwareConnectionResult
import de.acci.dcm.application.vmware.UpdateVmwareConfigError
import de.acci.dcm.application.vmware.UpdateVmwareConfigHandler
import de.acci.dcm.application.vmware.UpdateVmwareConfigResult
import de.acci.dcm.application.vmware.VmwareConfigResponse
import de.acci.dcm.domain.vmware.VmwareConfigurationId
import de.acci.eaf.core.result.Result
import de.acci.eaf.core.result.failure
import de.acci.eaf.core.result.success
import de.acci.eaf.core.types.TenantId
import de.acci.eaf.core.types.UserId
import de.acci.eaf.tenant.TenantContextElement
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.oauth2.jwt.Jwt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Instant

/**
 * Unit tests for VmwareConfigController.
 *
 * Tests all endpoints for VMware configuration management:
 * - GET /api/admin/vmware-config
 * - PUT /api/admin/vmware-config
 * - POST /api/admin/vmware-config/test
 * - GET /api/admin/vmware-config/exists
 */
class VmwareConfigControllerTest {

    private lateinit var getVmwareConfigHandler: GetVmwareConfigHandler
    private lateinit var createVmwareConfigHandler: CreateVmwareConfigHandler
    private lateinit var updateVmwareConfigHandler: UpdateVmwareConfigHandler
    private lateinit var testVmwareConnectionHandler: TestVmwareConnectionHandler
    private lateinit var checkVmwareConfigExistsHandler: CheckVmwareConfigExistsHandler
    private lateinit var controller: VmwareConfigController

    private val testTenantId = TenantId.generate()
    private val testUserId = UserId.generate()
    private val testConfigId = VmwareConfigurationId.generate()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        getVmwareConfigHandler = mockk()
        createVmwareConfigHandler = mockk()
        updateVmwareConfigHandler = mockk()
        testVmwareConnectionHandler = mockk()
        checkVmwareConfigExistsHandler = mockk()

        controller = VmwareConfigController(
            getVmwareConfigHandler = getVmwareConfigHandler,
            createVmwareConfigHandler = createVmwareConfigHandler,
            updateVmwareConfigHandler = updateVmwareConfigHandler,
            testVmwareConnectionHandler = testVmwareConnectionHandler,
            checkVmwareConfigExistsHandler = checkVmwareConfigExistsHandler
        )
    }

    private fun createJwt(subject: String = testUserId.value.toString()): Jwt {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("tenant_id", testTenantId.value.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private suspend fun <T> withTenant(block: suspend () -> T): T {
        return withContext(TenantContextElement(testTenantId)) {
            block()
        }
    }

    private fun createMockConfigResponse(): VmwareConfigResponse {
        val now = Instant.now()
        return VmwareConfigResponse(
            id = testConfigId,
            tenantId = testTenantId,
            vcenterUrl = "https://vcenter.example.com/sdk",
            username = "admin@vsphere.local",
            datacenterName = "DC1",
            clusterName = "Cluster1",
            datastoreName = "Datastore1",
            networkName = "VM Network",
            templateName = "ubuntu-22.04-template",
            folderPath = "/VMs/DCM",
            verifiedAt = now,
            createdAt = now,
            updatedAt = now,
            createdBy = testUserId,
            updatedBy = testUserId,
            version = 1L
        )
    }

    @Nested
    inner class GetConfiguration {

        @Test
        fun `returns 200 OK with configuration on success`() = runTest {
            // Given
            val configResponse = createMockConfigResponse()
            coEvery { getVmwareConfigHandler.handle(any()) } returns configResponse.success()

            // When
            val response = withTenant {
                controller.getConfiguration(createJwt())
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as VmwareConfigApiResponse
            assertEquals("https://vcenter.example.com/sdk", body.vcenterUrl)
            assertEquals("admin@vsphere.local", body.username)
            assertEquals("DC1", body.datacenterName)
            assertTrue(body.hasPassword)
            assertEquals(1L, body.version)

            coVerify {
                getVmwareConfigHandler.handle(match<GetVmwareConfigQuery> { it.tenantId == testTenantId })
            }
        }

        @Test
        fun `returns 404 Not Found when configuration does not exist`() = runTest {
            // Given
            coEvery { getVmwareConfigHandler.handle(any()) } returns
                GetVmwareConfigError.NotFound(testTenantId).failure()

            // When
            val response = withTenant {
                controller.getConfiguration(createJwt())
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `returns 404 Not Found for Forbidden error to prevent tenant enumeration`() = runTest {
            // Given - SECURITY: Forbidden should return 404 to prevent enumeration attacks
            coEvery { getVmwareConfigHandler.handle(any()) } returns
                GetVmwareConfigError.Forbidden().failure()

            // When
            val response = withTenant {
                controller.getConfiguration(createJwt())
            }

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `returns 500 Internal Server Error on query failure`() = runTest {
            // Given
            coEvery { getVmwareConfigHandler.handle(any()) } returns
                GetVmwareConfigError.QueryFailure("Database error").failure()

            // When
            val response = withTenant {
                controller.getConfiguration(createJwt())
            }

            // Then
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            val problemDetail = response.body as ProblemDetail
            assertEquals("Failed to retrieve configuration", problemDetail.detail)
        }
    }

    @Nested
    inner class SaveConfiguration {

        @Nested
        inner class CreateConfiguration {

            @Test
            fun `returns 201 Created on successful create`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = "secret123",
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = null // null = create
                )
                coEvery { createVmwareConfigHandler.handle(any()) } returns
                    CreateVmwareConfigResult(configurationId = testConfigId).success()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.CREATED, response.statusCode)
                val body = response.body as SaveVmwareConfigApiResponse
                assertEquals(testConfigId.value.toString(), body.id)
                assertEquals(1L, body.version)
                assertEquals("VMware configuration created successfully", body.message)
            }

            @Test
            fun `returns 400 Bad Request when password is missing for create`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = null, // Missing password for create
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = null // create
                )

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
                val problemDetail = response.body as ProblemDetail
                assertEquals("Password is required when creating configuration", problemDetail.detail)
            }

            @Test
            fun `returns 409 Conflict when configuration already exists`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = "secret123",
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = null
                )
                coEvery { createVmwareConfigHandler.handle(any()) } returns
                    CreateVmwareConfigError.ConfigurationAlreadyExists(testTenantId).failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.CONFLICT, response.statusCode)
            }

            @Test
            fun `returns 500 on encryption failure`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = "secret123",
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = null
                )
                coEvery { createVmwareConfigHandler.handle(any()) } returns
                    CreateVmwareConfigError.EncryptionFailed("Key not available").failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
                val problemDetail = response.body as ProblemDetail
                assertEquals("Failed to secure credentials", problemDetail.detail)
            }

            @Test
            fun `returns 500 on persistence failure`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = "secret123",
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = null
                )
                coEvery { createVmwareConfigHandler.handle(any()) } returns
                    CreateVmwareConfigError.PersistenceFailure("DB connection lost").failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            }
        }

        @Nested
        inner class UpdateConfiguration {

            @Test
            fun `returns 200 OK on successful update`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = null, // Keep existing password
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = 1L // Update existing
                )
                coEvery { updateVmwareConfigHandler.handle(any()) } returns
                    UpdateVmwareConfigResult(newVersion = 2L).success()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.OK, response.statusCode)
                val body = response.body as SaveVmwareConfigApiResponse
                assertEquals(2L, body.version)
                assertEquals("VMware configuration updated successfully", body.message)
            }

            @Test
            fun `returns 404 Not Found when configuration does not exist for update`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = null,
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = 1L
                )
                coEvery { updateVmwareConfigHandler.handle(any()) } returns
                    UpdateVmwareConfigError.NotFound(testTenantId).failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            }

            @Test
            fun `returns 409 Conflict on concurrency conflict`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = null,
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = 1L
                )
                coEvery { updateVmwareConfigHandler.handle(any()) } returns
                    UpdateVmwareConfigError.ConcurrencyConflict(
                        expectedVersion = 1L,
                        actualVersion = 2L
                    ).failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.CONFLICT, response.statusCode)
                val problemDetail = response.body as ProblemDetail
                assertTrue(problemDetail.detail!!.contains("modified by another admin"))
            }

            @Test
            fun `returns 500 on encryption failure during update`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = "new-password", // Changing password
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = 1L
                )
                coEvery { updateVmwareConfigHandler.handle(any()) } returns
                    UpdateVmwareConfigError.EncryptionFailed("Key rotation in progress").failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            }

            @Test
            fun `returns 500 on persistence failure during update`() = runTest {
                // Given
                val request = SaveVmwareConfigRequest(
                    vcenterUrl = "https://vcenter.example.com/sdk",
                    username = "admin@vsphere.local",
                    password = null,
                    datacenterName = "DC1",
                    clusterName = "Cluster1",
                    datastoreName = "Datastore1",
                    networkName = "VM Network",
                    version = 1L
                )
                coEvery { updateVmwareConfigHandler.handle(any()) } returns
                    UpdateVmwareConfigError.PersistenceFailure("Write failed").failure()

                // When
                val response = withTenant {
                    controller.saveConfiguration(request, createJwt())
                }

                // Then
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            }
        }
    }

    @Nested
    inner class TestConnection {

        @Test
        fun `returns 200 OK with connection info on success`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionResult(
                    vcenterVersion = "8.0.1",
                    clusterName = "Cluster1",
                    clusterHosts = 5,
                    datastoreFreeGb = 1000,
                    message = "Connected successfully"
                ).success()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as TestVmwareConnectionApiResponse
            assertTrue(body.success)
            assertEquals("8.0.1", body.vcenterVersion)
            assertEquals("Cluster1", body.clusterName)
            assertEquals(5, body.clusterHosts)
            assertEquals(1000, body.datastoreFreeGb)
        }

        @Test
        fun `returns 422 Unprocessable Entity on connection refused`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.ConnectionRefused("Connection timed out").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals(false, body["success"])
            assertEquals("CONNECTION_REFUSED", body["error"])
        }

        @Test
        fun `returns 422 on SSL certificate error`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.SslCertificateError("Certificate expired").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("SSL_ERROR", body["error"])
        }

        @Test
        fun `returns 422 on authentication failed`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "wrong-password",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.AuthenticationFailed().failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("AUTH_FAILED", body["error"])
        }

        @Test
        fun `returns 422 on datacenter not found`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "NonExistentDC",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.DatacenterNotFound("NonExistentDC").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("DATACENTER_NOT_FOUND", body["error"])
        }

        @Test
        fun `returns 422 on cluster not found`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "NonExistentCluster",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.ClusterNotFound("NonExistentCluster").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("CLUSTER_NOT_FOUND", body["error"])
        }

        @Test
        fun `returns 422 on datastore not found`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "NonExistentDS",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.DatastoreNotFound("NonExistentDS").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("DATASTORE_NOT_FOUND", body["error"])
        }

        @Test
        fun `returns 422 on network not found`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "NonExistentNetwork"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.NetworkNotFound("NonExistentNetwork").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("NETWORK_NOT_FOUND", body["error"])
        }

        @Test
        fun `returns 422 on template not found`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network",
                templateName = "non-existent-template"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.TemplateNotFound("non-existent-template").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("TEMPLATE_NOT_FOUND", body["error"])
        }

        @Test
        fun `returns 422 on API error`() = runTest {
            // Given
            val request = TestVmwareConnectionRequest(
                vcenterUrl = "https://vcenter.example.com/sdk",
                username = "admin@vsphere.local",
                password = "secret123",
                datacenterName = "DC1",
                clusterName = "Cluster1",
                datastoreName = "Datastore1",
                networkName = "VM Network"
            )
            coEvery { testVmwareConnectionHandler.handle(any()) } returns
                TestVmwareConnectionError.ApiError("vCenter API unavailable").failure()

            // When
            val response = withTenant {
                controller.testConnection(request, createJwt())
            }

            // Then
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("API_ERROR", body["error"])
        }
    }

    @Nested
    inner class CheckConfigExists {

        @Test
        fun `returns 200 OK with exists true when configuration exists and is verified`() = runTest {
            // Given
            val verifiedAt = Instant.now()
            coEvery { checkVmwareConfigExistsHandler.handle(any()) } returns true
            coEvery { getVmwareConfigHandler.handle(any()) } returns
                createMockConfigResponse().copy(verifiedAt = verifiedAt).success()

            // When
            val response = withTenant {
                controller.checkConfigExists(createJwt())
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as VmwareConfigExistsResponse
            assertTrue(body.exists)
            assertNotNull(body.verifiedAt)

            coVerify {
                checkVmwareConfigExistsHandler.handle(
                    match<CheckVmwareConfigExistsQuery> { it.tenantId == testTenantId }
                )
            }
        }

        @Test
        fun `returns 200 OK with exists false when configuration does not exist`() = runTest {
            // Given
            coEvery { checkVmwareConfigExistsHandler.handle(any()) } returns false

            // When
            val response = withTenant {
                controller.checkConfigExists(createJwt())
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as VmwareConfigExistsResponse
            assertEquals(false, body.exists)
            assertEquals(null, body.verifiedAt)
        }

        @Test
        fun `returns verifiedAt as null when config exists but getConfig fails`() = runTest {
            // Given
            coEvery { checkVmwareConfigExistsHandler.handle(any()) } returns true
            coEvery { getVmwareConfigHandler.handle(any()) } throws RuntimeException("DB error")

            // When
            val response = withTenant {
                controller.checkConfigExists(createJwt())
            }

            // Then
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body as VmwareConfigExistsResponse
            assertTrue(body.exists)
            assertEquals(null, body.verifiedAt) // Should be null due to exception handling
        }
    }
}
