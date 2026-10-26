package vn.hongducdev.honortoolkit.core.quickshare.connection

import vn.hongducdev.honortoolkit.core.quickshare.endpoint.DeviceType
import java.io.InputStream

public class RemoteDevice(public val name: String, public val type: DeviceType)

public class OfferedFile(
    public val name: String,
    public val size: Long,
    public val mimeType: String,
    internal val payloadId: Long,
)

public class OfferedText(public val title: String, public val isUrl: Boolean, internal val payloadId: Long)

/** [pin] is the 4-digit code both screens show so the user can tell the right devices are talking. */
public class TransferOffer(
    public val device: RemoteDevice,
    public val pin: String,
    public val files: List<OfferedFile>,
    public val text: OfferedText?,
)

public enum class RejectReason { DECLINED, NOT_ENOUGH_SPACE, UNSUPPORTED_TYPE, TIMED_OUT }

public sealed interface TransferOutcome {
    public data object Completed : TransferOutcome

    public data class Rejected(val reason: RejectReason) : TransferOutcome

    public data class Cancelled(val byPeer: Boolean) : TransferOutcome

    public data class Failed(val cause: Throwable) : TransferOutcome
}

/** Destination of one received file. [abort] must remove whatever [write] produced. */
public interface FileSink {
    public fun write(buffer: ByteArray, length: Int)

    public fun commit()

    public fun abort()
}

/** Answers a [TransferOffer]; safe to call from any thread, only the first call counts. */
public interface ConsentResponder {
    public fun accept()

    public fun reject()
}

public interface InboundListener {
    /** Runs on the connection thread; answer later through [responder] without blocking here. */
    public fun onOffer(offer: TransferOffer, responder: ConsentResponder)

    public fun openSink(file: OfferedFile): FileSink

    public fun onProgress(bytesReceived: Long, bytesTotal: Long)

    public fun onTextReceived(text: String)
}

public class OutgoingFile(
    public val name: String,
    public val size: Long,
    public val mimeType: String,
    public val open: () -> InputStream,
)

public interface OutboundListener {
    public fun onPin(pin: String)

    public fun onAccepted()

    public fun onProgress(bytesSent: Long, bytesTotal: Long)
}
