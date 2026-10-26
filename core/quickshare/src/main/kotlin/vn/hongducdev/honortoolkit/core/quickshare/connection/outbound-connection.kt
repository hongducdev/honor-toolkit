package vn.hongducdev.honortoolkit.core.quickshare.connection

import com.google.android.gms.nearby.sharing.Protocol.ConnectionResponseFrame.Status
import com.google.android.gms.nearby.sharing.Protocol.FileMetadata
import com.google.android.gms.nearby.sharing.Protocol.Frame as SharingFrame
import com.google.android.gms.nearby.sharing.Protocol.IntroductionFrame
import com.google.android.gms.nearby.sharing.Protocol.V1Frame.FrameType as SharingType
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader.PayloadType
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame.FrameType
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import vn.hongducdev.honortoolkit.core.quickshare.channel.SecureChannel
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.DeviceType
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointInfo
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointNaming
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.Ukey2Client
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.Ukey2Exception
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.ukey2AlertMessage
import java.io.IOException
import java.net.Socket
import java.util.logging.Level

private const val CHUNK_SIZE = 512 * 1024
private val UNSAFE_NAME_CHARACTERS = Regex("[/\\\\?%*:|\"<>=]")

private enum class OutboundPhase { SERVER_INIT, CONNECTION_RESPONSE, NEGOTIATING, AWAITING_CONSENT, SENDING }

internal class OutboundSession(
    socket: Socket,
    private val localName: String,
    private val files: List<OutgoingFile>,
    private val listener: OutboundListener,
) : NearbyConnection(socket) {
    private var phase = OutboundPhase.SERVER_INIT
    private val handshake = Ukey2Client()
    private val payloadIds = files.map { randomPayloadId() }
    private var answeredPairedKey = false
    private var peerPairedKeyResult = false
    private var sentIntroduction = false
    private val totalBytes = files.sumOf { it.size }

    override fun begin() {
        val info = EndpointInfo(localName, DeviceType.PHONE).serialize()
        sendPlain(connectionRequestFrame(EndpointNaming.newEndpointId(), localName, info))
        framed.writeFrame(handshake.clientInit())
    }

    override fun handlePlainFrame(raw: ByteArray) {
        try {
            when (phase) {
                OutboundPhase.SERVER_INIT -> onServerInit(raw)
                OutboundPhase.CONNECTION_RESPONSE -> onConnectionResponse(OfflineFrame.parseFrom(raw))
                else -> throw NearbyProtocolException("Unexpected plain frame in $phase")
            }
        } catch (e: Ukey2Exception) {
            runCatching { framed.writeFrame(ukey2AlertMessage(e.alert)) }
            throw e
        }
    }

    private fun onServerInit(raw: ByteArray) {
        val (clientFinish, result) = handshake.onServerInit(raw)
        channel = SecureChannel(result.keys, framed)
        framed.writeFrame(clientFinish)
        sendPlain(connectionResponseFrame())
        listener.onPin(result.pin)
        phase = OutboundPhase.CONNECTION_RESPONSE
    }

    private fun onConnectionResponse(frame: OfflineFrame) {
        if (!frame.hasV1() || frame.v1.type != FrameType.CONNECTION_RESPONSE) {
            throw NearbyProtocolException("Expected a connection response")
        }
        val response = frame.v1.connectionResponse
        if (response.response != com.google.location.nearby.connections.proto.OfflineWireFormatsProto.ConnectionResponseFrame.ResponseStatus.ACCEPT) {
            throw NearbyProtocolException("The receiver refused the connection")
        }
        startEncryptedPhase()
        sendSharing(pairedKeyEncryptionFrame())
        phase = OutboundPhase.NEGOTIATING
    }

    override fun handleSharingFrame(frame: SharingFrame) {
        if (!frame.hasV1()) return
        when (frame.v1.type) {
            SharingType.CANCEL -> finish(TransferOutcome.Cancelled(byPeer = true))
            SharingType.PAIRED_KEY_ENCRYPTION -> {
                sendSharing(pairedKeyResultFrame())
                answeredPairedKey = true
                sendIntroductionWhenReady()
            }
            SharingType.PAIRED_KEY_RESULT -> {
                peerPairedKeyResult = true
                sendIntroductionWhenReady()
            }
            SharingType.RESPONSE -> onResponse(frame.v1.connectionResponse.status)
            else -> log.fine("Ignoring sharing frame ${frame.v1.type}")
        }
    }

    private fun sendIntroductionWhenReady() {
        if (sentIntroduction || !answeredPairedKey || !peerPairedKeyResult) return
        sentIntroduction = true
        val introduction = IntroductionFrame.newBuilder().setUseCase(IntroductionFrame.SharingUseCase.NEARBY_SHARE)
        files.forEachIndexed { index, file ->
            introduction.addFileMetadata(
                FileMetadata.newBuilder()
                    .setName(UNSAFE_NAME_CHARACTERS.replace(file.name, "_"))
                    .setType(fileKind(file))
                    .setPayloadId(payloadIds[index])
                    .setSize(file.size)
                    .setMimeType(file.mimeType)
                    .setId(payloadIds[index]),
            )
        }
        sendSharing(introductionFrame(introduction.build()))
        phase = OutboundPhase.AWAITING_CONSENT
    }

    private fun fileKind(file: OutgoingFile): FileMetadata.Type = when {
        file.mimeType.startsWith("image/") -> FileMetadata.Type.IMAGE
        file.mimeType.startsWith("video/") -> FileMetadata.Type.VIDEO
        file.mimeType.startsWith("audio/") -> FileMetadata.Type.AUDIO
        file.name.endsWith(".apk", ignoreCase = true) -> FileMetadata.Type.ANDROID_APP
        else -> FileMetadata.Type.UNKNOWN
    }

    private fun onResponse(status: Status) {
        if (phase != OutboundPhase.AWAITING_CONSENT) throw NearbyProtocolException("Response received in $phase")
        when (status) {
            Status.ACCEPT -> {
                phase = OutboundPhase.SENDING
                listener.onAccepted()
                Thread({ sendFiles() }, "quickshare-send").apply { isDaemon = true }.start()
            }
            Status.NOT_ENOUGH_SPACE -> finish(TransferOutcome.Rejected(RejectReason.NOT_ENOUGH_SPACE))
            Status.UNSUPPORTED_ATTACHMENT_TYPE -> finish(TransferOutcome.Rejected(RejectReason.UNSUPPORTED_TYPE))
            Status.TIMED_OUT -> finish(TransferOutcome.Rejected(RejectReason.TIMED_OUT))
            else -> finish(TransferOutcome.Rejected(RejectReason.DECLINED))
        }
    }

    private fun sendFiles() {
        try {
            var sent = 0L
            val buffer = ByteArray(CHUNK_SIZE)
            files.forEachIndexed { index, file ->
                var offset = 0L
                file.open().use { input ->
                    while (offset < file.size) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), file.size - offset).toInt())
                        if (read < 0) throw NearbyProtocolException("${file.name} ended before ${file.size} bytes")
                        sendOffline(filePayloadFrame(payloadIds[index], file.size, offset, buffer.copyOf(read), last = false))
                        offset += read
                        sent += read
                        listener.onProgress(sent, totalBytes)
                    }
                }
                sendOffline(filePayloadFrame(payloadIds[index], file.size, offset, null, last = true))
            }
            finish(TransferOutcome.Completed)
        } catch (e: IOException) {
            log.log(Level.FINE, "Sending failed", e)
            finish(TransferOutcome.Failed(e))
        } catch (e: NearbyProtocolException) {
            finish(TransferOutcome.Failed(e))
        }
    }

    private fun filePayloadFrame(id: Long, size: Long, offset: Long, body: ByteArray?, last: Boolean) =
        payloadFrame(id, PayloadType.FILE, size, offset, body, last)

    override fun handleFilePayload(transfer: PayloadTransferFrame) {
        log.fine("Ignoring file data from the receiver")
    }
}

/**
 * The sending side: connects to a receiver's address, shows [OutboundListener.onPin] for comparison and
 * streams [files] once the receiver accepts. [run] blocks until the transfer ends.
 */
public class OutboundConnection(
    socket: Socket,
    localName: String,
    files: List<OutgoingFile>,
    listener: OutboundListener,
) {
    private val session = OutboundSession(socket, localName, files, listener)

    public fun run(): TransferOutcome = session.run()

    public fun cancel(): Unit = session.cancel()
}
