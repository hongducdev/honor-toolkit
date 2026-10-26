package vn.hongducdev.honortoolkit.core.quickshare.transport

import vn.hongducdev.honortoolkit.core.quickshare.NearbyProtocolException
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

internal const val MAX_FRAME_LENGTH = 5 * 1024 * 1024

/** Messages are prefixed with a 4-byte big-endian length. */
internal class FramedConnection(input: InputStream, private val output: OutputStream) {
    private val input = DataInputStream(input)
    private val writeLock = Any()

    /** Returns null when the peer closed the stream cleanly between two frames. */
    fun readFrame(): ByteArray? {
        val first = input.read()
        if (first < 0) return null
        val length = (first shl 24) or (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
        if (length < 0 || length >= MAX_FRAME_LENGTH) throw NearbyProtocolException("Unexpected frame length $length")
        val frame = ByteArray(length)
        try {
            input.readFully(frame)
        } catch (e: EOFException) {
            throw NearbyProtocolException("Connection closed in the middle of a frame", e)
        }
        return frame
    }

    fun writeFrame(frame: ByteArray) {
        synchronized(writeLock) {
            val length = frame.size
            output.write(
                byteArrayOf((length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()),
            )
            output.write(frame)
            output.flush()
        }
    }
}
