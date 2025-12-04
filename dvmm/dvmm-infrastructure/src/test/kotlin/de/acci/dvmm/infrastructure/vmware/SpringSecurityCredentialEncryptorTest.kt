package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.DecryptionException
import de.acci.dvmm.application.vmware.EncryptionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("SpringSecurityCredentialEncryptor")
class SpringSecurityCredentialEncryptorTest {

    private val testPassword = "test-encryption-password-32-chars!"
    private val testSalt = "0123456789abcdef0123456789abcdef"

    private fun createEncryptor(
        password: String = testPassword,
        salt: String = testSalt,
        profiles: String = "test"
    ) = SpringSecurityCredentialEncryptor(
        encryptionPassword = password,
        encryptionSalt = salt,
        activeProfiles = profiles
    )

    @Nested
    @DisplayName("encrypt()")
    inner class EncryptTests {

        @Test
        @DisplayName("should encrypt plaintext password successfully")
        fun `should encrypt plaintext password successfully`() {
            // Given
            val encryptor = createEncryptor()
            val plaintext = "my-vcenter-password"

            // When
            val encrypted = encryptor.encrypt(plaintext)

            // Then
            assertFalse(encrypted.isEmpty())
            assertNotEquals(plaintext.toByteArray(), encrypted)
        }

        @Test
        @DisplayName("should produce different ciphertext for same plaintext (random IV)")
        fun `should produce different ciphertext for same plaintext`() {
            // Given
            val encryptor = createEncryptor()
            val plaintext = "test-password"

            // When
            val encrypted1 = encryptor.encrypt(plaintext)
            val encrypted2 = encryptor.encrypt(plaintext)

            // Then - different due to random IV
            assertFalse(encrypted1.contentEquals(encrypted2))
        }

        @Test
        @DisplayName("should throw EncryptionException for empty password")
        fun `should throw exception for empty password`() {
            // Given
            val encryptor = createEncryptor()

            // When/Then
            assertThrows<IllegalArgumentException> {
                encryptor.encrypt("")
            }
        }
    }

    @Nested
    @DisplayName("decrypt()")
    inner class DecryptTests {

        @Test
        @DisplayName("should decrypt encrypted password successfully")
        fun `should decrypt encrypted password successfully`() {
            // Given
            val encryptor = createEncryptor()
            val originalPlaintext = "my-vcenter-password"
            val encrypted = encryptor.encrypt(originalPlaintext)

            // When
            val decrypted = encryptor.decrypt(encrypted)

            // Then
            assertEquals(originalPlaintext, decrypted)
        }

        @Test
        @DisplayName("should throw DecryptionException for empty data")
        fun `should throw exception for empty encrypted data`() {
            // Given
            val encryptor = createEncryptor()

            // When/Then
            assertThrows<IllegalArgumentException> {
                encryptor.decrypt(ByteArray(0))
            }
        }

        @Test
        @DisplayName("should throw DecryptionException for invalid data")
        fun `should throw exception for invalid encrypted data`() {
            // Given
            val encryptor = createEncryptor()
            val invalidData = "not-encrypted-data".toByteArray()

            // When/Then
            assertThrows<DecryptionException> {
                encryptor.decrypt(invalidData)
            }
        }

        @Test
        @DisplayName("should throw DecryptionException when key mismatch")
        fun `should throw exception when decrypting with different key`() {
            // Given
            val encryptor1 = createEncryptor(password = "password-one")
            val encryptor2 = createEncryptor(password = "password-two")
            val encrypted = encryptor1.encrypt("test-password")

            // When/Then - decrypting with different key should fail
            assertThrows<DecryptionException> {
                encryptor2.decrypt(encrypted)
            }
        }
    }

    @Nested
    @DisplayName("round-trip")
    inner class RoundTripTests {

        @Test
        @DisplayName("should encrypt and decrypt special characters")
        fun `should encrypt and decrypt special characters`() {
            // Given
            val encryptor = createEncryptor()
            val plaintext = "p@ssw0rd!#\$%^&*()"

            // When
            val encrypted = encryptor.encrypt(plaintext)
            val decrypted = encryptor.decrypt(encrypted)

            // Then
            assertEquals(plaintext, decrypted)
        }

        @Test
        @DisplayName("should encrypt and decrypt unicode characters")
        fun `should encrypt and decrypt unicode characters`() {
            // Given
            val encryptor = createEncryptor()
            val plaintext = "Passwörd-日本語-🔐"

            // When
            val encrypted = encryptor.encrypt(plaintext)
            val decrypted = encryptor.decrypt(encrypted)

            // Then
            assertEquals(plaintext, decrypted)
        }

        @Test
        @DisplayName("should encrypt and decrypt long password")
        fun `should encrypt and decrypt long password`() {
            // Given
            val encryptor = createEncryptor()
            val plaintext = "a".repeat(1000)

            // When
            val encrypted = encryptor.encrypt(plaintext)
            val decrypted = encryptor.decrypt(encrypted)

            // Then
            assertEquals(plaintext, decrypted)
        }
    }

    @Nested
    @DisplayName("production configuration")
    inner class ProductionConfigTests {

        @Test
        @DisplayName("should fail fast in production without configured salt")
        fun `should fail in production without configured salt`() {
            // Given - no salt in production profile
            val encryptor = createEncryptor(salt = "", profiles = "prod")

            // When/Then - encrypt wraps the IllegalStateException in EncryptionException
            val exception = assertThrows<EncryptionException> {
                encryptor.encrypt("test") // Triggers lazy initialization
            }

            // Verify the cause is IllegalStateException with proper message
            val cause = exception.cause
            assertNotNull(cause)
            assertTrue(cause is IllegalStateException)
            assertTrue(cause!!.message!!.contains("dvmm.encryption.salt is required in production"))
        }

        @Test
        @DisplayName("should fail fast in 'production' profile without configured salt")
        fun `should fail in 'production' profile without configured salt`() {
            // Given - no salt in production profile
            val encryptor = createEncryptor(salt = "", profiles = "production")

            // When/Then - encrypt wraps the IllegalStateException in EncryptionException
            val exception = assertThrows<EncryptionException> {
                encryptor.encrypt("test") // Triggers lazy initialization
            }

            // Verify the cause is IllegalStateException with proper message
            val cause = exception.cause
            assertNotNull(cause)
            assertTrue(cause is IllegalStateException)
            assertTrue(cause!!.message!!.contains("dvmm.encryption.salt is required in production"))
        }

        @Test
        @DisplayName("should allow random salt in non-production profiles")
        fun `should allow random salt in non-production profiles`() {
            // Given - no salt, but 'test' profile
            val encryptor = createEncryptor(salt = "", profiles = "test")

            // When/Then - should work (uses random salt)
            val plaintext = "test-password"
            val encrypted = encryptor.encrypt(plaintext)
            val decrypted = encryptor.decrypt(encrypted)
            assertEquals(plaintext, decrypted)
        }
    }
}
