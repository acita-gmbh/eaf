package de.acci.eaf.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailAddressTest {

    @Test
    fun `of creates email address from valid input`() {
        val email = EmailAddress.of("user@example.com")

        assertEquals("user@example.com", email.value)
    }

    @Test
    fun `of normalizes email to lowercase`() {
        val email = EmailAddress.of("User@EXAMPLE.COM")

        assertEquals("user@example.com", email.value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "simple@example.com",
            "user.name@example.com",
            "user+tag@example.org",
            "user123@subdomain.example.co.uk"
        ]
    )
    fun `of accepts valid email formats`(validEmail: String) {
        val email = EmailAddress.of(validEmail)

        assertNotNull(email)
        assertEquals(validEmail.lowercase(), email.value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "invalid",
            "@example.com",
            "user@",
            "user@example",
            "user @example.com",
            "user@ example.com"
        ]
    )
    fun `of throws for invalid email formats`(invalidEmail: String) {
        assertThrows(IllegalArgumentException::class.java) {
            EmailAddress.of(invalidEmail)
        }
    }

    @Test
    fun `of throws for empty email`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmailAddress.of("")
        }
    }

    @Test
    fun `ofOrNull returns email for valid input`() {
        val email = EmailAddress.ofOrNull("valid@example.com")

        assertNotNull(email)
        assertEquals("valid@example.com", email!!.value)
    }

    @Test
    fun `ofOrNull returns null for invalid input`() {
        val email = EmailAddress.ofOrNull("invalid-email")

        assertNull(email)
    }

    @Test
    fun `toString returns the email value`() {
        val email = EmailAddress.of("user@example.com")

        assertEquals("user@example.com", email.toString())
    }

    @Test
    fun `value classes are equal when values match`() {
        val email1 = EmailAddress.of("user@example.com")
        val email2 = EmailAddress.of("USER@EXAMPLE.COM")

        assertEquals(email1, email2)
    }
}
