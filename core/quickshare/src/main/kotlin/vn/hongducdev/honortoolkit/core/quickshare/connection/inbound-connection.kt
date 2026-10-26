package vn.hongducdev.honortoolkit.core.quickshare.connection

import com.google.android.gms.nearby.sharing.Protocol.ConnectionResponseFrame.Status
import com.google.android.gms.nearby.sharing.Protocol.Frame as SharingFrame
import com.google.android.gms.nearby.sharing.Protocol.IntroductionFrame
import com.google.android.gms.nearby.sharing.Protocol.TextMetadata
import com.google.android.gms.nearby.sharing.Protocol.V1Frame.FrameType as SharingType
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame.FrameType
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import vn.hongducdev.honortoolkit.core.quickshare.channel.SecureChannel
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.DeviceType
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointInfo
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.Ukey2Exception
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.Ukey2Server
import vn.hongducdev.honortoolkit.core.quickshare.ukey2.ukey2AlertMessage
import java.net.Socket

private enum class InboundPhase { REQUEST, CLIENT_INIT, CLIENT_FINISH, CONNECTION_RESPONSE, NEGOTIATING, AWAITING_CONSENT, RECEIVING }

private class IncomingFileState(val file: OfferedFile) {
    var sink: FileSink? = null
    var written = 0L
    var done = false
}

internal class InboundSession(
    socket: Socket,
    private val listener: InboundListener,
) : NearbyConnection(socket) {
    private var phase = InboundPhase.REQUEST
    private val handshake = Ukey2Server()
    private var device = RemoteDevice("", DeviceType.UNKNOWN)
    private var pin = ""
    private val files = LinkedHashMap<Long, IncomingFileState>()
    private var textPayloadId: Long? = null
    private var totalBytes = 0L
    private var receivedBytes = 0L

    @Volatile
    private var decided = false

    override fun begin() = Unit

    override fun handlePlainFrame(raw: ByteArray) {
        try {
            when (phase) {
                InboundPhase.REQUEST -> onConnectionRequest(OfflineFrame.parseFrom(raw))
                InboundPhase.CLIENT_INIT -> {
                    sendRaw(handshake.onClientInit(raw))
                    phase = InboundPhase.CLIENT_FINISH
                }
                InboundPhase.CLIENT_FINISH -> {
                    val result = handshake.onClientFinish(raw)
                    channel = SecureChannel(result.keys, framed)
                    pin = result.pin
                    phase = InboundPhase.CONNECTION_RESPONSE
                }
                InboundPhase.CONNECTION_RESPONSE -> onConnectionResponse(OfflineFrame.parseFrom(raw))
                else -> throw NearbyProtocolException("Unexpected plain frame in $phase")
            }
        } catch (e: Ukey2Exception) {
            runCatching { sendRaw(ukey2AlertMessage(e.alert)) }
            throw e
        }
    }

    private fun sendRaw(frame: ByteArray) = framed.writeFrame(frame)

    private fun onConnectionRequest(frame: OfflineFrame) {
        if (!frame.hasV1() || frame.v1.type != FrameType.CONNECTION_REQUEST || !frame.v1.connectionRequest.hasEndpointInfo()) {
            throw NearbyProtocolException("Expected a connection request with endpoint info")
        }
        val info = EndpointInfo.parse(frame.v1.connectionRequest.endpointInfo.toByteArray())
            ?: throw NearbyProtocolException("Endpoint info could not be read")
        device = RemoteDevice(info.name ?: frame.v1.connectionRequest.endpointName, info.deviceType)
        phase = InboundPhase.CLIENT_INIT
    }

    private fun onConnectionResponse(frame: OfflineFrame) {
        if (!frame.hasV1() || frame.v1.type != FrameType.CONNECTION_RESPONSE) {
            throw NearbyProtocolException("Expected a connection response")
        }
        sendPlain(connectionResponseFrame())
        startEncryptedPhase()
        sendSharing(pairedKeyEncryptionFrame())
        phase = InboundPhase.NEGOTIATING
    }

    override fun handleSharingFrame(frame: SharingFrame) {
        if (!frame.hasV1()) return
        when (frame.v1.type) {
            SharingType.CANCEL -> finish(TransferOutcome.Cancelled(byPeer = true))
            SharingType.PAIRED_KEY_ENCRYPTION -> sendSharing(pairedKeyResultFrame())
            SharingType.PAIRED_KEY_RESULT -> Unit
            SharingType.INTRODUCTION -> onIntroduction(frame.v1.introduction)
            else -> log.fine("Ignoring sharing frame ${frame.v1.type}")
        }
    }

    private fun onIntroduction(introduction: IntroductionFrame) {
        if (phase != InboundPhase.NEGOTIATING) throw NearbyProtocolException("Introduction received in $phase")
        val texts = introduction.textMetadataList
        val supportedText = texts.singleOrNull()?.takeIf { it.type == TextMetadata.Type.URL || it.type == TextMetadata.Type.TEXT }
        val unsupported = introduction.wifiCredentialsMetadataCount > 0 || introduction.appMetadataCount > 0 ||
            (texts.isNotEmpty() && (supportedText == null || introduction.fileMetadataCount > 0))
        if (unsupported || (introduction.fileMetadataCount == 0 && supportedText == null)) {
            refuse(Status.UNSUPPORTED_ATTACHMENT_TYPE, RejectReason.UNSUPPORTED_TYPE)
            return
        }
        for (meta in introduction.fileMetadataList) {
            val file = OfferedFile(meta.name, meta.size, meta.mimeType, meta.payloadId)
            files[meta.payloadId] = IncomingFileState(file)
            totalBytes += meta.size
        }
        supportedText?.let { textPayloadId = it.payloadId }
        val offer = TransferOffer(
            device = device,
            pin = pin,
            files = files.values.map { it.file },
            text = supportedText?.let { OfferedText(it.textTitle, it.type == TextMetadata.Type.URL, it.payloadId) },
        )
        phase = InboundPhase.AWAITING_CONSENT
        listener.onOffer(offer, Responder())
    }

    private fun refuse(status: Status, reason: RejectReason) {
        decided = true
        sendSharing(responseFrame(status))
        finish(TransferOutcome.Rejected(reason))
    }

    private inner class Responder : ConsentResponder {
        override fun accept() = decide { acceptTransfer() }

        override fun reject() = decide { refuse(Status.REJECT, RejectReason.DECLINED) }

        private fun decide(action: () -> Unit) {
            synchronized(this@InboundSession) {
                if (decided) return
                decided = true
            }
            try {
                action()
            } catch (e: java.io.IOException) {
                finish(TransferOutcome.Failed(e))
            }
        }
    }

    private fun acceptTransfer() {
        for (state in files.values) state.sink = listener.openSink(state.file)
        phase = InboundPhase.RECEIVING
        sendSharing(responseFrame(Status.ACCEPT))
        if (files.isEmpty() && textPayloadId == null) finish(TransferOutcome.Completed)
    }

    override fun handleFilePayload(transfer: PayloadTransferFrame) {
        if (phase != InboundPhase.RECEIVING) throw NearbyProtocolException("File data received in $phase")
        val state = files[transfer.payloadHeader.id] ?: throw NearbyProtocolException("Unknown file payload ${transfer.payloadHeader.id}")
        val chunk = transfer.payloadChunk
        if (chunk.offset != state.written) throw NearbyProtocolException("Invalid file offset ${chunk.offset}, expected ${state.written}")
        val body = chunk.body.toByteArray()
        if (state.written + body.size > state.file.size) throw NearbyProtocolException("File is larger than announced")
        if (body.isNotEmpty()) {
            state.sink?.write(body, body.size)
            state.written += body.size
            receivedBytes += body.size
            listener.onProgress(receivedBytes, totalBytes)
        }
        if (chunk.flags and LAST_CHUNK_FLAG != 0) completeFile(state)
    }

    private fun completeFile(state: IncomingFileState) {
        if (state.written != state.file.size) throw NearbyProtocolException("File ended early: ${state.written} of ${state.file.size}")
        state.sink?.commit()
        state.done = true
        if (files.values.all { it.done }) finish(TransferOutcome.Completed)
    }

    override fun handleBytesPayload(id: Long, data: ByteArray): Boolean {
        if (id != textPayloadId) return false
        listener.onTextReceived(String(data, Charsets.UTF_8))
        finish(TransferOutcome.Completed)
        return true
    }

    /** Partial files must not stay on disk if the transfer did not complete. */
    fun discardUnfinished() {
        for (state in files.values) if (!state.done) state.sink?.abort()
    }
}

/**
 * The receiving side: the peer connects to our advertised port and sends files once the user agrees.
 * [run] blocks until the transfer ends; call [cancel] from another thread to stop it.
 */
public class InboundConnection(socket: Socket, listener: InboundListener) {
    private val session = InboundSession(socket, listener)

    public fun run(): TransferOutcome {
        val outcome = session.run()
        if (outcome != TransferOutcome.Completed) session.discardUnfinished()
        return outcome
    }

    public fun cancel(): Unit = session.cancel()
}
