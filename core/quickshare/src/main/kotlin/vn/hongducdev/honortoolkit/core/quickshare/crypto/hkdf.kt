package vn.hongducdev.honortoolkit.core.quickshare.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val HMAC_SHA256 = "HmacSHA256"
private const val HASH_LENGTH = 32

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance(HMAC_SHA256)
    mac.init(SecretKeySpec(key, HMAC_SHA256))
    return mac.doFinal(data)
}

/** RFC 5869 HKDF-SHA256. An empty salt is replaced by a zero-filled block, as the RFC specifies. */
internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    require(length in 1..255 * HASH_LENGTH) { "HKDF output length out of range: $length" }
    val prk = hmacSha256(if (salt.isEmpty()) ByteArray(HASH_LENGTH) else salt, ikm)
    val okm = ByteArray(length)
    var block = ByteArray(0)
    var written = 0
    var counter = 1
    while (written < length) {
        block = hmacSha256(prk, block + info + byteArrayOf(counter.toByte()))
        val take = minOf(block.size, length - written)
        System.arraycopy(block, 0, okm, written, take)
        written += take
        counter += 1
    }
    return okm
}
