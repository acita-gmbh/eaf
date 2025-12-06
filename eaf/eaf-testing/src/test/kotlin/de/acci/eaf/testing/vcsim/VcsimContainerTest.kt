package de.acci.eaf.testing.vcsim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Isolated

/**
 * Unit tests for [VcsimContainer] configuration and methods.
 *
 * These tests verify container configuration without actually starting
 * the container (except where noted for integration tests).
 *
 * Note: @Isolated is required because some tests modify System.getProperty("os.arch")
 * which is global JVM state that cannot be safely modified in parallel tests.
 */
@Isolated
class VcsimContainerTest {

    @Test
    fun `container uses correct default image`() {
        val container = VcsimContainer()
        assertEquals(VcsimContainer.DEFAULT_IMAGE, container.dockerImageName)
    }

    @Test
    fun `container exposes default port`() {
        val container = VcsimContainer()
        assertTrue(container.exposedPorts.contains(VcsimContainer.DEFAULT_PORT))
    }

    @Test
    fun `getUsername returns default username`() {
        val container = VcsimContainer()
        assertEquals(VcsimContainer.DEFAULT_USERNAME, container.getUsername())
    }

    @Test
    fun `getPassword returns default password`() {
        val container = VcsimContainer()
        assertEquals(VcsimContainer.DEFAULT_PASSWORD, container.getPassword())
    }

    @Test
    fun `withClusters rejects zero value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withClusters(0)
        }
    }

    @Test
    fun `withClusters rejects negative value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withClusters(-1)
        }
    }

    @Test
    fun `withClusters accepts positive value`() {
        val container = VcsimContainer()
        val result = container.withClusters(5)
        assertEquals(container, result) // returns self for chaining
    }

    @Test
    fun `withHostsPerCluster rejects zero value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withHostsPerCluster(0)
        }
    }

    @Test
    fun `withHostsPerCluster accepts positive value`() {
        val container = VcsimContainer()
        val result = container.withHostsPerCluster(8)
        assertEquals(container, result)
    }

    @Test
    fun `withVmsPerHost rejects negative value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withVmsPerHost(-1)
        }
    }

    @Test
    fun `withVmsPerHost accepts zero value`() {
        val container = VcsimContainer()
        val result = container.withVmsPerHost(0)
        assertEquals(container, result)
    }

    @Test
    fun `withVmsPerHost accepts positive value`() {
        val container = VcsimContainer()
        val result = container.withVmsPerHost(20)
        assertEquals(container, result)
    }

    @Test
    fun `withResourcePools rejects negative value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withResourcePools(-1)
        }
    }

    @Test
    fun `withResourcePools accepts zero value`() {
        val container = VcsimContainer()
        val result = container.withResourcePools(0)
        assertEquals(container, result)
    }

    @Test
    fun `withFolders rejects negative value`() {
        val container = VcsimContainer()
        assertThrows<IllegalArgumentException> {
            container.withFolders(-1)
        }
    }

    @Test
    fun `withFolders accepts zero value`() {
        val container = VcsimContainer()
        val result = container.withFolders(0)
        assertEquals(container, result)
    }

    @Test
    fun `fluent configuration chaining works`() {
        val container = VcsimContainer()
            .withClusters(4)
            .withHostsPerCluster(8)
            .withVmsPerHost(5)
            .withResourcePools(3)
            .withFolders(2)

        // Container should be configured without exceptions
        assertTrue(container.exposedPorts.contains(VcsimContainer.DEFAULT_PORT))
    }

    // ==========================================
    // Architecture Detection Tests
    // ==========================================

    @Test
    fun `isArm64 returns true for aarch64 architecture`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "aarch64")
            assertTrue(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    @Test
    fun `isArm64 returns true for arm64 architecture`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "arm64")
            assertTrue(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    @Test
    fun `isArm64 returns false for amd64 architecture`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "amd64")
            assertFalse(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    @Test
    fun `isArm64 returns false for x86_64 architecture`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "x86_64")
            assertFalse(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    @Test
    fun `isArm64 handles mixed case architecture names`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "AARCH64")
            assertTrue(VcsimContainer.isArm64())

            System.setProperty("os.arch", "ARM64")
            assertTrue(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    @Test
    fun `isArm64 returns false for empty architecture`() {
        val original = System.getProperty("os.arch")
        try {
            System.setProperty("os.arch", "")
            assertFalse(VcsimContainer.isArm64())
        } finally {
            restoreProperty("os.arch", original)
        }
    }

    private fun restoreProperty(key: String, value: String?) {
        if (value != null) {
            System.setProperty(key, value)
        } else {
            System.clearProperty(key)
        }
    }
}
