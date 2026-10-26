package vn.hongducdev.honortoolkit.core.quickshare.ukey2

import com.google.protobuf.ByteString
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Alert
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ClientFinished
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ClientInit
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2HandshakeCipher
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2Message
import com.google.security.cryptauth.lib.securegcm.UkeyProto.Ukey2ServerInit
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import vn.hongducdev.honortoolkit.core.quickshare.crypto.HandshakeResult
import vn.hongducdev.honortoolkit.core.quickshare.crypto.decodeP256PublicKey
import vn.hongducdev.honortoolkit.core.quickshare.crypto.deriveHandshakeResult
import vn.hongducdev.honortoolkit.core.quickshare.crypto.ecdhSharedX
import vn.hongducdev.honortoolkit.core.quickshare.crypto.encodeP256PublicKey
import vn.hongducdev.honortoolkit.core.quickshare.crypto.generateP256KeyPair
import vn.hongducdev.honortoolkit.core.quickshare.crypto.sha512
import java.security.MessageDigest
import java.security.SecureRandom

private const val NEXT_PROTOCOL = "AES_256_CBC-HMAC_SHA256"
private const val RANDOM_LENGTH = 32

/** A handshake rule was broken; [alert] is what the peer should be told before the socket closes. */
internal class Ukey2Exception(val alert: Ukey2Alert.AlertType, message: String) : NearbyProtocolException(message)

private fun message(type: Ukey2Message.Type, data: ByteString): ByteArray =
    Ukey2Message.newBuilder().setMessageType(type).setMessageData(data).build().toByteArray()

internal fun ukey2AlertMessage(type: Ukey2Alert.AlertType): ByteArray =
    message(Ukey2Message.Type.ALERT, Ukey2Alert.newBuilder().setType(type).build().toByteString())

/** Receiver side: answers a ClientInit with a ServerInit, then derives keys from the ClientFinish. */
internal class Ukey2Server {
    private val keyPair = generateP256KeyPair()
    private var commitment: ByteArray? = null
    private var clientInitRaw: ByteArray? = null
    private var serverInitRaw: ByteArray? = null

    fun onClientInit(raw: ByteArray): ByteArray {
        val wrapper = parse(raw)
        if (wrapper.messageType != Ukey2Message.Type.CLIENT_INIT) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_MESSAGE_TYPE, "Expected ClientInit")
        }
        val init = try {
            Ukey2ClientInit.parseFrom(wrapper.messageData)
        } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_MESSAGE_DATA, "Malformed ClientInit")
        }
        if (init.version != 1) throw Ukey2Exception(Ukey2Alert.AlertType.BAD_VERSION, "Unsupported version ${init.version}")
        if (init.random.size() != RANDOM_LENGTH) throw Ukey2Exception(Ukey2Alert.AlertType.BAD_RANDOM, "Bad random length")
        val chosen = init.cipherCommitmentsList.firstOrNull { it.handshakeCipher == Ukey2HandshakeCipher.P256_SHA512 }
            ?: throw Ukey2Exception(Ukey2Alert.AlertType.BAD_HANDSHAKE_CIPHER, "No P256_SHA512 commitment")
        if (init.nextProtocol != NEXT_PROTOCOL) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_NEXT_PROTOCOL, "Unsupported next protocol ${init.nextProtocol}")
        }
        commitment = chosen.commitment.toByteArray()
        clientInitRaw = raw

        val serverInit = Ukey2ServerInit.newBuilder()
            .setVersion(1)
            .setRandom(ByteString.copyFrom(ByteArray(RANDOM_LENGTH).also(SecureRandom()::nextBytes)))
            .setHandshakeCipher(Ukey2HandshakeCipher.P256_SHA512)
            .setPublicKey(ByteString.copyFrom(encodeP256PublicKey(keyPair.public)))
            .build()
        return message(Ukey2Message.Type.SERVER_INIT, serverInit.toByteString()).also { serverInitRaw = it }
    }

    fun onClientFinish(raw: ByteArray): HandshakeResult {
        val wrapper = parse(raw)
        if (wrapper.messageType != Ukey2Message.Type.CLIENT_FINISH) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_MESSAGE_TYPE, "Expected ClientFinish")
        }
        if (!MessageDigest.isEqual(commitment, sha512(raw))) {
            throw Ukey2Exception(Ukey2Alert.AlertType.INCORRECT_MESSAGE, "ClientFinish does not match the commitment")
        }
        val finish = Ukey2ClientFinished.parseFrom(wrapper.messageData)
        if (!finish.hasPublicKey()) throw Ukey2Exception(Ukey2Alert.AlertType.BAD_PUBLIC_KEY, "ClientFinish has no key")
        val peerKey = decodeP256PublicKey(finish.publicKey.toByteArray())
        return deriveHandshakeResult(
            sharedX = ecdhSharedX(keyPair.private, peerKey),
            clientInit = checkNotNull(clientInitRaw),
            serverInit = checkNotNull(serverInitRaw),
            isServer = true,
        )
    }
}

/** Sender side: commits to its key in ClientInit, reveals it in ClientFinish after seeing the ServerInit. */
internal class Ukey2Client {
    private val keyPair = generateP256KeyPair()
    private val clientFinishRaw: ByteArray = message(
        Ukey2Message.Type.CLIENT_FINISH,
        Ukey2ClientFinished.newBuilder()
            .setPublicKey(ByteString.copyFrom(encodeP256PublicKey(keyPair.public)))
            .build()
            .toByteString(),
    )
    private val clientInitRaw: ByteArray = message(
        Ukey2Message.Type.CLIENT_INIT,
        Ukey2ClientInit.newBuilder()
            .setVersion(1)
            .setRandom(ByteString.copyFrom(ByteArray(RANDOM_LENGTH).also(SecureRandom()::nextBytes)))
            .setNextProtocol(NEXT_PROTOCOL)
            .addCipherCommitments(
                Ukey2ClientInit.CipherCommitment.newBuilder()
                    .setHandshakeCipher(Ukey2HandshakeCipher.P256_SHA512)
                    .setCommitment(ByteString.copyFrom(sha512(clientFinishRaw))),
            )
            .build()
            .toByteString(),
    )

    fun clientInit(): ByteArray = clientInitRaw

    /** Returns the ClientFinish to send and the derived keys. */
    fun onServerInit(raw: ByteArray): Pair<ByteArray, HandshakeResult> {
        val wrapper = parse(raw)
        if (wrapper.messageType == Ukey2Message.Type.ALERT) {
            throw NearbyProtocolException("Peer rejected the handshake: ${Ukey2Alert.parseFrom(wrapper.messageData).type}")
        }
        if (wrapper.messageType != Ukey2Message.Type.SERVER_INIT) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_MESSAGE_TYPE, "Expected ServerInit")
        }
        val init = Ukey2ServerInit.parseFrom(wrapper.messageData)
        if (init.version != 1) throw Ukey2Exception(Ukey2Alert.AlertType.BAD_VERSION, "Unsupported version ${init.version}")
        if (init.random.size() != RANDOM_LENGTH) throw Ukey2Exception(Ukey2Alert.AlertType.BAD_RANDOM, "Bad random length")
        if (init.handshakeCipher != Ukey2HandshakeCipher.P256_SHA512) {
            throw Ukey2Exception(Ukey2Alert.AlertType.BAD_HANDSHAKE_CIPHER, "Unexpected handshake cipher")
        }
        val peerKey = decodeP256PublicKey(init.publicKey.toByteArray())
        val result = deriveHandshakeResult(
            sharedX = ecdhSharedX(keyPair.private, peerKey),
            clientInit = clientInitRaw,
            serverInit = raw,
            isServer = false,
        )
        return clientFinishRaw to result
    }
}

private fun parse(raw: ByteArray): Ukey2Message = try {
    Ukey2Message.parseFrom(raw)
} catch (e: com.google.protobuf.InvalidProtocolBufferException) {
    throw Ukey2Exception(Ukey2Alert.AlertType.BAD_MESSAGE, "Malformed UKEY2 message")
}
