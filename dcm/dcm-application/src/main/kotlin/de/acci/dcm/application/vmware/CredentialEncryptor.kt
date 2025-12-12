package de.acci.dcm.application.vmware

/**
 * Port for encrypting and decrypting sensitive credentials.
 *
 * This interface abstracts the encryption mechanism used for storing
 * sensitive data like vCenter passwords. The implementation uses
 * AES-256 encryption via Spring Security Crypto.
 *
 * ## Security Requirements (AC-3.1.4)
 *
 * - MUST use AES-256 encryption
 * - Encrypted data MUST be stored as bytes (not base64 string)
 * - Plaintext passwords MUST NOT appear in logs
 * - Decryption should only occur when making vSphere API calls
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Encrypt password before storing
 * val encrypted = encryptor.encrypt("my-secret-password")
 * val config = VmwareConfiguration.create(
 *     // ... other params
 *     passwordEncrypted = encrypted,
 *     // ...
 * )
 *
 * // Decrypt password when needed for vSphere API call
 * val plaintext = encryptor.decrypt(config.passwordEncrypted)
 * vsphereClient.connect(config.vcenterUrl, config.username, plaintext)
 * ```
 */
public interface CredentialEncryptor {

    /**
     * Encrypt a plaintext password.
     *
     * @param plaintext The password to encrypt
     * @return Encrypted bytes (AES-256)
     * @throws EncryptionException if encryption fails
     */
    public fun encrypt(plaintext: String): ByteArray

    /**
     * Decrypt an encrypted password.
     *
     * @param encrypted The encrypted bytes
     * @return Plaintext password
     * @throws DecryptionException if decryption fails (e.g., wrong key, corrupted data)
     */
    public fun decrypt(encrypted: ByteArray): String
}

/**
 * Exception thrown when encryption fails.
 */
public class EncryptionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when decryption fails.
 */
public class DecryptionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * No-op encryptor for testing.
 *
 * WARNING: This is only for unit testing handlers in isolation.
 * Never use in production - it stores plaintext!
 */
public object NoOpCredentialEncryptor : CredentialEncryptor {
    override fun encrypt(plaintext: String): ByteArray = plaintext.toByteArray(Charsets.UTF_8)
    override fun decrypt(encrypted: ByteArray): String = String(encrypted, Charsets.UTF_8)
}
