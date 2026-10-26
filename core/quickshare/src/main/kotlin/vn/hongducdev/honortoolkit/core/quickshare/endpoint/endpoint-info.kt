package vn.hongducdev.honortoolkit.core.quickshare.endpoint

import java.security.SecureRandom
import java.util.Base64

public enum class DeviceType(internal val code: Int) {
    UNKNOWN(0),
    PHONE(1),
    TABLET(2),
    LAPTOP(3),
    ;

    internal companion object {
        fun fromCode(code: Int): DeviceType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** What a peer announces about itself, both in the mDNS `n` record and in the connection request. */
public class EndpointInfo(
    /** Null when the peer is hidden and sends no plain-text name. */
    public val name: String?,
    public val deviceType: DeviceType,
) {
    public fun serialize(): ByteArray {
        val nameBytes = name?.toByteArray(Charsets.UTF_8)?.take(MAX_NAME_BYTES)?.toByteArray()
        val hiddenBit = if (nameBytes == null) HIDDEN_BIT else 0
        val header = (deviceType.code shl DEVICE_TYPE_SHIFT) or hiddenBit
        val metadataKey = ByteArray(METADATA_LENGTH).also(random::nextBytes)
        val out = byteArrayOf(header.toByte()) + metadataKey
        return if (nameBytes == null) out else out + nameBytes.size.toByte() + nameBytes
    }

    public companion object {
        private const val HIDDEN_BIT = 0x10
        private const val DEVICE_TYPE_SHIFT = 1
        private const val DEVICE_TYPE_MASK = 0x7
        private const val METADATA_LENGTH = 16
        private const val NAME_LENGTH_OFFSET = 1 + METADATA_LENGTH
        private const val MAX_NAME_BYTES = 255
        private val random = SecureRandom()

        public fun parse(data: ByteArray): EndpointInfo? {
            if (data.size < NAME_LENGTH_OFFSET) return null
            val header = data[0].toInt() and 0xFF
            val type = DeviceType.fromCode((header shr DEVICE_TYPE_SHIFT) and DEVICE_TYPE_MASK)
            if (header and HIDDEN_BIT != 0) return EndpointInfo(null, type)
            if (data.size == NAME_LENGTH_OFFSET) return null
            val nameLength = data[NAME_LENGTH_OFFSET].toInt() and 0xFF
            val nameEnd = NAME_LENGTH_OFFSET + 1 + nameLength
            if (data.size < nameEnd) return null
            val name = String(data, NAME_LENGTH_OFFSET + 1, nameLength, Charsets.UTF_8)
            return EndpointInfo(name, type)
        }
    }
}

public object EndpointNaming {
    private const val ENDPOINT_ID_LENGTH = 4
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val PCP = 0x23
    private val serviceId = byteArrayOf(0xFC.toByte(), 0x9F.toByte(), 0x5E)

    /** mDNS service type that Quick Share senders browse for. */
    public const val MDNS_SERVICE_TYPE: String = "_FC9F5ED42C8A._tcp"

    /** Key of the TXT record that carries the base64 endpoint info. */
    public const val MDNS_INFO_KEY: String = "n"

    public fun newEndpointId(random: SecureRandom = SecureRandom()): String =
        String(CharArray(ENDPOINT_ID_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** The mDNS instance name: PCP byte, endpoint id, service id and two zero bytes, URL-safe base64. */
    public fun mdnsServiceName(endpointId: String): String {
        require(endpointId.length == ENDPOINT_ID_LENGTH) { "Endpoint id must be $ENDPOINT_ID_LENGTH characters" }
        val raw = byteArrayOf(PCP.toByte()) + endpointId.toByteArray(Charsets.US_ASCII) + serviceId + byteArrayOf(0, 0)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    public fun endpointInfoRecord(info: EndpointInfo): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(info.serialize())
}
