package de.acci.dvmm.infrastructure.vmware

import de.acci.dvmm.application.vmware.CredentialEncryptor
import de.acci.dvmm.application.vmware.DecryptionException
import de.acci.dvmm.application.vmware.EncryptionException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.encrypt.AesBytesEncryptor
import org.springframework.security.crypto.keygen.KeyGenerators
import org.springframework.stereotype.Component

/**
 * Spring Security Crypto implementation of [CredentialEncryptor].
 *
 * Uses AES-256-GCM encryption via [AesBytesEncryptor] for secure credential storage.
 *
 * ## Security Features (AC-3.1.4)
 *
 * - **AES-256-GCM**: Authenticated encryption with 256-bit key
 * - **Random IV**: Each encryption generates a unique initialization vector
 * - **Key Derivation**: Password-based key derivation with random salt
 * - **No Plaintext Logging**: Passwords are never logged
 *
 * ## Configuration
 *
 * Set `dvmm.encryption.password` in application.yml (preferably via Vault/Secret Manager):
 *
 * ```yaml
 * dvmm:
 *   encryption:
 *     password: ${DVMM_ENCRYPTION_PASSWORD}
 * ```
 *
 * The salt is generated once at startup. For production, consider storing the salt
 * persistently or using a fixed salt via configuration to ensure decryption works
 * across restarts.
 *
 * ## Usage
 *
 * ```kotlin
 * @Autowired
 * lateinit var encryptor: CredentialEncryptor
 *
 * // Encrypt before storing
 * val encrypted = encryptor.encrypt("my-vcenter-password")
 *
 * // Decrypt when needed for API calls
 * val plaintext = encryptor.decrypt(encrypted)
 * ```
 *
 * @property encryptionPassword Master password for key derivation
 * @property encryptionSalt Optional hex-encoded salt (defaults to random)
 */
@Component
public class SpringSecurityCredentialEncryptor(
    @Value("\${dvmm.encryption.password}")
    private val encryptionPassword: String,
    @Value("\${dvmm.encryption.salt:}")
    private val encryptionSalt: String = "",
    @Value("\${spring.profiles.active:}")
    private val activeProfiles: String = ""
) : CredentialEncryptor {

    private val logger = KotlinLogging.logger {}

    /**
     * The AES encryptor instance.
     * Uses AES-GCM mode which provides authenticated encryption.
     */
    private val encryptor: AesBytesEncryptor by lazy {
        // Use configured salt or generate random one
        // WARNING: Random salt means data encrypted before restart cannot be decrypted after
        // For production, always configure a persistent salt
        val salt = if (encryptionSalt.isNotBlank()) {
            encryptionSalt
        } else {
            // FAIL FAST in production - random salt causes data loss after restart
            // Use exact profile matching to avoid false positives (e.g., "nonprod", "preprod")
            val profileList = activeProfiles.split(",").map { it.trim().lowercase() }
            val isProduction = profileList.any { it == "prod" || it == "production" }
            if (isProduction) {
                throw IllegalStateException(
                    "dvmm.encryption.salt is required in production. " +
                        "Generate a hex-encoded salt: openssl rand -hex 16"
                )
            }
            logger.warn {
                "Using randomly generated encryption salt. " +
                    "Data will be unrecoverable after restart. " +
                    "Configure dvmm.encryption.salt for production."
            }
            KeyGenerators.string().generateKey()
        }

        AesBytesEncryptor(
            encryptionPassword,
            salt,
            KeyGenerators.secureRandom(16), // 128-bit IV
            AesBytesEncryptor.CipherAlgorithm.GCM // AES-GCM for authenticated encryption
        )
    }

    /**
     * Encrypt a plaintext password.
     *
     * The encrypted output includes:
     * - Random IV (16 bytes)
     * - Ciphertext
     * - GCM authentication tag (16 bytes)
     *
     * @param plaintext The password to encrypt
     * @return Encrypted bytes
     * @throws EncryptionException if encryption fails
     */
    override fun encrypt(plaintext: String): ByteArray {
        require(plaintext.isNotEmpty()) { "Cannot encrypt empty password" }

        return try {
            val encrypted = encryptor.encrypt(plaintext.toByteArray(Charsets.UTF_8))
            logger.debug { "Encrypted credential: ${encrypted.size} bytes" }
            encrypted
        } catch (e: Exception) {
            // Log error without revealing plaintext
            logger.error(e) { "Encryption failed" }
            throw EncryptionException("Failed to encrypt credential", e)
        }
    }

    /**
     * Decrypt an encrypted password.
     *
     * @param encrypted The encrypted bytes
     * @return Decrypted plaintext password
     * @throws DecryptionException if decryption fails (wrong key, corrupted data, tampered)
     */
    override fun decrypt(encrypted: ByteArray): String {
        require(encrypted.isNotEmpty()) { "Cannot decrypt empty data" }

        return try {
            val decrypted = encryptor.decrypt(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // Log error without revealing any data
            logger.error(e) { "Decryption failed - possible key mismatch or data corruption" }
            throw DecryptionException("Failed to decrypt credential", e)
        }
    }
}
