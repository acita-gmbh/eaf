package de.acci.dcm.domain.vmrequest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class VmNameTest {

    @Test
    fun `should create valid vm name`() {
        val result = VmName.create("web-server-01")
        assertTrue(result.isSuccess)
        assertEquals("web-server-01", result.getOrNull()?.value)
    }

    @Test
    fun `should create vm name with minimum length`() {
        val result = VmName.create("abc")
        assertTrue(result.isSuccess)
        assertEquals("abc", result.getOrNull()?.value)
    }

    @Test
    fun `should create vm name with maximum length`() {
        val name = "a" + "b".repeat(61) + "c" // 63 chars total
        val result = VmName.create(name)
        assertTrue(result.isSuccess)
        assertEquals(63, result.getOrNull()?.value?.length)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "web-server",
        "db01",
        "my-app-prod-01",
        "a1b2c3",
        "test123"
    ])
    fun `should accept valid vm names`(name: String) {
        val result = VmName.create(name)
        assertTrue(result.isSuccess, "Expected '$name' to be valid, but got: ${result.exceptionOrNull()?.message}")
    }

    @Test
    fun `should reject name too short`() {
        val result = VmName.create("ab")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VmNameValidationException)
        assertTrue(result.exceptionOrNull()?.message?.contains("at least 3 characters") == true)
    }

    @Test
    fun `should reject name too long`() {
        val name = "a".repeat(64)
        val result = VmName.create(name)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VmNameValidationException)
        assertTrue(result.exceptionOrNull()?.message?.contains("exceed 63") == true)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "Web-Server",      // uppercase
        "web_server",      // underscore
        "web server",      // space
        "-webserver",      // starts with hyphen
        "webserver-",      // ends with hyphen
        "web--server",     // consecutive hyphens
        "123.456"          // dot
    ])
    fun `should reject invalid vm names`(name: String) {
        val result = VmName.create(name)
        assertTrue(result.isFailure, "Expected '$name' to be invalid")
        assertTrue(result.exceptionOrNull() is VmNameValidationException)
    }

    @Test
    fun `should reject consecutive hyphens with specific message`() {
        val result = VmName.create("web--server")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("consecutive hyphens") == true)
    }

    @Test
    fun `of method should throw for invalid name`() {
        assertThrows<VmNameValidationException> {
            VmName.of("INVALID NAME")
        }
    }

    @Test
    fun `of method should return value for valid name`() {
        val vmName = VmName.of("valid-name")
        assertEquals("valid-name", vmName.value)
    }

    @Test
    fun `should trim whitespace from name`() {
        val result = VmName.create("  valid-name  ")
        assertTrue(result.isSuccess)
        assertEquals("valid-name", result.getOrNull()?.value)
    }
}
