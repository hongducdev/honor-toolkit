package vn.hongducdev.honortoolkit.core.quickshare.connection

import com.google.android.gms.nearby.sharing.Protocol.ConnectionResponseFrame as SharingResponseFrame
import com.google.android.gms.nearby.sharing.Protocol.Frame as SharingFrame
import com.google.android.gms.nearby.sharing.Protocol.IntroductionFrame
import com.google.android.gms.nearby.sharing.Protocol.PairedKeyEncryptionFrame
import com.google.android.gms.nearby.sharing.Protocol.PairedKeyResultFrame
import com.google.android.gms.nearby.sharing.Protocol.V1Frame as SharingV1Frame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.ConnectionRequestFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.ConnectionResponseFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.DisconnectionFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.KeepAliveFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OfflineFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.OsInfo
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.V1Frame
import com.google.protobuf.ByteString
import java.security.SecureRandom

internal const val KEEP_ALIVE_INTERVAL_MILLIS = 10_000
internal const val KEEP_ALIVE_TIMEOUT_MILLIS = 30_000
internal const val LAST_CHUNK_FLAG = 1

private val random = SecureRandom()

internal fun randomBytes(length: Int): ByteArray = ByteArray(length).also(random::nextBytes)

internal fun randomPayloadId(): Long = random.nextLong()

private fun offline(type: V1Frame.FrameType, fill: V1Frame.Builder.() -> Unit): OfflineFrame =
    OfflineFrame.newBuilder()
        .setVersion(OfflineFrame.Version.V1)
        .setV1(V1Frame.newBuilder().setType(type).apply(fill))
        .build()

internal fun connectionRequestFrame(endpointId: String, endpointName: String, endpointInfo: ByteArray): OfflineFrame =
    offline(V1Frame.FrameType.CONNECTION_REQUEST) {
        connectionRequest = ConnectionRequestFrame.newBuilder()
            .setEndpointId(endpointId)
            .setEndpointName(endpointName)
            .setEndpointInfo(ByteString.copyFrom(endpointInfo))
            .addMediums(ConnectionRequestFrame.Medium.WIFI_LAN)
            .setKeepAliveIntervalMillis(KEEP_ALIVE_INTERVAL_MILLIS)
            .setKeepAliveTimeoutMillis(KEEP_ALIVE_TIMEOUT_MILLIS)
            .build()
    }

/**
 * Samsung One UI drops the link unless the OS is Android and the multiplex and safe-disconnect fields are present.
 * The deprecated `status` is still set because older receivers read it.
 */
@Suppress("DEPRECATION")
internal fun connectionResponseFrame(): OfflineFrame =
    offline(V1Frame.FrameType.CONNECTION_RESPONSE) {
        connectionResponse = ConnectionResponseFrame.newBuilder()
            .setStatus(0)
            .setResponse(ConnectionResponseFrame.ResponseStatus.ACCEPT)
            .setOsInfo(OsInfo.newBuilder().setType(OsInfo.OsType.ANDROID))
            .setMultiplexSocketBitmask(0)
            .setSafeToDisconnectVersion(1)
            .build()
    }

internal fun keepAliveFrame(ack: Boolean): OfflineFrame =
    offline(V1Frame.FrameType.KEEP_ALIVE) { keepAlive = KeepAliveFrame.newBuilder().setAck(ack).build() }

internal fun disconnectionFrame(requestSafe: Boolean = false, ackSafe: Boolean = false): OfflineFrame =
    offline(V1Frame.FrameType.DISCONNECTION) {
        disconnection = DisconnectionFrame.newBuilder()
            .setRequestSafeToDisconnect(requestSafe)
            .setAckSafeToDisconnect(ackSafe)
            .build()
    }

internal fun payloadFrame(
    id: Long,
    type: PayloadTransferFrame.PayloadHeader.PayloadType,
    totalSize: Long,
    offset: Long,
    body: ByteArray?,
    last: Boolean,
): OfflineFrame = offline(V1Frame.FrameType.PAYLOAD_TRANSFER) {
    val chunk = PayloadTransferFrame.PayloadChunk.newBuilder()
        .setFlags(if (last) LAST_CHUNK_FLAG else 0)
        .setOffset(offset)
    if (body != null) chunk.body = ByteString.copyFrom(body)
    payloadTransfer = PayloadTransferFrame.newBuilder()
        .setPacketType(PayloadTransferFrame.PacketType.DATA)
        .setPayloadHeader(
            PayloadTransferFrame.PayloadHeader.newBuilder().setId(id).setType(type).setTotalSize(totalSize).setIsSensitive(false),
        )
        .setPayloadChunk(chunk)
        .build()
}

private fun sharing(type: SharingV1Frame.FrameType, fill: SharingV1Frame.Builder.() -> Unit): SharingFrame =
    SharingFrame.newBuilder()
        .setVersion(SharingFrame.Version.V1)
        .setV1(SharingV1Frame.newBuilder().setType(type).apply(fill))
        .build()

/** The signed data and hash are placeholders: verifying contacts needs Google's servers, and peers accept random values. */
internal fun pairedKeyEncryptionFrame(): SharingFrame =
    sharing(SharingV1Frame.FrameType.PAIRED_KEY_ENCRYPTION) {
        pairedKeyEncryption = PairedKeyEncryptionFrame.newBuilder()
            .setSecretIdHash(ByteString.copyFrom(randomBytes(6)))
            .setSignedData(ByteString.copyFrom(randomBytes(72)))
            .build()
    }

internal fun pairedKeyResultFrame(): SharingFrame =
    sharing(SharingV1Frame.FrameType.PAIRED_KEY_RESULT) {
        pairedKeyResult = PairedKeyResultFrame.newBuilder().setStatus(PairedKeyResultFrame.Status.UNABLE).build()
    }

internal fun introductionFrame(introduction: IntroductionFrame): SharingFrame =
    sharing(SharingV1Frame.FrameType.INTRODUCTION) { setIntroduction(introduction) }

internal fun responseFrame(status: SharingResponseFrame.Status): SharingFrame =
    sharing(SharingV1Frame.FrameType.RESPONSE) {
        connectionResponse = SharingResponseFrame.newBuilder().setStatus(status).build()
    }

internal fun cancelFrame(): SharingFrame = sharing(SharingV1Frame.FrameType.CANCEL) {}
