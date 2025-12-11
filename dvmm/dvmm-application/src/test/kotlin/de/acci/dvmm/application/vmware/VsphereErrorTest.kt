package de.acci.dvmm.application.vmware

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

/**
 * Tests for VsphereError sealed class hierarchy.
 *
 * Verifies that errors are correctly classified as retriable vs permanent,
 * supporting the retry logic in TriggerProvisioningHandler (AC-3.6.1, AC-3.6.3).
 */
class VsphereErrorTest {

    @Nested
    inner class RetriableErrors {

        @Test
        fun `ConnectionError is retriable`() {
            val error = VsphereError.ConnectionError("Connection refused")
            assertTrue(error.retriable)
        }

        @Test
        fun `Timeout is retriable`() {
            val error = VsphereError.Timeout("Operation timed out")
            assertTrue(error.retriable)
        }

        @Test
        fun `ApiError is retriable by default`() {
            val error = VsphereError.ApiError("Temporary API failure")
            assertTrue(error.retriable)
        }

        @Test
        fun `ResourceExhausted is retriable - resources may free up`() {
            val error = VsphereError.ResourceExhausted(
                message = "Insufficient resources in cluster",
                resourceType = "MEMORY",
                requested = 32,
                available = 16
            )
            assertTrue(error.retriable)
        }

        @Test
        fun `OperationFailed is retriable`() {
            val error = VsphereError.OperationFailed(
                operation = "cloneVm",
                details = "Clone task failed temporarily"
            )
            assertTrue(error.retriable)
        }

        @Test
        fun `ProvisioningError is retriable`() {
            val error = VsphereError.ProvisioningError(
                message = "Clone failed",
                cause = RuntimeException("underlying cause")
            )
            assertTrue(error.retriable)
        }

        @Test
        fun `DeletionError is retriable`() {
            val error = VsphereError.DeletionError(
                message = "Delete failed",
                cause = RuntimeException("underlying cause")
            )
            assertTrue(error.retriable)
        }
    }

    @Nested
    inner class PermanentErrors {

        @Test
        fun `InvalidConfiguration is not retriable`() {
            val error = VsphereError.InvalidConfiguration(
                message = "Invalid VM configuration",
                field = "cpuCores"
            )
            assertFalse(error.retriable)
        }

        @Test
        fun `ResourceNotFound is not retriable`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Template",
                resourceId = "missing-template"
            )
            assertFalse(error.retriable)
        }

        @Test
        fun `AuthenticationError is not retriable - needs config change`() {
            val error = VsphereError.AuthenticationError("Invalid credentials")
            assertFalse(error.retriable)
        }
    }

    @Nested
    inner class UserFriendlyMessages {

        @Test
        fun `ResourceExhausted has user-friendly message`() {
            val error = VsphereError.ResourceExhausted(
                message = "InsufficientResourcesFault",
                resourceType = "MEMORY",
                requested = 32,
                available = 16
            )
            assertEquals(
                "Cluster capacity reached. Please try a smaller size or contact support.",
                error.userMessage
            )
        }

        @Test
        fun `ResourceNotFound for template has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Template",
                resourceId = "ubuntu-template"
            )
            assertEquals(
                "VM template missing. IT has been notified.",
                error.userMessage
            )
        }

        @Test
        fun `ResourceNotFound for datastore has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Datastore",
                resourceId = "datastore-01"
            )
            assertEquals(
                "Storage unavailable. Please contact support.",
                error.userMessage
            )
        }

        @Test
        fun `ResourceNotFound for network has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Network",
                resourceId = "vm-network"
            )
            assertEquals(
                "Network not available. IT has been notified.",
                error.userMessage
            )
            assertEquals(ProvisioningErrorCode.NETWORK_NOT_FOUND, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for resourcepool has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "ResourcePool",
                resourceId = "production-pool"
            )
            assertEquals(
                "Resource pool not available. IT has been notified.",
                error.userMessage
            )
            assertEquals(ProvisioningErrorCode.RESOURCE_POOL_NOT_FOUND, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for cluster has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Cluster",
                resourceId = "compute-cluster"
            )
            assertEquals(
                "Cluster not available. IT has been notified.",
                error.userMessage
            )
            assertEquals(ProvisioningErrorCode.CLUSTER_NOT_FOUND, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for folder has user-friendly message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Folder",
                resourceId = "vm-folder"
            )
            assertEquals(
                "VM folder not available. IT has been notified.",
                error.userMessage
            )
            assertEquals(ProvisioningErrorCode.FOLDER_NOT_FOUND, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for unknown type uses generic message`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Datacenter",
                resourceId = "dc-01"
            )
            // Generic message avoids exposing internal resource type names
            assertEquals(
                "Resource not available. IT has been notified.",
                error.userMessage
            )
            assertEquals(ProvisioningErrorCode.UNKNOWN, error.errorCode)
        }

        @Test
        fun `InvalidConfiguration has user-friendly message`() {
            val error = VsphereError.InvalidConfiguration(
                message = "VmConfigFault",
                field = "numCPUs"
            )
            assertEquals(
                "Invalid configuration. Please check your request parameters.",
                error.userMessage
            )
        }

        @Test
        fun `AuthenticationError has user-friendly message`() {
            val error = VsphereError.AuthenticationError("NotAuthenticated")
            assertEquals(
                "System authentication failed. IT has been notified.",
                error.userMessage
            )
        }

        @Test
        fun `ConnectionError has user-friendly message`() {
            val error = VsphereError.ConnectionError("Socket timeout")
            assertEquals(
                "Temporary connection issue. We will retry automatically.",
                error.userMessage
            )
        }

        @Test
        fun `Timeout has user-friendly message`() {
            val error = VsphereError.Timeout("VMware Tools timeout")
            assertEquals(
                "VM started but tools didn't respond. Please restart the VM.",
                error.userMessage
            )
        }

        @Test
        fun `OperationFailed for network has user-friendly message`() {
            val error = VsphereError.OperationFailed(
                operation = "configureNetwork",
                details = "Network configuration failed"
            )
            assertEquals(
                "Network setup failed. IT has been notified.",
                error.userMessage
            )
        }

        @Test
        fun `OperationFailed for non-network uses generic message`() {
            val error = VsphereError.OperationFailed(
                operation = "cloneVm",
                details = "Clone task failed"
            )
            assertEquals(
                "Unexpected error. IT has been notified.",
                error.userMessage
            )
        }

        @Test
        fun `ApiError has generic user-friendly message`() {
            val error = VsphereError.ApiError("Unknown error")
            assertEquals(
                "Unexpected error. IT has been notified.",
                error.userMessage
            )
        }
    }

    @Nested
    inner class ErrorCodeMapping {

        @Test
        fun `ResourceExhausted has correct error code`() {
            val error = VsphereError.ResourceExhausted(
                message = "test",
                resourceType = "CPU",
                requested = 8,
                available = 4
            )
            assertEquals(ProvisioningErrorCode.INSUFFICIENT_RESOURCES, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for template has correct error code`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Template",
                resourceId = "test"
            )
            assertEquals(ProvisioningErrorCode.TEMPLATE_NOT_FOUND, error.errorCode)
        }

        @Test
        fun `ResourceNotFound for datastore has correct error code`() {
            val error = VsphereError.ResourceNotFound(
                resourceType = "Datastore",
                resourceId = "test"
            )
            assertEquals(ProvisioningErrorCode.DATASTORE_NOT_AVAILABLE, error.errorCode)
        }

        @Test
        fun `InvalidConfiguration has correct error code`() {
            val error = VsphereError.InvalidConfiguration(
                message = "test",
                field = "test"
            )
            assertEquals(ProvisioningErrorCode.VM_CONFIG_INVALID, error.errorCode)
        }

        @Test
        fun `AuthenticationError has correct error code`() {
            val error = VsphereError.AuthenticationError("test")
            assertEquals(ProvisioningErrorCode.CONNECTION_FAILED, error.errorCode)
        }

        @Test
        fun `ConnectionError has correct error code`() {
            val error = VsphereError.ConnectionError("test")
            assertEquals(ProvisioningErrorCode.CONNECTION_TIMEOUT, error.errorCode)
        }

        @Test
        fun `Timeout has correct error code`() {
            val error = VsphereError.Timeout("test")
            assertEquals(ProvisioningErrorCode.VMWARE_TOOLS_TIMEOUT, error.errorCode)
        }

        @Test
        fun `OperationFailed for network has correct error code`() {
            val error = VsphereError.OperationFailed(
                operation = "configureNetwork",
                details = "test"
            )
            assertEquals(ProvisioningErrorCode.NETWORK_CONFIG_FAILED, error.errorCode)
        }

        @Test
        fun `OperationFailed for non-network has UNKNOWN error code`() {
            val error = VsphereError.OperationFailed(
                operation = "cloneVm",
                details = "Clone task failed"
            )
            assertEquals(ProvisioningErrorCode.UNKNOWN, error.errorCode)
        }

        @Test
        fun `ApiError has correct error code`() {
            val error = VsphereError.ApiError("test")
            assertEquals(ProvisioningErrorCode.UNKNOWN, error.errorCode)
        }

        @Test
        fun `ProvisioningError has correct error code`() {
            val error = VsphereError.ProvisioningError("Clone failed")
            assertEquals(ProvisioningErrorCode.UNKNOWN, error.errorCode)
        }

        @Test
        fun `DeletionError has correct error code`() {
            val error = VsphereError.DeletionError("Delete failed")
            assertEquals(ProvisioningErrorCode.UNKNOWN, error.errorCode)
        }
    }
}
