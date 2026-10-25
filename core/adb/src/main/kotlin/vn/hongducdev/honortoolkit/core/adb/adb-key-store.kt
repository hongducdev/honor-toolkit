package vn.hongducdev.honortoolkit.core.adb

import android.content.Context
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** Keeps the ADB identity in the app's private storage so a pairing survives app restarts. */
internal class AdbKeyStore(context: Context) {
    val privateKey: PrivateKey
    val certificate: Certificate

    init {
        val keyFile = File(context.filesDir, KEY_FILE)
        val certFile = File(context.filesDir, CERT_FILE)
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(KEY_SIZE_BITS, SecureRandom.getInstance("SHA1PRNG"))
            val pair = generator.generateKeyPair()
            privateKey = pair.private
            certificate = selfSign(pair.public, pair.private)
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    private fun selfSign(publicKey: PublicKey, signer: PrivateKey): Certificate {
        val subject = X500Name("CN=honor-toolkit")
        val notBefore = Date()
        val notAfter = Date(System.currentTimeMillis() + VALIDITY_MS)
        val serial = BigInteger.valueOf(SecureRandom().nextLong() and Long.MAX_VALUE)
        val contentSigner = JcaContentSignerBuilder("SHA512withRSA").build(signer)
        val holder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, publicKey).build(contentSigner)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private companion object {
        const val KEY_FILE = "adb-key.pk8"
        const val CERT_FILE = "adb-cert.der"
        const val KEY_SIZE_BITS = 2048
        const val VALIDITY_MS = 10L * 365 * 86_400_000
    }
}
