package vn.hongducdev.honortoolkit.core.quickshare.crypto

import java.security.MessageDigest

internal class SessionKeys(
    val encryptKey: ByteArray,
    val sendHmacKey: ByteArray,
    val decryptKey: ByteArray,
    val recvHmacKey: ByteArray,
)

internal class HandshakeResult(
    val keys: SessionKeys,
    val authString: ByteArray,
    val pin: String,
)

private val D2D_SALT = sha256("D2D".toByteArray())
private val SECURE_MESSAGE_SALT = sha256("SecureMessage".toByteArray())
private const val PIN_MODULUS = 9973
private const val PIN_MULTIPLIER = 31

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

/**
 * Turns the ECDH x-coordinate and the two raw UKEY2 handshake messages into the traffic keys and the
 * 4-digit PIN. Keys are stored from the local point of view, so the client swaps the server's roles.
 */
internal fun deriveHandshakeResult(
    sharedX: ByteArray,
    clientInit: ByteArray,
    serverInit: ByteArray,
    isServer: Boolean,
): HandshakeResult {
    val derivedSecret = sha256(sharedX)
    val info = clientInit + serverInit
    val authString = hkdfSha256(derivedSecret, "UKEY2 v1 auth".toByteArray(), info, 32)
    val nextSecret = hkdfSha256(derivedSecret, "UKEY2 v1 next".toByteArray(), info, 32)

    val d2dClient = hkdfSha256(nextSecret, D2D_SALT, "client".toByteArray(), 32)
    val d2dServer = hkdfSha256(nextSecret, D2D_SALT, "server".toByteArray(), 32)
    val clientEnc = hkdfSha256(d2dClient, SECURE_MESSAGE_SALT, "ENC:2".toByteArray(), 32)
    val clientSig = hkdfSha256(d2dClient, SECURE_MESSAGE_SALT, "SIG:1".toByteArray(), 32)
    val serverEnc = hkdfSha256(d2dServer, SECURE_MESSAGE_SALT, "ENC:2".toByteArray(), 32)
    val serverSig = hkdfSha256(d2dServer, SECURE_MESSAGE_SALT, "SIG:1".toByteArray(), 32)

    val keys = if (isServer) {
        SessionKeys(encryptKey = serverEnc, sendHmacKey = serverSig, decryptKey = clientEnc, recvHmacKey = clientSig)
    } else {
        SessionKeys(encryptKey = clientEnc, sendHmacKey = clientSig, decryptKey = serverEnc, recvHmacKey = serverSig)
    }
    return HandshakeResult(keys, authString, pinFromAuthString(authString))
}

internal fun pinFromAuthString(authString: ByteArray): String {
    var hash = 0
    var multiplier = 1
    for (byte in authString) {
        hash = (hash + byte.toInt() * multiplier) % PIN_MODULUS
        multiplier = (multiplier * PIN_MULTIPLIER) % PIN_MODULUS
    }
    return Math.abs(hash).toString().padStart(4, '0')
}
