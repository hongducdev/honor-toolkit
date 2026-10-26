package vn.hongducdev.honortoolkit.core.quickshare.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

class CryptoPrimitivesTest {
    @Test
    fun `hkdf matches RFC 5869 test case 1`() {
        val okm = hkdfSha256(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm.toHex(),
        )
    }

    @Test
    fun `hkdf matches RFC 5869 test case 3 with empty salt and info`() {
        val okm = hkdfSha256(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = ByteArray(0),
            info = ByteArray(0),
            length = 42,
        )
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
            okm.toHex(),
        )
    }

    @Test
    fun `D2D salt is the SHA-256 of D2D as documented`() {
        assertEquals(
            "82aa55a0d397f88346ca1cee8d3909b95f13fa7deb1d4ab38376b8256da85510",
            sha256("D2D".toByteArray()).toHex(),
        )
        assertEquals(
            "bf9d2a53c63616d75db0a7165b91c1ef73e537f2427405fa23610a4be657642e",
            sha256("SecureMessage".toByteArray()).toHex(),
        )
    }

    @Test
    fun `pin treats auth bytes as signed and pads to four digits`() {
        assertEquals("0000", pinFromAuthString(ByteArray(32)))
        assertEquals("0001", pinFromAuthString(byteArrayOf(1)))
        assertEquals("0001", pinFromAuthString(byteArrayOf(-1)))
    }

    @Test
    fun `client and server derive mirrored keys`() {
        val clientInit = byteArrayOf(1, 2, 3)
        val serverInit = byteArrayOf(4, 5, 6)
        val server = deriveHandshakeResult(byteArrayOf(9, 9), clientInit, serverInit, isServer = true)
        val client = deriveHandshakeResult(byteArrayOf(9, 9), clientInit, serverInit, isServer = false)
        assertArrayEquals(server.keys.encryptKey, client.keys.decryptKey)
        assertArrayEquals(server.keys.sendHmacKey, client.keys.recvHmacKey)
        assertArrayEquals(server.keys.decryptKey, client.keys.encryptKey)
        assertArrayEquals(server.keys.recvHmacKey, client.keys.sendHmacKey)
        assertEquals(server.pin, client.pin)
    }

    @Test
    fun `ecdh agrees on both sides and survives a key round trip`() {
        val a = generateP256KeyPair()
        val b = generateP256KeyPair()
        val bPublic = decodeP256PublicKey(encodeP256PublicKey(b.public))
        val aPublic = decodeP256PublicKey(encodeP256PublicKey(a.public))
        assertArrayEquals(ecdhSharedX(a.private, bPublic), ecdhSharedX(b.private, aPublic))
    }

    @Test(expected = vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException::class)
    fun `a point that is not on the curve is rejected`() {
        val valid = generateP256KeyPair()
        val encoded = com.google.security.cryptauth.lib.securemessage.SecureMessageProto.GenericPublicKey
            .parseFrom(encodeP256PublicKey(valid.public))
        val tampered = encoded.toBuilder().setEcP256PublicKey(
            encoded.ecP256PublicKey.toBuilder().setY(
                com.google.protobuf.ByteString.copyFrom(BigInteger.valueOf(7).toByteArray()),
            ),
        ).build()
        decodeP256PublicKey(tampered.toByteArray())
    }
}
