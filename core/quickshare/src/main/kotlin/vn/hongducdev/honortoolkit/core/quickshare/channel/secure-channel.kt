package vn.hongducdev.honortoolkit.core.quickshare.channel

import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
import com.google.protobuf.ByteString
import com.google.security.cryptauth.lib.securegcm.DeviceToDeviceMessagesProto.DeviceToDeviceMessage
import com.google.security.cryptauth.lib.securegcm.SecureGcmProto.GcmMetadata
import com.google.security.cryptauth.lib.securegcm.SecureGcmProto.Type
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.EncScheme
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.Header
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.HeaderAndBody
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.SecureMessage
import com.google.security.cryptauth.lib.securemessage.SecureMessageProto.SigScheme
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import vn.hongducdev.honortoolkit.core.quickshare.crypto.SessionKeys
import vn.hongducdev.honortoolkit.core.quickshare.crypto.hmacSha256
import vn.hongducdev.honortoolkit.core.quickshare.transport.FramedConnection
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val CIPHER = "AES/CBC/PKCS5Padding"
private const val IV_LENGTH = 16

/** AES-256-CBC + HMAC-SHA256 envelope; the MAC is checked before any byte is decrypted. */
internal class SecureChannel(
    private val keys: SessionKeys,
    private val connection: FramedConnection,
) {
    private val random = SecureRandom()
    private var sendSequence = 0
    private var receiveSequence = 0

    @Synchronized
    fun send(frame: OfflineFrame) {
        sendSequence += 1
        val plain = DeviceToDeviceMessage.newBuilder()
            .setSequenceNumber(sendSequence)
            .setMessage(frame.toByteString())
            .build()
            .toByteArray()
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.encryptKey, "AES"), IvParameterSpec(iv))
        val metadata = GcmMetadata.newBuilder().setType(Type.DEVICE_TO_DEVICE_MESSAGE).setVersion(1).build()
        val header = Header.newBuilder()
            .setSignatureScheme(SigScheme.HMAC_SHA256)
            .setEncryptionScheme(EncScheme.AES_256_CBC)
            .setIv(ByteString.copyFrom(iv))
            .setPublicMetadata(metadata.toByteString())
            .build()
        val headerAndBody = HeaderAndBody.newBuilder()
            .setHeader(header)
            .setBody(ByteString.copyFrom(cipher.doFinal(plain)))
            .build()
            .toByteString()
        val message = SecureMessage.newBuilder()
            .setHeaderAndBody(headerAndBody)
            .setSignature(ByteString.copyFrom(hmacSha256(keys.sendHmacKey, headerAndBody.toByteArray())))
            .build()
        connection.writeFrame(message.toByteArray())
    }

    fun receive(raw: ByteArray): OfflineFrame {
        val message = SecureMessage.parseFrom(raw)
        if (!message.hasHeaderAndBody() || !message.hasSignature()) {
            throw NearbyProtocolException("Secure message is missing its body or signature")
        }
        val expected = hmacSha256(keys.recvHmacKey, message.headerAndBody.toByteArray())
        if (!MessageDigest.isEqual(expected, message.signature.toByteArray())) {
            throw NearbyProtocolException("Secure message signature mismatch")
        }
        val headerAndBody = HeaderAndBody.parseFrom(message.headerAndBody)
        val plain = try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keys.decryptKey, "AES"),
                IvParameterSpec(headerAndBody.header.iv.toByteArray()),
            )
            cipher.doFinal(headerAndBody.body.toByteArray())
        } catch (e: GeneralSecurityException) {
            throw NearbyProtocolException("Secure message could not be decrypted", e)
        }
        val device = DeviceToDeviceMessage.parseFrom(plain)
        if (!device.hasMessage() || !device.hasSequenceNumber()) {
            throw NearbyProtocolException("Device message is missing its payload or sequence number")
        }
        receiveSequence += 1
        if (device.sequenceNumber != receiveSequence) {
            throw NearbyProtocolException("Unexpected sequence number ${device.sequenceNumber}, expected $receiveSequence")
        }
        return OfflineFrame.parseFrom(device.message)
    }
}
