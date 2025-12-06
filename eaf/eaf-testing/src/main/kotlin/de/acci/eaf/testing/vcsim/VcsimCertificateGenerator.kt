package de.acci.eaf.testing.vcsim

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.StringWriter
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Generates X.509 certificates for VCSIM TLS testing.
 *
 * This generator creates a complete certificate chain at runtime:
 * 1. A self-signed CA certificate
 * 2. A server certificate signed by the CA with appropriate SANs
 *
 * The generated certificates are used to:
 * - Configure VCSIM to use known certificates (via -tlscert/-tlskey flags)
 * - Create a truststore for the test HTTP client that trusts only our CA
 *
 * This approach eliminates the need for:
 * - Storing certificates in git
 * - Trust-all TrustManager implementations (CodeQL java/insecure-trustmanager)
 * - Disabling certificate validation
 *
 * ## Usage
 * ```kotlin
 * val bundle = VcsimCertificateGenerator.generate()
 *
 * val container = VcsimContainer()
 *     .withCertificates(bundle)
 * container.start()
 *
 * val sslContext = bundle.createSslContext()
 * val httpClient = HttpClient.newBuilder()
 *     .sslContext(sslContext)
 *     .build()
 * ```
 *
 * @see VcsimCertificateBundle
 */
public object VcsimCertificateGenerator {

    private const val KEY_SIZE = 2048
    private const val SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption"
    private const val VALIDITY_DAYS = 365L

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Generates a complete certificate bundle for VCSIM testing.
     *
     * The bundle includes:
     * - CA certificate (self-signed, for signing server cert)
     * - Server certificate (signed by CA, with SANs for localhost and common Docker IPs)
     * - Server private key (for VCSIM's -tlskey flag)
     *
     * @return Certificate bundle ready for use with VCSIM
     */
    public fun generate(): VcsimCertificateBundle {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA").apply {
            initialize(KEY_SIZE, SecureRandom())
        }

        // Generate CA
        val caKeyPair = keyPairGenerator.generateKeyPair()
        val caCert = generateCaCertificate(caKeyPair)

        // Generate server certificate signed by CA
        val serverKeyPair = keyPairGenerator.generateKeyPair()
        val serverCert = generateServerCertificate(
            serverKeyPair = serverKeyPair,
            caKeyPair = caKeyPair,
            caCert = caCert
        )

        return VcsimCertificateBundle(
            caCertificate = caCert,
            serverCertificate = serverCert,
            serverKeyPair = serverKeyPair
        )
    }

    private fun generateCaCertificate(keyPair: KeyPair): X509Certificate {
        val now = Instant.now()
        val notBefore = Date.from(now)
        val notAfter = Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS))

        val issuer = X500Name("CN=VCSIM Test CA,O=EAF Testing,C=DE")
        val serial = BigInteger(64, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            issuer, // Self-signed: subject = issuer
            keyPair.public
        )

        // CA certificate extensions
        certBuilder.addExtension(
            Extension.basicConstraints,
            true, // Critical
            BasicConstraints(true) // isCA = true
        )
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )

        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)

        val certHolder: X509CertificateHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder)
    }

    private fun generateServerCertificate(
        serverKeyPair: KeyPair,
        caKeyPair: KeyPair,
        caCert: X509Certificate
    ): X509Certificate {
        val now = Instant.now()
        val notBefore = Date.from(now)
        val notAfter = Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS))

        val issuer = X500Name(caCert.subjectX500Principal.name)
        val subject = X500Name("CN=localhost,O=VCSIM,C=DE")
        val serial = BigInteger(64, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            serverKeyPair.public
        )

        // Subject Alternative Names for various environments
        // Covers: local development, Docker, GitHub Actions, Kubernetes
        val sans = GeneralNames(
            arrayOf(
                GeneralName(GeneralName.dNSName, "localhost"),
                GeneralName(GeneralName.dNSName, "vcsim"),
                GeneralName(GeneralName.dNSName, "*.local"),
                GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                // Docker default bridge network
                GeneralName(GeneralName.iPAddress, "172.17.0.1"),
                GeneralName(GeneralName.iPAddress, "172.17.0.2"),
                GeneralName(GeneralName.iPAddress, "172.17.0.3"),
                GeneralName(GeneralName.iPAddress, "172.17.0.4"),
                GeneralName(GeneralName.iPAddress, "172.17.0.5"),
                // Docker compose default network
                GeneralName(GeneralName.iPAddress, "172.18.0.1"),
                GeneralName(GeneralName.iPAddress, "172.18.0.2"),
                GeneralName(GeneralName.iPAddress, "172.18.0.3"),
                // GitHub Actions runners (common subnets)
                GeneralName(GeneralName.iPAddress, "172.19.0.1"),
                GeneralName(GeneralName.iPAddress, "172.19.0.2"),
                GeneralName(GeneralName.iPAddress, "172.20.0.1"),
                GeneralName(GeneralName.iPAddress, "172.20.0.2"),
            )
        )
        certBuilder.addExtension(Extension.subjectAlternativeName, false, sans)

        // Server certificate extensions
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false) // isCA = false
        )
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )

        // Sign with CA's private key
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(caKeyPair.private)

        val certHolder: X509CertificateHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder)
    }
}

/**
 * Bundle containing all certificates and keys needed for VCSIM TLS.
 *
 * @property caCertificate Self-signed CA certificate (added to truststore)
 * @property serverCertificate Server certificate signed by CA (used by VCSIM)
 * @property serverKeyPair Server key pair (private key used by VCSIM)
 */
public class VcsimCertificateBundle(
    public val caCertificate: X509Certificate,
    public val serverCertificate: X509Certificate,
    public val serverKeyPair: KeyPair
) {
    /**
     * Writes certificate and key files to the specified directory.
     *
     * Creates:
     * - `server.crt` - Server certificate in PEM format
     * - `server.key` - Server private key in PEM format
     * - `ca.crt` - CA certificate in PEM format (for reference)
     *
     * @param directory Target directory (will be created if needed)
     * @return Paths to the created files
     */
    public fun writeTo(directory: Path): CertificateFiles {
        Files.createDirectories(directory)

        val serverCertPath = directory.resolve("server.crt")
        val serverKeyPath = directory.resolve("server.key")
        val caCertPath = directory.resolve("ca.crt")

        Files.writeString(serverCertPath, toPem(serverCertificate))
        Files.writeString(serverKeyPath, toPem(serverKeyPair.private))
        Files.writeString(caCertPath, toPem(caCertificate))

        return CertificateFiles(
            serverCert = serverCertPath,
            serverKey = serverKeyPath,
            caCert = caCertPath
        )
    }

    /**
     * Creates an SSLContext that trusts only this bundle's CA certificate.
     *
     * Use this to create HTTP clients that securely connect to VCSIM
     * without trusting all certificates.
     *
     * @return SSLContext configured with a truststore containing only our CA
     */
    public fun createSslContext(): SSLContext {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(createTrustStore())

        return SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, SecureRandom())
        }
    }

    /**
     * Creates an in-memory KeyStore containing only the CA certificate.
     *
     * Useful for advanced SSL configuration or debugging.
     *
     * @return KeyStore with the CA certificate
     */
    public fun createTrustStore(): KeyStore {
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("vcsim-ca", caCertificate)
        }
    }

    /**
     * Creates TrustManagers that trust only this bundle's CA certificate.
     *
     * Useful for configuring CXF HTTPConduit's TLSClientParameters,
     * which requires TrustManagers rather than an SSLContext.
     *
     * @return Array of TrustManagers trusting only our CA
     */
    public fun createTrustManagers(): Array<javax.net.ssl.TrustManager> {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(createTrustStore())
        return tmf.trustManagers
    }

    private fun toPem(obj: Any): String {
        val writer = StringWriter()
        JcaPEMWriter(writer).use { pemWriter ->
            pemWriter.writeObject(obj)
        }
        return writer.toString()
    }
}

/**
 * Paths to certificate files written by [VcsimCertificateBundle.writeTo].
 */
public data class CertificateFiles(
    val serverCert: Path,
    val serverKey: Path,
    val caCert: Path
)
