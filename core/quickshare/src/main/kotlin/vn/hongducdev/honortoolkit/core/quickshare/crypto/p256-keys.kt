package vn.hongducdev.honortoolkit.core.quickshare.crypto

import com.google.protobuf.ByteString
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.EcP256PublicKey
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.GenericPublicKey
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.PublicKeyType
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve
import javax.crypto.KeyAgreement

private const val CURVE = "secp256r1"
private const val COORDINATE_BYTES = 32

private val curveParameters: ECParameterSpec by lazy {
    val parameters = AlgorithmParameters.getInstance("EC")
    parameters.init(ECGenParameterSpec(CURVE))
    parameters.getParameterSpec(ECParameterSpec::class.java)
}

internal fun generateP256KeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec(CURVE))
    return generator.generateKeyPair()
}

/** Coordinates are written as signed big-endian integers, so they can carry a leading zero byte. */
internal fun encodeP256PublicKey(key: PublicKey): ByteArray {
    val point = (key as ECPublicKey).w
    val ecKey = EcP256PublicKey.newBuilder()
        .setX(ByteString.copyFrom(point.affineX.toByteArray()))
        .setY(ByteString.copyFrom(point.affineY.toByteArray()))
    return GenericPublicKey.newBuilder()
        .setType(PublicKeyType.EC_P256)
        .setEcP256PublicKey(ecKey)
        .build()
        .toByteArray()
}

internal fun decodeP256PublicKey(encoded: ByteArray): PublicKey {
    val generic = GenericPublicKey.parseFrom(encoded)
    if (!generic.hasEcP256PublicKey()) throw NearbyProtocolException("Peer key is not an EC P-256 key")
    val x = unsignedCoordinate(generic.ecP256PublicKey.x.toByteArray())
    val y = unsignedCoordinate(generic.ecP256PublicKey.y.toByteArray())
    if (!isOnCurve(curveParameters.curve, x, y)) throw NearbyProtocolException("Peer key is not on the P-256 curve")
    return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), curveParameters))
}

/**
 * Returns the minimal magnitude of x, not the fixed 32-byte form: NearDrop hashes it that way, so a
 * leading zero byte is dropped. Unverified against GMS for the roughly 1-in-256 case with a zero byte.
 */
internal fun ecdhSharedX(privateKey: PrivateKey, peerKey: PublicKey): ByteArray {
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(privateKey)
    agreement.doPhase(peerKey, true)
    return magnitude(BigInteger(1, agreement.generateSecret()))
}

internal fun magnitude(value: BigInteger): ByteArray {
    val bytes = value.toByteArray()
    return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
}

private fun unsignedCoordinate(raw: ByteArray): BigInteger {
    val trimmed = if (raw.size > COORDINATE_BYTES) raw.copyOfRange(raw.size - COORDINATE_BYTES, raw.size) else raw
    return BigInteger(1, trimmed)
}

private fun isOnCurve(curve: EllipticCurve, x: BigInteger, y: BigInteger): Boolean {
    val p = (curve.field as java.security.spec.ECFieldFp).p
    if (x.signum() < 0 || x >= p || y.signum() < 0 || y >= p) return false
    val left = y.multiply(y).mod(p)
    val right = x.pow(3).add(curve.a.multiply(x)).add(curve.b).mod(p)
    return left == right
}
