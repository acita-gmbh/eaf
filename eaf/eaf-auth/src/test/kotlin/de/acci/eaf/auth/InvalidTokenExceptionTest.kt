package de.acci.eaf.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InvalidTokenExceptionTest {

    @Test
    fun `invalidSignature creates exception with correct reason`() {
        val ex = InvalidTokenException.invalidSignature()
        assertEquals(InvalidTokenException.Reason.INVALID_SIGNATURE, ex.reason)
        assertEquals("Token signature verification failed", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun `invalidSignature with custom message`() {
        val ex = InvalidTokenException.invalidSignature("Custom signature error")
        assertEquals(InvalidTokenException.Reason.INVALID_SIGNATURE, ex.reason)
        assertEquals("Custom signature error", ex.message)
    }

    @Test
    fun `expired creates exception with correct reason`() {
        val ex = InvalidTokenException.expired()
        assertEquals(InvalidTokenException.Reason.EXPIRED, ex.reason)
        assertEquals("Token has expired", ex.message)
    }

    @Test
    fun `notYetValid creates exception with correct reason`() {
        val ex = InvalidTokenException.notYetValid()
        assertEquals(InvalidTokenException.Reason.NOT_YET_VALID, ex.reason)
        assertEquals("Token is not yet valid", ex.message)
    }

    @Test
    fun `invalidIssuer creates exception with details`() {
        val ex = InvalidTokenException.invalidIssuer(
            expected = "http://expected.com",
            actual = "http://actual.com",
        )
        assertEquals(InvalidTokenException.Reason.INVALID_ISSUER, ex.reason)
        assertEquals("Token issuer mismatch. Expected: http://expected.com, Actual: http://actual.com", ex.message)
    }

    @Test
    fun `invalidAudience creates exception with details`() {
        val ex = InvalidTokenException.invalidAudience(
            expected = "my-app",
            actual = "other-app",
        )
        assertEquals(InvalidTokenException.Reason.INVALID_AUDIENCE, ex.reason)
        assertEquals("Token audience mismatch. Expected: my-app, Actual: other-app", ex.message)
    }

    @Test
    fun `invalidAudience with null actual`() {
        val ex = InvalidTokenException.invalidAudience(
            expected = "my-app",
            actual = null,
        )
        assertEquals(InvalidTokenException.Reason.INVALID_AUDIENCE, ex.reason)
        assertEquals("Token audience mismatch. Expected: my-app, Actual: null", ex.message)
    }

    @Test
    fun `missingClaim creates exception with claim name`() {
        val ex = InvalidTokenException.missingClaim("tenant_id")
        assertEquals(InvalidTokenException.Reason.MISSING_CLAIM, ex.reason)
        assertEquals("Required claim 'tenant_id' is missing from token", ex.message)
    }

    @Test
    fun `malformed creates exception with cause`() {
        val cause = IllegalArgumentException("Bad format")
        val ex = InvalidTokenException.malformed("Invalid JWT structure", cause)
        assertEquals(InvalidTokenException.Reason.MALFORMED, ex.reason)
        assertEquals("Invalid JWT structure", ex.message)
        assertNotNull(ex.cause)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `validationFailed creates exception with cause`() {
        val cause = RuntimeException("Validation error")
        val ex = InvalidTokenException.validationFailed("Token validation failed", cause)
        assertEquals(InvalidTokenException.Reason.VALIDATION_FAILED, ex.reason)
        assertEquals("Token validation failed", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun `exception can be thrown and caught`() {
        val caught = try {
            throw InvalidTokenException.expired("Token expired at 12:00")
        } catch (e: InvalidTokenException) {
            e
        }
        assertEquals(InvalidTokenException.Reason.EXPIRED, caught.reason)
    }

    @Test
    fun `all reasons are defined`() {
        val reasons = InvalidTokenException.Reason.entries
        assertEquals(9, reasons.size)
        assertEquals(
            listOf(
                "INVALID_SIGNATURE",
                "EXPIRED",
                "NOT_YET_VALID",
                "INVALID_ISSUER",
                "INVALID_AUDIENCE",
                "MISSING_CLAIM",
                "MALFORMED",
                "UNSUPPORTED_TOKEN_TYPE",
                "VALIDATION_FAILED",
            ),
            reasons.map { it.name },
        )
    }
}
