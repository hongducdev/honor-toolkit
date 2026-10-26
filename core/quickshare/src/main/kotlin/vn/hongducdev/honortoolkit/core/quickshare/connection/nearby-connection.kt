package vn.hongducdev.honortoolkit.core.quickshare.connection

import com.google.android.gms.nearby.sharing.Protocol.Frame as SharingFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader.PayloadType
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame
import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import vn.hongducdev.honortoolkit.core.quickshare.channel.SecureChannel
import vn.hongducdev.honortoolkit.core.quickshare.transport.FramedConnection
import vn.hongducdev.honortoolkit.core.quickshare.transport.MAX_FRAME_LENGTH
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.logging.Level
import java.util.logging.Logger

private const val DRAIN_TIMEOUT_MILLIS = 1_500

/**
 * Plumbing shared by both roles: framing, the encrypted channel, keep-alives, bytes payload
 * reassembly and the shutdown sequence. Subclasses drive the handshake and the sharing frames.
 */
internal abstract class NearbyConnection(private val socket: Socket) {
    protected val log: Logger = Logger.getLogger(javaClass.name)
    protected val framed = FramedConnection(socket.getInputStream(), socket.getOutputStream())
    protected var channel: SecureChannel? = null

    @Volatile
    private var encrypted = false

    @Volatile
    private var draining = false

    @Volatile
    protected var outcome: TransferOutcome? = null
    private var keepAliveThread: Thread? = null
    private val bytePayloads = HashMap<Long, ByteArrayOutputStream>()

    protected abstract fun begin()

    protected abstract fun handlePlainFrame(raw: ByteArray)

    protected abstract fun handleSharingFrame(frame: SharingFrame)

    protected abstract fun handleFilePayload(transfer: PayloadTransferFrame)

    /** Return true when [data] belongs to the subclass and must not be parsed as a sharing frame. */
    protected open fun handleBytesPayload(id: Long, data: ByteArray): Boolean = false

    fun run(): TransferOutcome {
        socket.soTimeout = KEEP_ALIVE_TIMEOUT_MILLIS
        try {
            begin()
            readLoop()
        } catch (e: SocketTimeoutException) {
            if (outcome == null) outcome = TransferOutcome.Failed(e)
        } catch (e: IOException) {
            if (outcome == null) outcome = TransferOutcome.Failed(e)
        } catch (e: NearbyProtocolException) {
            if (outcome == null) outcome = TransferOutcome.Failed(e)
        } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
            if (outcome == null) outcome = TransferOutcome.Failed(NearbyProtocolException("Malformed frame", e))
        } finally {
            shutdown()
        }
        return outcome ?: TransferOutcome.Failed(NearbyProtocolException("Connection closed before the transfer finished"))
    }

    fun cancel() {
        if (encrypted) {
            runCatching { sendSharing(cancelFrame()) }.onFailure { log.log(Level.FINE, "Cancel frame not sent", it) }
        }
        finish(TransferOutcome.Cancelled(byPeer = false))
    }

    private fun readLoop() {
        while (true) {
            val raw = try {
                framed.readFrame()
            } catch (e: SocketTimeoutException) {
                if (draining) return
                throw e
            } ?: return
            val secure = channel
            if (secure != null && encrypted) dispatch(secure.receive(raw)) else handlePlainFrame(raw)
            if (draining && socket.isClosed) return
        }
    }

    protected fun startEncryptedPhase() {
        encrypted = true
        keepAliveThread = Thread({ keepAliveLoop() }, "quickshare-keepalive").apply {
            isDaemon = true
            start()
        }
    }

    private fun keepAliveLoop() {
        try {
            while (!Thread.currentThread().isInterrupted && !socket.isClosed) {
                Thread.sleep(KEEP_ALIVE_INTERVAL_MILLIS.toLong())
                sendOffline(keepAliveFrame(ack = false))
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: IOException) {
            log.log(Level.FINE, "Keep-alive stopped", e)
        }
    }

    protected fun sendPlain(frame: OfflineFrame) = framed.writeFrame(frame.toByteArray())

    protected fun sendOffline(frame: OfflineFrame) {
        val secure = channel
        if (secure != null && encrypted) secure.send(frame) else sendPlain(frame)
    }

    /** Negotiation messages travel as a bytes payload: one chunk with the data, one empty chunk to end it. */
    protected fun sendSharing(frame: SharingFrame) {
        val data = frame.toByteArray()
        val id = randomPayloadId()
        sendOffline(payloadFrame(id, PayloadType.BYTES, data.size.toLong(), 0, data, last = false))
        sendOffline(payloadFrame(id, PayloadType.BYTES, data.size.toLong(), data.size.toLong(), null, last = true))
    }

    private fun dispatch(frame: OfflineFrame) {
        if (!frame.hasV1()) return
        val v1 = frame.v1
        when (v1.type) {
            V1Frame.FrameType.PAYLOAD_TRANSFER -> handlePayload(v1.payloadTransfer)
            V1Frame.FrameType.KEEP_ALIVE -> if (!v1.keepAlive.ack) sendOffline(keepAliveFrame(ack = true))
            V1Frame.FrameType.DISCONNECTION -> {
                if (v1.disconnection.requestSafeToDisconnect) sendOffline(disconnectionFrame(ackSafe = true))
                if (outcome == null) outcome = TransferOutcome.Cancelled(byPeer = true)
                closeSocket()
            }
            else -> log.fine("Ignoring frame ${v1.type}")
        }
    }

    private fun handlePayload(transfer: PayloadTransferFrame) {
        val header = transfer.payloadHeader
        when (header.type) {
            PayloadType.BYTES -> assembleBytes(transfer)
            PayloadType.FILE -> handleFilePayload(transfer)
            else -> log.fine("Ignoring payload type ${header.type}")
        }
    }

    private fun assembleBytes(transfer: PayloadTransferFrame) {
        val id = transfer.payloadHeader.id
        if (transfer.payloadHeader.totalSize > MAX_FRAME_LENGTH) {
            bytePayloads.remove(id)
            throw NearbyProtocolException("Bytes payload too large: ${transfer.payloadHeader.totalSize}")
        }
        val buffer = bytePayloads.getOrPut(id) { ByteArrayOutputStream() }
        if (transfer.payloadChunk.offset != buffer.size().toLong()) {
            bytePayloads.remove(id)
            throw NearbyProtocolException("Unexpected chunk offset ${transfer.payloadChunk.offset}, expected ${buffer.size()}")
        }
        if (transfer.payloadChunk.hasBody()) transfer.payloadChunk.body.writeTo(buffer)
        if (transfer.payloadChunk.flags and LAST_CHUNK_FLAG == 0) return
        bytePayloads.remove(id)
        val data = buffer.toByteArray()
        if (!handleBytesPayload(id, data)) handleSharingFrame(SharingFrame.parseFrom(data))
    }

    /** Records the result and says goodbye; the read loop then waits briefly for the peer to close first. */
    @Synchronized
    protected fun finish(result: TransferOutcome) {
        if (outcome == null) outcome = result
        if (draining) return
        draining = true
        try {
            if (encrypted && result !is TransferOutcome.Failed) {
                sendOffline(disconnectionFrame(requestSafe = true))
                socket.soTimeout = DRAIN_TIMEOUT_MILLIS
                return
            }
        } catch (e: IOException) {
            log.log(Level.FINE, "Disconnection not sent", e)
        }
        closeSocket()
    }

    private fun closeSocket() {
        try {
            socket.close()
        } catch (e: IOException) {
            log.log(Level.FINE, "Socket close failed", e)
        }
    }

    private fun shutdown() {
        keepAliveThread?.interrupt()
        closeSocket()
    }
}
